package top.niunaijun.blackboxa.view.main

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class CalculatorGateActivity : Activity() {
    private val expression = StringBuilder()
    private lateinit var display: TextView
    private lateinit var hint: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(android.R.style.Theme_Material_NoActionBar)
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(12, 16, 27)
        window.navigationBarColor = Color.rgb(12, 16, 27)
        buildCalculator()
    }

    private fun buildCalculator() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(22), dp(18), dp(18))
            setBackgroundColor(Color.rgb(12, 16, 27))
        }

        val heading = TextView(this).apply {
            text = "CALCULATOR"
            textSize = 18f
            letterSpacing = 0.16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.rgb(185, 198, 222))
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(heading, LinearLayout.LayoutParams(-1, dp(46)))

        display = TextView(this).apply {
            text = "0"
            textSize = 42f
            maxLines = 1
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextColor(Color.WHITE)
            setPadding(dp(8), 0, dp(8), 0)
        }
        root.addView(display, LinearLayout.LayoutParams(-1, 0, 1f))

        hint = TextView(this).apply {
            text = " "
            textSize = 12f
            setTextColor(Color.rgb(255, 150, 120))
            gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
        }
        root.addView(hint, LinearLayout.LayoutParams(-1, dp(28)))

        val keys = listOf(
            listOf("C", "DEL", "÷", "×"),
            listOf("7", "8", "9", "−"),
            listOf("4", "5", "6", "+"),
            listOf("1", "2", "3", "="),
            listOf("0", "0", ".", "=")
        )
        keys.forEachIndexed { rowIndex, row ->
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            row.forEachIndexed { columnIndex, key ->
                if (rowIndex == 4 && columnIndex == 1) return@forEachIndexed
                val actualKey = if (rowIndex == 4 && columnIndex == 0) "0" else key
                val button = Button(this).apply {
                    text = actualKey
                    textSize = if (actualKey == "DEL") 15f else 21f
                    isAllCaps = false
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(15).toFloat()
                        setColor(buttonColor(actualKey))
                    }
                    setOnClickListener { press(actualKey) }
                    stateListAnimator = null
                    minHeight = 0
                    minimumHeight = 0
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(0, 0, 0, 0)
                }
                val params = LinearLayout.LayoutParams(0, dp(62), 1f).apply {
                    val gap = dp(6)
                    setMargins(gap, dp(5), gap, dp(5))
                }
                line.addView(button, params)
            }
            root.addView(line, LinearLayout.LayoutParams(-1, dp(72)))
        }
        setContentView(root)
    }

    private fun buttonColor(key: String): Int = when (key) {
        "=" -> Color.rgb(61, 105, 220)
        "C", "DEL" -> Color.rgb(62, 72, 94)
        "+", "−", "×", "÷" -> Color.rgb(39, 53, 78)
        else -> Color.rgb(27, 35, 52)
    }

    private fun press(key: String) {
        hint.text = " "
        when (key) {
            "C" -> expression.setLength(0)
            "DEL" -> if (expression.isNotEmpty()) expression.deleteCharAt(expression.lastIndex)
            "=" -> calculateOrUnlock()
            else -> appendKey(key)
        }
        if (!isFinishing) {
            display.text = if (expression.isEmpty()) "0" else expression.toString()
        }
    }

    private fun appendKey(key: String) {
        if (expression.length >= 28) {
            hint.text = "Maximum input length reached"
            return
        }
        val operators = setOf('+', '−', '×', '÷')
        if (key.length == 1 && key[0] in operators) {
            if (expression.isEmpty()) {
                if (key != "−") return
            } else if (expression.last() in operators) {
                expression.setCharAt(expression.lastIndex, key[0])
                return
            }
        }
        expression.append(key)
    }

    private fun calculateOrUnlock() {
        if (expression.toString() == "6767") {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }
        if (expression.isEmpty()) return
        try {
            val value = ExpressionParser(expression.toString()).parse()
            val formatted = String.format(Locale.US, "%.8f", value)
                .trimEnd('0')
                .trimEnd('.')
            expression.setLength(0)
            expression.append(if (formatted.isEmpty() || formatted == "-0") "0" else formatted)
            hint.text = " "
        } catch (_: Exception) {
            hint.text = "Invalid expression"
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class ExpressionParser(source: String) {
        private val input = source.replace('×', '*').replace('÷', '/').replace('−', '-')
        private var index = 0

        fun parse(): Double {
            val result = parseExpression()
            if (index != input.length || !result.isFinite()) error("Invalid expression")
            return result
        }

        private fun parseExpression(): Double {
            var value = parseTerm()
            while (index < input.length) {
                value = when (input[index]) {
                    '+' -> { index++; value + parseTerm() }
                    '-' -> { index++; value - parseTerm() }
                    else -> return value
                }
            }
            return value
        }

        private fun parseTerm(): Double {
            var value = parseFactor()
            while (index < input.length) {
                value = when (input[index]) {
                    '*' -> { index++; value * parseFactor() }
                    '/' -> {
                        index++
                        val divisor = parseFactor()
                        if (divisor == 0.0) error("Division by zero")
                        value / divisor
                    }
                    else -> return value
                }
            }
            return value
        }

        private fun parseFactor(): Double {
            if (index < input.length && input[index] == '+') {
                index++
                return parseFactor()
            }
            if (index < input.length && input[index] == '-') {
                index++
                return -parseFactor()
            }
            val start = index
            var decimalSeen = false
            while (index < input.length) {
                val ch = input[index]
                if (ch.isDigit()) {
                    index++
                } else if (ch == '.' && !decimalSeen) {
                    decimalSeen = true
                    index++
                } else {
                    break
                }
            }
            if (start == index) error("Number expected")
            return input.substring(start, index).toDouble()
        }
    }
}
