package com.shortvideoguard

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * 核心无障碍服务
 * 监听短视频应用的前台状态，触发屏幕截图和内容分析
 */
class GuardAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "GuardAccessibilityService"
        const val ACTION_STOP_SERVICE = "com.shortvideoguard.STOP_SERVICE"
        const val ACTION_UPDATE_THRESHOLD = "com.shortvideoguard.UPDATE_THRESHOLD"
        const val EXTRA_THRESHOLD = "threshold"
        
        @Volatile
        var isRunning = false
            private set
        
        // 目标短视频应用包名
        val TARGET_PACKAGES = setOf(
            "com.ss.android.ugc.aweme",      // 抖音
            "com.ss.android.ugc.aweme.lite", // 抖音极速版
            "com.zhiliaoapp.musically",      // TikTok
            "com.smile.gifmaker",            // 快手
            "com.kuaishou.nebula",           // 快手极速版
            "com.xingin.xhs"                 // 小红书
        )
        
        // 检测间隔（毫秒）
        const val DETECTION_INTERVAL = 1200L
        
        // 默认阈值（0.0 - 1.0）
        var detectionThreshold = 0.65f
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var overlayManager: OverlayManager
    private lateinit var contentAnalyzer: ContentAnalyzer
    private var isTargetAppActive = false
    private var isAnalyzing = false
    private var lastPackageName: String? = null

    private val detectionRunnable = object : Runnable {
        override fun run() {
            if (isTargetAppActive && !isAnalyzing) {
                captureAndAnalyze()
            }
            handler.postDelayed(this, DETECTION_INTERVAL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        overlayManager = OverlayManager(this)
        contentAnalyzer = ContentAnalyzer()
        
        val filter = IntentFilter().apply {
            addAction(ACTION_STOP_SERVICE)
            addAction(ACTION_UPDATE_THRESHOLD)
        }
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(serviceReceiver, filter)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.i(TAG, "无障碍服务已连接")
        handler.post(detectionRunnable)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                val packageName = event.packageName?.toString()
                if (packageName != null && packageName != lastPackageName) {
                    lastPackageName = packageName
                    val wasActive = isTargetAppActive
                    isTargetAppActive = TARGET_PACKAGES.any { packageName.contains(it) }
                    
                    if (isTargetAppActive != wasActive) {
                        Log.i(TAG, "目标应用状态变化: $packageName, active=$isTargetAppActive")
                        if (!isTargetAppActive) {
                            // 离开目标应用，移除遮挡
                            overlayManager.hideOverlay()
                        }
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(detectionRunnable)
        overlayManager.hideOverlay()
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceReceiver)
        } catch (_: Exception) { }
        Log.i(TAG, "服务已销毁")
    }

    private fun captureAndAnalyze() {
        if (!isTargetAppActive) return
        
        isAnalyzing = true
        
        // 使用 AccessibilityService 的 takeScreenshot API (API 30+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
                if (display != null) {
                    takeScreenshot(
                        display.displayId,
                        applicationContext.mainExecutor,
                        object : TakeScreenshotCallback {
                            override fun onSuccess(screenshot: ScreenshotResult) {
                                val bitmap = screenshot.hardwareBuffer?.let { buffer ->
                                    val bmp = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                                    buffer.close()
                                    bmp
                                }
                                
                                if (bitmap != null) {
                                    analyzeAndOverlay(bitmap)
                                } else {
                                    isAnalyzing = false
                                }
                            }

                            override fun onError(errorCode: Int) {
                                Log.e(TAG, "截图失败: $errorCode")
                                isAnalyzing = false
                            }
                        }
                    )
                } else {
                    analyzeWithoutScreenshot()
                }
            } catch (e: Exception) {
                Log.e(TAG, "截图调用异常", e)
                analyzeWithoutScreenshot()
            }
        } else {
            // API < 30 使用替代方案（通过辅助功能节点分析）
            analyzeWithoutScreenshot()
        }
    }

    private fun analyzeAndOverlay(bitmap: Bitmap) {
        try {
            val score = contentAnalyzer.analyze(bitmap)
            Log.d(TAG, "内容分析得分: $score (阈值: $detectionThreshold)")
            
            if (score >= detectionThreshold) {
                // 检测到不适宜内容，显示遮挡层
                handler.post {
                    overlayManager.showOverlay(score)
                }
            } else {
                // 内容安全，确保遮挡层已隐藏
                handler.post {
                    overlayManager.hideOverlay()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "分析过程出错", e)
        } finally {
            // 不要回收 bitmap，可能还在使用
            isAnalyzing = false
        }
    }

    private fun analyzeWithoutScreenshot() {
        // 低版本替代方案：通过节点信息分析（准确率较低）
        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            // 分析当前界面的节点特征
            val hasVideoContent = findVideoNodes(rootNode)
            if (!hasVideoContent) {
                overlayManager.hideOverlay()
            }
            rootNode.recycle()
        }
        isAnalyzing = false
    }

    private fun findVideoNodes(node: AccessibilityNodeInfo): Boolean {
        // 通过内容描述或类名检测视频内容
        val contentDesc = node.contentDescription?.toString() ?: ""
        val className = node.className?.toString() ?: ""
        
        if (contentDesc.contains("视频") || 
            contentDesc.contains("video") ||
            className.contains("VideoView") ||
            className.contains("SurfaceView") ||
            className.contains("TextureView")) {
            return true
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findVideoNodes(child)) {
                return true
            }
        }
        return false
    }

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_STOP_SERVICE -> {
                    overlayManager.hideOverlay()
                    disableSelf()
                }
                ACTION_UPDATE_THRESHOLD -> {
                    intent.getFloatExtra(EXTRA_THRESHOLD, 0.65f)?.let {
                        detectionThreshold = it
                    }
                }
            }
        }
    }
}
