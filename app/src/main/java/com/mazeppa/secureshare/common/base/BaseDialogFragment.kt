package com.mazeppa.secureshare.common.base

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.viewbinding.ViewBinding

/**
 * @author Rashad Musayev (https://github.com/RashadMusayev23) on 8/22/2023 - 0:55
 */
abstract class BaseDialogFragment<Binding : ViewBinding> : DialogFragment() {

    private var _binding: Binding? = null
    val binding: Binding
        get() = checkNotNull(_binding) { "Binding is null" }

    abstract val onInflate: (LayoutInflater, ViewGroup?, Boolean) -> Binding

    open val isCancellable: Boolean
        get() = true

    abstract val dialogView: View

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = onInflate(inflater, container, false)
        return _binding?.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        println("Current fragment ${this::class.java.simpleName}")
        binding.apply {
            setListeners()
            initViews()
            observeValues()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    open fun Binding.setListeners() {}

    open fun Binding.initViews() {}

    open fun Binding.observeValues() {}

}