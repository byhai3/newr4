package com.shortvideoguard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager

/**
 * 屏幕捕获管理器 (备用方案)
 * 当无障碍服务的 takeScreenshot 不可用时，使用 MediaProjection 作为备选
 * 注意：此方式需要用户授予录屏权限，体验不如无障碍截图无缝
 */
class ScreenCaptureManager(private val context: Context) {
    
    companion object {
        const val TAG = "ScreenCaptureManager"
        const val MAX_WIDTH = 720  // 限制截图分辨率以提升性能
        const val MAX_HEIGHT = 1280
    }
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    
    /**
     * 使用 MediaProjection 截图
     * 需要在 Activity 中先获取用户授权（startActivityForResult）
     */
    fun captureWithMediaProjection(projection: MediaProjection, callback: (Bitmap?) -> Unit) {
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = windowManager.defaultDisplay
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getMetrics(metrics)
            
            // 限制分辨率
            val scale = Math.min(
                MAX_WIDTH.toFloat() / metrics.widthPixels,
                MAX_HEIGHT.toFloat() / metrics.heightPixels
            ).coerceAtMost(1.0f)
            
            val width = (metrics.widthPixels * scale).toInt()
            val height = (metrics.heightPixels * scale).toInt()
            
            imageReader = ImageReader.newInstance(
                width, height,
                PixelFormat.RGBA_8888, 2
            )
            
            virtualDisplay = projection.createVirtualDisplay(
                "ShortVideoGuard",
                width, height, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface, null, handler
            )
            
            handler.postDelayed({
                val bitmap = imageReader?.acquireLatestBitmap()
                callback(bitmap)
                release()
            }, 200)
            
        } catch (e: Exception) {
            Log.e(TAG, "MediaProjection 截图失败", e)
            callback(null)
            release()
        }
    }
    
    fun release() {
        try {
            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()
        } catch (_: Exception) { }
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }
}
