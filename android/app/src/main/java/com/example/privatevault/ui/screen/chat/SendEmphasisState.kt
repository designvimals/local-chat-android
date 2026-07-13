package com.example.privatevault.ui.screen.chat

import androidx.compose.runtime.Immutable
import com.example.privatevault.model.MessageEmphasis
import kotlin.math.pow

enum class SendInteractionPhase {
    Idle,
    Pressed,
    Holding,
    Adjusting,
    AtMaximum,
    Sending,
    Cancelling
}

@Immutable
data class SendEmphasisUiState(
    val phase: SendInteractionPhase = SendInteractionPhase.Idle,
    val progress: Float = 0f,
    val popCount: Int = 0,
    val maximumArmed: Boolean = true,
    val messageSnapshot: String = ""
) {
    val isActive: Boolean
        get() = phase != SendInteractionPhase.Idle && phase != SendInteractionPhase.Sending

    val showsPreview: Boolean
        get() = phase in setOf(
            SendInteractionPhase.Holding,
            SendInteractionPhase.Adjusting,
            SendInteractionPhase.AtMaximum,
            SendInteractionPhase.Cancelling
        )
}

/** Pure gesture math kept independent from Compose pointer events for testing. */
object SendEmphasisMath {
    const val MAXIMUM_ENTER_THRESHOLD = 0.98f
    const val MAXIMUM_REARM_THRESHOLD = 0.90f

    fun clamp(progress: Float): Float = progress.coerceIn(0f, 1f)

    /** Ease-out growth: early feedback is obvious while the final range is deliberate. */
    fun holdProgress(elapsedMillis: Long, growthDurationMillis: Long): Float {
        if (growthDurationMillis <= 0L) return 1f
        val linear = (elapsedMillis.toFloat() / growthDurationMillis).coerceIn(0f, 1f)
        return 1f - (1f - linear).pow(1.65f)
    }

    fun dragProgress(baseProgress: Float, horizontalDeltaPx: Float, dragRangePx: Float): Float {
        if (dragRangePx <= 0f) return clamp(baseProgress)
        return clamp(baseProgress + horizontalDeltaPx / dragRangePx)
    }

    fun textSizeIncreaseSp(messageLength: Int): Float = when {
        messageLength <= 24 -> 15f
        messageLength <= 80 -> 10f
        messageLength <= 200 -> 6f
        else -> 3f
    }

    fun bubblePaddingIncreaseDp(messageLength: Int): Float = when {
        messageLength <= 24 -> 5f
        messageLength <= 80 -> 3f
        else -> 2f
    }
}

/**
 * Deterministic state machine for the send gesture. Pointer tracking remains in
 * the UI, while all state transitions and maximum hysteresis live here.
 */
class SendEmphasisStateMachine {
    var state: SendEmphasisUiState = SendEmphasisUiState()
        private set

    fun press(message: String): SendEmphasisUiState {
        if (message.isBlank() || state.phase != SendInteractionPhase.Idle) return state
        state = SendEmphasisUiState(
            phase = SendInteractionPhase.Pressed,
            messageSnapshot = message
        )
        return state
    }

    fun beginHolding(): SendEmphasisUiState {
        if (state.phase != SendInteractionPhase.Pressed) return state
        state = state.copy(phase = SendInteractionPhase.Holding)
        return state
    }

    fun beginAdjusting(): SendEmphasisUiState {
        if (state.phase !in setOf(
                SendInteractionPhase.Holding,
                SendInteractionPhase.AtMaximum,
                SendInteractionPhase.Adjusting
            )
        ) return state
        state = state.copy(phase = SendInteractionPhase.Adjusting)
        return state
    }

    fun updateProgress(value: Float): SendEmphasisUiState {
        if (state.phase !in setOf(
                SendInteractionPhase.Holding,
                SendInteractionPhase.Adjusting,
                SendInteractionPhase.AtMaximum
            )
        ) return state

        val progress = SendEmphasisMath.clamp(value)
        val enteringMaximum = state.maximumArmed && progress >= SendEmphasisMath.MAXIMUM_ENTER_THRESHOLD
        val rearmed = !state.maximumArmed && progress < SendEmphasisMath.MAXIMUM_REARM_THRESHOLD
        state = state.copy(
            phase = when {
                enteringMaximum || (state.phase == SendInteractionPhase.AtMaximum && !rearmed) ->
                    SendInteractionPhase.AtMaximum
                state.phase == SendInteractionPhase.Holding -> SendInteractionPhase.Holding
                else -> SendInteractionPhase.Adjusting
            },
            progress = if (enteringMaximum) 1f else progress,
            popCount = state.popCount + if (enteringMaximum) 1 else 0,
            maximumArmed = when {
                enteringMaximum -> false
                rearmed -> true
                else -> state.maximumArmed
            }
        )
        return state
    }

    fun release(): MessageEmphasis? {
        if (state.phase == SendInteractionPhase.Idle || state.phase == SendInteractionPhase.Cancelling) {
            return null
        }
        val emphasis = if (state.phase == SendInteractionPhase.Pressed) {
            MessageEmphasis.Normal
        } else {
            MessageEmphasis.fromProgress(state.progress)
        }
        state = state.copy(phase = SendInteractionPhase.Sending)
        return emphasis
    }

    fun cancel(): SendEmphasisUiState {
        if (state.phase == SendInteractionPhase.Idle) return state
        state = state.copy(phase = SendInteractionPhase.Cancelling, progress = 0f)
        return state
    }

    fun reset(): SendEmphasisUiState {
        state = SendEmphasisUiState()
        return state
    }
}
