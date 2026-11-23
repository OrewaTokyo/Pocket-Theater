package com.pockettheater.ui.screens

import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.pockettheater.R
import com.pockettheater.ui.components.dp
import com.pockettheater.ui.components.textView
import com.pockettheater.ui.theme.Theme
import com.pockettheater.util.CrashLog

/** The opening screen: the Pocket Theater logo on a purple glow with a loading bar, then the app. */
class SplashActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var barAnimator: ValueAnimator? = null
    private val openApp = Runnable { openMain() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Theme.SplashEdge
        window.navigationBarColor = Theme.SplashEdge

        val screen = resources.displayMetrics
        val root = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = screen.heightPixels * 0.65f
                setGradientCenter(0.5f, 0.4f)
                colors = intArrayOf(Theme.SplashGlow, Theme.SplashEdge)
            }
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.logo_splash)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Pocket Theater"
            alpha = 0f
            scaleX = 0.94f
            scaleY = 0.94f
        }

        val barWidth = (screen.widthPixels * 0.52f).toInt()
        val fill = View(this).apply { background = pill(0xFFF3EEF8.toInt()) }
        val track = FrameLayout(this).apply {
            background = pill(0x33FFFFFF)
            addView(fill, FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(logo, LinearLayout.LayoutParams((screen.widthPixels * 0.86f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(track, LinearLayout.LayoutParams(barWidth, dp(6)).apply { topMargin = dp(8) })
        }
        root.addView(
            column,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )
        root.addView(
            textView("Series  \u00B7  Anime  \u00B7  Movies  \u00B7  Gaming", 14f, Theme.Muted).apply { gravity = Gravity.CENTER },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                .apply { bottomMargin = dp(56) }
        )
        setContentView(root)

        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(600).start()
        barAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = LOADING_MS
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener {
                val params = fill.layoutParams
                params.width = (barWidth * (it.animatedValue as Float)).toInt()
                fill.layoutParams = params
            }
            start()
        }
        val report = CrashLog.pending(this)
        if (report == null) handler.postDelayed(openApp, LOADING_MS + 150) else showReport(report)
    }

    /** The app closed on its own last time. Offer the report so it can be copied and sent, then carry on. */
    private fun showReport(report: String) {
        val text = TextView(this).apply {
            this.text = report
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        AlertDialog.Builder(this)
            .setTitle("Pocket Theater had a problem")
            .setMessage("Something went wrong last time. Copy this report and send it so the problem can be fixed.")
            .setView(ScrollView(this).apply { addView(text) })
            .setPositiveButton("Copy and continue") { _, _ ->
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Problem report", report))
                Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show()
                CrashLog.clear(this)
                handler.postDelayed(openApp, 300)
            }
            .setNegativeButton("Continue") { _, _ ->
                CrashLog.clear(this)
                handler.postDelayed(openApp, 300)
            }
            .setCancelable(false)
            .show()
    }

    private fun pill(color: Int) = GradientDrawable().apply {
        cornerRadius = dp(3).toFloat()
        setColor(color)
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        handler.removeCallbacks(openApp)
        barAnimator?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val LOADING_MS = 1600L
    }
}
