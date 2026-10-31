package com.calculator.simple

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToLong

class MainActivity : Activity() {
    private lateinit var display: TextView
    private var first: Double? = null
    private var pending: String? = null
    private var startNew = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildCalculator()
    }

    private fun buildCalculator() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 28, 18, 18)
            setBackgroundColor(Color.rgb(245, 246, 248))
        }
        display = TextView(this).apply {
            text = "0"
            textSize = 42f
            setTextColor(Color.rgb(25, 32, 38))
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            typeface = Typeface.DEFAULT
            setPadding(12, 12, 12, 12)
            maxLines = 1
        }
        root.addView(display, LinearLayout.LayoutParams(-1, 0, 1f))

        val keys = arrayOf(
            arrayOf("AC", "±", "%", "÷"),
            arrayOf("7", "8", "9", "×"),
            arrayOf("4", "5", "6", "−"),
            arrayOf("1", "2", "3", "+"),
            arrayOf("⌫", "0", ".", "=")
        )
        val grid = GridLayout(this).apply { columnCount = 4; rowCount = 5 }
        keys.forEach { row ->
            row.forEach { key ->
                val button = Button(this).apply {
                    text = key
                    textSize = 21f
                    isAllCaps = false
                    setTextColor(if (key in listOf("÷", "×", "−", "+", "=")) Color.WHITE else Color.rgb(35, 43, 49))
                    setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                        if (key in listOf("÷", "×", "−", "+", "=")) Color.rgb(55, 112, 170)
                        else if (key in listOf("AC", "±", "%", "⌫")) Color.rgb(218, 225, 231)
                        else Color.WHITE
                    ))
                    setOnClickListener { press(key) }
                }
                val params = GridLayout.LayoutParams().apply {
                    width = 0
                    height = 0
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(4, 4, 4, 4)
                }
                grid.addView(button, params)
            }
        }
        root.addView(grid, LinearLayout.LayoutParams(-1, 0, 4f))
        setContentView(root)
    }

    private fun press(key: String) {
        val current = display.text.toString()
        when (key) {
            "AC" -> { display.text = "0"; first = null; pending = null; startNew = false }
            "⌫" -> display.text = if (current.length <= 1) "0" else current.dropLast(1)
            "±" -> display.text = if (current.startsWith("-")) current.drop(1) else if (current == "0") current else "-$current"
            "%" -> display.text = format((current.toDoubleOrNull() ?: 0.0) / 100.0)
            "÷", "×", "−", "+" -> {
                first = current.toDoubleOrNull()
                pending = key
                startNew = true
            }
            "=" -> calculateOrOpen()
            "." -> if (startNew) { display.text = "0."; startNew = false } else if (!current.contains(".")) display.text = "$current."
            else -> {
                if (startNew || current == "0") { display.text = key; startNew = false }
                else if (current.length < 14) display.text = current + key
            }
        }
    }

    private fun calculateOrOpen() {
        val typed = display.text.toString()
        if (typed == "6767" && pending == null) {
            val launch = packageManager.getLaunchIntentForPackage("com.instagram.android")
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launch)
            } else {
                Toast.makeText(this, "Instagram is not installed on this phone.", Toast.LENGTH_LONG).show()
            }
            return
        }
        val a = first
        val b = typed.toDoubleOrNull()
        val op = pending
        if (a != null && b != null && op != null) {
            val result = when (op) {
                "+" -> a + b
                "−" -> a - b
                "×" -> a * b
                "÷" -> if (b == 0.0) null else a / b
                else -> null
            }
            display.text = result?.let { format(it) } ?: "Error"
            first = null
            pending = null
            startNew = true
        }
    }

    private fun format(value: Double): String {
        if (!value.isFinite()) return "Error"
        if (value % 1.0 == 0.0 && kotlin.math.abs(value) < 1.0e15) return value.roundToLong().toString()
        return value.toString().take(14)
    }
}
