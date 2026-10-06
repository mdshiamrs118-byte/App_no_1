package com.openmedia.app

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class GridItem(val label: String, val icon: Int, val active: Boolean = false, val onClick: () -> Unit)

class MaxScroll(c: Context, private val maxH: Int) : ScrollView(c) {
    override fun onMeasure(w: Int, h: Int) {
        super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST))
    }
}

/** All popups in the app are custom (rounded, dark) - no default Android dialogs. */
object Pop {
    val ACCENT = 0xFF4C8DFF.toInt()
    val DIM = 0xFF9AA0AE.toInt()

    private fun dp(a: Context, v: Int) = U.dp(a, v)

    private fun ripple(a: Context): Int {
        val tv = TypedValue()
        a.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        return tv.resourceId
    }

    fun dialog(a: Context, content: View, frac: Float = 0.88f): Dialog {
        val d = Dialog(a)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(content)
        d.window?.let { w ->
            w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val dm = a.resources.displayMetrics
            val width = minOf((dm.widthPixels * frac).toInt(), dp(a, 440))
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        return d
    }

    fun text(a: Context, s: String, sp: Float, color: Int, bold: Boolean = false): TextView {
        val t = TextView(a)
        t.text = s
        t.textSize = sp
        t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        return t
    }

    fun card(a: Context, title: String?): LinearLayout {
        val l = LinearLayout(a)
        l.orientation = LinearLayout.VERTICAL
        l.setBackgroundResource(R.drawable.bg_popup)
        l.setPadding(dp(a, 20), dp(a, 18), dp(a, 20), dp(a, 12))
        if (title != null) {
            val t = text(a, title, 18f, Color.WHITE, true)
            t.setPadding(0, 0, 0, dp(a, 10))
            t.maxLines = 2
            l.addView(t)
        }
        return l
    }

    private fun btn(a: Context, s: String, color: Int, click: () -> Unit): TextView {
        val t = text(a, s, 15f, color, true)
        t.setPadding(dp(a, 16), dp(a, 10), dp(a, 16), dp(a, 10))
        t.setBackgroundResource(ripple(a))
        t.setOnClickListener { click() }
        return t
    }

    fun buttons(a: Context, d: Dialog, neg: String?, pos: String, onPos: () -> Unit): LinearLayout {
        val r = LinearLayout(a)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.END
        if (neg != null) r.addView(btn(a, neg, DIM) { d.dismiss() })
        r.addView(btn(a, pos, ACCENT) { d.dismiss(); onPos() })
        return r
    }

    fun list(a: Context, title: String, items: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val card = card(a, title)
        val d = dialog(a, card)
        val sv = MaxScroll(a, (a.resources.displayMetrics.heightPixels * 0.6f).toInt())
        val box = LinearLayout(a)
        box.orientation = LinearLayout.VERTICAL
        sv.addView(box)
        items.forEachIndexed { i, s ->
            val tv = text(a, s, 16f, if (i == selected) ACCENT else Color.WHITE, i == selected)
            tv.setPadding(dp(a, 6), dp(a, 12), dp(a, 6), dp(a, 12))
            tv.setBackgroundResource(ripple(a))
            tv.setOnClickListener { d.dismiss(); onPick(i) }
            box.addView(tv)
        }
        card.addView(sv)
        d.show()
    }

    fun confirm(a: Context, title: String, msg: String, yes: String, onYes: () -> Unit) {
        val card = card(a, title)
        val d = dialog(a, card)
        val m = text(a, msg, 15f, DIM)
        m.setPadding(0, 0, 0, dp(a, 12))
        card.addView(m)
        card.addView(buttons(a, d, "Cancel", yes, onYes))
        d.show()
    }

    fun info(a: Context, title: String, msg: String) {
        val card = card(a, title)
        val d = dialog(a, card)
        val sv = MaxScroll(a, (a.resources.displayMetrics.heightPixels * 0.6f).toInt())
        val m = text(a, msg, 14f, 0xFFD5D9E2.toInt())
        m.setTextIsSelectable(true)
        m.setPadding(0, 0, 0, dp(a, 12))
        sv.addView(m)
        card.addView(sv)
        card.addView(buttons(a, d, null, "OK") {})
        d.show()
    }

    fun input(a: Context, title: String, hint: String, type: Int, initial: String = "", onOk: (String) -> Unit) {
        val card = card(a, title)
        val d = dialog(a, card)
        val et = EditText(a)
        et.hint = hint
        et.inputType = type
        et.setText(initial)
        et.setTextColor(Color.WHITE)
        et.setHintTextColor(DIM)
        et.setBackgroundResource(R.drawable.bg_input)
        et.setPadding(dp(a, 12), dp(a, 10), dp(a, 12), dp(a, 10))
        et.setSingleLine(true)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(a, 12)
        card.addView(et, lp)
        card.addView(buttons(a, d, "Cancel", "OK") { onOk(et.text.toString().trim()) })
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        d.show()
        et.requestFocus()
    }

    fun grid(a: Context, items: List<GridItem>, cols: Int = 4) {
        val card = card(a, null)
        card.setPadding(dp(a, 8), dp(a, 10), dp(a, 8), dp(a, 10))
        val d = dialog(a, card, 0.94f)
        items.chunked(cols).forEach { rowItems ->
            val row = LinearLayout(a)
            row.orientation = LinearLayout.HORIZONTAL
            for (i in 0 until cols) {
                val g = rowItems.getOrNull(i)
                val cell = LinearLayout(a)
                cell.orientation = LinearLayout.VERTICAL
                cell.gravity = Gravity.CENTER_HORIZONTAL
                cell.setPadding(dp(a, 2), dp(a, 12), dp(a, 2), dp(a, 12))
                cell.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                if (g != null) {
                    val color = if (g.active) ACCENT else Color.WHITE
                    val iv = ImageView(a)
                    iv.setImageResource(g.icon)
                    iv.setColorFilter(color)
                    cell.addView(iv, LinearLayout.LayoutParams(dp(a, 24), dp(a, 24)))
                    val tv = text(a, g.label, 11.5f, color)
                    tv.gravity = Gravity.CENTER
                    tv.maxLines = 2
                    tv.setPadding(0, dp(a, 6), 0, 0)
                    cell.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                    cell.setBackgroundResource(ripple(a))
                    cell.setOnClickListener { d.dismiss(); g.onClick() }
                }
                row.addView(cell)
            }
            card.addView(row)
        }
        d.show()
    }

    fun textInputTypeUrl() = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
}
