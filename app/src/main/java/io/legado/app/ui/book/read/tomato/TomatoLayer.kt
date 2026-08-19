package io.legado.app.ui.book.read.tomato

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.compose.AppDialogFrame
import io.legado.app.ui.widget.compose.AppDialogStyle
import io.legado.app.ui.widget.compose.LegadoComposeTheme
import io.legado.app.ui.widget.compose.rememberAppDialogStyle

/**
 * 番茄钟 UI 层：设置/状态面板 + 休息大番茄覆盖层。
 * 入口按钮位于阅读菜单底部面板的上下页滑条右上方（由 ReadMenu 提供），
 * 点击后通过 showPanel 打开番茄面板。
 * UI 统一采用 Legado 风格（AppDialogStyle 配色 + Legado 字体）；休息覆盖层除外。
 */
@Composable
fun TomatoLayer(
    showPanel: Boolean,
    onClosePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dark = AppConfig.isNightTheme
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        LegadoComposeTheme {
            TomatoLayerContent(showPanel = showPanel, onClosePanel = onClosePanel, modifier = modifier)
        }
    }
}

@Composable
private fun TomatoLayerContent(
    showPanel: Boolean,
    onClosePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by TomatoClock.state.collectAsState()
    var nextRoundTip by remember { mutableStateOf<Int?>(null) }
    var allDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        TomatoClock.onNextRound = { round -> nextRoundTip = round }
        TomatoClock.onAllCompleted = { allDone = true }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 休息覆盖层：大番茄 + 倒计时，仅点击番茄并二次确认后提前结束
        if (state.isRest) {
            RestTomatoOverlay(state = state)
        }
    }

    if (showPanel) {
        Dialog(onDismissRequest = onClosePanel) {
            TomatoPanel(
                state = state,
                onDismiss = onClosePanel,
                onStart = {
                    TomatoClock.start()
                    onClosePanel()
                },
                onResume = {
                    TomatoClock.resume()
                    onClosePanel()
                },
                onStop = {
                    TomatoClock.stop()
                    onClosePanel()
                },
                onConfigChange = { focus, rest, rounds ->
                    TomatoClock.updateConfig(focus, rest, rounds)
                }
            )
        }
    }

    nextRoundTip?.let { round ->
        AlertDialog(
            onDismissRequest = { nextRoundTip = null },
            title = { Text("🍅 开始第 $round 轮") },
            text = { Text("休息结束，开始专注阅读 ${state.focusMinutes} 分钟") },
            confirmButton = {
                TextButton(
                    onClick = { nextRoundTip = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = rememberAppDialogStyle().accent)
                ) { Text("知道了") }
            }
        )
    }

    if (allDone) {
        AlertDialog(
            onDismissRequest = { allDone = false },
            title = { Text("🍅 全部完成") },
            text = { Text("恭喜完成全部 ${state.totalRounds} 轮番茄钟！") },
            confirmButton = {
                TextButton(
                    onClick = { allDone = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = rememberAppDialogStyle().accent)
                ) { Text("好的") }
            }
        )
    }
}

/**
 * 休息覆盖层：保持 Legado 之外的专属视觉（黑底白字大番茄 + 倒计时）。
 * 仅点击大番茄并二次确认后提前结束休息；点击其余任意位置不结束、也不穿透翻页。
 */
@Composable
private fun RestTomatoOverlay(
    state: TomatoUiState,
    modifier: Modifier = Modifier
) {
    var grown by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { grown = true }
    val scale by animateFloatAsState(
        targetValue = if (grown) 1f else 0.2f,
        animationSpec = tween(durationMillis = 650),
        label = "tomatoGrow"
    )
    val alpha by animateFloatAsState(
        targetValue = if (grown) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "tomatoAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            // 拦截点击：防止穿透到阅读页，且点击任意位置不结束休息
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(alpha)
        ) {
            // 只有点击大番茄才可提前结束休息（二次确认）
            Image(
                painter = painterResource(R.drawable.ic_tomato_big),
                contentDescription = null,
                modifier = Modifier
                    .size(230.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clickable { confirmEnd = true }
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "休息时间",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatTime(state.remainingSeconds),
                color = Color.White,
                fontSize = 46.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "第 ${state.currentRound}/${state.totalRounds} 轮 · 点击番茄可提前结束",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp
            )
        }
    }

    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("🍅 提前结束休息？") },
            text = { Text("休息有助于保持专注，确定要提前结束休息吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmEnd = false
                        TomatoClock.endRestEarly()
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnd = false }) { Text("取消") }
            }
        )
    }
}

/** 番茄钟设置/状态面板：Legado 风格对话框（AppDialogFrame + AppDialogStyle）。 */
@Composable
private fun TomatoPanel(
    state: TomatoUiState,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onConfigChange: (Int, Int, Int) -> Unit
) {
    val style = rememberAppDialogStyle()
    var focus by rememberSaveable { mutableIntStateOf(state.focusMinutes) }
    var rest by rememberSaveable { mutableIntStateOf(state.restMinutes) }
    var rounds by rememberSaveable { mutableIntStateOf(state.totalRounds) }

    AppDialogFrame(
        title = "🍅 番茄钟",
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.running) {
                    if (state.paused) {
                        Button(
                            onClick = onResume,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = style.accent,
                                contentColor = style.onAccent
                            )
                        ) {
                            Text("继续")
                        }
                        Spacer(Modifier.width(10.dp))
                        TextButton(
                            onClick = onStop,
                            colors = ButtonDefaults.textButtonColors(contentColor = style.danger)
                        ) {
                            Text("停止")
                        }
                    } else {
                        Button(
                            onClick = onStop,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = style.danger,
                                contentColor = Color.White
                            )
                        ) {
                            Text("停止")
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = style.secondaryText)
                    ) {
                        Text("关闭")
                    }
                } else {
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = style.accent,
                            contentColor = style.onAccent
                        )
                    ) {
                        Text("开始")
                    }
                    Spacer(Modifier.width(10.dp))
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = style.secondaryText)
                    ) {
                        Text("关闭")
                    }
                }
            }
        }
    ) {
        if (state.running) {
            Text(
                text = when (state.phase) {
                    TomatoPhase.FOCUS -> "专注中 · 第 ${state.currentRound}/${state.totalRounds} 轮"
                    TomatoPhase.REST -> "休息中 · 第 ${state.currentRound}/${state.totalRounds} 轮"
                    TomatoPhase.IDLE -> ""
                },
                fontSize = 13.sp,
                color = style.secondaryText
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = formatTime(state.remainingSeconds),
                fontSize = 44.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = style.primaryText
            )
            if (state.paused) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "已暂停（返回阅读页继续）",
                    fontSize = 12.sp,
                    color = style.danger
                )
            }
        } else {
            StepperRow(
                label = "专注",
                unit = "分钟",
                value = focus,
                min = 10,
                max = 120,
                step = 10,
                style = style,
                onChange = {
                    focus = it
                    onConfigChange(focus, rest, rounds)
                }
            )
            Spacer(Modifier.height(6.dp))
            StepperRow(
                label = "休息",
                unit = "分钟",
                value = rest,
                min = 5,
                max = 30,
                step = 5,
                style = style,
                onChange = {
                    rest = it
                    onConfigChange(focus, rest, rounds)
                }
            )
            Spacer(Modifier.height(6.dp))
            StepperRow(
                label = "总轮次",
                unit = "轮",
                value = rounds,
                min = 1,
                max = 12,
                step = 1,
                style = style,
                onChange = {
                    rounds = it
                    onConfigChange(focus, rest, rounds)
                }
            )
        }
    }
}

/** 数字步进行（± 按钮）：Legado 风格 accent 着色。 */
@Composable
private fun StepperRow(
    label: String,
    unit: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    style: AppDialogStyle,
    onChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = style.primaryText,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = { onChange((value - step).coerceAtLeast(min)) },
            enabled = value > min,
            colors = ButtonDefaults.textButtonColors(contentColor = style.accent),
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
        ) {
            Text("−", fontSize = 18.sp)
        }
        Text(
            text = "$value",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = style.primaryText,
            modifier = Modifier.width(52.dp)
        )
        TextButton(
            onClick = { onChange((value + step).coerceAtMost(max)) },
            enabled = value < max,
            colors = ButtonDefaults.textButtonColors(contentColor = style.accent),
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
        ) {
            Text("+", fontSize = 18.sp)
        }
        Text(
            text = unit,
            fontSize = 12.sp,
            color = style.secondaryText,
            modifier = Modifier.width(36.dp)
        )
    }
}

private fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}