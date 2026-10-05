package com.openbrows.app

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.constraintlayout.widget.ConstraintLayout

class BottomUrlBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

    var onNavigate: ((String) -> Unit)? = null
    var onBack: (() -> Unit)? = null
    var onForward: (() -> Unit)? = null
    var onMore: (() -> Unit)? = null

    private val urlInput: EditText
    private val btnBack: ImageButton
    private val btnForward: ImageButton
    private val btnMore: ImageButton
    private val progressBar: ProgressBar

    init {
        LayoutInflater.from(context).inflate(R.layout.layout_url_bar, this, true)
        urlInput = findViewById(R.id.urlInput)
        btnBack = findViewById(R.id.btnBack)
        btnForward = findViewById(R.id.btnForward)
        btnMore = findViewById(R.id.btnMore)
        progressBar = findViewById(R.id.progressBar)

        btnBack.setOnClickListener { onBack?.invoke() }
        btnForward.setOnClickListener { onForward?.invoke() }
        btnMore.setOnClickListener { onMore?.invoke() }

        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                onNavigate?.invoke(urlInput.text.toString())
                urlInput.clearFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(urlInput.windowToken, 0)
                true
            } else false
        }
    }

    fun updateUrl(url: String) {
        if (!urlInput.hasFocus()) urlInput.setText(url)
    }

    fun setProgress(progress: Int) {
        progressBar.progress = progress
        progressBar.visibility = if (progress in 1..99) View.VISIBLE else View.GONE
    }

    fun hide() {
        if (visibility == View.GONE) return
        animate().translationY(height.toFloat()).setDuration(250)
            .withEndAction { visibility = View.GONE }.start()
    }

    fun visible() {
        if (visibility == View.VISIBLE && translationY == 0f) return
        visibility = View.VISIBLE
        animate().translationY(0f).setDuration(250).start()
    }
}
