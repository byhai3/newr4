package com.shortvideoguard

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable

/**
 * 悬浮遮挡层管理器
 * 当检测到不适宜内容时，在屏幕中央显示半透明遮挡层
 * 同时保留屏幕两侧和底部的滑动区域
 */
class OverlayManager(private val context: Context) {
    
    companion object {
        const val TAG = "OverlayManager"
        // 边缘保留区域宽度（dp）
        const val EDGE_MARGIN_DP = 80
        // 底部滑动区域高度（dp）
        const val BOTTOM_MARGIN_DP = 120
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isShowing = false
    private val handler = Handler(Looper.getMainLooper())
    
    // 自动隐藏延迟
    private var hideRunnable: Runnable? = null

    init {
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    }

    /**
     * 显示遮挡层
     * 中间区域被遮挡，四周保留滑动区域
     */
    fun showOverlay(confidenceScore: Float) {
        if (isShowing) {
            updateOverlayContent(confidenceScore)
            return
        }
        
        handler.post {
            try {
                removeOverlayInternal()
                
                val layoutParams = createLayoutParams()
                overlayView = createOverlayView(confidenceScore)
                
                windowManager?.addView(overlayView, layoutParams)
                isShowing = true
                
                Log.i(TAG, "遮挡层已显示 (置信度: ${"%.2f".format(confidenceScore)})")
                
                // 设置自动隐藏（如果用户没有手动操作）
                scheduleAutoHide()
                
            } catch (e: Exception) {
                Log.e(TAG, "显示遮挡层失败", e)
            }
        }
    }

    /**
     * 隐藏遮挡层
     */
    fun hideOverlay() {
        if (!isShowing) return
        
        handler.post {
            removeOverlayInternal()
            isShowing = false
            Log.i(TAG, "遮挡层已隐藏")
        }
    }

    private fun removeOverlayInternal() {
        try {
            overlayView?.let { view ->
                windowManager?.removeView(view)
            }
        } catch (_: Exception) { }
        overlayView = null
        hideRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            // 设置窗口动画
            windowAnimations = android.R.style.Animation_Dialog
        }
    }

    /**
     * 创建遮挡层视图
     * 中央区域：深色半透明遮挡 + 提示文字 + 继续按钮
     * 边缘区域：完全透明，允许触摸穿透到底层应用
     */
    private fun createOverlayView(confidenceScore: Float): View {
        val density = context.resources.displayMetrics.density
        val edgeMarginPx = (EDGE_MARGIN_DP * density).toInt()
        val bottomMarginPx = (BOTTOM_MARGIN_DP * density).toInt()
        
        // 根布局：全屏 FrameLayout
        val rootLayout = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 左侧透明滑动区域
        val leftTouchArea = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                edgeMarginPx,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.START
            }
            setBackgroundColor(Color.TRANSPARENT)
        }
        rootLayout.addView(leftTouchArea)

        // 右侧透明滑动区域
        val rightTouchArea = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                edgeMarginPx,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.END
            }
            setBackgroundColor(Color.TRANSPARENT)
        }
        rootLayout.addView(rightTouchArea)

        // 底部透明滑动区域（用于上下滑动切视频）
        val bottomTouchArea = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                bottomMarginPx
            ).apply {
                gravity = Gravity.BOTTOM
            }
            setBackgroundColor(Color.TRANSPARENT)
        }
        rootLayout.addView(bottomTouchArea)

        // 中央遮挡区域容器
        val centerContainer = LinearLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                marginStart = edgeMarginPx
                marginEnd = edgeMarginPx
                bottomMargin = bottomMarginPx
                topMargin = (40 * density).toInt() // 顶部留一点状态栏空间
            }
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            
            // 毛玻璃效果背景
            val backgroundDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#E61A1A2E")) // 深色半透明
                cornerRadius = 24f * density
            }
            background = backgroundDrawable
            
            // 模糊效果
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                    20f, 20f,
                    android.graphics.Shader.TileMode.CLAMP
                ))
            }
        }

        // 提示图标/文字
        val titleText = TextView(context).apply {
            text = "🔒 已自动拦截"
            setTextColor(Color.WHITE)
            textSize = 22f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(0, 0, 0, (16 * density).toInt())
        }
        centerContainer.addView(titleText)

        // 详细说明
        val descText = TextView(context).apply {
            text = "检测到不适宜内容\n已从视野中遮挡"
            setTextColor(Color.parseColor("#B0B0B0"))
            textSize = 14f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(
                (24 * density).toInt(),
                0,
                (24 * density).toInt(),
                (24 * density).toInt()
            )
        }
        centerContainer.addView(descText)

        // 置信度显示（调试用，可在发布版中移除）
        val scoreText = TextView(context).apply {
            text = "匹配度: ${"%.1f".format(confidenceScore * 100)}%"
            setTextColor(Color.parseColor("#808080"))
            textSize = 12f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(0, 0, 0, (20 * density).toInt())
        }
        centerContainer.addView(scoreText)

        // 继续观看按钮
        val continueButton = Button(context).apply {
            text = "继续观看"
            setTextColor(Color.WHITE)
            textSize = 16f
            
            val buttonBg = GradientDrawable().apply {
                setColor(Color.parseColor("#FF6B6B"))
                cornerRadius = 12f * density
            }
            background = buttonBg
            
            setPadding(
                (32 * density).toInt(),
                (12 * density).toInt(),
                (32 * density).toInt(),
                (12 * density).toInt()
            )
            
            setOnClickListener {
                // 临时解除遮挡，10秒后恢复检测
                hideOverlay()
                handler.postDelayed({
                    // 恢复检测状态
                }, 10000)
            }
        }
        centerContainer.addView(continueButton)

        // 边缘滑动提示
        val hintText = TextView(context).apply {
            text = "💡 提示：可在屏幕两侧或底部滑动切换视频"
            setTextColor(Color.parseColor("#808080"))
            textSize = 12f
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(0, (20 * density).toInt(), 0, 0)
        }
        centerContainer.addView(hintText)

        rootLayout.addView(centerContainer)
        
        // 入场动画
        rootLayout.alpha = 0f
        rootLayout.animate()
            .alpha(1f)
            .setDuration(300)
            .start()

        return rootLayout
    }

    private fun updateOverlayContent(confidenceScore: Float) {
        // 更新现有遮挡层的置信度显示
        // 实际实现中可以遍历子视图更新
    }

    private fun scheduleAutoHide() {
        hideRunnable = Runnable {
            // 如果当前视频还在播放（可以通过其他方式判断），可以选择不自动隐藏
            // 这里保持显示直到内容变化或用户手动解除
        }
        // 不自动隐藏，保持遮挡直到用户操作或内容变化
    }
}
