package com.mazeppa.secureshare.utils.generic_recycler_view

data class RecyclerViewItemClickListener<T>(
    private val itemClick: (item: T) -> Unit
) {
    fun onItemClick(item: T) = itemClick(item)
}