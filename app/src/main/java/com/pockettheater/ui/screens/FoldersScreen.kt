package com.pockettheater.ui.screens

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.pockettheater.R
import com.pockettheater.core.storage.DocumentBrowser
import com.pockettheater.core.storage.FolderStats
import com.pockettheater.core.storage.ShareStore
import com.pockettheater.core.storage.SharedLibrary
import com.pockettheater.ui.components.MATCH
import com.pockettheater.ui.components.WRAP
import com.pockettheater.ui.components.actionButton
import com.pockettheater.ui.components.card
import com.pockettheater.ui.components.dp
import com.pockettheater.ui.components.glassDrawable
import com.pockettheater.ui.components.gradientFill
import com.pockettheater.ui.components.iconBadgeRes
import com.pockettheater.ui.components.lp
import com.pockettheater.ui.components.pillButton
import com.pockettheater.ui.components.sectionLabel
import com.pockettheater.ui.components.textView
import com.pockettheater.ui.theme.Theme
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * The shared folders, grouped by shelf. Each card shows what is inside and whether the folder is live on the website.
 * Folders that are empty (or that the phone can no longer read) are kept off the website, and so are shelves with no folders.
 */
class FoldersScreen(private val activity: Activity, private val onAddFolder: () -> Unit) : Screen {
    private val store = ShareStore(activity)
    private val browser = DocumentBrowser(activity.contentResolver)
    private val counter = Executors.newSingleThreadExecutor { task -> Thread(task, "pocket-theater-count").apply { isDaemon = true } }
    private val counting = HashSet<String>()
    private val countsDone = AtomicInteger(0)
    private var shownDone = -1
    private var shownSignature = ""
    private var filter: String? = null // null shows every shelf

    private val subtitle = activity.textView("", 14f, Theme.Muted)
    private val chipRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
    private val listBox = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

    override val view: View = build()

    private fun build(): View {
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(20), activity.dp(20), activity.dp(20), activity.dp(28))
        }
        val header = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(activity.textView("Folders", 30f, Theme.Ink, bold = true), activity.lp(0, WRAP, 1f))
        header.addView(activity.pillButton("+ Add folder", Theme.Marquee) { onAddFolder() })
        column.addView(header)
        column.addView(subtitle, activity.lp(MATCH, WRAP, top = 2))

        val chips = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipRow)
        }
        column.addView(chips, activity.lp(MATCH, WRAP, top = 16))
        column.addView(listBox, activity.lp(MATCH, WRAP, top = 4))
        return ScrollView(activity).apply { addView(column) }
    }

    /** Redraws the list now. Called after a folder was added, moved, renamed or removed. */
    fun reload() {
        shownSignature = ""
        refresh()
    }

    override fun refresh() {
        val libraries = store.libraries()
        // Count whatever has no recent count. The numbers arrive a moment later and the list redraws.
        libraries.forEach { lib ->
            if (!FolderStats.isFresh(FolderStats.cached(lib.uri))) startCount(lib.uri)
        }
        val signature = libraries.joinToString("|") { it.id + it.name + it.category } + filter
        if (signature != shownSignature || countsDone.get() != shownDone) {
            shownSignature = signature
            shownDone = countsDone.get()
            draw(libraries)
        }
    }

    private fun startCount(uri: String) {
        val started = synchronized(counting) { counting.add(uri) }
        if (!started) return
        counter.execute {
            try {
                FolderStats.compute(browser, uri)
            } finally {
                synchronized(counting) { counting.remove(uri) }
                countsDone.incrementAndGet()
            }
        }
    }

    // ---- drawing ----

    private fun draw(libraries: List<SharedLibrary>) {
        val live = libraries.count { FolderStats.cached(it.uri)?.visible == true }
        subtitle.text = when {
            libraries.isEmpty() -> "Nothing shared yet"
            else -> "${libraries.size} shared \u00B7 $live live on the website"
        }
        if (filter != null && libraries.none { it.category == filter }) filter = null

        chipRow.removeAllViews()
        chipRow.addView(chip("All", libraries.size, Theme.Marquee, filter == null) { filter = null; reload() })
        ShareStore.CATEGORIES.forEach { category ->
            val n = libraries.count { it.category == category }
            if (n > 0) {
                val label = Theme.categoryLabel(category).substringAfter(' ')
                chipRow.addView(chip(label, n, Theme.categoryColor(category), filter == category) { filter = category; reload() })
            }
        }

        listBox.removeAllViews()
        if (libraries.isEmpty()) {
            listBox.addView(emptyState(), activity.lp(MATCH, WRAP, top = 20))
            return
        }
        val shown = if (filter == null) libraries else libraries.filter { it.category == filter }
        ShareStore.CATEGORIES.forEach { category ->
            val inShelf = shown.filter { it.category == category }
            if (inShelf.isEmpty()) return@forEach
            if (filter == null) {
                listBox.addView(activity.sectionLabel(Theme.categoryLabel(category).substringAfter(' ') + " \u00B7 " + inShelf.size), activity.lp(WRAP, WRAP, top = 22, bottom = 10))
            }
            inShelf.forEach { listBox.addView(folderCard(it), activity.lp(MATCH, WRAP, top = if (filter == null) 0 else 12, bottom = 12)) }
        }
    }

    private fun chip(label: String, count: Int, color: Int, selected: Boolean, onClick: () -> Unit): View =
        activity.textView("$label  $count", 14f, if (selected) Theme.Midnight else Theme.Ink, bold = true).apply {
            setPadding(activity.dp(16), activity.dp(9), activity.dp(16), activity.dp(9))
            background = if (selected) activity.gradientFill(color, 20)
            else activity.glassDrawable(20, strokeColor = (color and 0x00FFFFFF) or 0xCC000000.toInt())
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = activity.lp(WRAP, WRAP, right = 8)
        }

    private fun folderCard(lib: SharedLibrary): View {
        val accent = Theme.categoryColor(lib.category)
        val summary = FolderStats.cached(lib.uri)
        val (status, statusColor) = when {
            summary == null -> "Checking\u2026" to Theme.Muted
            !summary.readable -> "Can't read this folder. Remove it and add it again." to Theme.Red
            summary.items == 0 -> "Hidden from the website while it is empty" to Theme.Marquee
            else -> "Live on the website" to Theme.Neon
        }

        val card = activity.card(0).apply {
            clipToOutline = true
            isClickable = true
            setOnClickListener { actions(lib) }
        }
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(
            View(activity).apply {
                // the colour bar down the side of a folder fades towards the bottom, like light on glass
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(accent, (accent and 0x00FFFFFF) or 0x55000000))
            },
            activity.lp(activity.dp(5), MATCH)
        )

        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(14), activity.dp(14), activity.dp(12), activity.dp(14))
        }
        body.addView(activity.iconBadgeRes(Theme.categoryIcon(lib.category), accent, 48), activity.lp(activity.dp(48), activity.dp(48), right = 14))

        val texts = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(activity.textView(lib.name, 17f, Theme.Ink, bold = true))
        texts.addView(activity.textView(if (summary == null) "Counting\u2026" else FolderStats.describe(summary), 14f, Theme.Muted))
        val state = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        state.addView(View(activity).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(statusColor) }
        }, activity.lp(activity.dp(8), activity.dp(8), right = 8))
        state.addView(activity.textView(status, 13f, statusColor, bold = true))
        texts.addView(state, activity.lp(MATCH, WRAP, top = 6))

        body.addView(texts, activity.lp(0, WRAP, 1f))
        body.addView(activity.textView("\u203A", 28f, Theme.Muted))
        row.addView(body, activity.lp(0, WRAP, 1f))
        card.addView(row)
        return card
    }

    private fun emptyState(): View {
        val c = activity.card(24)
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.addView(activity.iconBadgeRes(R.drawable.ic_tab_folders, Theme.Marquee, 72))
        c.addView(activity.textView("No folders yet", 22f, Theme.Ink, bold = true).apply { gravity = Gravity.CENTER }, activity.lp(MATCH, WRAP, top = 14))
        c.addView(
            activity.textView(
                "Share a folder from this phone, then say what is inside: Series, Anime, Movies, Gaming and so on. " +
                    "A shelf shows up on the website once a folder in it holds something.",
                15f, Theme.Muted
            ).apply { gravity = Gravity.CENTER },
            activity.lp(MATCH, WRAP, top = 6)
        )
        c.addView(activity.actionButton("Add folder", filled = true) { onAddFolder() }, activity.lp(MATCH, WRAP, top = 18))
        return c
    }

    // ---- what you can do with a folder ----

    private fun actions(lib: SharedLibrary) {
        AlertDialog.Builder(activity)
            .setTitle(lib.name)
            .setItems(arrayOf("Move to another shelf", "Rename", "Count again", "Stop sharing")) { _, which ->
                when (which) {
                    0 -> chooseShelf(lib)
                    1 -> rename(lib)
                    2 -> {
                        FolderStats.forget(lib.uri)
                        reload()
                    }
                    else -> confirmRemove(lib)
                }
            }
            .show()
    }

    private fun chooseShelf(lib: SharedLibrary) {
        val labels = ShareStore.CATEGORIES.map { Theme.categoryLabel(it) }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("Move \"${lib.name}\" to")
            .setItems(labels) { _, which ->
                store.update(lib.id, lib.name, ShareStore.CATEGORIES[which])
                reload()
            }
            .show()
    }

    private fun rename(lib: SharedLibrary) {
        val input = EditText(activity).apply {
            setText(lib.name)
            setSelectAllOnFocus(true)
            setSingleLine(true)
            setTextColor(Theme.Ink)
        }
        val frame = FrameLayout(activity).apply {
            setPadding(activity.dp(20), activity.dp(8), activity.dp(20), 0)
            addView(input)
        }
        AlertDialog.Builder(activity)
            .setTitle("Rename")
            .setMessage("This only changes the name people see. The folder on the phone keeps its name.")
            .setView(frame)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim().take(60)
                if (name.isEmpty()) {
                    Toast.makeText(activity, "A folder needs a name", Toast.LENGTH_SHORT).show()
                } else {
                    store.update(lib.id, name, lib.category)
                    reload()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmRemove(lib: SharedLibrary) {
        AlertDialog.Builder(activity)
            .setTitle("Stop sharing \"${lib.name}\"?")
            .setMessage("People will no longer see it on the website. Your files are not touched.")
            .setPositiveButton("Stop sharing") { _, _ ->
                try {
                    activity.contentResolver.releasePersistableUriPermission(Uri.parse(lib.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    // Already released; nothing to do.
                }
                store.remove(lib.id)
                FolderStats.forget(lib.uri)
                reload()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
