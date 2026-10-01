package com.tasker.app.domain

import com.tasker.app.data.Task
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

fun today(): LocalDate = LocalDate.now()
fun LocalDate.epoch(): Long = toEpochDay()
fun Long.toDate(): LocalDate = LocalDate.ofEpochDay(this)
fun LocalTime.minutes(): Int = hour * 60 + minute
fun Int.toTime(): LocalTime = LocalTime.of((this / 60).coerceIn(0, 23), (this % 60).coerceIn(0, 59))

fun millisOf(day: Long, minutes: Int, zone: ZoneId = ZoneId.systemDefault()): Long =
    LocalDateTime.of(day.toDate(), minutes.toTime()).atZone(zone).toInstant().toEpochMilli()

fun Long.toLocalDateTime(zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), zone)

fun LocalDate.startOfWeek(weekStart: Int): LocalDate =
    with(TemporalAdjusters.previousOrSame(DayOfWeek.of(weekStart.coerceIn(1, 7))))

/** Moment the task is due: its explicit end, start + duration, or its start (end of day if untimed). */
fun Task.dueMillis(): Long? = when {
    endDate != null -> millisOf(endDate, endTime ?: (23 * 60 + 59))
    date != null && time != null && durationMin != null -> millisOf(date, time) + durationMin * 60_000L
    date != null -> millisOf(date, time ?: (23 * 60 + 59))
    else -> null
}

/** Moment the task starts, if it is timed. */
fun Task.startMillis(): Long? = if (date != null && time != null) millisOf(date, time) else null

fun Task.isOverdue(now: Long = System.currentTimeMillis()): Boolean {
    if (done) return false
    val due = dueMillis() ?: return false
    return due < now
}

/** Effective duration in minutes for timeline layouts. */
fun Task.blockMinutes(): Int {
    durationMin?.let { return it.coerceAtLeast(15) }
    if (endDate != null && endDate == date && endTime != null && time != null && endTime > time) return endTime - time
    return 30
}

/** Fractional index between two neighbours (either may be missing). */
fun orderBetween(before: Double?, after: Double?): Double = when {
    before == null && after == null -> 0.0
    before == null -> after!! - 1.0
    after == null -> before + 1.0
    else -> (before + after) / 2.0
}
