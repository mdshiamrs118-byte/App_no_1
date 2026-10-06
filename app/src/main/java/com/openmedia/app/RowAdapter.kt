package com.openmedia.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class Row(
    val title: String,
    val sub: String,
    val chip: String,
    val media: Media?,
    val folderKey: String?
)

class RowAdapter(
    private val onClick: (Row) -> Unit,
    private val onMore: (Row) -> Unit
) : RecyclerView.Adapter<RowAdapter.VH>() {

    private var rows: List<Row> = emptyList()

    fun set(r: List<Row>) {
        rows = r
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.thumb)
        val title: TextView = v.findViewById(R.id.rowTitle)
        val sub: TextView = v.findViewById(R.id.rowSub)
        val chip: TextView = v.findViewById(R.id.rowChip)
        val more: ImageView = v.findViewById(R.id.rowMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false)
        v.findViewById<ImageView>(R.id.thumb).clipToOutline = true
        return VH(v)
    }

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = rows[position]
        h.title.text = r.title
        h.sub.text = r.sub
        h.chip.text = r.chip
        h.chip.visibility = if (r.chip.isEmpty()) View.GONE else View.VISIBLE
        h.itemView.setOnClickListener { onClick(r) }
        val m = r.media
        h.more.visibility = if (m != null) View.VISIBLE else View.GONE
        h.more.setOnClickListener { onMore(r) }
        h.thumb.tag = null
        h.thumb.setColorFilter(0xFF6B7280.toInt())
        h.thumb.scaleType = ImageView.ScaleType.CENTER_INSIDE
        when {
            m == null -> h.thumb.setImageResource(R.drawable.ic_folder)
            m.video -> Thumbs.load(h.itemView.context, m, h.thumb)
            else -> h.thumb.setImageResource(R.drawable.ic_music)
        }
    }
}
