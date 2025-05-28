package com.mazeppa.secureshare.data.lan

import androidx.recyclerview.widget.DiffUtil

class IncomingFileDiffCallback : DiffUtil.ItemCallback<IncomingFile>() {
    override fun areItemsTheSame(oldItem: IncomingFile, newItem: IncomingFile): Boolean {
        return oldItem.name == newItem.name
    }

    override fun areContentsTheSame(oldItem: IncomingFile, newItem: IncomingFile): Boolean {
        return oldItem == newItem
    }
}