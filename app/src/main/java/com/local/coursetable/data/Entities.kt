package com.local.coursetable.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.local.coursetable.model.CourseModel
import com.local.coursetable.model.CourseSessionModel
import com.local.coursetable.model.ScheduleModel
import com.local.coursetable.model.SectionTimeModel
import com.local.coursetable.model.WeekType
import java.time.LocalDate
import java.time.LocalTime

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val firstWeekStartEpochDay: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "section_times",
    primaryKeys = ["scheduleId", "section"],
    foreignKeys = [
        ForeignKey(
            entity = ScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("scheduleId")]
)
data class SectionTimeEntity(
    val scheduleId: Long,
    val section: Int,
    val startMinute: Int,
    val endMinute: Int
)

@Entity(
    tableName = "courses",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("scheduleId")]
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long,
    val name: String,
    val teacher: String,
    val location: String,
    val colorArgb: Int,
    val note: String
)

@Entity(
    tableName = "course_sessions",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleEntity::class,
            parentColumns = ["id"],
            childColumns = ["scheduleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("scheduleId"), Index("courseId")]
)
data class CourseSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleId: Long,
    val courseId: Long,
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int,
    val startWeek: Int,
    val endWeek: Int,
    val weekType: String,
    val customWeeks: String = ""
)

fun ScheduleEntity.toModel() = ScheduleModel(
    id = id,
    name = name,
    firstWeekStart = LocalDate.ofEpochDay(firstWeekStartEpochDay)
)

fun SectionTimeEntity.toModel() = SectionTimeModel(
    section = section,
    start = LocalTime.of(startMinute / 60, startMinute % 60),
    end = LocalTime.of(endMinute / 60, endMinute % 60)
)

fun CourseEntity.toModel() = CourseModel(
    id = id,
    scheduleId = scheduleId,
    name = name,
    teacher = teacher,
    location = location,
    colorArgb = colorArgb,
    note = note
)

fun CourseSessionEntity.toModel() = CourseSessionModel(
    id = id,
    scheduleId = scheduleId,
    courseId = courseId,
    dayOfWeek = dayOfWeek,
    startSection = startSection,
    endSection = endSection,
    startWeek = startWeek,
    endWeek = endWeek,
    weekType = runCatching { WeekType.valueOf(weekType) }.getOrDefault(WeekType.ALL),
    customWeeks = customWeeks
)

fun SectionTimeModel.toEntity(scheduleId: Long) = SectionTimeEntity(
    scheduleId = scheduleId,
    section = section,
    startMinute = start.hour * 60 + start.minute,
    endMinute = end.hour * 60 + end.minute
)
