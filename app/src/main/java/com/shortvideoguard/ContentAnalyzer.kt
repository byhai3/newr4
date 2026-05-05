package com.shortvideoguard

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.abs

/**
 * 内容分析器
 * 使用启发式算法分析 Bitmap 中是否包含大量暴露皮肤区域
 * 在 HSV 颜色空间中检测肤色，结合区域占比判断
 */
class ContentAnalyzer {
    
    companion object {
        const val TAG = "ContentAnalyzer"
        
        // 肤色检测的 HSV 范围（可以根据需要调整）
        // H: 色调 (0-360), S: 饱和度 (0-1), V: 明度 (0-1)
        const val SKIN_H_MIN = 0f
        const val SKIN_H_MAX = 50f  // 红色到橙黄色范围
        const val SKIN_H2_MIN = 340f
        const val SKIN_H2_MAX = 360f // 洋红色到红色（部分肤色）
        
        const val SKIN_S_MIN = 0.15f
        const val SKIN_S_MAX = 0.85f
        
        const val SKIN_V_MIN = 0.25f
        const val SKIN_V_MAX = 0.98f
        
        // 采样步长（每隔 N 个像素采样一次，提高性能）
        const val SAMPLE_STEP = 6
        
        // 最小肤色区域占比阈值（触发遮挡）
        const val MIN_SKIN_RATIO = 0.15f
        
        // 最大肤色区域占比（超过这个值很可能是正常人物/自拍，不是擦边）
        const val MAX_SKIN_RATIO = 0.65f
    }

    /**
     * 分析 Bitmap，返回不适宜内容置信度 (0.0 - 1.0)
     * 分数越高表示越可能是擦边内容
     */
    fun analyze(bitmap: Bitmap): Float {
        try {
            val width = bitmap.width
            val height = bitmap.height
            
            // 采样像素数
            var skinPixelCount = 0
            var totalSampledPixels = 0
            
            // 同时统计屏幕各区域的分布
            var topHalfSkinPixels = 0
            var centerSkinPixels = 0
            
            for (y in 0 until height step SAMPLE_STEP) {
                for (x in 0 until width step SAMPLE_STEP) {
                    val pixel = bitmap.getPixel(x, y)
                    val isSkin = isSkinPixel(pixel)
                    
                    if (isSkin) {
                        skinPixelCount++
                        
                        // 统计上半屏和中心区域
                        if (y < height / 2) {
                            topHalfSkinPixels++
                        }
                        if (x > width * 0.25 && x < width * 0.75 && 
                            y > height * 0.2 && y < height * 0.8) {
                            centerSkinPixels++
                        }
                    }
                    totalSampledPixels++
                }
            }
            
            if (totalSampledPixels == 0) return 0f
            
            val skinRatio = skinPixelCount.toFloat() / totalSampledPixels
            
            // 计算分数
            var score = 0f
            
            // 基础分数：基于肤色占比
            when {
                skinRatio < 0.08f -> score = 0f  // 太少，安全
                skinRatio < MIN_SKIN_RATIO -> score = skinRatio * 2f
                skinRatio < 0.30f -> score = 0.3f + (skinRatio - MIN_SKIN_RATIO) * 1.5f
                skinRatio < 0.50f -> score = 0.55f + (skinRatio - 0.30f) * 1.0f
                skinRatio < MAX_SKIN_RATIO -> score = 0.75f + (skinRatio - 0.50f) * 0.5f
                else -> score = 0.4f // 太多反而可能是正常自拍/人物
            }
            
            // 区域分布加成：如果肤色主要集中在上半身和中心区域，提高分数
            val topRatio = if (skinPixelCount > 0) topHalfSkinPixels.toFloat() / skinPixelCount else 0f
            val centerRatio = if (skinPixelCount > 0) centerSkinPixels.toFloat() / skinPixelCount else 0f
            
            if (topRatio > 0.6f && centerRatio > 0.4f) {
                score = (score * 1.15f).coerceAtMost(1.0f)
            }
            
            Log.d(TAG, "分析结果: skinRatio=${"%.3f".format(skinRatio)}, " +
                    "topRatio=${"%.3f".format(topRatio)}, " +
                    "centerRatio=${"%.3f".format(centerRatio)}, " +
                    "score=${"%.3f".format(score)}")
            
            return score.coerceIn(0f, 1f)
            
        } catch (e: Exception) {
            Log.e(TAG, "分析失败", e)
            return 0f
        }
    }
    
    /**
     * 判断单个像素是否为肤色
     */
    private fun isSkinPixel(pixel: Int): Boolean {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        
        // 快速过滤：排除明显不是肤色的像素
        if (r < 60 || g < 40 || b < 30) return false // 太暗
        if (r > 250 && g > 250 && b > 250) return false // 太白（可能是背景）
        if (r < g || r < b) return false // 红色分量不够（肤色偏红）
        
        // RGB 色差判断（简化版）
        val rgDiff = r - g
        val rbDiff = r - b
        
        // 肤色特征：R > G > B，且 R-G 和 R-B 在一定范围内
        if (rgDiff < 10 || rbDiff < 10) return false
        if (rgDiff > 120 || rbDiff > 120) return false // 色差太大
        
        // 转换为 HSV 精确判断
        val hsv = rgbToHsv(r, g, b)
        val h = hsv[0] // 0-360
        val s = hsv[1] // 0-1
        val v = hsv[2] // 0-1
        
        // 检查是否在肤色 HSV 范围内
        val hueMatch = (h in SKIN_H_MIN..SKIN_H_MAX) || (h in SKIN_H2_MIN..SKIN_H2_MAX)
        val satMatch = s in SKIN_S_MIN..SKIN_S_MAX
        val valMatch = v in SKIN_V_MIN..SKIN_V_MAX
        
        return hueMatch && satMatch && valMatch
    }
    
    /**
     * RGB 转 HSV
     * 返回 FloatArray: [H(0-360), S(0-1), V(0-1)]
     */
    private fun rgbToHsv(r: Int, g: Int, b: Int): FloatArray {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val diff = max - min
        
        // 计算 V
        val v = max
        
        // 计算 S
        val s = if (max == 0f) 0f else diff / max
        
        // 计算 H
        val h = when {
            diff == 0f -> 0f
            max == rf -> (60f * ((gf - bf) / diff) + 360f) % 360f
            max == gf -> (60f * ((bf - rf) / diff) + 120f)
            else -> (60f * ((rf - gf) / diff) + 240f)
        }
        
        return floatArrayOf(h, s, v)
    }
}
