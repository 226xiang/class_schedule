package com.local.coursetable

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.local.coursetable.model.ColorPalette
import com.local.coursetable.model.CourseModel
import com.local.coursetable.model.CourseSessionModel
import com.local.coursetable.model.EditableCourse
import com.local.coursetable.model.ScheduleModel
import com.local.coursetable.model.ScheduleSnapshot
import com.local.coursetable.model.WeekType
import com.local.coursetable.ui.CourseTableUiState
import com.local.coursetable.ui.CourseTableViewModel
import com.local.coursetable.ui.toEditable
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val app = application as CourseTableApplication
            val viewModel: CourseTableViewModel = viewModel(
                factory = CourseTableViewModel.Factory(app.repository)
            )
            CourseTableTheme {
                CourseTableApp(viewModel)
            }
        }
    }
}

@Composable
private fun CourseTableTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = Color(0xFF256D5D),
            secondary = Color(0xFF4E6F9E),
            surface = Color(0xFFF7F8FC),
            background = Color(0xFFF0F3FA),
            error = Color(0xFFB3261E)
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseTableApp(viewModel: CourseTableViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycleCompat()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showManager by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<EditableCourse?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = context.displayName(uri).removeSuffix(".ics").ifBlank { "导入课表" }
            val text = withContext(Dispatchers.IO) { context.readText(uri) }
            viewModel.importIcs(name, text)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = viewModel.exportCurrentIcs()
            withContext(Dispatchers.IO) { context.writeText(uri, text) }
            viewModel.showUserMessage("已导出 ICS")
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                title = {
                    Column {
                        Text(
                            text = state.weekTitle(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${state.todayTitle()} · ${state.currentScheduleName()}",
                            color = Color(0xFF667085),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    if (!state.isViewingCurrentWeek()) {
                        IconButton(onClick = viewModel::backToToday) {
                            Icon(Icons.Default.Today, contentDescription = "回到本周")
                        }
                    }
                    IconButton(onClick = { importLauncher.launch(arrayOf("text/calendar", "text/*", "*/*")) }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "导入 ICS")
                    }
                    IconButton(
                        onClick = {
                            val fileName = "${state.currentScheduleName().ifBlank { "课程表" }}.ics"
                            exportLauncher.launch(fileName)
                        }
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = "导出 ICS")
                    }
                    IconButton(onClick = { showManager = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "课表管理")
                    }
                    IconButton(
                        onClick = {
                            editor = EditableCourse(
                                startWeek = state.displayWeek,
                                endWeek = maxOf(state.displayWeek, 18)
                            )
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加课程")
                    }
                }
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            val snapshot = state.snapshot
            if (snapshot == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("正在加载课表")
                }
            } else {
                WeekScheduleGrid(
                    snapshot = snapshot,
                    week = state.displayWeek,
                    nowDate = state.now.toLocalDate(),
                    nowTime = state.now.toLocalTime(),
                    onEmptyClick = { day, section ->
                        editor = EditableCourse(
                            dayOfWeek = day,
                            startSection = section,
                            endSection = if (section % 2 == 1) (section + 1).coerceAtMost(10) else section,
                            startWeek = state.displayWeek,
                            endWeek = maxOf(state.displayWeek, 18)
                        )
                    },
                    onCourseClick = { session, course ->
                        editor = session.toEditable(course)
                    },
                    onWeekSelected = viewModel::goToWeek
                )
            }
        }
    }

    if (showManager) {
        ScheduleManagerDialog(
            schedules = state.schedules,
            currentScheduleId = state.currentScheduleId,
            onDismiss = { showManager = false },
            onSelect = viewModel::selectSchedule,
            onCreate = viewModel::createSchedule,
            onRename = viewModel::renameSchedule,
            onDelete = viewModel::deleteSchedule
        )
    }

    editor?.let { value ->
        CourseEditorDialog(
            initial = value,
            onDismiss = { editor = null },
            onSave = {
                viewModel.saveCourse(it)
                editor = null
            },
            onDelete = {
                viewModel.deleteCourse(it)
                editor = null
            }
        )
    }
}

@Composable
private fun WeekScheduleGrid(
    snapshot: ScheduleSnapshot,
    week: Int,
    nowDate: LocalDate,
    nowTime: LocalTime,
    onEmptyClick: (dayOfWeek: Int, section: Int) -> Unit,
    onCourseClick: (CourseSessionModel, CourseModel) -> Unit,
    onWeekSelected: (Int) -> Unit
) {
    val rowHeight = 82.dp
    val timeWidth = 42.dp
    val headerHeight = 66.dp
    val currentPage = (week - 1).coerceIn(0, MAX_WEEK_PAGES - 1)
    val pagerState = rememberPagerState(
        initialPage = currentPage,
        pageCount = { MAX_WEEK_PAGES }
    )

    LaunchedEffect(week) {
        val targetPage = (week - 1).coerceIn(0, MAX_WEEK_PAGES - 1)
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val selectedWeek = page + 1
                if (selectedWeek != week) onWeekSelected(selectedWeek)
            }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp, vertical = 8.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) { page ->
            WeekSchedulePage(
                snapshot = snapshot,
                week = page + 1,
                nowDate = nowDate,
                nowTime = nowTime,
                rowHeight = rowHeight,
                timeWidth = timeWidth,
                headerHeight = headerHeight,
                modifier = Modifier.fillMaxSize(),
                onEmptyClick = onEmptyClick,
                onCourseClick = onCourseClick
            )
        }
    }
}

@Composable
private fun WeekSchedulePage(
    snapshot: ScheduleSnapshot,
    week: Int,
    nowDate: LocalDate,
    nowTime: LocalTime,
    rowHeight: Dp,
    timeWidth: Dp,
    headerHeight: Dp,
    modifier: Modifier = Modifier,
    onEmptyClick: (dayOfWeek: Int, section: Int) -> Unit,
    onCourseClick: (CourseSessionModel, CourseModel) -> Unit
) {
    val weekStart = snapshot.weekStartFor(week)
    val activeSessions = snapshot.sessionsForWeek(week)
    val vertical = rememberScrollState()
    val visibleDays = remember(activeSessions) {
        val weekendDays = activeSessions
            .map { it.dayOfWeek }
            .filter { it in 6..7 }
            .toSortedSet()
        ((1..5).toList() + weekendDays).distinct()
    }

    BoxWithConstraints(modifier = modifier.verticalScroll(vertical)) {
        val dayWidth = (maxWidth - timeWidth) / visibleDays.size.toFloat()

        Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.height(headerHeight)) {
            Box(Modifier.width(timeWidth).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                Text("${weekStart.monthValue}月", color = Color(0xFF667085), fontWeight = FontWeight.SemiBold)
            }
            visibleDays.forEach { day ->
                val date = weekStart.plusDays((day - 1).toLong())
                val isToday = date == nowDate
                DayHeader(
                    dayName = weekDayNames[day - 1],
                    date = date.dayOfMonth.toString(),
                    isToday = isToday,
                    width = dayWidth
                )
            }
        }

        Row {
            Column(modifier = Modifier.width(timeWidth)) {
                snapshot.sectionTimes.forEach { section ->
                    SectionLabel(
                        index = section.section,
                        start = section.start.format(timeFormatter),
                        end = section.end.format(timeFormatter),
                        height = rowHeight
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(dayWidth * visibleDays.size.toFloat())
                    .height(rowHeight * 10f)
            ) {
                visibleDays.forEachIndexed { columnIndex, day ->
                    val date = weekStart.plusDays((day - 1).toLong())
                    val isToday = date == nowDate
                    Box(
                        modifier = Modifier
                            .offset(x = dayWidth * columnIndex.toFloat())
                            .width(dayWidth)
                            .height(rowHeight * 10f)
                            .background(if (isToday) Color(0x14256D5D) else Color.Transparent)
                    )
                }
                visibleDays.forEachIndexed { columnIndex, day ->
                    repeat(10) { sectionIndex ->
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = dayWidth * columnIndex.toFloat(),
                                    y = rowHeight * sectionIndex.toFloat()
                                )
                                .width(dayWidth)
                                .height(rowHeight)
                                .border(0.5.dp, Color(0xFFE1E6EF))
                                .clickable { onEmptyClick(day, sectionIndex + 1) }
                        )
                    }
                }
                activeSessions.forEach { session ->
                    val course = snapshot.courseById[session.courseId] ?: return@forEach
                    val columnIndex = visibleDays.indexOf(session.dayOfWeek)
                    if (columnIndex == -1) return@forEach
                    val date = weekStart.plusDays((session.dayOfWeek - 1).toLong())
                    val isCurrent = date == nowDate &&
                        nowTime >= snapshot.sectionTimes.first { it.section == session.startSection }.start &&
                        nowTime <= snapshot.sectionTimes.first { it.section == session.endSection }.end
                    CourseCard(
                        course = course,
                        isCurrent = isCurrent,
                        modifier = Modifier
                            .offset(
                                x = dayWidth * columnIndex.toFloat() + 3.dp,
                                y = rowHeight * (session.startSection - 1).toFloat() + 3.dp
                            )
                            .width(dayWidth - 6.dp)
                            .height(rowHeight * (session.endSection - session.startSection + 1).toFloat() - 6.dp)
                            .clickable { onCourseClick(session, course) }
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun DayHeader(dayName: String, date: String, isToday: Boolean, width: Dp) {
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isToday) Color(0x20256D5D) else Color.Transparent),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(dayName, color = if (isToday) Color(0xFF256D5D) else Color(0xFF98A2B3), fontWeight = FontWeight.Bold)
        Text(date, color = if (isToday) Color(0xFF101828) else Color(0xFF98A2B3), fontSize = 18.sp)
    }
}

@Composable
private fun SectionLabel(index: Int, start: String, end: String, height: Dp) {
    Column(
        modifier = Modifier
            .height(height)
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(index.toString(), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF101828))
        Text(start, color = Color(0xFF667085), fontSize = 12.sp)
        Text(end, color = Color(0xFF667085), fontSize = 12.sp)
    }
}

@Composable
private fun CourseCard(course: CourseModel, isCurrent: Boolean, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(7.dp),
        border = if (isCurrent) BorderStroke(2.dp, Color.White) else null,
        colors = CardDefaults.cardColors(containerColor = Color(course.colorArgb))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                course.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            if (course.location.isNotBlank()) {
                Text("@${course.location}", color = Color.White, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 3)
            }
            if (course.teacher.isNotBlank()) {
                Text(course.teacher, color = Color.White, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ScheduleManagerDialog(
    schedules: List<ScheduleModel>,
    currentScheduleId: Long,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<ScheduleModel?>(null) }
    var renameName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("课表管理") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("新课表名称") }
                    )
                    Button(onClick = {
                        onCreate(newName.ifBlank { "新课表" })
                        newName = ""
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    schedules.forEach { schedule ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelect(schedule.id) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = schedule.id == currentScheduleId,
                                onClick = { onSelect(schedule.id) }
                            )
                            Text(schedule.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = {
                                renameTarget = schedule
                                renameName = schedule.name
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "重命名")
                            }
                            IconButton(onClick = { onDelete(schedule.id) }, enabled = schedules.size > 1) {
                                Icon(Icons.Default.Delete, contentDescription = "删除")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名课表") },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    label = { Text("名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(target.id, renameName)
                    renameTarget = null
                }) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun CourseEditorDialog(
    initial: EditableCourse,
    onDismiss: () -> Unit,
    onSave: (EditableCourse) -> Unit,
    onDelete: (EditableCourse) -> Unit
) {
    var edit by remember(initial) { mutableStateOf(initial) }
    var startWeek by remember(initial) { mutableStateOf(initial.startWeek.toString()) }
    var endWeek by remember(initial) { mutableStateOf(initial.endWeek.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.sessionId == 0L) "添加课程" else "编辑课程") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = edit.name,
                    onValueChange = { edit = edit.copy(name = it) },
                    label = { Text("课程名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = edit.location,
                        onValueChange = { edit = edit.copy(location = it) },
                        label = { Text("地点") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = edit.teacher,
                        onValueChange = { edit = edit.copy(teacher = it) },
                        label = { Text("教师") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text("星期", fontWeight = FontWeight.SemiBold)
                ChoiceRow {
                    weekDayNames.forEachIndexed { index, name ->
                        FilterChip(
                            selected = edit.dayOfWeek == index + 1,
                            onClick = { edit = edit.copy(dayOfWeek = index + 1) },
                            label = { Text(name) }
                        )
                    }
                }
                Text("节次", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionStepper("开始", edit.startSection) {
                        edit = edit.copy(startSection = it, endSection = maxOf(it, edit.endSection))
                    }
                    SectionStepper("结束", edit.endSection) {
                        edit = edit.copy(endSection = maxOf(it, edit.startSection))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startWeek,
                        onValueChange = { startWeek = it.filter(Char::isDigit) },
                        label = { Text("起始周") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endWeek,
                        onValueChange = { endWeek = it.filter(Char::isDigit) },
                        label = { Text("结束周") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text("周次规则", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WeekType.entries.forEach { type ->
                        FilterChip(
                            selected = edit.weekType == type,
                            onClick = { edit = edit.copy(weekType = type) },
                            label = {
                                Text(
                                    when (type) {
                                        WeekType.ALL -> "每周"
                                        WeekType.ODD -> "单周"
                                        WeekType.EVEN -> "双周"
                                        WeekType.CUSTOM -> "自定义"
                                    }
                                )
                            }
                        )
                    }
                }
                if (edit.weekType == WeekType.CUSTOM) {
                    OutlinedTextField(
                        value = edit.customWeeks,
                        onValueChange = { edit = edit.copy(customWeeks = it) },
                        label = { Text("自定义周，例如 1,3,5-7") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text("颜色", fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ColorPalette.colors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .border(
                                    width = if (edit.colorArgb == color) 3.dp else 1.dp,
                                    color = if (edit.colorArgb == color) Color(0xFF101828) else Color.White,
                                    shape = CircleShape
                                )
                                .clickable { edit = edit.copy(colorArgb = color) }
                        )
                    }
                }
                OutlinedTextField(
                    value = edit.note,
                    onValueChange = { edit = edit.copy(note = it) },
                    label = { Text("备注") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val start = startWeek.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val end = endWeek.toIntOrNull()?.coerceAtLeast(start) ?: start
                onSave(edit.copy(startWeek = start, endWeek = end))
            }) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("保存")
            }
        },
        dismissButton = {
            Row {
                if (initial.sessionId != 0L) {
                    TextButton(onClick = { onDelete(edit) }) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("删除")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("取消")
                }
            }
        }
    )
}

@Composable
private fun ChoiceRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() }
    )
}

@Composable
private fun SectionStepper(label: String, value: Int, onValueChange: (Int) -> Unit) {
    AssistChip(
        onClick = {},
        label = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label)
                IconButton(onClick = { onValueChange((value - 1).coerceAtLeast(1)) }) {
                    Text("-", fontSize = 20.sp)
                }
                Text(value.toString(), fontWeight = FontWeight.Bold)
                IconButton(onClick = { onValueChange((value + 1).coerceAtMost(10)) }) {
                    Text("+", fontSize = 18.sp)
                }
            }
        }
    )
}

@Composable
private fun CourseTableUiState.todayTitle(): String {
    val date = now.toLocalDate()
    return "${date.year}/${date.monthValue}/${date.dayOfMonth}"
}

@Composable
private fun CourseTableUiState.weekTitle(): String {
    val suffix = if (isViewingCurrentWeek()) "" else "(非本周)"
    return "第${displayWeek}周$suffix"
}

private fun CourseTableUiState.isViewingCurrentWeek(): Boolean {
    val currentWeek = snapshot?.weekForDate(now.toLocalDate())?.coerceAtLeast(1) ?: displayWeek
    return displayWeek == currentWeek
}

private fun CourseTableUiState.currentScheduleName(): String =
    schedules.firstOrNull { it.id == currentScheduleId }?.name ?: snapshot?.schedule?.name ?: "课程表"

private fun android.content.Context.displayName(uri: Uri): String {
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
    }
    return uri.lastPathSegment.orEmpty()
}

private fun android.content.Context.readText(uri: Uri): String =
    contentResolver.openInputStream(uri)?.use { input ->
        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
    }.orEmpty()

private fun android.content.Context.writeText(uri: Uri, text: String) {
    contentResolver.openOutputStream(uri)?.use { output ->
        output.writer(Charsets.UTF_8).use { it.write(text) }
    }
}

@Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateWithLifecycleCompat():
    State<T> = collectAsState()

private val weekDayNames = listOf("一", "二", "三", "四", "五", "六", "日")
private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private const val MAX_WEEK_PAGES = 520
