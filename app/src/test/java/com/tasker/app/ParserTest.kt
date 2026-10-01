package com.tasker.app

import com.tasker.app.domain.*
import java.time.*
import org.junit.Test
import org.junit.Assert.*

class ParserTest {
    val today = LocalDate.of(2026, 10, 1) // Thursday
    val now = LocalTime.of(10, 0)
    fun p(s: String) = NlpParser.parse(s, today, now).also { println("$s => $it") }

    @Test fun basic() {
        val r = p("Call mom tomorrow at 5pm #family !1")
        assertEquals("Call mom", r.title); assertEquals(today.plusDays(1), r.date); assertEquals(LocalTime.of(17,0), r.time)
        assertEquals(listOf("family"), r.tags); assertEquals(3, r.priority)
    }
    @Test fun recurring() {
        val r = p("Team sync every monday 10:00-11:00 +Work @office")
        assertEquals("Team sync", r.title); assertEquals("WEEKLY:1", r.recurrence); assertEquals(LocalDate.of(2026,10,5), r.date)
        assertEquals(LocalTime.of(10,0), r.time); assertEquals(LocalTime.of(11,0), r.endTime); assertEquals("Work", r.project); assertEquals(listOf("@office"), r.tags)
    }
    @Test fun range() {
        val r = p("Write report from mon to fri remind me 1h before")
        assertEquals("Write report", r.title); assertEquals(LocalDate.of(2026,10,5), r.date); assertEquals(LocalDate.of(2026,10,9), r.endDate); assertEquals(60, r.reminderOffsetMin)
    }
    @Test fun duration() {
        val r = p("Gym today at 7pm for 90 min *")
        assertEquals("Gym", r.title); assertEquals(today, r.date); assertEquals(LocalTime.of(19,0), r.time); assertEquals(90, r.durationMin); assertTrue(r.top3)
    }
    @Test fun waiting() {
        val r = p("Contract waiting for Sarah")
        assertEquals("Contract", r.title); assertEquals("Sarah", r.waitingFor); assertEquals(2, r.gtd)
    }
    @Test fun dates() {
        assertEquals(LocalDate.of(2026,12,25), p("Xmas dec 25").date)
        assertEquals(LocalDate.of(2027,3,5), p("Thing 5 march 2027").date)
        assertEquals(LocalDate.of(2026,10,8), p("Pay rent in 1 week").date)
        assertEquals(LocalDate.of(2026,10,3), p("Hike this weekend").date)
        assertEquals("Buy 2 - 3 apples", p("Buy 2 - 3 apples").title)
        assertEquals(LocalTime.of(15,0), p("Dentist 3-5pm friday").time)
        assertEquals(LocalDate.of(2026,10,2), p("Dentist 3-5pm friday").date)
        assertEquals(LocalTime.of(20,0), p("Movie tonight").time)
        assertEquals("Read book", p("Read book /someday").title)
        assertEquals(LocalTime.of(10,30), p("Call in 30 min").time)
        assertEquals(LocalDate.of(2026,11,15), p("Ship 2026-11-15").date)
        assertEquals("DAILY", p("Meditate daily").recurrence)
        assertEquals(2, p("x p2").priority)
        assertEquals(LocalTime.of(9,0), p("Standup at 9am").time)
        assertEquals(LocalDate.of(2026,10,2), p("Standup at 9am").date)
    }
}
class RecTest {
    @Test fun rec() {
        val d = LocalDate.of(2026, 10, 2) // Fri
        assertEquals(LocalDate.of(2026,10,5), Recurrence.next("WEEKDAYS", d))
        assertEquals(LocalDate.of(2026,10,7), Recurrence.next("WEEKLY:3", d))
        assertEquals(LocalDate.of(2026,11,2), Recurrence.next("MONTHLY", d))
    }
}
