package com.example.gesturerecord

import android.app.Application
import com.example.gesturerecord.data.GestureDatabase

class GestureApp : Application() {
    val database: GestureDatabase by lazy { GestureDatabase.getDatabase(this) }
}
