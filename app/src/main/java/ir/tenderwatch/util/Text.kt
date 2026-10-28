package ir.tenderwatch.util

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

object Fa {
    private val spaces = Regex("\\s+")

    /** Unifies Arabic/Persian letter variants, digits, ZWNJ and case so keyword matching is spelling-tolerant. */
    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                'ي', 'ى' -> sb.append('ی')
                'ك' -> sb.append('ک')
                'ة', 'ۀ' -> sb.append('ه')
                'أ', 'إ', 'آ' -> sb.append('ا')
                '‌', '‍', '‎', '‏', '¬', '-', '_', '/', '،', ',' -> sb.append(' ')
                in 'ً'..'ْ' -> Unit // Arabic diacritics
                in '۰'..'۹' -> sb.append('0' + (c - '۰'))
                in '٠'..'٩' -> sb.append('0' + (c - '٠'))
                else -> sb.append(c.lowercaseChar())
            }
        }
        return sb.toString().replace(spaces, " ").trim()
    }

    fun toPersianDigits(s: String): String =
        s.map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")

    /** "256,860,142,885" rial -> "۲۵٫۷ میلیارد تومان" */
    fun rialHuman(rial: String): String {
        val v = normalize(rial).filter { it.isDigit() }.toLongOrNull() ?: return ""
        val toman = v / 10.0
        val s = when {
            toman >= 1e9 -> String.format(Locale.US, "%.1f میلیارد تومان", toman / 1e9)
            toman >= 1e6 -> String.format(Locale.US, "%.0f میلیون تومان", toman / 1e6)
            else -> String.format(Locale.US, "%,.0f تومان", toman)
        }
        return toPersianDigits(s)
    }
}

class KeywordMatcher(keywords: List<String>, excludes: List<String>) {
    private class Rule(val label: String, val test: (String, String) -> Boolean)

    private val rules = keywords.map(::rule)
    private val ex = excludes.map(::rule)

    /** Returns the keywords found in [text], or empty when nothing matches or an exclude word is present. */
    fun match(text: String): List<String> {
        val n = Fa.normalize(text)
        val d = n.replace(" ", "")
        if (ex.any { it.test(n, d) }) return emptyList()
        return rules.filter { it.test(n, d) }.map { it.label }.distinct()
    }

    fun excluded(text: String): Boolean {
        val n = Fa.normalize(text)
        val d = n.replace(" ", "")
        return ex.any { it.test(n, d) }
    }

    private fun rule(kw: String): Rule {
        val k = Fa.normalize(kw)
        if (k.isEmpty()) return Rule(kw) { _, _ -> false }
        // Latin tokens like "PE" or "PVC" must stand alone, otherwise "PE" hits every English word.
        if (k.all { it in 'a'..'z' || it in '0'..'9' || it == ' ' }) {
            val re = Regex("(?<![a-z0-9])" + Regex.escape(k) + "(?![a-z0-9])")
            return Rule(kw) { n, _ -> re.containsMatchIn(n) }
        }
        val kd = k.replace(" ", "")
        return Rule(kw) { n, d -> n.contains(k) || d.contains(kd) }
    }
}

object Jalali {
    private val tehran: TimeZone = TimeZone.getTimeZone("Asia/Tehran")

    fun fromGregorian(gy: Int, gm: Int, gd: Int): IntArray {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + gdm[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
        val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
        return intArrayOf(jy, jm, jd)
    }

    private fun cal(offsetDays: Int): Calendar =
        Calendar.getInstance(tehran).apply { add(Calendar.DAY_OF_YEAR, offsetDays) }

    /** "1405/08/06" */
    fun date(offsetDays: Int = 0): String {
        val c = cal(offsetDays)
        val j = fromGregorian(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        return String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2])
    }

    /** "1405/08/06 14:05:00", comparable as a string with Setad timestamps. */
    fun now(): String {
        val c = cal(0)
        return date() + String.format(
            Locale.US, " %02d:%02d:%02d",
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND),
        )
    }
}
