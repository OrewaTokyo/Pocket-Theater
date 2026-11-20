package com.pockettheater.ui.screens

import android.app.Activity
import android.view.Gravity
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

/** A placeholder. This section needs its own careful design, so we will build it slowly, later. */
class FoldersScreen(private val activity: Activity, private val onOpenAdminTools: () -> Unit) : Screen {
    override val view: View = build()

    private fun build(): View {
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(20), activity.dp(20), activity.dp(20), activity.dp(24))
        }
        column.addView(activity.textView("Folders", 30f, Theme.Ink, bold = true))

        val hero = activity.card(24)
        hero.gravity = Gravity.CENTER_HORIZONTAL
        hero.addView(activity.textView("\uD83D\uDCC1", 56f, Theme.Ink).apply { gravity = Gravity.CENTER })
        hero.addView(activity.textView("Coming soon", 22f, Theme.Marquee, bold = true).apply { gravity = Gravity.CENTER }, activity.lp(MATCH, WRAP, top = 8))
        hero.addView(
            activity.textView("This section will be built slowly, one careful step at a time.", 15f, Theme.Muted).apply { gravity = Gravity.CENTER },
            activity.lp(MATCH, WRAP, top = 6)
        )
        column.addView(hero, activity.lp(MATCH, WRAP, top = 18))

        val note = activity.card(18)
        note.addView(activity.textView("For now", 16f, Theme.Ink, bold = true))
        note.addView(
            activity.textView("Add or remove shared folders in Profile \u203A Admin tools.", 15f, Theme.Muted),
            activity.lp(MATCH, WRAP, top = 4)
        )
        note.addView(activity.actionButton("Open admin tools", filled = false) { onOpenAdminTools() }, activity.lp(MATCH, WRAP, top = 12))
        column.addView(note, activity.lp(MATCH, WRAP, top = 14))

        return ScrollView(activity).apply { addView(column) }
    }

    override fun refresh() = Unit
}
