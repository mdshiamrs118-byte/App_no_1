package com.openbrows.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TabAdapter(
    private val tabs: List<Tab>,
    private var currentIndex: Int
) : RecyclerView.Adapter<TabAdapter.TabViewHolder>() {

    var onTabClick: ((Int) -> Unit)? = null
    var onTabClose: ((Int) -> Unit)? = null

    class TabViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tabTitle)
        val close: ImageButton = view.findViewById(R.id.tabClose)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tab, parent, false)
        return TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = tabs[position]
        holder.title.text = tab.title.ifBlank { "New Tab" }
        val colorRes = if (position == currentIndex) R.color.tab_active else R.color.tab_inactive
        holder.title.setTextColor(holder.itemView.context.getColor(colorRes))
        holder.itemView.setOnClickListener { onTabClick?.invoke(position) }
        holder.close.setOnClickListener { onTabClose?.invoke(position) }
    }

    override fun getItemCount() = tabs.size
}
