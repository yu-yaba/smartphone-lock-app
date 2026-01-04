package jp.kawai.ultrafocus.ui.emergency

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputContentInfo
import androidx.appcompat.widget.AppCompatEditText

class NoPasteEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {

    init {
        isLongClickable = false
        setTextIsSelectable(false)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs) ?: return null
        return NoPasteInputConnection(connection, true)
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        return when (id) {
            android.R.id.paste,
            android.R.id.pasteAsPlainText -> false
            else -> super.onTextContextMenuItem(id)
        }
    }

    private class NoPasteInputConnection(
        target: InputConnection,
        mutable: Boolean
    ) : InputConnectionWrapper(target, mutable) {

        @androidx.annotation.RequiresApi(Build.VERSION_CODES.N_MR1)
        override fun commitContent(
            inputContentInfo: InputContentInfo,
            flags: Int,
            opts: Bundle?
        ): Boolean {
            return false
        }

    }
}
