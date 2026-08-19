package io.legado.app.ui.book.read.tomato

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import io.legado.app.help.config.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TomatoPhase { IDLE, FOCUS, REST }

data class TomatoUiState(
    val phase: TomatoPhase = TomatoPhase.IDLE,
    val focusMinutes: Int = AppConfig.tomatoFocusMin,
    val restMinutes: Int = AppConfig.tomatoRestMin,
    val totalRounds: Int = AppConfig.tomatoRounds,
    val currentRound: Int = 1,
    val remainingSeconds: Int = 0,
    val paused: Boolean = false
) {
    val running: Boolean get() = phase != TomatoPhase.IDLE
    val isRest: Boolean get() = phase == TomatoPhase.REST
    val totalSeconds: Int
        get() = when (phase) {
            TomatoPhase.FOCUS -> focusMinutes * 60
            TomatoPhase.REST -> restMinutes * 60
            TomatoPhase.IDLE -> 0
        }
}

/**
 * 番茄钟：专注(默认40分钟) → 休息(默认10分钟) → 下一轮，共 N 轮。
 *
 * - 专注阶段：退出阅读页/退后台自动暂停，返回继续；
 * - 休息阶段：不受页面生命周期影响，后台/息屏/退出到书架均继续计时
 *   （基于结束时间戳计算剩余，系统冻结唤醒后按真实流逝时间扣除）；
 * - 开始休息 / 结束休息时播放提示音并震动。
 */
object TomatoClock {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickJob: Job? = null

    /** 当前阶段应结束的时间戳（elapsedRealtime 毫秒），用于息屏/后台按真实时间计算剩余 */
    private var phaseEndAtMillis: Long = 0L

    private val _state = MutableStateFlow(TomatoUiState())
    val state: StateFlow<TomatoUiState> = _state.asStateFlow()

    /** App 上下文（由 ReadBookActivity 注册），用于提示音/震动 */
    private var appContext: Context? = null

    /** 休息结束进入下一轮时回调（参数为新一轮序号） */
    var onNextRound: ((round: Int) -> Unit)? = null

    /** 全部轮次完成时回调 */
    var onAllCompleted: (() -> Unit)? = null

    /** 暂停时持久化（退出应用后恢复） */
    var persist: ((phase: TomatoPhase, currentRound: Int, remainingSeconds: Int) -> Unit)? = null

    /** 停止/全部完成时清除持久化 */
    var clearPersist: (() -> Unit)? = null

    /** 由阅读页注册 app 上下文（提示音/震动用） */
    fun attach(context: Context) {
        appContext = context.applicationContext
    }

    fun start() {
        if (_state.value.running) return
        _state.value = TomatoUiState(
            phase = TomatoPhase.FOCUS,
            focusMinutes = AppConfig.tomatoFocusMin,
            restMinutes = AppConfig.tomatoRestMin,
            totalRounds = AppConfig.tomatoRounds,
            currentRound = 1,
            remainingSeconds = AppConfig.tomatoFocusMin * 60,
            paused = false
        )
        startTick()
    }

    fun stop() {
        tickJob?.cancel()
        tickJob = null
        _state.value = TomatoUiState()
        clearPersist?.invoke()
    }

    /**
     * 从持久化恢复（App 重启后进入阅读页时调用）。
     * 专注阶段恢复到暂停状态（onResume 后继续）；
     * 休息阶段立即继续计时（休息不受页面/后台影响）。
     */
    fun restore(phase: TomatoPhase, currentRound: Int, remainingSeconds: Int) {
        if (phase == TomatoPhase.IDLE) return
        tickJob?.cancel()
        val rest = phase == TomatoPhase.REST
        _state.value = TomatoUiState(
            phase = phase,
            currentRound = currentRound.coerceAtLeast(1),
            remainingSeconds = remainingSeconds.coerceAtLeast(0),
            paused = !rest
        )
        if (rest) startTick()
    }

    /** 退到后台/退出应用暂停（仅专注阶段；休息阶段继续计时） */
    fun pause() {
        val s = _state.value
        if (!s.running || s.paused) return
        if (s.phase == TomatoPhase.REST) return
        tickJob?.cancel()
        _state.value = s.copy(paused = true)
        persist?.invoke(s.phase, s.currentRound, s.remainingSeconds)
    }

    /** 回到前台继续（专注阶段恢复计时） */
    fun resume() {
        val s = _state.value
        if (!s.running || !s.paused) return
        _state.value = s.copy(paused = false)
        startTick()
    }

    /** 点击大番茄提前结束休息 */
    fun endRestEarly() {
        if (_state.value.phase != TomatoPhase.REST) return
        finishRest()
    }

    /** 面板中修改时长/轮次（未运行时立即生效，运行中仅保存下次使用） */
    fun updateConfig(focus: Int, rest: Int, rounds: Int) {
        AppConfig.tomatoFocusMin = focus
        AppConfig.tomatoRestMin = rest
        AppConfig.tomatoRounds = rounds
        if (!_state.value.running) {
            _state.value = _state.value.copy(
                focusMinutes = focus,
                restMinutes = rest,
                totalRounds = rounds
            )
        }
    }

    private fun startTick() {
        tickJob?.cancel()
        val s = _state.value
        phaseEndAtMillis = SystemClock.elapsedRealtime() + s.remainingSeconds * 1000L
        tickJob = scope.launch {
            while (true) {
                val st = _state.value
                if (!st.running || st.paused) break
                val remain = ((phaseEndAtMillis - SystemClock.elapsedRealtime() + 999) / 1000).toInt()
                if (remain <= 0) {
                    _state.value = st.copy(remainingSeconds = 0)
                    onPhaseEnd()
                    break
                }
                _state.value = st.copy(remainingSeconds = remain)
                delay(500)
            }
        }
    }

    private fun onPhaseEnd() {
        when (_state.value.phase) {
            TomatoPhase.FOCUS -> startRest()
            TomatoPhase.REST -> finishRest()
            TomatoPhase.IDLE -> {}
        }
    }

    /** 专注结束 → 开始休息：提示音 + 震动 */
    private fun startRest() {
        val restMin = _state.value.restMinutes
        _state.value = _state.value.copy(
            phase = TomatoPhase.REST,
            remainingSeconds = restMin * 60
        )
        notifyPhaseChange()
        startTick()
    }

    /** 休息结束：提示音 + 震动，进入下一轮或完成全部 */
    private fun finishRest() {
        val s = _state.value
        notifyPhaseChange()
        if (s.currentRound >= s.totalRounds) {
            stop()
            onAllCompleted?.invoke()
        } else {
            val next = s.currentRound + 1
            _state.value = s.copy(
                phase = TomatoPhase.FOCUS,
                currentRound = next,
                remainingSeconds = s.focusMinutes * 60,
                paused = false
            )
            startTick()
            onNextRound?.invoke(next)
        }
    }

    /** 系统通知提示音 + 震动（无需额外权限，VIBRATE 已在 Manifest 声明） */
    private fun notifyPhaseChange() {
        val ctx = appContext ?: return
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(ctx, uri)?.play()
        }
        runCatching {
            val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(300)
            }
        }
    }
}