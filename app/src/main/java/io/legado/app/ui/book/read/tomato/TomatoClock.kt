package io.legado.app.ui.book.read.tomato

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
 * 退出阅读页自动暂停，返回继续；销毁时停止。
 */
object TomatoClock {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickJob: Job? = null

    private val _state = MutableStateFlow(TomatoUiState())
    val state: StateFlow<TomatoUiState> = _state.asStateFlow()

    /** 休息结束进入下一轮时回调（参数为新一轮序号） */
    var onNextRound: ((round: Int) -> Unit)? = null

    /** 全部轮次完成时回调 */
    var onAllCompleted: (() -> Unit)? = null

    /** 暂停时持久化（退出应用后恢复） */
    var persist: ((phase: TomatoPhase, currentRound: Int, remainingSeconds: Int) -> Unit)? = null

    /** 停止/全部完成时清除持久化 */
    var clearPersist: (() -> Unit)? = null

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
     * 恢复到暂停状态，不自动开始计时，onResume 后继续。
     */
    fun restore(phase: TomatoPhase, currentRound: Int, remainingSeconds: Int) {
        if (phase == TomatoPhase.IDLE) return
        tickJob?.cancel()
        _state.value = TomatoUiState(
            phase = phase,
            currentRound = currentRound.coerceAtLeast(1),
            remainingSeconds = remainingSeconds.coerceAtLeast(0),
            paused = true
        )
    }

    /** 退到后台/退出应用暂停 */
    fun pause() {
        val s = _state.value
        if (!s.running || s.paused) return
        tickJob?.cancel()
        _state.value = s.copy(paused = true)
        persist?.invoke(s.phase, s.currentRound, s.remainingSeconds)
    }

    /** 回到前台继续 */
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
        tickJob = scope.launch {
            while (true) {
                val s = _state.value
                if (!s.running || s.paused) break
                if (s.remainingSeconds <= 0) {
                    onPhaseEnd()
                    break
                }
                delay(1000)
                _state.value = _state.value.copy(remainingSeconds = _state.value.remainingSeconds - 1)
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

    private fun startRest() {
        _state.value = _state.value.copy(
            phase = TomatoPhase.REST,
            remainingSeconds = _state.value.restMinutes * 60
        )
        startTick()
    }

    private fun finishRest() {
        val s = _state.value
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
}
