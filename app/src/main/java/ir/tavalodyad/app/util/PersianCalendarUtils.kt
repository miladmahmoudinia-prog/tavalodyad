package ir.tavalodyad.app.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Offline Jalali/Gregorian conversion using the jalaali-js breaks-array algorithm. */
object PersianCalendarUtils {
    private val breaks = intArrayOf(-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178)
    val monthNames: Array<String> = arrayOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")

    private fun div(a: Int, b: Int): Int = a / b // Kotlin integer division truncates toward zero, as in jalaali-js
    private fun mod(a: Int, b: Int): Int = a - div(a, b) * b

    private data class JalCal(val leap: Int, val gy: Int, val march: Int)

    private fun jalCal(jy: Int): JalCal {
        require(jy >= breaks.first() && jy < breaks.last()) { "Jalali year outside supported range: $jy" }
        var leapJ = -14
        var jp = breaks[0]
        var jump = 0
        for (i in 1 until breaks.size) {
            val jm = breaks[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += div(jump, 33) * 8 + div(mod(jump, 33), 4)
            jp = jm
        }
        var n = jy - jp
        leapJ += div(n, 33) * 8 + div(mod(n, 33) + 3, 4)
        if (mod(jump, 33) == 4 && jump - n == 4) leapJ += 1

        val gy = jy + 621
        // The Gregorian leap-day offset is essential to calculate Farvardin 1.
        val leapG = div(gy, 4) - div(div(gy, 100) + 1, 4) * 3 - 150
        val march = 20 + leapJ - leapG

        if (jump - n < 6) n = n - jump + div(jump + 4, 33) * 33
        var leap = mod(mod(n + 1, 33) - 1, 4)
        if (leap == -1) leap = 4
        return JalCal(leap, gy, march)
    }

    private fun g2d(gy: Int, gm: Int, gd: Int): Int {
        var jdn = div((gy + div(gm - 8, 6) + 100100) * 1461, 4) +
            div(153 * mod(gm + 9, 12) + 2, 5) + gd - 34840408
        jdn -= div(div(gy + 100100 + div(gm - 8, 6), 100) * 3, 4)
        jdn += 752
        return jdn
    }

    private fun d2g(jdn: Int): Triple<Int, Int, Int> {
        var j = 4 * jdn + 139361631
        j += div(div(4 * jdn + 183187720, 146097) * 3, 4) * 4 - 3908
        val i = div(mod(j, 1461), 4) * 5 + 308
        val gd = div(mod(i, 153), 5) + 1
        val gm = mod(div(i, 153), 12) + 1
        val gy = div(j, 1461) - 100100 + div(8 - gm, 6)
        return Triple(gy, gm, gd)
    }

    private fun jalaliToJdn(jy: Int, jm: Int, jd: Int): Int {
        require(jm in 1..12 && jd in 1..jalaliMonthLength(jy, jm)) { "Invalid Jalali date" }
        val r = jalCal(jy)
        return g2d(r.gy, 3, r.march) + (jm - 1) * 31 - div(jm, 7) * (jm - 7) + jd - 1
    }

    /** Gregorian date to Jalali date. */
    fun d2j(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        require(gm in 1..12 && gd in 1..LocalDate.of(gy, gm, 1).lengthOfMonth()) { "Invalid Gregorian date" }
        val jdn = g2d(gy, gm, gd)
        val (gYear, _, _) = d2g(jdn)
        var jy = gYear - 621
        val r = jalCal(jy)
        val jdn1f = g2d(gYear, 3, r.march)
        var k = jdn - jdn1f
        if (k >= 0) {
            if (k <= 185) return Triple(jy, 1 + div(k, 31), mod(k, 31) + 1)
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k += 1
        }
        val jm = 7 + div(k, 30)
        val jd = mod(k, 30) + 1
        return Triple(jy, jm, jd)
    }

    /** Jalali date to Gregorian date. */
    fun j2d(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        val jdn = jalaliToJdn(jy, jm, jd)
        return d2g(jdn)
    }

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> = d2j(gy, gm, gd)
    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> = j2d(jy, jm, jd)
    fun isLeapJalaliYear(jy: Int): Boolean = jalCal(jy).leap == 0
    fun jalaliMonthLength(jy: Int, jm: Int): Int {
        require(jm in 1..12) { "Jalali month must be 1..12" }
        return when (jm) {
            in 1..6 -> 31
            in 7..11 -> 30
            else -> if (isLeapJalaliYear(jy)) 30 else 29
        }
    }
    fun getTodayJalali(): Triple<Int, Int, Int> {
        val today = LocalDate.now()
        return d2j(today.year, today.monthValue, today.dayOfMonth)
    }
    fun daysBetweenJalali(y1: Int, m1: Int, d1: Int, y2: Int, m2: Int, d2: Int): Long {
        val a = j2d(y1, m1, d1)
        val b = j2d(y2, m2, d2)
        return ChronoUnit.DAYS.between(LocalDate.of(a.first, a.second, a.third), LocalDate.of(b.first, b.second, b.third))
    }
    fun toPersianDigits(value: Any): String = value.toString().map { ch ->
        if (ch in '0'..'9') ('۰'.code + (ch - '0')).toChar() else ch
    }.joinToString("")
    fun formatJalali(y: Int, m: Int, d: Int): String {
        require(m in 1..12 && d in 1..jalaliMonthLength(y, m))
        return "${toPersianDigits(d)} ${monthNames[m - 1]} ${toPersianDigits(y)}"
    }
}
