package com.local.coursetable.ics

import com.local.coursetable.model.ColorPalette
import com.local.coursetable.model.CourseModel
import com.local.coursetable.model.CourseSessionModel
import com.local.coursetable.model.DefaultSectionTimes
import com.local.coursetable.model.ScheduleModel
import com.local.coursetable.model.ScheduleSnapshot
import com.local.coursetable.model.SectionTimeModel
import com.local.coursetable.model.WeekType
import com.local.coursetable.model.parseWeekList
import com.local.coursetable.model.weekMonday
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.max

data class ParsedSchedule(
    val name: String,
    val firstWeekStart: LocalDate,
    val sectionTimes: List<SectionTimeModel>,
    val courses: List<ParsedCourse>,
    val sessions: List<ParsedSession>
)

data class ParsedCourse(
    val localKey: String,
    val name: String,
    val teacher: String,
    val location: String,
    val colorArgb: Int,
    val note: String
)

data class ParsedSession(
    val courseLocalKey: String,
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val startWeek: Int,
    val endWeek: Int,
    val weekType: WeekType,
    val customWeeks: String = ""
)

object IcsCourseTableCodec {
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val localFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val utcFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    fun parse(text: String, scheduleName: String): ParsedSchedule {
        val parsedEvents = parseEvents(text).mapNotNull(::toEventInfo)
        val firstWeekStart = parsedEvents.minOfOrNull { it.start.toLocalDate() }?.weekMonday()
            ?: LocalDate.now(zone).weekMonday()
        val courses = linkedMapOf<String, ParsedCourse>()
        val sessions = mutableListOf<ParsedSession>()

        parsedEvents.forEach { event ->
            val startWeek = weekNumber(firstWeekStart, event.start.toLocalDate())
            val last = lastOccurrence(event.start, event.until, event.interval)
            val endWeek = max(startWeek, weekNumber(firstWeekStart, last.toLocalDate()))
            val key = listOf(event.summary, event.location, event.teacher).joinToString("|")
            val course = courses.getOrPut(key) {
                ParsedCourse(
                    localKey = key,
                    name = event.summary,
                    teacher = event.teacher,
                    location = event.location,
                    colorArgb = ColorPalette.colors[courses.size % ColorPalette.colors.size],
                    note = ""
                )
            }
            sessions += ParsedSession(
                courseLocalKey = course.localKey,
                dayOfWeek = event.start.dayOfWeek.value,
                startSection = event.startSection,
                endSection = event.endSection,
                startWeek = startWeek,
                endWeek = endWeek,
                weekType = event.weekTypeFor(startWeek)
            )
        }

        return ParsedSchedule(
            name = scheduleName.ifBlank { "导入课表" },
            firstWeekStart = firstWeekStart,
            sectionTimes = DefaultSectionTimes.items,
            courses = courses.values.toList(),
            sessions = sessions
        )
    }

    fun export(snapshot: ScheduleSnapshot): String {
        val builder = StringBuilder()
        builder.appendLine("BEGIN:VCALENDAR")
        builder.appendLine("VERSION:2.0")
        builder.appendLine("PRODID:-//LocalCourseTable//WakeUpCompatible//CN")
        builder.appendLine("CALSCALE:GREGORIAN")
        builder.appendLine("METHOD:PUBLISH")
        builder.appendLine("X-WR-CALNAME:${escape(snapshot.schedule.name)}")
        builder.appendLine("BEGIN:VTIMEZONE")
        builder.appendLine("TZID:Asia/Shanghai")
        builder.appendLine("X-LIC-LOCATION:Asia/Shanghai")
        builder.appendLine("BEGIN:STANDARD")
        builder.appendLine("TZNAME:CST")
        builder.appendLine("TZOFFSETFROM:+0800")
        builder.appendLine("TZOFFSETTO:+0800")
        builder.appendLine("DTSTART:19700101T000000")
        builder.appendLine("END:STANDARD")
        builder.appendLine("END:VTIMEZONE")

        snapshot.sessions.sortedWith(
            compareBy<CourseSessionModel> { it.dayOfWeek }
                .thenBy { it.startSection }
                .thenBy { it.startWeek }
        ).forEach { session ->
            val course = snapshot.courseById[session.courseId] ?: return@forEach
            val activeStartWeek = firstActiveWeek(session) ?: return@forEach
            if (session.weekType == WeekType.CUSTOM) {
                parseWeekList(session.customWeeks)
                    .filter { it in session.startWeek..session.endWeek }
                    .forEach { week -> appendEvent(builder, snapshot, course, session, week, week, false) }
            } else {
                val endWeek = lastActiveWeek(session) ?: activeStartWeek
                appendEvent(builder, snapshot, course, session, activeStartWeek, endWeek, true)
            }
        }

        builder.appendLine("END:VCALENDAR")
        return builder.toString()
    }

    private fun parseEvents(text: String): List<Map<String, IcsProperty>> {
        val unfolded = mutableListOf<String>()
        text.replace("\r\n", "\n").replace('\r', '\n').lines().forEach { raw ->
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && unfolded.isNotEmpty()) {
                unfolded[unfolded.lastIndex] = unfolded.last() + raw.drop(1)
            } else {
                unfolded += raw
            }
        }

        val result = mutableListOf<Map<String, IcsProperty>>()
        var current: MutableMap<String, IcsProperty>? = null
        unfolded.forEach { line ->
            when (line.uppercase()) {
                "BEGIN:VEVENT" -> current = linkedMapOf()
                "END:VEVENT" -> {
                    current?.let { result += it.toMap() }
                    current = null
                }
                else -> current?.let { map ->
                    parseProperty(line)?.let { map[it.name] = it }
                }
            }
        }
        return result
    }

    private fun parseProperty(line: String): IcsProperty? {
        val colon = line.indexOf(':')
        if (colon <= 0) return null
        val left = line.substring(0, colon)
        val value = line.substring(colon + 1)
        val parts = left.split(';')
        val params = parts.drop(1).mapNotNull {
            val idx = it.indexOf('=')
            if (idx > 0) it.substring(0, idx).uppercase() to it.substring(idx + 1) else null
        }.toMap()
        return IcsProperty(parts.first().uppercase(), params, value)
    }

    private fun toEventInfo(props: Map<String, IcsProperty>): EventInfo? {
        val summary = props["SUMMARY"]?.value?.unescapeIcs()?.trim().orEmpty()
        if (summary.isBlank()) return null
        val start = props["DTSTART"]?.let(::parseDateTime) ?: return null
        val end = props["DTEND"]?.let(::parseDateTime) ?: start.plusMinutes(45)
        val description = props["DESCRIPTION"]?.value?.unescapeIcs().orEmpty()
        val locationRaw = props["LOCATION"]?.value?.unescapeIcs()?.trim().orEmpty()
        val descLines = description.lines().map { it.trim() }.filter { it.isNotBlank() }
        val sectionRange = sectionRangeFromDescription(description)
            ?: sectionRangeFromTimes(start, end)
        val room = descLines.getOrNull(1)?.trim().orEmpty()
            .ifBlank { locationRaw.substringBeforeLast(' ', locationRaw).trim() }
        val teacher = descLines.getOrNull(2)?.trim().orEmpty()
            .ifBlank {
                if (locationRaw.contains(' ')) locationRaw.substringAfterLast(' ').trim() else ""
            }
        val rrule = parseRRule(props["RRULE"]?.value.orEmpty())
        val interval = rrule["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val until = rrule["UNTIL"]?.let(::parseUntil)
        return EventInfo(
            summary = summary,
            start = start,
            end = end,
            location = room,
            teacher = teacher,
            startSection = sectionRange.first,
            endSection = sectionRange.second,
            interval = interval,
            until = until
        )
    }

    private fun parseDateTime(property: IcsProperty): LocalDateTime {
        val value = property.value
        return if (value.endsWith("Z")) {
            val localUtc = LocalDateTime.parse(value.removeSuffix("Z"), localFormatter)
            localUtc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime()
        } else {
            LocalDateTime.parse(value, localFormatter)
        }
    }

    private fun parseUntil(value: String): LocalDateTime {
        return if (value.endsWith("Z")) {
            val localUtc = LocalDateTime.parse(value.removeSuffix("Z"), localFormatter)
            localUtc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDateTime()
        } else {
            LocalDateTime.parse(value, localFormatter)
        }
    }

    private fun parseRRule(value: String): Map<String, String> =
        value.split(';').mapNotNull {
            val idx = it.indexOf('=')
            if (idx > 0) it.substring(0, idx).uppercase() to it.substring(idx + 1) else null
        }.toMap()

    private fun sectionRangeFromDescription(description: String): Pair<Int, Int>? {
        val match = Regex("""(\d+)\s*-\s*(\d+)""").find(description) ?: return null
        val start = match.groupValues[1].toIntOrNull() ?: return null
        val end = match.groupValues[2].toIntOrNull() ?: return null
        return start.coerceIn(1, 10) to end.coerceIn(start, 10)
    }

    private fun sectionRangeFromTimes(start: LocalDateTime, end: LocalDateTime): Pair<Int, Int> {
        val startSection = DefaultSectionTimes.items.firstOrNull { it.start == start.toLocalTime() }?.section ?: 1
        val endSection = DefaultSectionTimes.items.firstOrNull { it.end == end.toLocalTime() }?.section ?: startSection
        return startSection to endSection.coerceAtLeast(startSection)
    }

    private fun lastOccurrence(start: LocalDateTime, until: LocalDateTime?, interval: Int): LocalDateTime {
        if (until == null) return start
        var current = start
        while (true) {
            val next = current.plusWeeks(interval.toLong())
            if (next > until) return current
            current = next
        }
    }

    private fun weekNumber(firstWeekStart: LocalDate, date: LocalDate): Int =
        ChronoUnit.WEEKS.between(firstWeekStart, date.weekMonday()).toInt() + 1

    private fun firstActiveWeek(session: CourseSessionModel): Int? =
        (session.startWeek..session.endWeek).firstOrNull { session.isActiveInWeek(it) }

    private fun lastActiveWeek(session: CourseSessionModel): Int? =
        (session.endWeek downTo session.startWeek).firstOrNull { session.isActiveInWeek(it) }

    private fun EventInfo.weekTypeFor(startWeek: Int): WeekType {
        if (interval != 2) return WeekType.ALL
        return if (startWeek % 2 == 1) WeekType.ODD else WeekType.EVEN
    }

    private fun String.unescapeIcs(): String =
        replace("\\n", "\n")
            .replace("\\N", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")

    private fun escape(value: String): String =
        value.replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace(",", "\\,")
            .replace(";", "\\;")

    private fun appendEvent(
        builder: StringBuilder,
        snapshot: ScheduleSnapshot,
        course: CourseModel,
        session: CourseSessionModel,
        activeStartWeek: Int,
        endWeek: Int,
        includeRRule: Boolean
    ) {
        val startDate = snapshot.schedule.firstWeekStart
            .plusWeeks((activeStartWeek - 1).toLong())
            .plusDays((session.dayOfWeek - 1).toLong())
        val untilDate = snapshot.schedule.firstWeekStart
            .plusWeeks((endWeek - 1).toLong())
            .plusDays((session.dayOfWeek - 1).toLong())
        val startTime = DefaultSectionTimes.startOf(session.startSection, snapshot.sectionTimes)
        val endTime = DefaultSectionTimes.endOf(session.endSection, snapshot.sectionTimes)
        val dtStart = LocalDateTime.of(startDate, startTime)
        val dtEnd = LocalDateTime.of(startDate, endTime)
        val untilUtc = LocalDateTime.of(untilDate, endTime).atZone(zone)
            .withZoneSameInstant(ZoneOffset.UTC)
            .toLocalDateTime()
            .format(utcFormatter)
        val interval = if (session.weekType == WeekType.ALL) 1 else 2
        val locationLine = course.location.trim()
        val teacherLine = course.teacher.trim()
        val locationProp = listOf(locationLine, teacherLine).filter { it.isNotBlank() }.joinToString(" ")
        val description = buildString {
            append("第${session.startSection} - ${session.endSection}节")
            if (locationLine.isNotBlank()) append('\n').append(locationLine)
            if (teacherLine.isNotBlank()) append('\n').append(teacherLine)
        }

        appendFolded(builder, "BEGIN:VEVENT")
        appendFolded(builder, "DTSTAMP:${LocalDateTime.now(ZoneOffset.UTC).format(utcFormatter)}")
        appendFolded(builder, "UID:CourseTable-${UUID.randomUUID()}")
        appendFolded(builder, "SUMMARY:${escape(course.name)}")
        appendFolded(builder, "DTSTART;TZID=Asia/Shanghai:${dtStart.format(localFormatter)}")
        appendFolded(builder, "DTEND;TZID=Asia/Shanghai:${dtEnd.format(localFormatter)}")
        if (includeRRule) {
            appendFolded(builder, "RRULE:FREQ=WEEKLY;UNTIL=$untilUtc;INTERVAL=$interval")
        }
        appendFolded(builder, "LOCATION:${escape(locationProp)}")
        appendFolded(builder, "DESCRIPTION:${escape(description)}")
        appendFolded(builder, "END:VEVENT")
    }

    private fun appendFolded(builder: StringBuilder, line: String) {
        if (line.length <= 74) {
            builder.appendLine(line)
            return
        }
        var rest = line
        builder.appendLine(rest.take(74))
        rest = rest.drop(74)
        while (rest.length > 73) {
            builder.appendLine(" " + rest.take(73))
            rest = rest.drop(73)
        }
        builder.appendLine(" $rest")
    }
}

private data class IcsProperty(
    val name: String,
    val params: Map<String, String>,
    val value: String
)

private data class EventInfo(
    val summary: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val location: String,
    val teacher: String,
    val startSection: Int,
    val endSection: Int,
    val interval: Int,
    val until: LocalDateTime?
)
