package com.local.coursetable.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.room.withTransaction
import com.local.coursetable.ics.IcsCourseTableCodec
import com.local.coursetable.model.DefaultSectionTimes
import com.local.coursetable.model.EditableCourse
import com.local.coursetable.model.ScheduleSnapshot
import com.local.coursetable.model.weekMonday
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class CourseRepository(
    context: Context,
    private val database: AppDatabase = AppDatabase.get(context)
) {
    private val dao = database.dao()
    private val preferences: SharedPreferences =
        context.getSharedPreferences("course_table_state", Context.MODE_PRIVATE)
    private val currentScheduleId = MutableStateFlow(preferences.getLong(KEY_CURRENT_SCHEDULE_ID, 0L))

    fun observeSchedules() = dao.observeSchedules().map { list -> list.map { it.toModel() } }

    fun observeCurrentScheduleId(): Flow<Long> = currentScheduleId

    fun observeSnapshot(scheduleId: Long): Flow<ScheduleSnapshot?> {
        if (scheduleId == 0L) return MutableStateFlow<ScheduleSnapshot?>(null)
        return combine(
            dao.observeSchedule(scheduleId),
            dao.observeSectionTimes(scheduleId),
            dao.observeCourses(scheduleId),
            dao.observeSessions(scheduleId)
        ) { schedule, sectionTimes, courses, sessions ->
            schedule?.let {
                ScheduleSnapshot(
                    schedule = it.toModel(),
                    sectionTimes = sectionTimes.map { item -> item.toModel() },
                    courses = courses.map { item -> item.toModel() },
                    sessions = sessions.map { item -> item.toModel() }
                )
            }
        }
    }

    suspend fun ensureInitialSchedule() {
        if (dao.getSchedules().isNotEmpty()) return
        createSchedule(
            name = "我的课表",
            firstWeekStart = LocalDate.now(ZoneId.of("Asia/Shanghai")).weekMonday()
        )
    }

    suspend fun ensureCurrentSchedule() {
        ensureInitialSchedule()
        val schedules = dao.getSchedules()
        val current = currentScheduleId.value
        if (current == 0L || schedules.none { it.id == current }) {
            schedules.firstOrNull()?.id?.let { setCurrentSchedule(it) }
        }
    }

    suspend fun setCurrentSchedule(scheduleId: Long) {
        preferences.edit { putLong(KEY_CURRENT_SCHEDULE_ID, scheduleId) }
        currentScheduleId.value = scheduleId
    }

    suspend fun createSchedule(
        name: String,
        firstWeekStart: LocalDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).weekMonday()
    ): Long {
        val unique = uniqueScheduleName(name.ifBlank { "新课表" })
        val now = System.currentTimeMillis()
        val scheduleId = database.withTransaction {
            val id = dao.insertSchedule(
                ScheduleEntity(
                    name = unique,
                    firstWeekStartEpochDay = firstWeekStart.toEpochDay(),
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
            dao.insertSectionTimes(DefaultSectionTimes.items.map { it.toEntity(id) })
            id
        }
        setCurrentSchedule(scheduleId)
        return scheduleId
    }

    suspend fun renameSchedule(scheduleId: Long, name: String) {
        val schedule = dao.getSchedule(scheduleId) ?: return
        val clean = name.trim().ifBlank { schedule.name }
        dao.updateSchedule(schedule.copy(name = clean, updatedAtMillis = System.currentTimeMillis()))
    }

    suspend fun deleteSchedule(scheduleId: Long) {
        dao.deleteSchedule(scheduleId)
        val remaining = dao.getSchedules()
        val nextId = remaining.firstOrNull()?.id ?: createSchedule("我的课表")
        setCurrentSchedule(nextId)
    }

    suspend fun importIcs(scheduleName: String, text: String): Long {
        val parsed = IcsCourseTableCodec.parse(text, uniqueScheduleName(scheduleName))
        val now = System.currentTimeMillis()
        val scheduleId = database.withTransaction {
            val id = dao.insertSchedule(
                ScheduleEntity(
                    name = parsed.name,
                    firstWeekStartEpochDay = parsed.firstWeekStart.toEpochDay(),
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
            dao.insertSectionTimes(parsed.sectionTimes.map { it.toEntity(id) })
            val courseIds = mutableMapOf<String, Long>()
            parsed.courses.forEach { course ->
                courseIds[course.localKey] = dao.insertCourse(
                    CourseEntity(
                        scheduleId = id,
                        name = course.name,
                        teacher = course.teacher,
                        location = course.location,
                        colorArgb = course.colorArgb,
                        note = course.note
                    )
                )
            }
            parsed.sessions.forEach { session ->
                val courseId = courseIds[session.courseLocalKey] ?: return@forEach
                dao.insertSession(
                    CourseSessionEntity(
                        scheduleId = id,
                        courseId = courseId,
                        dayOfWeek = session.dayOfWeek,
                        startSection = session.startSection,
                        endSection = session.endSection,
                        startWeek = session.startWeek,
                        endWeek = session.endWeek,
                        weekType = session.weekType.name,
                        customWeeks = session.customWeeks
                    )
                )
            }
            id
        }
        setCurrentSchedule(scheduleId)
        return scheduleId
    }

    suspend fun exportIcs(scheduleId: Long): String {
        val snapshot = getSnapshot(scheduleId) ?: return IcsCourseTableCodec.export(emptySnapshot())
        return IcsCourseTableCodec.export(snapshot)
    }

    suspend fun getSnapshot(scheduleId: Long): ScheduleSnapshot? {
        val schedule = dao.getSchedule(scheduleId) ?: return null
        return ScheduleSnapshot(
            schedule = schedule.toModel(),
            sectionTimes = dao.getSectionTimes(scheduleId).map { it.toModel() },
            courses = dao.getCourses(scheduleId).map { it.toModel() },
            sessions = dao.getSessions(scheduleId).map { it.toModel() }
        )
    }

    suspend fun saveCourse(scheduleId: Long, edit: EditableCourse) {
        val courseName = edit.name.trim().ifBlank { "未命名课程" }
        database.withTransaction {
            val courseId = if (edit.courseId == 0L) {
                dao.insertCourse(
                    CourseEntity(
                        scheduleId = scheduleId,
                        name = courseName,
                        teacher = edit.teacher.trim(),
                        location = edit.location.trim(),
                        colorArgb = edit.colorArgb,
                        note = edit.note.trim()
                    )
                )
            } else {
                dao.updateCourse(
                    CourseEntity(
                        id = edit.courseId,
                        scheduleId = scheduleId,
                        name = courseName,
                        teacher = edit.teacher.trim(),
                        location = edit.location.trim(),
                        colorArgb = edit.colorArgb,
                        note = edit.note.trim()
                    )
                )
                edit.courseId
            }

            val session = CourseSessionEntity(
                id = edit.sessionId,
                scheduleId = scheduleId,
                courseId = courseId,
                dayOfWeek = edit.dayOfWeek.coerceIn(1, 7),
                startSection = edit.startSection.coerceIn(1, 10),
                endSection = edit.endSection.coerceIn(edit.startSection.coerceIn(1, 10), 10),
                startWeek = edit.startWeek.coerceAtLeast(1),
                endWeek = edit.endWeek.coerceAtLeast(edit.startWeek.coerceAtLeast(1)),
                weekType = edit.weekType.name,
                customWeeks = edit.customWeeks.trim()
            )
            if (edit.sessionId == 0L) {
                dao.insertSession(session)
            } else {
                dao.updateSession(session)
            }
        }
    }

    suspend fun deleteCourseSession(edit: EditableCourse) {
        if (edit.sessionId == 0L) return
        database.withTransaction {
            dao.deleteSession(edit.sessionId)
            if (edit.courseId != 0L && dao.countSessionsForCourse(edit.courseId) == 0) {
                dao.deleteCourse(edit.courseId)
            }
        }
    }

    private suspend fun uniqueScheduleName(base: String): String {
        val clean = base.trim().ifBlank { "导入课表" }
        val names = dao.getSchedules().map { it.name }.toSet()
        if (clean !in names) return clean
        var index = 2
        while ("$clean ($index)" in names) index++
        return "$clean ($index)"
    }

    private fun emptySnapshot() = ScheduleSnapshot(
        schedule = com.local.coursetable.model.ScheduleModel(
            id = 0,
            name = "空课表",
            firstWeekStart = LocalDate.now(ZoneId.of("Asia/Shanghai")).weekMonday()
        ),
        sectionTimes = DefaultSectionTimes.items,
        courses = emptyList(),
        sessions = emptyList()
    )

    companion object {
        private const val KEY_CURRENT_SCHEDULE_ID = "current_schedule_id"
    }
}
