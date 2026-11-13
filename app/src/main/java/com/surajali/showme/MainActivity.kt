package com.surajali.showme

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val bg = Color.rgb(13, 18, 28)
    private val panel = Color.rgb(23, 31, 44)
    private val muted = Color.rgb(150, 163, 180)
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showScreen("Quotes")
    }

    private fun showScreen(screen: String) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(18, 22, 18, 12)
        }

        val brand = TextView(this).apply {
            text = "SHOWME"
            textSize = 20f
            setTextColor(Color.WHITE)
            letterSpacing = 0.12f
        }
        root.addView(brand)

        val heading = TextView(this).apply {
            text = screen
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(0, 24, 0, 8)
        }
        root.addView(heading)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 18, 14, 18)
            setBackgroundColor(panel)
        }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        renderContent(screen)

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 0)
        }
        listOf("Quotes", "Chart", "Trade", "History", "Settings").forEach { tab ->
            val button = Button(this).apply {
                text = tab
                textSize = 10f
                isAllCaps = false
                setTextColor(if (tab == screen) Color.WHITE else muted)
                setBackgroundColor(if (tab == screen) Color.rgb(39, 73, 110) else bg)
                setOnClickListener { showScreen(tab) }
            }
            nav.addView(button, LinearLayout.LayoutParams(0, 54, 1f))
        }
        root.addView(nav)
        setContentView(root)
    }

    private fun renderContent(screen: String) {
        val message = when (screen) {
            "Quotes" -> "Market Watch\n\nAdd symbols here\nSymbol        Bid        Ask"
            "Chart" -> "Chart workspace\n\nCandlestick chart will be added only after your separate instruction."
            "Trade" -> "Open positions and pending orders\n\nNo broker connection configured."
            "History" -> "Trading history\n\nDeals • Orders • Positions"
            else -> "App settings\n\nAppearance • Notifications • About"
        }
        val text = TextView(this).apply {
            this.text = message
            textSize = 15f
            setTextColor(Color.WHITE)
            setLineSpacing(8f, 1f)
        }
        content.addView(text)
    }
}
