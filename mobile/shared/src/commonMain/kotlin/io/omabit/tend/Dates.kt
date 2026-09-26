package io.omabit.tend

import kotlinx.datetime.*
import kotlin.time.Instant
import kotlin.time.Clock
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.hours

fun parseUrbit(value: String): Instant {
    if (!value.startsWith("~")) return Instant.parse(value)
    val p = Regex("^~(\\d+)\\.(\\d+)\\.(\\d+)\\.\\.(\\d+)\\.(\\d+)\\.(\\d+)").find(value)?.groupValues ?: error("Invalid ship date")
    return LocalDateTime(p[1].toInt(), p[2].toInt(), p[3].toInt(), p[4].toInt(), p[5].toInt(), p[6].toInt()).toInstant(TimeZone.UTC)
}
fun wireDate(instant: Instant): String = instant.toLocalDateTime(TimeZone.UTC).let { "~${it.year}.${it.monthNumber}.${it.dayOfMonth}..${it.hour.toString().padStart(2,'0')}.${it.minute.toString().padStart(2,'0')}.${it.second.toString().padStart(2,'0')}" }
fun zone(name: String): TimeZone = TimeZone.of(name)
fun localDue(s: Schedule) = parseUrbit(s.dueAt).toLocalDateTime(zone(s.timezone))
fun isDueToday(s: Schedule, now: Instant): Boolean = runCatching { localDue(s).date <= now.toLocalDateTime(zone(s.timezone)).date }.getOrDefault(false)
fun dueLabel(s: Schedule): String = runCatching { localDue(s).let { if (s.allDay) it.date.toString() else "${it.date} · ${it.hour.toString().padStart(2,'0')}:${it.minute.toString().padStart(2,'0')}" } }.getOrDefault(s.dueAt)

private fun daysInMonth(year: Int, month: Int): Int = LocalDate(year, month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY).dayOfMonth
private fun weekday(date: LocalDate) = date.dayOfWeek.isoDayNumber % 7
private fun ordinalDay(year: Int, month: Int, ordinal: MonthWeek): Int {
    val maximum = daysInMonth(year, month)
    if (ordinal.index == 5) return maximum - ((weekday(LocalDate(year, month, maximum)) - ordinal.weekday + 7) % 7)
    val day = 1 + ((ordinal.weekday - weekday(LocalDate(year, month, 1)) + 7) % 7) + (ordinal.index - 1) * 7
    return if (day > maximum) day - 7 else day
}
fun nextWall(wall: LocalDateTime, r: RepeatRule): LocalDateTime {
    require(r.interval in 1..100000) { "Repeat interval is out of range" }
    val day = wall.date
    val next = when (r.frequency) {
        "daily" -> day.plus(r.interval, DateTimeUnit.DAY)
        "weekly" -> {
            val weekdays = r.weekdays.distinct().sorted(); val current = weekday(day)
            val delta = if (weekdays.isEmpty()) r.interval * 7 else weekdays.firstOrNull { it > current }?.minus(current) ?: (r.interval * 7 + weekdays.first() - current)
            day.plus(delta, DateTimeUnit.DAY)
        }
        "monthly" -> {
            val later = r.monthDays.sorted().firstOrNull { it > day.dayOfMonth && it <= daysInMonth(day.year, day.monthNumber) }
            val month = LocalDate(day.year, day.monthNumber, 1).plus(r.interval, DateTimeUnit.MONTH)
            when {
                later != null -> LocalDate(day.year, day.monthNumber, later)
                r.monthWeek != null -> {
                    val same = ordinalDay(day.year, day.monthNumber, r.monthWeek)
                    if (same > day.dayOfMonth) LocalDate(day.year, day.monthNumber, same) else LocalDate(month.year, month.monthNumber, ordinalDay(month.year, month.monthNumber, r.monthWeek))
                }
                else -> LocalDate(month.year, month.monthNumber, minOf(r.monthDays.minOrNull() ?: day.dayOfMonth, daysInMonth(month.year, month.monthNumber)))
            }
        }
        "yearly" -> LocalDate(day.year + r.interval, day.monthNumber, minOf(r.monthDays.minOrNull() ?: day.dayOfMonth, daysInMonth(day.year + r.interval, day.monthNumber)))
        else -> error("Unsupported recurrence ${r.frequency}")
    }
    return LocalDateTime(next, wall.time)
}

/** Same bounded protocol-2 semantics as the frozen browser client. DST gaps advance; folds choose earlier. */
fun recurrenceAdvance(s: Schedule?, now: Instant = Clock.System.now()): JsonElement {
    val r = s?.recurrence ?: return JsonNull
    var current = parseUrbit(s.dueAt); var occurrence = s.occurrence
    val tz = zone(s.timezone); val end = r.endAt?.let(::parseUrbit)
    fun result(due: Instant?) = buildJsonObject { put("due-at", due?.let { JsonPrimitive(wireDate(it)) } ?: JsonNull); put("occurrence", occurrence) }
    if (r.frequency == "hourly") {
        val seconds = r.interval.toLong() * 3600
        require(seconds > 0)
        val steps = if (current <= now) (now.epochSeconds - current.epochSeconds) / seconds + 1 else 1
        require(steps <= 100000) { "Recurrence is too far overdue to advance safely" }
        occurrence += steps; current += (r.interval.toLong() * steps).hours
        return result(if ((r.maxOccurrences != null && occurrence >= r.maxOccurrences) || (end != null && current > end)) null else current)
    }
    repeat(100000) {
        occurrence++
        if (r.maxOccurrences != null && occurrence >= r.maxOccurrences) return result(null)
        current = nextWall(current.toLocalDateTime(tz), r).toInstant(tz)
        if (end != null && current > end) return result(null)
        if (current > now) return result(current)
    }
    error("Recurrence is too far overdue to advance safely")
}
