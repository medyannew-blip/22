package com.tasker.app.domain

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Recurrence rules are compact strings:
 *  DAILY, WEEKDAYS, WEEKLY, WEEKLY:1,3 (ISO days), MONTHLY, YEARLY, DAYS:n, WEEKS:n, MONTHS:n
 */
object Recurrence {
    fun next(rule: String, from: LocalDate): LocalDate? {
        val parts = rule.split(":")
        val kind = parts[0].uppercase()
        val arg = parts.getOrNull(1)
        return when (kind) {
            "DAILY" -> from.plusDays(1)
            "WEEKDAYS" -> {
                var d = from.plusDays(1)
                while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) d = d.plusDays(1)
                d
            }
            "WEEKLY" -> {
                val days = arg?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.filter { it in 1..7 }.orEmpty()
                if (days.isEmpty()) from.plusWeeks(1)
                else {
                    var d = from.plusDays(1)
                    repeat(8) { if (d.dayOfWeek.value in days) return d; d = d.plusDays(1) }
                    from.plusWeeks(1)
                }
            }
            "MONTHLY" -> from.plusMonths(1)
            "YEARLY" -> from.plusYears(1)
            "DAYS" -> from.plusDays((arg?.toLongOrNull() ?: 1).coerceAtLeast(1))
            "WEEKS" -> from.plusWeeks((arg?.toLongOrNull() ?: 1).coerceAtLeast(1))
            "MONTHS" -> from.plusMonths((arg?.toLongOrNull() ?: 1).coerceAtLeast(1))
            else -> null
        }
    }
}
