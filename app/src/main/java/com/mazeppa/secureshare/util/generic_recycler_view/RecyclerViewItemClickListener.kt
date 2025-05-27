package com.mazeppa.secureshare.util.generic_recycler_view

data class RecyclerViewItemClickListener<T>(
    private val itemClick: (item: T) -> Unit
) {
    fun onItemClick(item: T) = itemClick(item)
}