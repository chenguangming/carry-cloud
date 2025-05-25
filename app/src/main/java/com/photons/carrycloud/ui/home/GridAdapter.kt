package com.photons.carrycloud.ui.home

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.photons.carrycloud.R

class GridAdapter(
    private val context: Context,
    private val items: List<GridItem>,
    private val onItemClick: (Int) -> Unit
) : BaseAdapter() {

    companion object {
        private const val TYPE_ITEM = 0
        private const val TYPE_ADD = 1
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Any = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getItemViewType(position: Int): Int {
        return if (items[position].isAddButton) TYPE_ADD else TYPE_ITEM
    }

    override fun getViewTypeCount(): Int = 2

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View
        val viewHolder: ViewHolder

        if (convertView == null) {
            view = if (getItemViewType(position) == TYPE_ADD) {
                LayoutInflater.from(context).inflate(R.layout.grid_item_add, parent, false)
            } else {
                LayoutInflater.from(context).inflate(R.layout.grid_item, parent, false)
            }
            viewHolder = when (getItemViewType(position)) {
                TYPE_ADD -> AddViewHolder(view)
                else -> ItemViewHolder(view)
            }
            view.tag = viewHolder
        } else {
            view = convertView
            viewHolder = view.tag as ViewHolder
        }

        when (viewHolder) {
            is ItemViewHolder -> viewHolder.textView.text = items[position].text
            is AddViewHolder -> viewHolder.imageView.setImageResource(android.R.drawable.ic_input_add)
        }

        view.setOnClickListener { onItemClick(position) }
        return view
    }

    private sealed class ViewHolder
    private class ItemViewHolder(view: View) : ViewHolder() {
        val textView: TextView = view.findViewById(R.id.text_view)
    }
    private class AddViewHolder(view: View) : ViewHolder() {
        val imageView: ImageView = view.findViewById(R.id.image_add)
    }
}