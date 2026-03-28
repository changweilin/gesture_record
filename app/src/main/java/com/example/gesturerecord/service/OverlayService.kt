package com.example.gesturerecord.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.gesturerecord.GestureApp
import com.example.gesturerecord.R
import com.example.gesturerecord.data.GestureDao
import com.example.gesturerecord.data.GestureItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var captureView: TransparentCaptureView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var captureParams: WindowManager.LayoutParams

    // slot 按鈕列表提升為 field，初始化一次後共用
    private lateinit var slotButtons: List<Button>

    private var combinationId: Long = -1
    private var combinationName: String = ""

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var gestureDao: GestureDao

    private var selectedSlot: Int = -1 // 0 to SLOT_COUNT-1

    // 路徑點位解析快取：key = slotIndex，儲存時清除對應 key，避免重複解析序列化字串
    private val parsedPointsCache = HashMap<Int, List<Pair<Float, Float>>>()

    // For path recording
    private var tempGesturePoints: List<Pair<Float, Float>>? = null
    private var tempGestureDuration: Long = 0

    // For smart click recording
    private var tempActionType: Int = -1 // 0 = Path, 1 = UI Click
    private var tempNodeText: String? = null
    private var tempNodeViewId: String? = null
    private var tempNodeClassName: String? = null

    override fun onCreate() {
        super.onCreate()
        gestureDao = (application as GestureApp).database.gestureDao()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlayView()
        createCaptureView()
        startForegroundService()
    }

    private fun startForegroundService() {
        val channelId = "OverlayServiceChannel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Gesture Record Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Gesture Service")
            .setContentText("Overlay is running")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        // foregroundServiceType specialUse is required in API 34+ for floating windows
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            combinationId = it.getLongExtra(EXTRA_COMBINATION_ID, -1)
            combinationName = it.getStringExtra(EXTRA_COMBINATION_NAME) ?: "Gesture Combo"
            overlayView.findViewById<TextView>(R.id.tvComboName).text = combinationName
            loadSlots()
        }
        return START_NOT_STICKY
    }

    private fun createOverlayView() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.layout_overlay, null)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
        }

        // 初始化 slotButtons（唯一一次），使用直接 R.id 參照避免反射
        slotButtons = listOf(
            overlayView.findViewById(R.id.slot0),
            overlayView.findViewById(R.id.slot1),
            overlayView.findViewById(R.id.slot2),
            overlayView.findViewById(R.id.slot3),
            overlayView.findViewById(R.id.slot4),
            overlayView.findViewById(R.id.slot5),
            overlayView.findViewById(R.id.slot6),
            overlayView.findViewById(R.id.slot7),
            overlayView.findViewById(R.id.slot8)
        )

        setupDraggableHeader()
        setupButtons()

        windowManager.addView(overlayView, params)
    }

    private fun createCaptureView() {
        captureView = TransparentCaptureView(this)
        captureView.onGestureComplete = {
            tempGesturePoints = ArrayList(captureView.recordedPoints)
            tempGestureDuration = captureView.durationMs
            windowManager.removeView(captureView)
            Toast.makeText(this, "錄製完成 (暫存)", Toast.LENGTH_SHORT).show()
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        captureParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
    }

    private fun setupDraggableHeader() {
        val header = overlayView.findViewById<View>(R.id.llDraggableHeader)
        header.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(overlayView, params)
                        return true
                    }
                }
                return false
            }
        })

        overlayView.findViewById<View>(R.id.btnClose).setOnClickListener {
            stopSelf()
        }
    }

    private fun setupButtons() {
        val btnSmartRecord = overlayView.findViewById<Button>(R.id.btnSmartRecord)
        val btnRecord = overlayView.findViewById<Button>(R.id.btnRecord)
        val btnSave = overlayView.findViewById<Button>(R.id.btnSave)
        val btnPlayTemp = overlayView.findViewById<Button>(R.id.btnPlayTemp)
        val btnPlaySelected = overlayView.findViewById<Button>(R.id.btnPlaySelected)

        btnSmartRecord.setOnClickListener {
            val service = GestureAccessibilityService.instance
            if (service == null) {
                Toast.makeText(this, "無障礙服務未啟動", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Toast.makeText(this, "進入智能錄製模式，請點擊任何按鈕", Toast.LENGTH_SHORT).show()
            // Hide the overlay temporarily while user clicks
            overlayView.visibility = View.GONE
            service.startSmartRecord { nodeInfo ->
                // Callback invoked when a click is detected
                scope.launch(Dispatchers.Main) {
                    overlayView.visibility = View.VISIBLE
                    tempActionType = 1 // 1 for UI Click
                    tempNodeText = nodeInfo.text?.toString()
                    tempNodeViewId = nodeInfo.viewIdResourceName
                    tempNodeClassName = nodeInfo.className?.toString()

                    val desc = tempNodeText ?: tempNodeViewId ?: tempNodeClassName ?: "未知元件"
                    Toast.makeText(this@OverlayService, "已捕捉點擊: $desc", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnRecord.setOnClickListener {
            captureView.reset()
            windowManager.addView(captureView, captureParams)
            Toast.makeText(this, "請開始在螢幕上滑動", Toast.LENGTH_SHORT).show()
        }

        slotButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                selectedSlot = index
                slotButtons.forEach { it.setBackgroundColor(android.graphics.Color.TRANSPARENT) }
                button.setBackgroundColor(android.graphics.Color.LTGRAY)
            }
        }

        btnSave.setOnClickListener {
            if (selectedSlot == -1) {
                Toast.makeText(this, "請先選擇一個手勢槽", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (tempGesturePoints.isNullOrEmpty() && tempActionType == -1) {
                Toast.makeText(this, "請先錄製手勢或點擊", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            scope.launch {
                val existing = gestureDao.getItemForSlot(combinationId, selectedSlot)
                if (existing == null) {
                    val item = if (tempActionType == 1) {
                        GestureItem(
                            combinationId = combinationId,
                            slotIndex = selectedSlot,
                            actionType = 1,
                            nodeText = tempNodeText,
                            nodeViewId = tempNodeViewId,
                            nodeClassName = tempNodeClassName,
                            durationMs = 0
                        )
                    } else {
                        val pathStr = tempGesturePoints!!.joinToString(";") { "${it.first},${it.second}" }
                        GestureItem(
                            combinationId = combinationId,
                            slotIndex = selectedSlot,
                            actionType = 0,
                            serializedPathData = pathStr,
                            durationMs = tempGestureDuration
                        )
                    }

                    gestureDao.insertItem(item)
                    parsedPointsCache.remove(selectedSlot) // 清除舊快取
                    withContext(Dispatchers.Main) {
                        slotButtons[selectedSlot].text = if (tempActionType == 1) "C" else "S"
                        Toast.makeText(this@OverlayService, "已儲存至槽位 ${selectedSlot + 1}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@OverlayService, "該槽位已有紀錄，請選擇空槽位", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        btnPlayTemp.setOnClickListener {
            if (tempActionType == 1) {
                playSmartClick(tempNodeText, tempNodeViewId, tempNodeClassName)
            } else if (!tempGesturePoints.isNullOrEmpty()) {
                playGesture(tempGesturePoints!!, tempGestureDuration)
            } else {
                Toast.makeText(this, "無暫存動作可播放", Toast.LENGTH_SHORT).show()
            }
        }

        btnPlaySelected.setOnClickListener {
            if (selectedSlot == -1) {
                Toast.makeText(this, "請先選擇一個手勢槽", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            scope.launch {
                val item = gestureDao.getItemForSlot(combinationId, selectedSlot)
                withContext(Dispatchers.Main) {
                    if (item == null) {
                        Toast.makeText(this@OverlayService, "所選槽位為空", Toast.LENGTH_SHORT).show()
                    } else {
                        if (item.actionType == 1) {
                            playSmartClick(item.nodeText, item.nodeViewId, item.nodeClassName)
                        } else {
                            val points = parsedPointsCache.getOrPut(item.slotIndex) {
                                item.serializedPathData.split(";").mapNotNull {
                                    val parts = it.split(",")
                                    if (parts.size == 2) Pair(parts[0].toFloat(), parts[1].toFloat())
                                    else null
                                }
                            }
                            if (points.isNotEmpty()) {
                                playGesture(points, item.durationMs)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadSlots() {
        scope.launch {
            val items = gestureDao.getItemsForCombination(combinationId)
            withContext(Dispatchers.Main) {
                items.forEach { item ->
                    if (item.slotIndex in 0 until SLOT_COUNT) {
                        slotButtons[item.slotIndex].text = if (item.actionType == 1) "C" else "S"
                    }
                }
            }
        }
    }

    private fun playGesture(points: List<Pair<Float, Float>>, durationMs: Long) {
        val service = GestureAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, "無障礙服務未啟動", Toast.LENGTH_SHORT).show()
            return
        }
        service.playGesture(points, durationMs)
    }

    private fun playSmartClick(text: String?, viewId: String?, className: String?) {
        val service = GestureAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, "無障礙服務未啟動", Toast.LENGTH_SHORT).show()
            return
        }
        service.playSmartClick(text, viewId, className)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        if (::windowManager.isInitialized) {
            if (::overlayView.isInitialized) {
                try {
                    windowManager.removeView(overlayView)
                } catch (e: Exception) {
                    Log.e(TAG, "移除 overlay view 失敗", e)
                }
            }
            if (::captureView.isInitialized) {
                try {
                    windowManager.removeView(captureView)
                } catch (e: Exception) {
                    Log.e(TAG, "移除 capture view 失敗", e)
                }
            }
        }
    }

    override fun onBind(intent: Intent): IBinder? = null

    companion object {
        private const val TAG = "OverlayService"
        const val SLOT_COUNT = 9
        const val EXTRA_COMBINATION_ID = "COMBINATION_ID"
        const val EXTRA_COMBINATION_NAME = "COMBINATION_NAME"
    }
}
