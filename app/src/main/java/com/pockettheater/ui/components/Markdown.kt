package com.pockettheater.ui.components

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.View
import android.widget.TextView
import com.pockettheater.ui.theme.Theme

/** Shows the Terms and policies on the phone: headings, paragraphs, lists and tables from a Markdown file. */
object Markdown {
    fun views(context: Context, text: String): List<View> {
        val out = ArrayList<View>()
        val lines = text.replace("\r", "").split("\n")
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.isBlank() -> i++
                line.startsWith("#") -> {
                    val level = line.takeWhile { it == '#' }.length
                    val title = line.drop(level).trim()
                    val (size, color, top) = when (level) {
                        1 -> Triple(26f, Theme.Ink, 6)
                        2 -> Triple(19f, Theme.Marquee, 24)
                        else -> Triple(16f, Theme.Ink, 16)
                    }
                    out.add(context.textView(title, size, color, bold = true).apply { layoutParams = context.lp(MATCH, WRAP, top = top, bottom = 4) })
                    i++
                }
                line.trimStart().startsWith("|") -> {
                    val rows = ArrayList<List<String>>()
                    while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                        val cells = lines[i].trim().trim('|').split("|").map { it.trim() }
                        if (!cells.all { c -> c.isNotEmpty() && c.all { it == '-' || it == ':' } }) rows.add(cells)
                        i++
                    }
                    rows.drop(1).forEach { row -> out.add(tableRow(context, row)) }
                }
                isListLine(line) -> {
                    while (i < lines.size && isListLine(lines[i])) {
                        val item = lines[i].trim().replace(Regex("^([-*]|\\d+\\.)\\s+"), "")
                        out.add(paragraph(context, "\u2022  $item", 12))
                        i++
                    }
                }
                else -> {
                    val para = ArrayList<String>()
                    while (i < lines.size && lines[i].isNotBlank() && !lines[i].startsWith("#") &&
                        !lines[i].trimStart().startsWith("|") && !isListLine(lines[i])
                    ) {
                        para.add(lines[i].trim())
                        i++
                    }
                    out.add(paragraph(context, para.joinToString(" "), 0))
                }
            }
        }
        return out
    }

    private fun isListLine(line: String): Boolean = Regex("^\\s*([-*]|\\d+\\.)\\s+").containsMatchIn(line)

    private fun paragraph(context: Context, text: String, indentDp: Int): View =
        TextView(context).apply {
            this.text = styled(text)
            textSize = 15f
            setTextColor(Theme.Ink)
            setLineSpacing(0f, 1.2f)
            setPadding(context.dp(indentDp), 0, 0, 0)
            layoutParams = context.lp(MATCH, WRAP, top = 6)
        }

    /** One table row as a small card: the first cell in bold, the rest underneath. */
    private fun tableRow(context: Context, cells: List<String>): View {
        val builder = SpannableStringBuilder()
        cells.forEachIndexed { index, cell ->
            if (index > 0) builder.append("\n")
            val start = builder.length
            builder.append(styled(cell))
            if (index == 0) builder.setSpan(StyleSpan(Typeface.BOLD), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return TextView(context).apply {
            text = builder
            textSize = 15f
            setTextColor(Theme.Ink)
            setLineSpacing(0f, 1.15f)
            setPadding(context.dp(14), context.dp(10), context.dp(14), context.dp(10))
            background = GradientDrawable().apply {
                cornerRadius = context.dp(12).toFloat()
                setColor(Theme.Surface)
                setStroke(context.dp(1), Theme.Border)
            }
            layoutParams = context.lp(MATCH, WRAP, top = 8)
        }
    }

    /** Bold and code marks become real bold and monospace text. Links keep only their words. */
    private fun styled(text: String): CharSequence {
        val plain = text.replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        val out = SpannableStringBuilder()
        val pattern = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`")
        var last = 0
        for (m in pattern.findAll(plain)) {
            out.append(plain.substring(last, m.range.first))
            val start = out.length
            val bold = m.groups[1]?.value
            if (bold != null) {
                out.append(bold)
                out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                out.append(m.groups[2]?.value.orEmpty())
                out.setSpan(TypefaceSpan("monospace"), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            last = m.range.last + 1
        }
        out.append(plain.substring(last))
        return out
    }
}
