package com.pockettheater.util

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan

/** Turns the Terms and Privacy pages (a small part of Markdown) into styled text for a TextView. */
object MarkdownText {
    private val inlinePattern = Regex("""\*\*([^*]+)\*\*|`([^`]+)`|\[([^\]]+)\]\(([^)]+)\)""")
    private val dashes = Regex("^:?-{2,}:?$")
    private val headingLine = Regex("^(#{1,3})\\s+(.*)$")
    private val bulletLine = Regex("^\\s*-\\s+")

    /** Reads web/legal/<name>.md from the app's own files. */
    fun fromAsset(context: Context, name: String): CharSequence =
        render(context.assets.open("web/legal/$name.md").bufferedReader().use { it.readText() })

    fun render(markdown: String): CharSequence {
        val out = SpannableStringBuilder()
        val lines = markdown.replace("\r\n", "\n").split("\n")
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) {
                i++
                continue
            }
            val heading = headingLine.find(line)
            when {
                heading != null -> {
                    startBlock(out)
                    val start = out.length
                    appendInline(out, heading.groupValues[2].trim())
                    val size = when (heading.groupValues[1].length) {
                        1 -> 1.5f
                        2 -> 1.25f
                        else -> 1.1f
                    }
                    out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    out.setSpan(RelativeSizeSpan(size), start, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    i++
                }
                bulletLine.containsMatchIn(line) -> {
                    startBlock(out)
                    var first = true
                    while (i < lines.size && bulletLine.containsMatchIn(lines[i])) {
                        if (!first) out.append("\n")
                        out.append("\u2022  ")
                        appendInline(out, lines[i].replace(bulletLine, "").trim())
                        first = false
                        i++
                    }
                }
                line.trim().startsWith("|") -> {
                    startBlock(out)
                    val rows = ArrayList<List<String>>()
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        rows.add(splitRow(lines[i]))
                        i++
                    }
                    // The first row holds the column names. Each other row reads as a bold name with its details under it.
                    val body = rows.filterNot { row -> row.all { cell -> dashes.matches(cell) } }.drop(1)
                    body.forEachIndexed { index, row ->
                        if (index > 0) out.append("\n\n")
                        val start = out.length
                        appendInline(out, row.firstOrNull().orEmpty())
                        out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        val rest = row.drop(1).filter { it.isNotEmpty() }
                        if (rest.isNotEmpty()) {
                            out.append("\n")
                            appendInline(out, rest.joinToString("\n"))
                        }
                    }
                }
                else -> {
                    startBlock(out)
                    val parts = ArrayList<String>()
                    while (i < lines.size && lines[i].isNotBlank() && headingLine.find(lines[i]) == null &&
                        !bulletLine.containsMatchIn(lines[i]) && !lines[i].trim().startsWith("|")
                    ) {
                        parts.add(lines[i].trim())
                        i++
                    }
                    appendInline(out, parts.joinToString(" "))
                }
            }
        }
        return out
    }

    private fun startBlock(out: SpannableStringBuilder) {
        if (out.isNotEmpty()) out.append("\n\n")
    }

    private fun splitRow(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

    /** Bold, code and links. A link shows its words, plus the address when it is a web address. */
    private fun appendInline(out: SpannableStringBuilder, text: String) {
        var last = 0
        for (m in inlinePattern.findAll(text)) {
            out.append(text.substring(last, m.range.first))
            val start = out.length
            when {
                m.groups[1] != null -> {
                    out.append(m.groupValues[1])
                    out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                m.groups[2] != null -> {
                    out.append(m.groupValues[2])
                    out.setSpan(TypefaceSpan("monospace"), start, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                else -> {
                    out.append(m.groupValues[3])
                    val address = m.groupValues[4]
                    if (address.startsWith("http://") || address.startsWith("https://")) out.append(" ($address)")
                }
            }
            last = m.range.last + 1
        }
        out.append(text.substring(last))
    }
}
