package com.mazeppa.secureshare.util.base

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding

abstract class BaseFragment<Binding : ViewBinding> : Fragment() {

    private var _binding: Binding? = null
    val binding: Binding
        get() = checkNotNull(_binding) {
            "Binding is null"
        }

    abstract val onInflate: (LayoutInflater, ViewGroup?, Boolean) -> Binding

    val screenBackground: Drawable
        get() = binding.root.background

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = onInflate(inflater, container, false)
        return _binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

    override fun onResume() {
        super.onResume()
        println("onResume: ${this::class.java.simpleName}")
    }

    override fun onPause() {
        super.onPause()
        println("onPause: ${this::class.java.simpleName}")
    }

    override fun onStop() {
        super.onStop()
        println("onStop: ${this::class.java.simpleName}")
    }

    override fun onStart() {
        super.onStart()
        println("onStart: ${this::class.java.simpleName}")
    }
}