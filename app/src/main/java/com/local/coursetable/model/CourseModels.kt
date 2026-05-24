package com.local.coursetable.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.time.temporal.ChronoUnit

enum class WeekType {
    ALL,
    ODD,
    EVEN,
    CUSTOM
}

data class ScheduleModel(
    val id: Long,
    val name: String,
    val firstWeekStart: LocalDate
)

data class SectionTimeModel(
    val section: Int,
    val start: LocalTime,
    val end: LocalTime
)

data class CourseModel(
    val id: Long,
    val scheduleId: Long,
    val name: String,
    val teacher: String,
    val location: String,
    val colorArgb: Int,
    val note: String
)

data class CourseSessionModel(
    val id: Long,
    val scheduleId: Long,
    val courseId: Long,
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val startWeek: Int,
    val endWeek: Int,
    val weekType: WeekType,
    val customWeeks: String = ""
) {
    fun isActiveInWeek(week: Int): Boolean {
        if (week !in startWeek..endWeek) return false
        return when (weekType) {
            WeekType.ALL -> true
            WeekType.ODD -> week % 2 == 1
            WeekType.EVEN -> week % 2 == 0
            WeekType.CUSTOM -> week in parseWeekList(customWeeks)
        }
    }
}

data class ScheduleSnapshot(
    val schedule: ScheduleModel,
    val sectionTimes: List<SectionTimeModel>,
    val courses: List<CourseModel>,
    val sessions: List<CourseSessionModel>
) {
    val courseById: Map<Long, CourseModel> = courses.associateBy { it.id }

    fun sessionsForWeek(week: Int): List<CourseSessionModel> =
        sessions.filter { it.isActiveInWeek(week) }

    fun weekStartFor(week: Int): LocalDate =
        schedule.firstWeekStart.plusWeeks((week - 1).coerceAtLeast(0).toLong())

    fun weekForDate(date: LocalDate): Int {
        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return ChronoUnit.WEEKS.between(schedule.firstWeekStart, monday).toInt() + 1
    }
}

data class EditableCourse(
    val courseId: Long = 0,
    val sessionId: Long = 0,
    val name: String = "",
    val teacher: String = "",
    val location: String = "",
    val colorArgb: Int = ColorPalette.colors.first(),
    val note: String = "",
    val dayOfWeek: Int = 1,
    val startSection: Int = 1,
    val endSection: Int = 2,
    val startWeek: Int = 1,
    val endWeek: Int = 18,
    val weekType: WeekType = WeekType.ALL,
    val customWeeks: String = ""
)

object DefaultSectionTimes {
    val items = listOf(
        SectionTimeModel(1, LocalTime.of(8, 10), LocalTime.of(8, 55)),
        SectionTimeModel(2, LocalTime.of(9, 5), LocalTime.of(9, 50)),
        SectionTimeModel(3, LocalTime.of(10, 20), LocalTime.of(11, 5)),
        SectionTimeModel(4, LocalTime.of(11, 15), LocalTime.of(12, 0)),
        SectionTimeModel(5, LocalTime.of(14, 0), LocalTime.of(14, 45)),
        SectionTimeModel(6, LocalTime.of(14, 55), LocalTime.of(15, 40)),
        SectionTimeModel(7, LocalTime.of(15, 55), LocalTime.of(16, 40)),
        SectionTimeModel(8, LocalTime.of(16, 50), LocalTime.of(17, 35)),
        SectionTimeModel(9, LocalTime.of(18, 30), LocalTime.of(19, 15)),
        SectionTimeModel(10, LocalTime.of(19, 25), LocalTime.of(20, 10))
    )

    fun startOf(section: Int, custom: List<SectionTimeModel> = items): LocalTime =
        custom.firstOrNull { it.section == section }?.start ?: items.first { it.section == section }.start

    fun endOf(section: Int, custom: List<SectionTimeModel> = items): LocalTime =
        custom.firstOrNull { it.section == section }?.end ?: items.first { it.section == section }.end
}

object ColorPalette {
    val colors = listOf(
        0xFFB6B34E.toInt(),
        0xFF2F7D49.toInt(),
        0xFF49A9BE.toInt(),
        0xFFE91F52.toInt(),
        0xFF2B9BE6.toInt(),
        0xFFA8417E.toInt(),
        0xFF6ED4DE.toInt(),
        0xFF21D6A5.toInt(),
        0xFFF35A75.toInt(),
        0xFF9668EA.toInt(),
        0xFFDF8D32.toInt(),
        0xFF6475D9.toInt()
    )
}

fun LocalDate.weekMonday(): LocalDate =
    with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun parseWeekList(value: String): Set<Int> {
    if (value.isBlank()) return emptySet()
    return value.split(',', '，', ' ')
        .mapNotNull { token ->
            val clean = token.trim()
            if (clean.isBlank()) return@mapNotNull null
            if ('-' in clean) {
                val start = clean.substringBefore('-').toIntOrNull() ?: return@mapNotNull null
                val end = clean.substringAfter('-').toIntOrNull() ?: return@mapNotNull null
                (start.coerceAtLeast(1)..end.coerceAtLeast(start)).toSet()
            } else {
                setOfNotNull(clean.toIntOrNull()?.coerceAtLeast(1))
            }
        }
        .flatten()
        .toSortedSet()
}
