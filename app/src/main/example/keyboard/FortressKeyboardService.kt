package com.example.keyboard

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

class FortressKeyboardService : InputMethodService() {

    override fun onCreateInputView(): View {
        val composeView = ComposeView(this)
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        
        composeView.setContent {
            FortressKeyboardView(
                onText = { text ->
                    currentInputConnection?.commitText(text, 1)
                },
                onDelete = {
                    currentInputConnection?.deleteSurroundingText(1, 0)
                },
                onAction = {
                    // Send keyboard Enter key down/up event actions
                    currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                    currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                },
                onSpace = {
                    currentInputConnection?.commitText(" ", 1)
                }
            )
        }
        
        return composeView
    }
}
