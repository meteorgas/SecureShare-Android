package com.mazeppa.secureshare.common.base

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

//    open fun configureSwipeRefreshListener(pagingAdapter: PagingDataAdapter<*, *>) {
//        val swipeRefreshLayout =
//            binding.root.findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
//
//        swipeRefreshLayout.setOnRefreshListener {
//            pagingAdapter.refresh()
//            swipeRefreshLayout.isRefreshing = false
//        }
//    }

    /**
     * Written for special cases
     */
//    fun Binding.configureRecyclerViewVisibility(data: List<Any>) {
//        viewLifecycleOwner.lifecycleScope.launch {
//            delay(MIN_SHIMMER_SHOW_DURATION)
//            if (data.isEmpty()) {
//                configureUIAccordingState(UIContentState.EMPTY)
//            } else {
//                configureUIAccordingState(UIContentState.NOT_EMPTY)
//            }
//        }
//    }
//
//    private fun Binding.configureShimmerVisibility(isLoading: Boolean) {
//        if (isLoading) {
//            configureUIAccordingState(UIContentState.LOADING)
//        }
//    }

//    private fun Binding.configureUIAccordingState(uiContentState: UIContentState) {
//        val groupNoData = root.findViewById<View>(R.id.groupNoData)
//        val recyclerView = root.findViewById<View>(R.id.recyclerView)
//        val shimmerFrameLayout = root.findViewById<View>(R.id.shimmerFrameLayout)
//
//        groupNoData?.isVisibleOrGone(uiContentState == UIContentState.EMPTY)
//        recyclerView?.isVisibleOrGone(uiContentState == UIContentState.NOT_EMPTY)
//        shimmerFrameLayout?.isVisibleOrGone(uiContentState == UIContentState.LOADING)
//    }

//    fun Binding.configureUIStateForPagination(pagingAdapter: PagingDataAdapter<*, *>) {
//        pagingAdapter.addLoadStateListener { loadState ->
//            val isListEmpty =
//                loadState.refresh is LoadState.NotLoading && pagingAdapter.itemCount == 0
//            if (isListEmpty) {
//                viewLifecycleOwner.lifecycleScope.launch {
//                    delay(MIN_SHIMMER_SHOW_DURATION)
//                    configureUIAccordingState(UIContentState.EMPTY)
//                }
//            }
//            configureShimmerVisibility(loadState.refresh is LoadState.Loading)
//            if (loadState.refresh is LoadState.NotLoading && pagingAdapter.itemCount > 0) {
//                viewLifecycleOwner.lifecycleScope.launch {
//                    delay(MIN_SHIMMER_SHOW_DURATION)
//                    configureUIAccordingState(UIContentState.NOT_EMPTY)
//                }
//            }
//        }
//    }

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

//    fun showError(errorMessage: String) =
//        (requireActivity() as BaseActivity<*>).showError(errorMessage)

}