package com.pockettheater.core.storage

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Subtitle files that sit next to a video ("Movie.mp4" with "Movie.srt" or "Movie.en.srt"). Browsers only read WebVTT,
 * so SRT and ASS/SSA files are turned into it on the way out.
 */
object Subtitles {
    val EXTENSIONS = setOf("srt", "vtt", "ass", "ssa")
    val FOLDER_NAMES = setOf("subs", "subtitles", "sub", "subtitle")
    const val MAX_BYTES = 3_000_000

    // Words people put in subtitle file names: name and two-letter code.
    private val languages: Map<String, Pair<String, String>> = mapOf(
        "en" to ("English" to "en"), "eng" to ("English" to "en"), "english" to ("English" to "en"),
        "es" to ("Spanish" to "es"), "spa" to ("Spanish" to "es"), "spanish" to ("Spanish" to "es"),
        "fr" to ("French" to "fr"), "fre" to ("French" to "fr"), "fra" to ("French" to "fr"), "french" to ("French" to "fr"),
        "de" to ("German" to "de"), "ger" to ("German" to "de"), "deu" to ("German" to "de"), "german" to ("German" to "de"),
        "it" to ("Italian" to "it"), "ita" to ("Italian" to "it"), "italian" to ("Italian" to "it"),
        "pt" to ("Portuguese" to "pt"), "por" to ("Portuguese" to "pt"), "portuguese" to ("Portuguese" to "pt"),
        "ru" to ("Russian" to "ru"), "rus" to ("Russian" to "ru"), "russian" to ("Russian" to "ru"),
        "ja" to ("Japanese" to "ja"), "jpn" to ("Japanese" to "ja"), "japanese" to ("Japanese" to "ja"),
        "zh" to ("Chinese" to "zh"), "chi" to ("Chinese" to "zh"), "zho" to ("Chinese" to "zh"), "chinese" to ("Chinese" to "zh"),
        "ko" to ("Korean" to "ko"), "kor" to ("Korean" to "ko"), "korean" to ("Korean" to "ko"),
        "ar" to ("Arabic" to "ar"), "ara" to ("Arabic" to "ar"), "arabic" to ("Arabic" to "ar"),
        "hi" to ("Hindi" to "hi"), "hin" to ("Hindi" to "hi"), "hindi" to ("Hindi" to "hi"),
        "tr" to ("Turkish" to "tr"), "tur" to ("Turkish" to "tr"), "turkish" to ("Turkish" to "tr"),
        "id" to ("Indonesian" to "id"), "ind" to ("Indonesian" to "id"), "indonesian" to ("Indonesian" to "id"),
        "vi" to ("Vietnamese" to "vi"), "vie" to ("Vietnamese" to "vi"), "vietnamese" to ("Vietnamese" to "vi"),
        "th" to ("Thai" to "th"), "tha" to ("Thai" to "th"), "thai" to ("Thai" to "th"),
        "nl" to ("Dutch" to "nl"), "dut" to ("Dutch" to "nl"), "nld" to ("Dutch" to "nl"), "dutch" to ("Dutch" to "nl"),
        "pl" to ("Polish" to "pl"), "pol" to ("Polish" to "pl"), "polish" to ("Polish" to "pl"),
        "sv" to ("Swedish" to "sv"), "swe" to ("Swedish" to "sv"), "swedish" to ("Swedish" to "sv"),
    )
    private val flags = mapOf("forced" to "forced", "sdh" to "SDH", "cc" to "CC")

    fun isSubtitle(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in EXTENSIONS

    private fun baseName(video: String) = video.substringBeforeLast('.')

    /** True when this subtitle file goes with this video: "Movie.srt", "Movie.en.srt", "Movie.English.forced.srt". */
    fun belongsTo(video: String, subtitle: String): Boolean {
        if (!isSubtitle(subtitle)) return false
        val base = baseName(video).lowercase()
        val stem = subtitle.substringBeforeLast('.').lowercase()
        return stem == base || stem.startsWith("$base.")
    }

    private fun words(video: String, subtitle: String): List<String> {
        val base = baseName(video)
        val stem = subtitle.substringBeforeLast('.')
        if (stem.length <= base.length) return emptyList()
        return stem.substring(base.length).split('.', '_', '-', ' ').filter { it.isNotEmpty() }
    }

    /** A readable name for the subtitle menu: "English", "English (forced)", or just "Subtitles". */
    fun label(video: String, subtitle: String): String {
        val all = words(video, subtitle)
        if (all.isEmpty()) return "Subtitles"
        val marks = all.mapNotNull { flags[it.lowercase()] }
        val rest = all.filter { flags[it.lowercase()] == null }
        val name = rest.firstOrNull()?.let { languages[it.lowercase()]?.first ?: it.replaceFirstChar { c -> c.uppercase() } } ?: "Subtitles"
        return if (marks.isEmpty()) name else "$name (" + marks.joinToString(", ") + ")"
    }

    /** The two-letter language code if the name says one, otherwise an empty text. */
    fun language(video: String, subtitle: String): String =
        words(video, subtitle).firstNotNullOfOrNull { languages[it.lowercase()]?.second } ?: ""

    // ---- reading and converting ----

    /** Turns the bytes of a subtitle file into text. Handles the byte-order marks and falls back to Windows-1252 for old files. */
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
    }

    /** WebVTT text for a subtitle file of the given type (srt, vtt, ass or ssa). */
    fun toVtt(text: String, extension: String): String {
        val clean = text.replace("\r\n", "\n").replace('\r', '\n').removePrefix("\uFEFF")
        return when (extension.lowercase()) {
            "vtt" -> if (clean.trimStart().startsWith("WEBVTT")) clean else "WEBVTT\n\n$clean"
            "ass", "ssa" -> fromAss(clean)
            else -> fromSrt(clean)
        }
    }

    private val clock = Regex("(\\d+):(\\d{2}):(\\d{2})[,.](\\d{1,3})")
    // Every brace is escaped on purpose: Android's regex engine rejects a bare "}" that desktop Java accepts.
    private val styleTag = Regex("\\{[^\\}]*\\}")

    private fun stamp(m: MatchResult): String =
        m.groupValues[1].padStart(2, '0') + ":" + m.groupValues[2] + ":" + m.groupValues[3] + "." + m.groupValues[4].padEnd(3, '0')

    private fun fromSrt(text: String): String {
        val out = StringBuilder("WEBVTT\n\n")
        for (line in text.split('\n')) {
            if (line.contains("-->")) {
                val times = clock.findAll(line).toList()
                if (times.size >= 2) out.append(stamp(times[0])).append(" --> ").append(stamp(times[1])) else out.append(line)
            } else {
                out.append(line.replace(styleTag, ""))
            }
            out.append('\n')
        }
        return out.toString()
    }

    private fun fromAss(text: String): String {
        val out = StringBuilder("WEBVTT\n\n")
        var fields = 10
        var startAt = 1
        var endAt = 2
        var textAt = 9
        var inEvents = false
        for (raw in text.split('\n')) {
            val line = raw.trim()
            if (line.startsWith("[")) {
                inEvents = line.equals("[Events]", ignoreCase = true)
                continue
            }
            if (!inEvents) continue
            if (line.startsWith("Format:", ignoreCase = true)) {
                val names = line.substringAfter(':').split(',').map { it.trim().lowercase() }
                fields = names.size
                startAt = names.indexOf("start")
                endAt = names.indexOf("end")
                textAt = names.indexOf("text")
                continue
            }
            if (!line.startsWith("Dialogue:", ignoreCase = true)) continue
            val parts = line.substringAfter(':').trimStart().split(',', limit = fields)
            if (parts.size < fields || startAt < 0 || endAt < 0 || textAt < 0) continue
            val body = parts[textAt].replace(styleTag, "").replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ")
                .replace(Regex("\n{2,}"), "\n").trim()
            val start = clock.matchEntire(parts[startAt].trim())
            val end = clock.matchEntire(parts[endAt].trim())
            if (body.isEmpty() || start == null || end == null) continue
            out.append(stamp(start)).append(" --> ").append(stamp(end)).append('\n').append(body).append("\n\n")
        }
        return out.toString()
    }
}
