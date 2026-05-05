package com.shortvideoguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shortvideoguard.ui.components.PermissionCard
import com.shortvideoguard.ui.theme.ShortVideoGuardTheme
import android.provider.Settings
import android.content.Intent
import android.net.Uri

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShortVideoGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isServiceEnabled by remember { mutableStateOf(false) }
    var detectionThreshold by remember { mutableFloatStateOf(0.6f) }
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceEnabled = GuardAccessibilityService.isRunning
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("短视频守护") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 服务状态卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isServiceEnabled) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isServiceEnabled) "✅ 守护服务运行中" else "⚠️ 守护服务未开启",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isServiceEnabled) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isServiceEnabled) 
                            "正在保护您的短视频浏览体验" 
                        else 
                            "需要开启无障碍服务才能自动检测和过滤内容",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 开启服务按钮
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                enabled = !isServiceEnabled
            ) {
                Text(
                    text = if (isServiceEnabled) "服务已开启" else "前往开启无障碍服务",
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // 检测阈值调节
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "检测敏感度",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Slider(
                        value = detectionThreshold,
                        onValueChange = { detectionThreshold = it },
                        valueRange = 0.3f..0.9f,
                        steps = 5
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("宽松", style = MaterialTheme.typography.labelSmall)
                        Text("严格", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // 支持的应用
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "已支持应用",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    val apps = listOf(
                        "抖音 (com.ss.android.ugc.aweme)",
                        "抖音极速版",
                        "TikTok (com.zhiliaoapp.musically)",
                        "快手 (com.smile.gifmaker)",
                        "快手极速版",
                        "小红书 (com.xingin.xhs)"
                    )
                    apps.forEach { app ->
                        Text(
                            "• $app",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            // 使用说明
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "使用说明",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        "1. 点击上方按钮开启无障碍服务\n\n" +
                        "2. 打开抖音、快手等短视频应用\n\n" +
                        "3. 当检测到不适宜内容时，屏幕中央会自动出现遮挡层\n\n" +
                        "4. 左右两侧和底部保留滑动区域，您可以继续切换视频\n\n" +
                        "5. 点击遮挡层上的「继续观看」按钮可临时解除遮挡",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
