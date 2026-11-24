package com.lastcoffee.top.oppo_call_compose.feature.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.InputType
import android.text.InputFilter
import android.text.TextWatcher
import android.text.method.DialerKeyListener
import android.util.TypedValue
import android.view.Gravity
import android.widget.EditText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.lastcoffee.top.oppo_call_compose.ui.common.OppoGreen
import com.lastcoffee.top.oppo_call_compose.ui.common.OppoPrimary

/** Matches DialEditText: real selection and paste, with the app's own keypad. */
@Composable
internal fun DialNumberEditor(input: DialInput, textSizeDp: Float, onInputChange: (DialInput) -> Unit, modifier: Modifier = Modifier) {
    val onChange by rememberUpdatedState(onInputChange)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            DialNumberEditText(context).apply {
                background = null
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                setSingleLine(true)
                setTextColor(OppoPrimary.toArgb())
                typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.SANS_SERIF, 600, false)
                    else Typeface.create("sans-serif-medium", Typeface.NORMAL)
                keyListener = DialerKeyListener.getInstance()
                setRawInputType(InputType.TYPE_CLASS_PHONE)
                showSoftInputOnFocus = false
                isCursorVisible = true
                isFocusableInTouchMode = true
                highlightColor = OppoGreen.copy(alpha = 0.25f).toArgb()
                if (Build.VERSION.SDK_INT >= 29) {
                    textCursorDrawable = GradientDrawable().apply {
                        setColor(OppoGreen.toArgb())
                        setSize((2 * resources.displayMetrics.density).toInt(), 1)
                    }
                }
                contentDescription = "电话号码"
                onInputEdited = { onChange(it) }
                filters = arrayOf(InputFilter { source, start, end, _, _, _ ->
                    if (updating) null else {
                        val inserted = source.subSequence(start, end).toString()
                        val cleaned = if (inserted.length > 1) pastedDialNumber(inserted) else inserted.filter { it.isDialCharacter() }
                        cleaned.takeUnless { it == inserted }
                    }
                })
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: Editable?) {
                        if (!updating) onInputEdited?.invoke(currentInput())
                    }
                })
            }
        },
        update = { editor ->
            editor.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSizeDp)
            editor.updateInput(input)
            if (input.number.isNotEmpty() && !editor.isFocused) editor.requestFocus()
        },
    )
}

private class DialNumberEditText(context: Context) : EditText(context) {
    var onInputEdited: ((DialInput) -> Unit)? = null
    var updating = false
        private set

    fun currentInput(): DialInput {
        val display = text?.toString().orEmpty()
        val raw = display.filter { it.isDialCharacter() }.take(100)
        return DialInput(raw, rawDialOffset(display, selectionStart).coerceAtMost(raw.length),
            rawDialOffset(display, selectionEnd).coerceAtMost(raw.length))
    }

    fun updateInput(input: DialInput) {
        updating = true
        try {
            val display = formatDialNumber(input.number)
            if (text?.toString() != display) setText(display)
            val start = formattedDialOffset(display, input.selectionStart)
            val end = formattedDialOffset(display, input.selectionEnd)
            if (selectionStart != start || selectionEnd != end) setSelection(start, end)
        } finally {
            updating = false
        }
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!updating && selStart >= 0 && selEnd >= 0) onInputEdited?.invoke(currentInput())
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        val input = currentInput()
        val handled = super.onTextContextMenuItem(id)
        // Preserve the native menu/selection behavior, copying only dial characters.
        if (handled && (id == android.R.id.copy || id == android.R.id.cut)) {
            val start = minOf(input.selectionStart, input.selectionEnd)
            val end = maxOf(input.selectionStart, input.selectionEnd)
            if (start < end) {
                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                    ClipData.newPlainText("电话号码", input.number.substring(start, end)))
            }
        }
        return handled
    }
}
