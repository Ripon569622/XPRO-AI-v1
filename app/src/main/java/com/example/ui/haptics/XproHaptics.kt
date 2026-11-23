package com.example.ui.haptics

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

@Immutable
class XproHaptics(
    private val view: View,
    private val composeHaptic: HapticFeedback
) {
    /** Crisp tactile click for standard buttons, chips, switches, and navigation items. */
    fun click() {
        val handled = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        if (!handled) {
            composeHaptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    /** Slightly firmer tap for primary action buttons like Send, Generate, Voice Mic, and Sign In. */
    fun primaryAction() {
        val handled = view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        if (!handled) {
            composeHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /** Distinct completion feedback when the AI finishes typing/streaming or artwork synthesis completes. */
    fun confirm() {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }
        val handled = view.performHapticFeedback(constant)
        if (!handled) {
            composeHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    /** Subtle error/rejection feedback when a request fails or is rejected. */
    fun reject() {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        val handled = view.performHapticFeedback(constant)
        if (!handled) {
            composeHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
}

@Composable
fun rememberXproHaptics(): XproHaptics {
    val view = LocalView.current
    val composeHaptic = LocalHapticFeedback.current
    return remember(view, composeHaptic) {
        XproHaptics(view = view, composeHaptic = composeHaptic)
    }
}
