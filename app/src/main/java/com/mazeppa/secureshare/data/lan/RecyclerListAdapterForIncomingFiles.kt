package com.mazeppa.secureshare.data.lan

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import com.mazeppa.secureshare.databinding.ListItemIncomingFileBinding
import com.mazeppa.secureshare.util.generic_recycler_view.RecyclerViewHolder

class RecyclerListAdapterForIncomingFiles(
    private val onInflate: (LayoutInflater, ViewGroup?, Boolean) -> ListItemIncomingFileBinding,
    private val onBind: (ListItemIncomingFileBinding, IncomingFile, Int) -> Unit
) : ListAdapter<IncomingFile, RecyclerViewHolder<ListItemIncomingFileBinding, IncomingFile>>(
    IncomingFileDiffCallback()
) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerViewHolder<ListItemIncomingFileBinding, IncomingFile> {
        return RecyclerViewHolder.Companion.create(parent, onInflate, onBind)
    }

    override fun onBindViewHolder(
        holder: RecyclerViewHolder<ListItemIncomingFileBinding, IncomingFile>,
        position: Int
    ) {
        holder.bind(getItem(position))
    }
}