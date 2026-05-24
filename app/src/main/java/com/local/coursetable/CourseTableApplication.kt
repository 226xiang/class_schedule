package com.local.coursetable

import android.app.Application
import com.local.coursetable.data.CourseRepository

class CourseTableApplication : Application() {
    val repository by lazy { CourseRepository(this) }
}
