package com.local.coursetable.ics

import com.local.coursetable.model.CourseModel
import com.local.coursetable.model.CourseSessionModel
import com.local.coursetable.model.ScheduleModel
import com.local.coursetable.model.ScheduleSnapshot
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsCourseTableCodecTest {
    @Test
    fun wakeUpSampleParsesExpectedShape() {
        val parsed = IcsCourseTableCodec.parse(sampleIcs(), "大二下")

        assertEquals(LocalDate.of(2026, 3, 2), parsed.firstWeekStart)
        assertEquals(4, parsed.courses.size)
        assertEquals(5, parsed.sessions.size)
        assertEquals(10, parsed.sectionTimes.size)
    }

    @Test
    fun wakeUpSampleCalculatesCurrentWeekAndOccurrences() {
        val snapshot = snapshotFrom(IcsCourseTableCodec.parse(sampleIcs(), "大二下"))

        assertEquals(12, snapshot.weekForDate(LocalDate.of(2026, 5, 24)))
        assertEquals(LocalDate.of(2026, 5, 18), snapshot.weekStartFor(12))
        assertEquals(5, snapshot.sessionsForWeek(12).size)
    }

    @Test
    fun exportedIcsCanBeParsedAgain() {
        val first = snapshotFrom(IcsCourseTableCodec.parse(sampleIcs(), "大二下"))
        val exported = IcsCourseTableCodec.export(first)
        val second = IcsCourseTableCodec.parse(exported, "roundtrip")

        assertEquals(first.courses.size, second.courses.size)
        assertEquals(first.sessions.size, second.sessions.size)
        assertTrue(exported.contains("BEGIN:VCALENDAR"))
        assertTrue(exported.contains("SUMMARY:数据库原理及安全"))
    }

    private fun snapshotFrom(parsed: ParsedSchedule): ScheduleSnapshot {
        val courseIdByKey = parsed.courses.mapIndexed { index, course -> course.localKey to (index + 1L) }.toMap()
        val courses = parsed.courses.mapIndexed { index, course ->
            CourseModel(
                id = index + 1L,
                scheduleId = 1,
                name = course.name,
                teacher = course.teacher,
                location = course.location,
                colorArgb = course.colorArgb,
                note = course.note
            )
        }
        val sessions = parsed.sessions.mapIndexed { index, session ->
            CourseSessionModel(
                id = index + 1L,
                scheduleId = 1,
                courseId = courseIdByKey.getValue(session.courseLocalKey),
                dayOfWeek = session.dayOfWeek,
                startSection = session.startSection,
                endSection = session.endSection,
                startWeek = session.startWeek,
                endWeek = session.endWeek,
                weekType = session.weekType,
                customWeeks = session.customWeeks
            )
        }
        return ScheduleSnapshot(
            schedule = ScheduleModel(1, parsed.name, parsed.firstWeekStart),
            sectionTimes = parsed.sectionTimes,
            courses = courses,
            sessions = sessions
        )
    }

    private fun sampleIcs(): String = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//YZune//WakeUpSchedule//EN
        BEGIN:VEVENT
        UID:test-1
        SUMMARY:数据库原理及安全
        DTSTART;TZID=Asia/Shanghai:20260302T081000
        DTEND;TZID=Asia/Shanghai:20260302T095000
        RRULE:FREQ=WEEKLY;UNTIL=20260628T160000Z;INTERVAL=1
        LOCATION:信息楼408 王海霞
        DESCRIPTION:第1 - 2节\n信息楼408\n王海霞
        END:VEVENT
        BEGIN:VEVENT
        UID:test-2
        SUMMARY:数据库原理及安全
        DTSTART;TZID=Asia/Shanghai:20260302T102000
        DTEND;TZID=Asia/Shanghai:20260302T120000
        RRULE:FREQ=WEEKLY;UNTIL=20260628T160000Z;INTERVAL=1
        LOCATION:信息楼408 王海霞
        DESCRIPTION:第3 - 4节\n信息楼408\n王海霞
        END:VEVENT
        BEGIN:VEVENT
        UID:test-3
        SUMMARY:面向对象程序设计
        DTSTART;TZID=Asia/Shanghai:20260303T081000
        DTEND;TZID=Asia/Shanghai:20260303T095000
        RRULE:FREQ=WEEKLY;UNTIL=20260629T160000Z;INTERVAL=1
        LOCATION:信息楼202 朱林琴
        DESCRIPTION:第1 - 2节\n信息楼202\n朱林琴
        END:VEVENT
        BEGIN:VEVENT
        UID:test-4
        SUMMARY:大学体育
        DTSTART;TZID=Asia/Shanghai:20260304T155500
        DTEND;TZID=Asia/Shanghai:20260304T173500
        RRULE:FREQ=WEEKLY;UNTIL=20260630T160000Z;INTERVAL=1
        LOCATION:室外
        DESCRIPTION:第7 - 8节\n室外
        END:VEVENT
        BEGIN:VEVENT
        UID:test-5
        SUMMARY:网络空间安全导论
        DTSTART;TZID=Asia/Shanghai:20260306T081000
        DTEND;TZID=Asia/Shanghai:20260306T095000
        RRULE:FREQ=WEEKLY;UNTIL=20260702T160000Z;INTERVAL=1
        LOCATION:信息楼408 王硕
        DESCRIPTION:第1 - 2节\n信息楼408\n王硕
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()
}
