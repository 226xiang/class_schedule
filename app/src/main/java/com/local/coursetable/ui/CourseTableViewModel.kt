package com.local.coursetable.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.local.coursetable.data.CourseRepository
import com.local.coursetable.model.CourseModel
import com.local.coursetable.model.CourseSessionModel
import com.local.coursetable.model.EditableCourse
import com.local.coursetable.model.ScheduleModel
import com.local.coursetable.model.ScheduleSnapshot
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CourseTableUiState(
    val schedules: List<ScheduleModel> = emptyList(),
    val currentScheduleId: Long = 0,
    val snapshot: ScheduleSnapshot? = null,
    val selectedWeek: Int? = null,
    val now: LocalDateTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai")),
    val message: String? = null
) {
    val displayWeek: Int
        get() = selectedWeek ?: snapshot?.weekForDate(now.toLocalDate())?.coerceAtLeast(1) ?: 1
}

@OptIn(ExperimentalCoroutinesApi::class)
class CourseTableViewModel(
    private val repository: CourseRepository
) : ViewModel() {
    private val selectedWeek = MutableStateFlow<Int?>(null)
    private val now = MutableStateFlow(LocalDateTime.now(zone))
    private val message = MutableStateFlow<String?>(null)

    private val currentScheduleId = repository.observeCurrentScheduleId()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    private val snapshot = currentScheduleId.flatMapLatest { repository.observeSnapshot(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val baseUiState = combine(
        repository.observeSchedules(),
        currentScheduleId,
        snapshot,
        selectedWeek,
        now
    ) { schedules, currentId, currentSnapshot, selected, currentNow ->
        CourseTableUiState(
            schedules = schedules,
            currentScheduleId = currentId,
            snapshot = currentSnapshot,
            selectedWeek = selected,
            now = currentNow
        )
    }

    val uiState = combine(baseUiState, message) { state, text ->
        state.copy(message = text)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CourseTableUiState())

    init {
        viewModelScope.launch { repository.ensureCurrentSchedule() }
        viewModelScope.launch {
            while (true) {
                now.value = LocalDateTime.now(zone)
                delay(60_000)
            }
        }
    }

    fun selectSchedule(scheduleId: Long) {
        viewModelScope.launch {
            repository.setCurrentSchedule(scheduleId)
            selectedWeek.value = null
        }
    }

    fun previousWeek() {
        val current = uiState.value.displayWeek
        selectedWeek.value = (current - 1).coerceAtLeast(1)
    }

    fun nextWeek() {
        selectedWeek.value = uiState.value.displayWeek + 1
    }

    fun goToWeek(week: Int) {
        selectedWeek.value = week.coerceAtLeast(1)
    }

    fun backToToday() {
        selectedWeek.value = null
    }

    fun createSchedule(name: String) {
        viewModelScope.launch {
            repository.createSchedule(name)
            selectedWeek.value = null
            showMessage("已创建课表")
        }
    }

    fun renameSchedule(scheduleId: Long, name: String) {
        viewModelScope.launch {
            repository.renameSchedule(scheduleId, name)
            showMessage("已重命名")
        }
    }

    fun deleteSchedule(scheduleId: Long) {
        viewModelScope.launch {
            repository.deleteSchedule(scheduleId)
            selectedWeek.value = null
            showMessage("已删除课表")
        }
    }

    fun importIcs(name: String, text: String) {
        viewModelScope.launch {
            runCatching { repository.importIcs(name, text) }
                .onSuccess {
                    selectedWeek.value = null
                    showMessage("已导入课表")
                }
                .onFailure { showMessage("导入失败：${it.message ?: "文件格式不支持"}") }
        }
    }

    suspend fun exportCurrentIcs(): String {
        val id = uiState.value.currentScheduleId
        return repository.exportIcs(id)
    }

    fun saveCourse(edit: EditableCourse) {
        val scheduleId = uiState.value.currentScheduleId
        if (scheduleId == 0L) return
        viewModelScope.launch {
            repository.saveCourse(scheduleId, edit)
            showMessage("已保存课程")
        }
    }

    fun deleteCourse(edit: EditableCourse) {
        viewModelScope.launch {
            repository.deleteCourseSession(edit)
            showMessage("已删除课程")
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    fun showUserMessage(text: String) {
        showMessage(text)
    }

    private fun showMessage(text: String) {
        message.value = text
    }

    class Factory(private val repository: CourseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CourseTableViewModel(repository) as T
        }
    }

    companion object {
        private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    }
}

fun CourseSessionModel.toEditable(course: CourseModel) = EditableCourse(
    courseId = course.id,
    sessionId = id,
    name = course.name,
    teacher = course.teacher,
    location = course.location,
    colorArgb = course.colorArgb,
    note = course.note,
    dayOfWeek = dayOfWeek,
    startSection = startSection,
    endSection = endSection,
    startWeek = startWeek,
    endWeek = endWeek,
    weekType = weekType,
    customWeeks = customWeeks
)
