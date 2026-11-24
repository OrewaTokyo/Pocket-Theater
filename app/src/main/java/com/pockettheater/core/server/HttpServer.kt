package com.pockettheater.core.server

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.pockettheater.core.security.Auth
import com.pockettheater.core.security.LoginThrottle
import com.pockettheater.core.security.PathGuard
import com.pockettheater.core.security.SessionStore
import com.pockettheater.core.storage.Account
import com.pockettheater.core.storage.AccountStatus
import com.pockettheater.core.storage.AppSettings
import com.pockettheater.core.storage.DocumentBrowser
import com.pockettheater.core.storage.FolderStats
import com.pockettheater.core.storage.ShareStore
import com.pockettheater.core.storage.Subtitles
import com.pockettheater.core.storage.UserStore
import com.pockettheater.util.CrashLog
import com.pockettheater.util.DeviceLabel
import com.pockettheater.util.DeviceStats
import com.pockettheater.util.Format
import com.pockettheater.util.QrImage
import java.io.ByteArrayInputStream
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import fi.iki.elonen.NanoHTTPD.newFixedLengthResponse
import org.json.JSONArray
import org.json.JSONObject

/**
 * The server your laptop talks to. Shared folders are read-only. People can ask to join, sign in and change their own
 * password. Approving people, resetting passwords and similar admin work happens on the phone, or on the admin website
 * that the admin opens on one browser with a QR code scan and the admin password (see [SessionStore]).
 */
class PocketServer(
    context: Context,
    port: Int,
    private val secure: Boolean,
    private val onSignupRequest: (String) -> Unit,
) : NanoHTTPD(port) {
    private val appContext = context.applicationContext
    private val store = ShareStore(appContext)
    private val users = UserStore(appContext)
    private val settings = AppSettings(appContext)
    private val sessions = SessionStore.shared
    private val deviceStats = DeviceStats(appContext)
    private val loginThrottle = LoginThrottle(8, 10 * 60_000L)
    private val signupThrottle = LoginThrottle(5, 10 * 60_000L)
    private val browser = DocumentBrowser(appContext.contentResolver)
    private val assets = StaticAssets(appContext)

    override fun serve(session: IHTTPSession): Response {
        val response = try {
            route(session)
        } catch (e: SecurityException) {
            jsonError(Status.FORBIDDEN, "The phone no longer has access to that folder. Add it again in Admin tools.")
        } catch (e: Exception) {
            CrashLog.record(appContext, "serving " + session.uri, e)
            jsonError(Status.INTERNAL_ERROR, "Something went wrong on the phone. Check that it still has access to the folder.")
        }
        response.addHeader("X-Content-Type-Options", "nosniff")
        response.addHeader("Referrer-Policy", "no-referrer")
        response.addHeader("X-Frame-Options", "SAMEORIGIN") // the viewer shows PDFs in a frame from this same site
        // Pages and scripts may only load things from this site. Shared files are left alone so PDFs and media still work.
        if (session.uri != "/file") response.addHeader("Content-Security-Policy", PAGE_POLICY)
        return response
    }

    private fun route(session: IHTTPSession): Response {
        val uri = session.uri
        // The website's "are you there?" check and the later pieces of a video are not counted as requests.
        val range = session.headers["range"]
        val seekChunk = uri == "/file" && range != null && !range.startsWith("bytes=0-")
        if (uri != "/api/me" && !seekChunk) ServerStats.request()
        if (session.method == Method.POST && uri.startsWith("/api/")) return post(session, uri)
        if (session.method != Method.GET && session.method != Method.HEAD) {
            return jsonError(Status.METHOD_NOT_ALLOWED, "Not allowed")
        }
        val user = currentUser(session)
        if (user != null) ServerStats.visit(user.name)
        return when {
            uri == "/api/me" -> if (user == null) signedOut() else me(session, user)
            uri == "/api/pair/qr" -> if (user == null) signedOut() else pairingQr(session)
            uri.startsWith("/api/admin/") -> adminGet(session, uri, user)
            uri == "/api/libraries" -> if (user == null) signedOut() else libraries()
            uri == "/api/list" -> if (user == null) signedOut() else list(session)
            uri == "/file" -> if (user == null) signedOut() else file(session)
            uri == "/subtitle" -> if (user == null) signedOut() else subtitle(session)
            uri == "/" || uri == "/index.html" -> if (user == null) redirect("/login.html") else assets.serve(uri)
            uri == "/login.html" && user != null -> redirect("/")
            uri.startsWith("/api/") -> jsonError(Status.NOT_FOUND, "Not found")
            else -> assets.serve(uri) // the site's own pages, styles and scripts hold nothing private
        }
    }

    private fun signedOut(): Response = jsonError(Status.UNAUTHORIZED, "Sign in first.")

    // ---- admin website ----

    /** Who is asking, whether this browser has been opened as admin, and a pairing code if the phone started one. */
    private fun me(session: IHTTPSession, user: Account): Response {
        val token = cookie(session, Auth.COOKIE)
        val body = JSONObject().put("username", user.name)
        val adminOn = settings.adminWebEnabled && secure
        // Asking who you are must not keep the admin session alive, only real admin actions do.
        body.put("admin", adminOn && sessions.isAdmin(token, false))
        val pair = if (adminOn) sessions.pairing(token) else null
        if (pair != null) {
            val left = ((pair.second - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
            body.put("pair", JSONObject().put("code", pair.first).put("expires_in", left))
        }
        return jsonResponse(Status.OK, body)
    }

    /** The QR picture for this browser's own pairing code. The phone scans it. */
    private fun pairingQr(session: IHTTPSession): Response {
        val pair = (if (settings.adminWebEnabled && secure) sessions.pairing(cookie(session, Auth.COOKIE)) else null)
            ?: return jsonError(Status.NOT_FOUND, "Nothing to pair.")
        val bytes = QrImage.png("PT:" + pair.first, 420)
        return newFixedLengthResponse(Status.OK, "image/png", ByteArrayInputStream(bytes), bytes.size.toLong())
            .also { it.addHeader("Cache-Control", "no-store") }
    }

    /** Null when this browser may use the admin website, otherwise the error to send back. */
    private fun adminDenied(session: IHTTPSession, user: Account?): Response? {
        if (user == null) return signedOut()
        if (!secure) return jsonError(Status.FORBIDDEN, "The admin website only works over HTTPS.")
        if (!settings.adminWebEnabled || !sessions.isAdmin(cookie(session, Auth.COOKIE), true)) {
            return jsonError(Status.FORBIDDEN, "Admin access is not open on this device. Start it again from the phone.")
        }
        return null
    }

    private fun adminGet(session: IHTTPSession, uri: String, user: Account?): Response {
        adminDenied(session, user)?.let { return it }
        return when (uri) {
            "/api/admin/overview" -> adminOverview()
            "/api/admin/users" -> adminUsers()
            "/api/admin/devices" -> adminDevices(session)
            else -> jsonError(Status.NOT_FOUND, "Not found")
        }
    }

    private fun adminOverview(): Response {
        val stats = ServerStats.snapshot(appContext)
        val device = deviceStats.read()
        val body = JSONObject()
            .put("awake", Format.duration(stats.awakeMs))
            .put("awake_total", Format.duration(stats.lifetimeAwakeMs))
            .put("members", users.members().size)
            .put("waiting", users.pending().size)
            .put("online", stats.online)
            .put("data_total", Format.bytes(stats.lifetimeBytes))
            .put("data_session", Format.bytes(stats.sessionBytes))
            .put("requests_total", Format.count(stats.lifetimeRequests))
            .put("requests_session", Format.count(stats.sessionRequests))
            .put("cpu", device.cpuPercent.toInt())
            .put("ram", device.ramPercent.toInt())
            .put("battery", device.batteryPercent)
            .put("charging", device.charging)
        return jsonResponse(Status.OK, body)
    }

    private fun adminUsers(): Response {
        val pending = org.json.JSONArray()
        users.pending().forEach { pending.put(JSONObject().put("name", it.name).put("created", it.created)) }
        val members = org.json.JSONArray()
        users.members().forEach {
            members.put(JSONObject().put("name", it.name).put("status", if (it.status == AccountStatus.OFF) "off" else "active").put("created", it.created))
        }
        return jsonResponse(Status.OK, JSONObject().put("pending", pending).put("members", members))
    }

    private fun adminDevices(session: IHTTPSession): Response {
        val mine = sessions.deviceId(cookie(session, Auth.COOKIE))
        val list = org.json.JSONArray()
        sessions.devices().forEach {
            list.put(
                JSONObject().put("id", it.id).put("user", it.user).put("label", it.label).put("ip", it.ip)
                    .put("since", it.since).put("admin", it.admin).put("you", it.id == mine)
            )
        }
        return jsonResponse(Status.OK, JSONObject().put("devices", list))
    }

    private fun adminPost(session: IHTTPSession, uri: String, json: JSONObject): Response {
        val me = currentUser(session)
        adminDenied(session, me)?.let { return it }
        val token = cookie(session, Auth.COOKIE)
        when (uri) {
            "/api/admin/end" -> {
                sessions.deviceId(token)?.let { sessions.revokeAdmin(it) }
                return jsonResponse(Status.OK, JSONObject().put("ok", true))
            }
            "/api/admin/device" -> {
                val id = json.optString("id")
                when (json.optString("action")) {
                    "signout" -> sessions.endDevice(id)
                    "revoke" -> sessions.revokeAdmin(id)
                    else -> return jsonError(Status.BAD_REQUEST, "Unknown action.")
                }
                return jsonResponse(Status.OK, JSONObject().put("ok", true))
            }
            "/api/admin/user" -> {
                val name = json.optString("name").trim().lowercase()
                val target = users.find(name) ?: return jsonError(Status.NOT_FOUND, "No such account.")
                if (me != null && target.name == me.name) {
                    return jsonError(Status.BAD_REQUEST, "Use the phone to change your own account.")
                }
                val body = JSONObject().put("ok", true)
                when (json.optString("action")) {
                    "approve" -> users.approve(name)
                    "decline" -> users.decline(name)
                    "off" -> users.setOff(name, true)
                    "on" -> users.setOff(name, false)
                    "delete" -> users.delete(name)
                    "reset" -> body.put("password", users.resetPassword(name))
                    else -> return jsonError(Status.BAD_REQUEST, "Unknown action.")
                }
                return jsonResponse(Status.OK, body)
            }
        }
        return jsonError(Status.NOT_FOUND, "Not found")
    }

    // ---- accounts ----

    private fun post(session: IHTTPSession, uri: String): Response {
        val type = session.headers["content-type"].orEmpty()
        if (!type.startsWith("application/json")) return jsonError(Http.UNSUPPORTED_MEDIA, "Send JSON.")
        val length = session.headers["content-length"]?.toIntOrNull() ?: 0
        if (length > MAX_BODY) return jsonError(Http.PAYLOAD_TOO_LARGE, "That request is too big.")
        val json = try {
            val files = HashMap<String, String>()
            session.parseBody(files)
            JSONObject(files["postData"] ?: "{}")
        } catch (e: Exception) {
            return jsonError(Status.BAD_REQUEST, "That request could not be read.")
        }
        if (uri.startsWith("/api/admin/")) return adminPost(session, uri, json)
        return when (uri) {
            "/api/login" -> login(session, json)
            "/api/register" -> register(session, json)
            "/api/logout" -> logout(session)
            "/api/password" -> changePassword(session, json)
            else -> jsonError(Status.NOT_FOUND, "Not found")
        }
    }

    private fun register(session: IHTTPSession, json: JSONObject): Response {
        if (!settings.signupsOpen) {
            return jsonError(Status.FORBIDDEN, "New sign-ups are closed right now. Ask the admin.")
        }
        val ip = clientIp(session)
        if (signupThrottle.blocked(ip)) {
            return jsonError(Http.TOO_MANY, "Too many requests from this device. Try again in a few minutes.")
        }
        signupThrottle.hit(ip)
        val name = json.optString("username").trim().lowercase()
        val password = json.optString("password")
        if (!Auth.validUsername(name)) return jsonError(Status.BAD_REQUEST, Auth.USERNAME_RULE)
        Auth.passwordProblem(password)?.let { return jsonError(Status.BAD_REQUEST, it) }
        if (password.lowercase() == name) return jsonError(Status.BAD_REQUEST, "The password can't be your username.")
        if (!json.optBoolean("agree", false)) {
            return jsonError(Status.BAD_REQUEST, "Please accept the Terms of Use and the Privacy Policy.")
        }
        users.register(name, password, System.currentTimeMillis())?.let { return jsonError(Status.BAD_REQUEST, it) }
        onSignupRequest(name)
        return jsonResponse(Status.OK, JSONObject().put("ok", true))
    }

    private fun login(session: IHTTPSession, json: JSONObject): Response {
        val ip = clientIp(session)
        if (loginThrottle.blocked(ip)) {
            return jsonError(Http.TOO_MANY, "Too many tries. Wait a few minutes and try again.")
        }
        val account = users.check(json.optString("username").trim().lowercase(), json.optString("password"))
        if (account == null) {
            loginThrottle.hit(ip)
            Thread.sleep(300)
            return jsonError(Status.UNAUTHORIZED, "Wrong username or password.")
        }
        // The password was right, so it is safe to say why the account cannot be used yet.
        when (account.status) {
            AccountStatus.PENDING ->
                return jsonError(Status.FORBIDDEN, "Your request is still waiting for the admin to approve it.")
            AccountStatus.OFF ->
                return jsonError(Status.FORBIDDEN, "This account is turned off. Ask the admin.")
            AccountStatus.ACTIVE -> Unit
        }
        loginThrottle.clear(ip)
        return signedIn(session, account)
    }

    private fun logout(session: IHTTPSession): Response {
        sessions.end(cookie(session, Auth.COOKIE))
        return jsonResponse(Status.OK, JSONObject().put("ok", true))
            .also { it.addHeader("Set-Cookie", sessionCookie("", 0)) }
    }

    private fun changePassword(session: IHTTPSession, json: JSONObject): Response {
        val me = currentUser(session) ?: return signedOut()
        val ip = clientIp(session)
        if (loginThrottle.blocked(ip)) {
            return jsonError(Http.TOO_MANY, "Too many tries. Wait a few minutes and try again.")
        }
        if (users.check(me.name, json.optString("current")) == null) {
            loginThrottle.hit(ip)
            Thread.sleep(300)
            return jsonError(Status.FORBIDDEN, "Your current password is wrong.")
        }
        val next = json.optString("next")
        Auth.passwordProblem(next)?.let { return jsonError(Status.BAD_REQUEST, it) }
        users.setPassword(me.name, next)
        sessions.end(cookie(session, Auth.COOKIE))
        val fresh = users.find(me.name) ?: return signedOut()
        return signedIn(session, fresh)
    }

    private fun signedIn(session: IHTTPSession, account: Account): Response {
        val token = sessions.start(account.name, account.version, clientIp(session), DeviceLabel.from(session.headers["user-agent"]))
        return jsonResponse(Status.OK, JSONObject().put("username", account.name))
            .also { it.addHeader("Set-Cookie", sessionCookie(token, Auth.SESSION_SECONDS)) }
    }

    /** The person behind this request, or null if not signed in, signed out by the admin, or turned off. */
    private fun currentUser(session: IHTTPSession): Account? {
        val (name, version) = sessions.lookup(cookie(session, Auth.COOKIE)) ?: return null
        val account = users.find(name) ?: return null
        return if (account.status == AccountStatus.ACTIVE && account.version == version) account else null
    }

    private fun cookie(session: IHTTPSession, name: String): String? =
        session.headers["cookie"]?.split(';')?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')

    private fun sessionCookie(token: String, seconds: Long): String =
        "${Auth.COOKIE}=$token; Path=/; Max-Age=$seconds; HttpOnly; SameSite=Strict" + if (secure) "; Secure" else ""

    private fun clientIp(session: IHTTPSession): String = session.headers["remote-addr"] ?: "unknown"

    // ---- folders (read-only) ----

    private fun IHTTPSession.param(name: String): String? = parameters[name]?.firstOrNull()

    private fun libraries(): Response {
        val arr = JSONArray()
        store.libraries().forEach {
            // A folder with nothing in it, or one the phone can no longer read, stays off the website. So does a shelf with no folders left.
            val summary = FolderStats.get(browser, it.uri)
            if (summary.visible) {
                arr.put(JSONObject().put("id", it.id).put("name", it.name).put("category", it.category).put("items", summary.items))
            }
        }
        return jsonResponse(Status.OK, JSONObject().put("libraries", arr))
    }

    private fun list(session: IHTTPSession): Response {
        val lib = store.find(session.param("lib")) ?: return jsonError(Status.NOT_FOUND, "Unknown folder")
        val segments = PathGuard.segments(session.param("path")) ?: return jsonError(Status.BAD_REQUEST, "Invalid path")
        val tree = Uri.parse(lib.uri)
        val target = browser.resolve(tree, segments) ?: return jsonError(Status.NOT_FOUND, "Folder not found")
        if (!target.isDir) return jsonError(Status.BAD_REQUEST, "Not a folder")

        val all = browser.children(tree, target.docId).filter { !it.name.startsWith(".") }

        // Subtitles sit next to the video, or in a Subs or Subtitles folder beside it.
        class Sub(val name: String, val folder: List<String>)
        val subs = ArrayList<Sub>()
        all.filter { !it.isDir && Subtitles.isSubtitle(it.name) }.forEach { subs.add(Sub(it.name, segments)) }
        val subFolders = HashSet<String>()
        all.filter { it.isDir && it.name.lowercase() in Subtitles.FOLDER_NAMES }.forEach { dir ->
            val inside = browser.children(tree, dir.docId).filter { !it.name.startsWith(".") }
            inside.filter { !it.isDir && Subtitles.isSubtitle(it.name) }.forEach { subs.add(Sub(it.name, segments + dir.name)) }
            // A folder that holds nothing but subtitles is not a place to browse, so it is left out of the list.
            if (inside.all { !it.isDir && Subtitles.isSubtitle(it.name) }) subFolders.add(dir.name)
        }

        val entries = JSONArray()
        all.filter { !(it.isDir && it.name in subFolders) && (it.isDir || !Subtitles.isSubtitle(it.name)) }.forEach {
            val item = JSONObject().put("name", it.name).put("dir", it.isDir)
                .put("size", it.size).put("mime", it.mime).put("modified", it.modified)
            if (!it.isDir && FolderStats.isVideo(it.name, it.mime)) {
                val found = JSONArray()
                subs.filter { s -> Subtitles.belongsTo(it.name, s.name) }.forEach { s ->
                    found.put(
                        JSONObject().put("label", Subtitles.label(it.name, s.name))
                            .put("lang", Subtitles.language(it.name, s.name))
                            .put("path", (s.folder + s.name).joinToString("/"))
                    )
                }
                if (found.length() > 0) item.put("subs", found)
            }
            entries.put(item)
        }
        return jsonResponse(
            Status.OK,
            JSONObject()
                .put("library", JSONObject().put("id", lib.id).put("name", lib.name).put("category", lib.category))
                .put("path", JSONArray(segments))
                .put("entries", entries)
        )
    }

    /** A subtitle file as WebVTT, which is the only kind a browser reads. */
    private fun subtitle(session: IHTTPSession): Response {
        val lib = store.find(session.param("lib")) ?: return jsonError(Status.NOT_FOUND, "Unknown folder")
        val segments = PathGuard.segments(session.param("path")) ?: return jsonError(Status.BAD_REQUEST, "Invalid path")
        if (segments.isEmpty() || segments.any { it.startsWith(".") }) return jsonError(Status.NOT_FOUND, "File not found")
        val tree = Uri.parse(lib.uri)
        val target = browser.resolve(tree, segments) ?: return jsonError(Status.NOT_FOUND, "File not found")
        if (target.isDir || !Subtitles.isSubtitle(target.name)) return jsonError(Status.BAD_REQUEST, "Not a subtitle file")
        if (target.size > Subtitles.MAX_BYTES) return jsonError(Http.PAYLOAD_TOO_LARGE, "That subtitle file is too big")

        val bytes = appContext.contentResolver.openInputStream(browser.documentUri(tree, target.docId))?.use { it.readBytes() }
            ?: return jsonError(Status.NOT_FOUND, "File not found")
        val vtt = Subtitles.toVtt(Subtitles.decode(bytes), target.name.substringAfterLast('.', ""))
        ServerStats.bytes(vtt.length.toLong())
        return newFixedLengthResponse(Status.OK, "text/vtt; charset=utf-8", vtt)
            .also { it.addHeader("Cache-Control", "private, max-age=300") }
    }

    private fun file(session: IHTTPSession): Response {
        val lib = store.find(session.param("lib")) ?: return jsonError(Status.NOT_FOUND, "Unknown folder")
        val segments = PathGuard.segments(session.param("path")) ?: return jsonError(Status.BAD_REQUEST, "Invalid path")
        if (segments.isEmpty()) return jsonError(Status.BAD_REQUEST, "No file given")
        val tree = Uri.parse(lib.uri)
        if (segments.any { it.startsWith(".") }) return jsonError(Status.NOT_FOUND, "File not found") // hidden files stay hidden
        val target = browser.resolve(tree, segments) ?: return jsonError(Status.NOT_FOUND, "File not found")
        if (target.isDir) return jsonError(Status.BAD_REQUEST, "That is a folder")

        val ext = target.name.substringAfterLast('.', "").lowercase()
        var mime = target.mime
        if (mime.isBlank() || mime == "application/octet-stream") {
            mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        }

        // A web page or script inside a shared folder must never run on this site, so it is only ever offered as a download.
        val type = mime.substringBefore(';').trim().lowercase()
        val runnable = type in RUNNABLE_TYPES || ext in RUNNABLE_EXTENSIONS
        val picture = type == "image/svg+xml" || ext == "svg" || ext == "svgz"
        if (runnable) mime = "application/octet-stream"

        val response = RangeHandler.serve(
            appContext.contentResolver,
            browser.documentUri(tree, target.docId),
            mime,
            session.headers["range"]
        )
        if (response.status == Status.NOT_FOUND) browser.forget(tree, segments)
        if (runnable) response.addHeader("Content-Disposition", "attachment")
        // An SVG may show as a picture, but opened on its own it gets no scripts and no outside content.
        if (picture && !runnable) response.addHeader("Content-Security-Policy", "sandbox; default-src 'none'; style-src 'unsafe-inline'")
        return response
    }

    companion object {
        private const val MAX_BODY = 4096
        private const val PAGE_POLICY = "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; " +
            "frame-src 'self'; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'self'"
        private val RUNNABLE_TYPES = setOf(
            "text/html", "application/xhtml+xml", "text/xml", "application/xml", "text/javascript",
            "application/javascript", "application/x-javascript", "multipart/related", "message/rfc822",
        )
        private val RUNNABLE_EXTENSIONS = setOf(
            "html", "htm", "xhtml", "shtml", "xml", "xsl", "xslt", "js", "mjs", "hta", "mht", "mhtml",
        )
    }
}
