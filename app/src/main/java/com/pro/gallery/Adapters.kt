package com.pro.gallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class Cell(val m: Media, val album: String?, val count: Int)

class CellAdapter(val onClick: (Cell, Int) -> Unit, val onLong: (Cell) -> Unit) : RecyclerView.Adapter<CellAdapter.VH>() {
    var cells = listOf<Cell>(); var size = 0; var sel: Set<Long> = emptySet(); var favs: Set<String> = emptySet()

    class VH(val root: View) : RecyclerView.ViewHolder(root) {
        val img: ImageView = root.findViewById(R.id.thumb); val play: View = root.findViewById(R.id.play)
        val fav: View = root.findViewById(R.id.fav); val check: View = root.findViewById(R.id.check)
        val label: TextView = root.findViewById(R.id.label)
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(LayoutInflater.from(p.context).inflate(R.layout.item_cell, p, false))
    override fun getItemCount() = cells.size

    override fun onBindViewHolder(h: VH, i: Int) {
        val c = cells[i]; val media = c.album == null
        h.root.layoutParams.height = size
        Glide.with(h.img).load(c.m.uri).centerCrop().override(400).into(h.img)
        h.play.visibility = if (media && c.m.video) View.VISIBLE else View.GONE
        h.fav.visibility = if (media && c.m.id.toString() in favs) View.VISIBLE else View.GONE
        h.check.visibility = if (media && c.m.id in sel) View.VISIBLE else View.GONE
        h.label.visibility = if (media) View.GONE else View.VISIBLE
        h.label.text = "${c.album}\n${c.count} items"
        h.root.setOnClickListener { onClick(c, h.bindingAdapterPosition) }
        h.root.setOnLongClickListener { if (media) onLong(c); true }
    }
}
