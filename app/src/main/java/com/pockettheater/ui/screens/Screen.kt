package com.pockettheater.ui.screens

import android.view.View

/** One page of the app. The shell shows `view` and calls `refresh()` about once a second while it is on screen. */
interface Screen {
    val view: View
    fun refresh()

    /** Return true if the back button was used up inside this page (for example closing a sub-page). */
    fun onBack(): Boolean = false
}
