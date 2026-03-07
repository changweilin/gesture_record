package com.example.gesturerecord.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class GestureAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    private var smartRecordCallback: ((android.view.accessibility.AccessibilityNodeInfo) -> Unit)? = null
    private var isSmartRecording = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (isSmartRecording && event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val source = event.source
            if (source != null) {
                // We got a click! Return it and stop recording.
                isSmartRecording = false
                smartRecordCallback?.invoke(source)
                smartRecordCallback = null
            }
        }
    }

    fun startSmartRecord(callback: (android.view.accessibility.AccessibilityNodeInfo) -> Unit) {
        smartRecordCallback = callback
        isSmartRecording = true
    }

    fun playGesture(points: List<Pair<Float, Float>>, durationMs: Long) {
        if (points.isEmpty()) return

        val path = Path()
        path.moveTo(points.first().first, points.first().second)
        for (i in 1 until points.size) {
            path.lineTo(points[i].first, points[i].second)
        }

        // Must be > 0 and <= max duration
        val safeDuration = if (durationMs < 50L) 50L else if (durationMs > 60000L) 60000L else durationMs

        val stroke = GestureDescription.StrokeDescription(path, 0, safeDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val success = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                // Toast might be shown, but could be noisy
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Toast.makeText(this@GestureAccessibilityService, "手勢播放被取消", Toast.LENGTH_SHORT).show()
            }
        }, null)
    }

    fun playSmartClick(text: String?, viewId: String?, className: String?) {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Toast.makeText(this, "無法獲取當前畫面結構", Toast.LENGTH_SHORT).show()
            return
        }

        val clicked = findAndClickNode(rootNode, text, viewId, className)
        if (!clicked) {
            Toast.makeText(this, "在畫面上找不到指定的元件", Toast.LENGTH_SHORT).show()
        }
        rootNode.recycle()
    }

    private fun findAndClickNode(
        node: android.view.accessibility.AccessibilityNodeInfo, 
        targetText: String?, 
        targetViewId: String?, 
        targetClassName: String?
    ): Boolean {
        // Fast paths: Search by ID or Text first if available
        if (!targetViewId.isNullOrEmpty()) {
            val nodesById = node.findAccessibilityNodeInfosByViewId(targetViewId)
            for (n in nodesById) {
                if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    nodesById.forEach { it.recycle() }
                    return true
                }
            }
            nodesById.forEach { it.recycle() }
        }

        if (!targetText.isNullOrEmpty()) {
            val nodesByText = node.findAccessibilityNodeInfosByText(targetText)
            for (n in nodesByText) {
                if (n.isClickable && n.className?.toString() == targetClassName) {
                    if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        nodesByText.forEach { it.recycle() }
                        return true
                    }
                }
            }
            nodesByText.forEach { it.recycle() }
        }

        // Slow path: Recursive search
        var result = false
        if (targetClassName != null && node.className?.toString() == targetClassName) {
            val textMatches = targetText == null || node.text?.toString() == targetText
            val idMatches = targetViewId == null || node.viewIdResourceName == targetViewId
            
            if (textMatches && idMatches && node.isClickable) {
                result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (result) return true
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                result = findAndClickNode(child, targetText, targetViewId, targetClassName)
                child.recycle()
                if (result) return true
            }
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    companion object {
        var instance: GestureAccessibilityService? = null
            private set
    }
}
