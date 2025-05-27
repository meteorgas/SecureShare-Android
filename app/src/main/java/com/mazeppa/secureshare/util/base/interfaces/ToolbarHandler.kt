package com.mazeppa.secureshare.util.base.interfaces

import androidx.appcompat.widget.Toolbar
//import cr.app.mia.utils.enums.ToolbarItem

/**
 * Created by Mirkamal on 30 December 2022
 */

interface ToolbarHandler {

//    val items: Array<ToolbarItem>
//        get() = emptyArray()

    fun handleToolbar(toolbar: Toolbar)
}