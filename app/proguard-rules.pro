# ProGuard 规则
# 混淆配置

# 保留无障碍服务类
-keep class com.shortvideoguard.GuardAccessibilityService { *; }
-keep class com.shortvideoguard.MainActivity { *; }

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Kotlin
-keepclassmembers class * {
    @kotlin.jvm.internal.* <methods>;
}
