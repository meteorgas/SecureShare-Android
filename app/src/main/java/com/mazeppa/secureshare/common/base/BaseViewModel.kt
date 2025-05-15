package com.mazeppa.secureshare.common.base

import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Created by Mirkamal on 13 December 2022
 */

abstract class BaseViewModel<State>(
    val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<State> = _state.asStateFlow()

    abstract val initialState: State

//    fun postState(state: State?) {
//        state?.let { _state.postValue(it) }
//    }

    @MainThread
    fun setState(state: State) {
        state.let { _state.value = it }
    }

    fun doOnUIThread(block: () -> Unit) = Handler(Looper.getMainLooper()).post(block)

//    inline fun <reified Args : NavArgs> getArgs(): Args = Args::class.java.run {
//        val argsBundle = Bundle().apply {
//            savedStateHandle.keys().forEach {
//                putSerializable(it, savedStateHandle[it])
//            }
//        }
//        val method = getDeclaredMethod("fromBundle", Bundle::class.java)
//        val args = method.invoke(null, argsBundle) as Args
//        args
//    }

}