package ir.tavalodyad.app.util

import ir.tavalodyad.app.data.Person

data class BirthdayInfo(val person: Person, val daysLeft: Long, val turningAge: Int)

fun computeBirthdayInfo(person: Person, today: Triple<Int, Int, Int>): BirthdayInfo {
    require(person.jMonth in 1..12) { "Invalid birth month" }
    val thisYearDay = minOf(person.jDay, PersianCalendarUtils.jalaliMonthLength(today.first, person.jMonth))
    var targetYear = today.first
    var targetDay = thisYearDay
    val todayOrdinal = today.second * 100 + today.third
    val birthdayOrdinal = person.jMonth * 100 + thisYearDay
    if (birthdayOrdinal < todayOrdinal) {
        targetYear++
        targetDay = minOf(person.jDay, PersianCalendarUtils.jalaliMonthLength(targetYear, person.jMonth))
    }
    val left = PersianCalendarUtils.daysBetweenJalali(today.first, today.second, today.third, targetYear, person.jMonth, targetDay)
    return BirthdayInfo(person, left, targetYear - person.jYear)
}

fun sortPeopleByBirthday(people: List<Person>, today: Triple<Int, Int, Int>): List<BirthdayInfo> =
    people.map { computeBirthdayInfo(it, today) }.sortedWith(compareBy<BirthdayInfo> { it.daysLeft }.thenBy { it.person.name })
