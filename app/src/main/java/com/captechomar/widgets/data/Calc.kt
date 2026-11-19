package com.captechomar.widgets.data

/** Tiny calculator for the amount field: + - * / and brackets, e.g. "120+35.5" or "(40*3)-15". */
object Calc {
    fun eval(input: String): Double? {
        val s = input.replace(",", "").replace(" ", "").replace('×', '*').replace('x', '*').replace('X', '*').replace('÷', '/')
        if (s.isEmpty()) return null
        return try {
            val p = Parser(s)
            val v = p.expr()
            if (p.i != s.length || v.isNaN() || v.isInfinite()) null else v
        } catch (_: Throwable) {
            null
        }
    }

    private class Parser(val s: String) {
        var i = 0

        fun expr(): Double {
            var v = term()
            while (i < s.length) {
                when (s[i]) {
                    '+' -> { i++; v += term() }
                    '-' -> { i++; v -= term() }
                    else -> return v
                }
            }
            return v
        }

        private fun term(): Double {
            var v = factor()
            while (i < s.length) {
                when (s[i]) {
                    '*' -> { i++; v *= factor() }
                    '/' -> { i++; v /= factor() }
                    else -> return v
                }
            }
            return v
        }

        private fun factor(): Double {
            if (i >= s.length) throw IllegalArgumentException()
            when (s[i]) {
                '-' -> { i++; return -factor() }
                '+' -> { i++; return factor() }
                '(' -> {
                    i++
                    val v = expr()
                    if (i >= s.length || s[i] != ')') throw IllegalArgumentException()
                    i++
                    return v
                }
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (start == i) throw IllegalArgumentException()
            return s.substring(start, i).toDouble()
        }
    }
}
