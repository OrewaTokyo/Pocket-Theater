package com.pockettheater.ui.screens

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.pockettheater.ui.components.MATCH
import com.pockettheater.ui.components.WRAP
import com.pockettheater.ui.components.actionButton
import com.pockettheater.ui.components.card
import com.pockettheater.ui.components.dp
import com.pockettheater.ui.components.lp
import com.pockettheater.ui.components.textView
import com.pockettheater.ui.theme.Theme
import com.pockettheater.util.MarkdownText

/** The Terms of use and the Privacy policy, read from the same files the website shows. */
class LegalPage(private val activity: Activity) : Screen {
    private val tabs = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
    private val body = activity.textView("", 15f, Theme.Ink).apply {
        setLineSpacing(0f, 1.2f)
        setTextIsSelectable(true)
    }
    private val scroll = ScrollView(activity)

    override val view: View = build()

    private fun build(): View {
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(20), activity.dp(8), activity.dp(20), activity.dp(24))
        }
        column.addView(tabs)
        val card = activity.card(18)
        card.addView(body)
        column.addView(card, activity.lp(MATCH, WRAP, top = 14))
        scroll.addView(column)
        show("terms")
        return scroll
    }

    private fun show(name: String) {
        body.text = MarkdownText.fromAsset(activity, name)
        tabs.removeAllViews()
        tabs.addView(activity.actionButton("Terms of use", name == "terms") { show("terms") }, activity.lp(0, WRAP, 1f, right = 6))
        tabs.addView(activity.actionButton("Privacy policy", name == "privacy") { show("privacy") }, activity.lp(0, WRAP, 1f, left = 6))
        scroll.scrollTo(0, 0)
    }

    override fun refresh() = Unit
}
