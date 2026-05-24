package com.local.coursetable.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseTableDao {
    @Query("SELECT * FROM schedules ORDER BY createdAtMillis ASC")
    fun observeSchedules(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules ORDER BY createdAtMillis ASC")
    suspend fun getSchedules(): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun getSchedule(id: Long): ScheduleEntity?

    @Query("SELECT * FROM schedules WHERE id = :id")
    fun observeSchedule(id: Long): Flow<ScheduleEntity?>

    @Query("SELECT * FROM section_times WHERE scheduleId = :scheduleId ORDER BY section ASC")
    fun observeSectionTimes(scheduleId: Long): Flow<List<SectionTimeEntity>>

    @Query("SELECT * FROM courses WHERE scheduleId = :scheduleId ORDER BY id ASC")
    fun observeCourses(scheduleId: Long): Flow<List<CourseEntity>>

    @Query("SELECT * FROM course_sessions WHERE scheduleId = :scheduleId ORDER BY dayOfWeek ASC, startSection ASC")
    fun observeSessions(scheduleId: Long): Flow<List<CourseSessionEntity>>

    @Query("SELECT * FROM section_times WHERE scheduleId = :scheduleId ORDER BY section ASC")
    suspend fun getSectionTimes(scheduleId: Long): List<SectionTimeEntity>

    @Query("SELECT * FROM courses WHERE scheduleId = :scheduleId ORDER BY id ASC")
    suspend fun getCourses(scheduleId: Long): List<CourseEntity>

    @Query("SELECT * FROM course_sessions WHERE scheduleId = :scheduleId ORDER BY dayOfWeek ASC, startSection ASC")
    suspend fun getSessions(scheduleId: Long): List<CourseSessionEntity>

    @Query("SELECT COUNT(*) FROM course_sessions WHERE courseId = :courseId")
    suspend fun countSessionsForCourse(courseId: Long): Int

    @Insert
    suspend fun insertSchedule(schedule: ScheduleEntity): Long

    @Update
    suspend fun updateSchedule(schedule: ScheduleEntity)

    @Query("DELETE FROM schedules WHERE id = :scheduleId")
    suspend fun deleteSchedule(scheduleId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSectionTimes(sectionTimes: List<SectionTimeEntity>)

    @Insert
    suspend fun insertCourse(course: CourseEntity): Long

    @Update
    suspend fun updateCourse(course: CourseEntity)

    @Query("DELETE FROM courses WHERE id = :courseId")
    suspend fun deleteCourse(courseId: Long)

    @Insert
    suspend fun insertSession(session: CourseSessionEntity): Long

    @Update
    suspend fun updateSession(session: CourseSessionEntity)

    @Query("DELETE FROM course_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("DELETE FROM courses WHERE scheduleId = :scheduleId")
    suspend fun deleteCoursesForSchedule(scheduleId: Long)
}
