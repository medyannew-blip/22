package com.tasker.app.domain

import com.tasker.app.data.Gtd
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

data class ParsedTask(
    val title: String,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val endDate: LocalDate? = null,
    val endTime: LocalTime? = null,
    val durationMin: Int? = null,
    val tags: List<String> = emptyList(),
    val project: String? = null,
    val priority: Int? = null,
    val recurrence: String? = null,
    val reminderOffsetMin: Int? = null,
    val reminderTime: LocalTime? = null,
    val top3: Boolean = false,
    val gtd: Int? = null,
    val waitingFor: String? = null,
)

/**
 * Natural language quick-add parser.
 *
 * Examples:
 *  "Call mom tomorrow at 5pm #family !1"
 *  "Team sync every monday 10:00-11:00 +Work @office"
 *  "Write report from mon to fri remind me 1h before"
 *  "Gym today at 7pm for 90 min *"
 *  "Contract waiting for Sarah /waiting"
 */
object NlpParser {
    private const val WEEKDAY_FULL = "monday|tuesday|wednesday|thursday|friday|saturday|sunday"
    private const val WEEKDAY_SHORT = "mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun"
    private const val MONTHS =
        "january|february|march|april|may|june|july|august|september|october|november|december|" +
            "jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec"

    /** Date expressions (no capture groups of their own; resolved by [resolveDate]). */
    private val DATE = listOf(
        "day after tomorrow",
        "today", "tonight", "tomorrow", "tmrw", "tmr",
        "next week", "next month", "next year", "this weekend", "next weekend", "weekend",
        "in \\d+ (?:days?|weeks?|months?|years?)",
        "in (?:a|an|one) (?:day|week|month|year)",
        "(?:next |this )?(?:$WEEKDAY_FULL)",
        "(?:next |this |on )(?:$WEEKDAY_SHORT)",
        "\\d{4}-\\d{1,2}-\\d{1,2}",
        "\\d{1,2}[/.]\\d{1,2}(?:[/.]\\d{2,4})?",
        "(?:$MONTHS)\\.? \\d{1,2}(?:st|nd|rd|th)?(?:,? \\d{4})?",
        "\\d{1,2}(?:st|nd|rd|th)? (?:of )?(?:$MONTHS)(?: \\d{4})?",
    ).joinToString("|", "(?:", ")")

    private val DATE_IN_RANGE = "(?:$DATE|$WEEKDAY_SHORT)"

    private const val TIME = "(?:\\d{1,2}(?::\\d{2})?\\s?(?:am|pm|a\\.m\\.|p\\.m\\.)|\\d{1,2}:\\d{2}|noon|midnight)"

    private class Scanner(input: String) {
        var s = " " + input.replace('\n', ' ') + " "
        fun take(pattern: String): MatchResult? {
            val rx = Regex(pattern, setOf(RegexOption.IGNORE_CASE))
            val m = rx.find(s) ?: return null
            s = s.substring(0, m.range.first) + " " + s.substring(m.range.last + 1)
            return m
        }
        fun takeAll(pattern: String): List<MatchResult> {
            val out = mutableListOf<MatchResult>()
            while (true) out += take(pattern) ?: break
            return out
        }
    }

    fun parse(input: String, today: LocalDate = LocalDate.now(), now: LocalTime = LocalTime.now(), usDates: Boolean = true): ParsedTask {
        val sc = Scanner(input)
        val b = "(?<=\\s)"
        val e = "(?=\\s|[,.;!?]\\s)"

        // Tags, contexts and projects
        val tags = mutableListOf<String>()
        sc.takeAll("$b#([\\p{L}\\p{N}_\\-/]+)").forEach { tags += it.groupValues[1] }
        sc.takeAll("$b@([\\p{L}\\p{N}_\\-]+)").forEach { tags += "@" + it.groupValues[1] }
        val project = sc.take("$b\\+(?:\"([^\"]+)\"|([\\p{L}\\p{N}_\\-]+))")?.let {
            it.groupValues[1].ifEmpty { it.groupValues[2] }
        }

        // GTD slash commands
        var gtd: Int? = null
        sc.take("$b/(inbox|next|waiting|someday|maybe|ref|reference)$e")?.let {
            gtd = when (it.groupValues[1].lowercase()) {
                "inbox" -> Gtd.INBOX
                "next" -> Gtd.NEXT
                "waiting" -> Gtd.WAITING
                "ref", "reference" -> Gtd.REFERENCE
                else -> Gtd.SOMEDAY
            }
        }
        var waitingFor: String? = null
        sc.take("\\bwaiting (?:for|on) (?:\"([^\"]+)\"|([\\p{L}\\p{N}_\\-]+))")?.let {
            waitingFor = it.groupValues[1].ifEmpty { it.groupValues[2] }
            gtd = Gtd.WAITING
        }
        if (sc.take("$b(?:someday|maybe someday)$e") != null && gtd == null) gtd = Gtd.SOMEDAY

        // Priority & top-3
        var priority: Int? = null
        sc.take("$b!(1|2|3|high|medium|med|low)$e")?.let {
            priority = when (it.groupValues[1].lowercase()) {
                "1", "high" -> 3
                "2", "medium", "med" -> 2
                else -> 1
            }
        }
        if (priority == null) sc.take("${b}p([1-3])$e")?.let { priority = 4 - it.groupValues[1].toInt() }
        var top3 = false
        if (sc.take("$b(?:\\*|!top|top3|top 3)$e") != null) top3 = true
        if (priority == null) sc.take("$b(!!!|!!|!)$e")?.let { priority = it.groupValues[1].length }

        // Recurrence
        var recurrence: String? = null
        var recurrenceDay: DayOfWeek? = null
        sc.take("\\b(?:every|each) (\\d+) (days?|weeks?|months?)\\b")?.let {
            val n = it.groupValues[1].toInt()
            recurrence = when (it.groupValues[2].lowercase().first()) {
                'd' -> "DAYS:$n"
                'w' -> "WEEKS:$n"
                else -> "MONTHS:$n"
            }
        }
        if (recurrence == null) sc.take("\\b(?:every|each) ($WEEKDAY_FULL|$WEEKDAY_SHORT)\\b")?.let {
            recurrenceDay = weekday(it.groupValues[1])
            recurrence = "WEEKLY:${recurrenceDay!!.value}"
        }
        if (recurrence == null) sc.take("\\b(?:every|each) (day|weekday|week|month|year)\\b|\\b(daily|weekly|monthly|yearly|annually)\\b")?.let {
            recurrence = when ((it.groupValues[1] + it.groupValues[2]).lowercase()) {
                "day", "daily" -> "DAILY"
                "weekday" -> "WEEKDAYS"
                "week", "weekly" -> "WEEKLY"
                "month", "monthly" -> "MONTHLY"
                else -> "YEARLY"
            }
        }

        // Reminders
        var reminderOffset: Int? = null
        var reminderTime: LocalTime? = null
        sc.take("\\bremind(?: me)? (\\d+)\\s?(m|min|mins|minutes?|h|hr|hrs|hours?|d|days?) (?:before|early|earlier)\\b")?.let {
            reminderOffset = toMinutes(it.groupValues[1].toDouble(), it.groupValues[2])
        }
        if (reminderOffset == null) sc.take("\\bremind(?: me)? (?:at )?($TIME)")?.let { reminderTime = parseTime(it.groupValues[1]) }

        // Duration
        var duration: Int? = null
        sc.take("\\bfor (\\d+)\\s?h(?:ours?|rs?)?\\s?(\\d+)\\s?m(?:in|ins|inutes?)?\\b")?.let {
            duration = it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt()
        }
        if (duration == null) sc.take("\\bfor (\\d+(?:\\.\\d+)?)\\s?(m|min|mins|minutes?|h|hr|hrs|hours?)\\b")?.let {
            duration = toMinutes(it.groupValues[1].toDouble(), it.groupValues[2])
        }
        if (duration == null) sc.take("\\bfor (?:an|one) hour\\b")?.let { duration = 60 }
        if (duration == null) sc.take("\\bfor half an hour\\b")?.let { duration = 30 }

        // Relative time ("in 2 hours")
        var date: LocalDate? = null
        var time: LocalTime? = null
        var endDate: LocalDate? = null
        var endTime: LocalTime? = null
        sc.take("\\bin (\\d+)\\s?(m|min|mins|minutes?|h|hr|hrs|hours?)\\b")?.let {
            val dt = LocalDateTime.of(today, now).plusMinutes(toMinutes(it.groupValues[1].toDouble(), it.groupValues[2]).toLong())
            date = dt.toLocalDate(); time = dt.toLocalTime().withSecond(0).withNano(0)
        }

        // Date ranges
        sc.take("\\b(?:from )?($DATE_IN_RANGE) (?:to|until|till|through|-) ($DATE_IN_RANGE)\\b")?.let {
            val start = resolveDate(it.groupValues[1], today, usDates)
            val end = start?.let { st -> resolveDate(it.groupValues[2], st, usDates) }
            if (start != null && end != null) { date = start; endDate = if (end.isBefore(start)) start else end }
        }

        // Time ranges
        val rawRange = sc.take("\\b(?:from )?(\\d{1,2}(?::\\d{2})?\\s?(?:am|pm)?) ?(?:-|–|to|until|till) ?(\\d{1,2}(?::\\d{2})?\\s?(?:am|pm)?)\\b")
        if (rawRange != null) {
            val a = rawRange.groupValues[1]; val c = rawRange.groupValues[2]
            val looksLikeTime = listOf(a, c).any { it.contains(':') || it.contains("am", true) || it.contains("pm", true) } ||
                rawRange.value.trim().startsWith("from", true)
            val endT = parseTime(c)
            var startT = parseTime(a)
            if (looksLikeTime && startT != null && endT != null) {
                // "3-5pm": inherit the meridiem of the end time
                if (!a.contains("am", true) && !a.contains("pm", true) && c.contains("pm", true) && startT.hour < 12 && startT.hour + 12 <= endT.hour) {
                    startT = startT.plusHours(12)
                }
                time = startT; endTime = endT
            } else {
                // Not a time range: put the text back
                sc.s = sc.s.substring(0, rawRange.range.first) + rawRange.value + sc.s.substring(rawRange.range.first + 1)
            }
        }

        // Single date
        if (date == null) {
            sc.take("\\b(?:on |due |by |until |starting |start )?($DATE)\\b")?.let { m ->
                val word = m.groupValues[1].lowercase()
                date = resolveDate(word, today, usDates)
                if (word == "tonight" && time == null) time = LocalTime.of(20, 0)
            }
        }

        // Single time
        if (time == null) {
            val m = sc.take("\\b(?:at|@) (\\d{1,2}(?::\\d{2})?\\s?(?:am|pm|a\\.m\\.|p\\.m\\.)?)(?=\\s)")
                ?: sc.take("\\b(?:at )?($TIME)(?=\\s|[,.;])")
            if (m != null) {
                time = parseTime(m.groupValues[1], assumePmForSmall = true)
            } else {
                sc.take("\\b(?:in the )?(morning|afternoon|evening)\\b")?.let {
                    time = when (it.groupValues[1].lowercase()) {
                        "morning" -> LocalTime.of(9, 0)
                        "afternoon" -> LocalTime.of(14, 0)
                        else -> LocalTime.of(18, 0)
                    }
                }
            }
        }

        if (recurrenceDay != null && date == null) date = today.with(TemporalAdjusters.nextOrSame(recurrenceDay!!))
        if (recurrence != null && date == null) date = today
        if (time != null && date == null) date = if (time!!.isBefore(now) && endTime == null) today.plusDays(1) else today
        if (endTime != null && endDate == null) endDate = date
        if (reminderTime != null && date == null) date = today

        val title = sc.s
            .replace(Regex("\\s+"), " ")
            .trim()
            .replace(Regex("(?i)\\s+(on|at|by|due|from|and|for)$"), "")
            .trim(' ', ',', '-')

        return ParsedTask(
            title = title,
            date = date,
            time = time,
            endDate = endDate,
            endTime = endTime,
            durationMin = duration,
            tags = tags.distinct(),
            project = project,
            priority = priority,
            recurrence = recurrence,
            reminderOffsetMin = reminderOffset,
            reminderTime = reminderTime,
            top3 = top3,
            gtd = gtd,
            waitingFor = waitingFor,
        )
    }

    private fun toMinutes(n: Double, unit: String): Int = when (unit.lowercase().first()) {
        'h' -> (n * 60).toInt()
        'd' -> (n * 1440).toInt()
        else -> n.toInt()
    }

    private fun weekday(s: String): DayOfWeek = when (s.lowercase().take(3)) {
        "mon" -> DayOfWeek.MONDAY
        "tue" -> DayOfWeek.TUESDAY
        "wed" -> DayOfWeek.WEDNESDAY
        "thu" -> DayOfWeek.THURSDAY
        "fri" -> DayOfWeek.FRIDAY
        "sat" -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }

    private fun month(s: String): Int = when (s.lowercase().take(3)) {
        "jan" -> 1; "feb" -> 2; "mar" -> 3; "apr" -> 4; "may" -> 5; "jun" -> 6
        "jul" -> 7; "aug" -> 8; "sep" -> 9; "oct" -> 10; "nov" -> 11; else -> 12
    }

    fun parseTime(raw: String, assumePmForSmall: Boolean = false): LocalTime? {
        val s = raw.lowercase().replace(".", "").replace(" ", "")
        if (s == "noon") return LocalTime.NOON
        if (s == "midnight") return LocalTime.MIDNIGHT
        val m = Regex("^(\\d{1,2})(?::(\\d{2}))?(am|pm)?$").find(s) ?: return null
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        val ap = m.groupValues[3]
        if (min > 59) return null
        when (ap) {
            "am" -> { if (h > 12) return null; if (h == 12) h = 0 }
            "pm" -> { if (h > 12) return null; if (h < 12) h += 12 }
            else -> {
                if (h > 23) return null
                if (assumePmForSmall && m.groupValues[2].isEmpty() && h in 1..6) h += 12
            }
        }
        return LocalTime.of(h, min)
    }

    fun resolveDate(raw: String, today: LocalDate, usDates: Boolean = true): LocalDate? {
        val s = raw.lowercase().trim().replace(Regex("\\s+"), " ")
        when (s) {
            "today", "tonight" -> return today
            "tomorrow", "tmrw", "tmr" -> return today.plusDays(1)
            "day after tomorrow" -> return today.plusDays(2)
            "next week" -> return today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
            "next month" -> return today.plusMonths(1).withDayOfMonth(1)
            "next year" -> return today.plusYears(1).withDayOfYear(1)
            "this weekend", "weekend" -> return if (today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY) today
                else today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
            "next weekend" -> return today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)).let {
                if (today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY) it else it.plusWeeks(1)
            }
        }
        Regex("^in (\\d+|a|an|one) (day|week|month|year)s?$").find(s)?.let {
            val n = it.groupValues[1].toLongOrNull() ?: 1L
            return when (it.groupValues[2]) {
                "day" -> today.plusDays(n)
                "week" -> today.plusWeeks(n)
                "month" -> today.plusMonths(n)
                else -> today.plusYears(n)
            }
        }
        Regex("^(next |this |on )?($WEEKDAY_FULL|$WEEKDAY_SHORT)$").find(s)?.let {
            val d = weekday(it.groupValues[2])
            return if (it.groupValues[1].trim() == "next") today.with(TemporalAdjusters.next(d))
            else today.with(TemporalAdjusters.nextOrSame(d))
        }
        Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$").find(s)?.let {
            return runCatching { LocalDate.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()) }.getOrNull()
        }
        Regex("^(\\d{1,2})([/.])(\\d{1,2})(?:[/.](\\d{2,4}))?$").find(s)?.let {
            val a = it.groupValues[1].toInt(); val c = it.groupValues[3].toInt()
            val monthFirst = usDates && it.groupValues[2] == "/"
            val (mo, d) = if (monthFirst) a to c else c to a
            return withYear(mo, d, it.groupValues[4], today)
        }
        Regex("^($MONTHS)\\.? (\\d{1,2})(?:st|nd|rd|th)?(?:,? (\\d{4}))?$").find(s)?.let {
            return withYear(month(it.groupValues[1]), it.groupValues[2].toInt(), it.groupValues[3], today)
        }
        Regex("^(\\d{1,2})(?:st|nd|rd|th)? (?:of )?($MONTHS)(?: (\\d{4}))?$").find(s)?.let {
            return withYear(month(it.groupValues[2]), it.groupValues[1].toInt(), it.groupValues[3], today)
        }
        return null
    }

    private fun withYear(month: Int, day: Int, yearRaw: String, today: LocalDate): LocalDate? {
        if (yearRaw.isNotEmpty()) {
            var y = yearRaw.toInt()
            if (y < 100) y += 2000
            return runCatching { LocalDate.of(y, month, day) }.getOrNull()
        }
        val d = runCatching { LocalDate.of(today.year, month, day) }.getOrNull() ?: return null
        return if (d.isBefore(today)) d.plusYears(1) else d
    }
}
