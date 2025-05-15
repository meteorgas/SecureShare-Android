package com.mazeppa.secureshare.utils.generic_recycler_view

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.viewbinding.ViewBinding

class RecyclerListAdapter<VB : ViewBinding, D : Any>(
    private val onInflate: (LayoutInflater, ViewGroup?, Boolean) -> VB,
    private val onBind: (VB, D, Int) -> Unit
) : ListAdapter<D, RecyclerViewHolder<VB, D>>(GenericDiffCallback<D>()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerViewHolder<VB, D> {
        return RecyclerViewHolder.Companion.create(parent, onInflate, onBind)
    }

    override fun onBindViewHolder(holder: RecyclerViewHolder<VB, D>, position: Int) {
        holder.bind(getItem(position))
    }

}