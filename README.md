# 课程表

一个完全本地运行的 Android 课程表 App，使用 Kotlin、Jetpack Compose、Material 3 和 Room 实现。

## 已实现

- 多课表本地管理：新建、重命名、删除、切换。
- WakeUp 优先兼容的 `.ics` 导入：支持课程名、地点、教师、节次、每周循环、起止周。
- `.ics` 导出：导出 UTF-8 iCalendar 文件，可再导入本 App。
- 周视图课表：周一到周日、1-10 节、当天高亮、当前课程高亮。
- 图形化手动编辑：点击空白节次新增课程，点击课程编辑或删除。
- 启动图标使用项目中的 `课程表图标.png`，已复制到 Android 资源目录。
- 单元测试覆盖内置 WakeUp 风格样例的导入、周计算、当周课程展开、导出再导入。

## 构建

当前目录是标准 Android Gradle 工程，已生成 Gradle Wrapper。当前 Windows 中文路径下构建时，工程已通过 `gradle.properties` 启用 `android.overridePathCheck=true`，并把 Gradle 构建输出重定向到 `D:/codex-course-build`。

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

本机调试时已安装的 Android SDK 位于：

```text
D:/codex-android-sdk
```

如果在中文用户名或中文项目路径下遇到 Gradle native loader 问题，可临时设置：

```powershell
$env:_JAVA_OPTIONS='-Djava.io.tmpdir=D:\codex-gradle-tmp'
$env:GRADLE_USER_HOME='D:\codex-gradle-user-home'
```

Debug APK 已复制到项目根目录：

```text
课程表-debug.apk
```

## 说明

- App 不申请网络权限，不依赖远程服务器。
- 默认时区为 `Asia/Shanghai`，一周从周一开始。
- 默认节次为 1-10 节，时间参考 WakeUp 截图和样例日历。
- `.ics` 导入以 WakeUp 导出格式为优先兼容目标，也支持常见 iCalendar `VEVENT`、`SUMMARY`、`LOCATION`、`DESCRIPTION`、`DTSTART/DTEND` 和 weekly `RRULE`。如果通用 `.ics` 没有“第1 - 2节”这类节次描述，App 会尽量按开始/结束时间匹配默认节次。
