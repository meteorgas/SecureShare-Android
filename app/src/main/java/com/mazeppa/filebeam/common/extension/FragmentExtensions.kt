package com.mazeppa.filebeam.common.extension

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.widget.Toast
import androidx.annotation.AnimRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.navigation.NavDirections
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.findNavController
import com.mazeppa.filebeam.R
import java.io.Serializable
import kotlin.reflect.KClass

fun <A : Activity> Fragment.startActivity(
    cls: KClass<A>,
    finishCurrent: Boolean = false,
    intentFlags: Int? = null,
    @AnimRes enterAnim: Int? = R.anim.from_right,
    @AnimRes exitAnim: Int? = R.anim.fade_out
) {
    requireActivity().startActivity(Intent(this.activity, cls.java).apply {
        intentFlags?.let { this.flags = it }
    })
    if (finishCurrent) requireActivity().finish()
    if (enterAnim != null && exitAnim != null) {
        requireActivity().overridePendingTransition(enterAnim, exitAnim)
    }
}

fun <A : Activity> Fragment.startActivity(
    cls: KClass<A>,
    finishCurrent: Boolean = false,
    @AnimRes enterAnim: Int? = null,
    @AnimRes exitAnim: Int? = null,
    arguments: Bundle? = null
) {
    activity?.startActivity(
        cls = cls,
        finishCurrent = finishCurrent,
        enterAnim = enterAnim,
        exitAnim = exitAnim,
        arguments = arguments
    )
}

fun Fragment.safeNavigate(
    directions: NavDirections,
    extras: FragmentNavigator.Extras? = null
) = try {
    val navController = findNavController()
    if (extras != null) {
        navController.navigate(directions, extras)
    } else navController.navigate(directions)
} catch (e: Exception) {
    e.printStackTrace()
}

fun <D> Fragment.setFragmentResult(key: String, data: D) {
    findNavController().previousBackStackEntry?.savedStateHandle?.set(key, data)
}

fun <D> Fragment.getFragmentResultLiveData(key: String): MutableLiveData<D>? =
    findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData(key)

@Suppress("DEPRECATION")
inline fun <reified T : Serializable> Fragment.arg(key: String): T? = arguments?.run {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getSerializable(key, T::class.java)
    } else {
        getSerializable(key) as? T
    }
}

@Suppress("DEPRECATION")
fun Fragment.activityTransition(enterAnim: Int, exitAnim: Int) {
    requireActivity().overridePendingTransition(enterAnim, exitAnim)
}

fun Fragment.showToast(
    @StringRes message: Int,
    duration: Int = Toast.LENGTH_SHORT
) {
    Toast.makeText(
        requireContext(),
        message,
        duration
    ).show()
}

fun Fragment.showToast(
    message: String,
    duration: Int = Toast.LENGTH_SHORT
) {
    Toast.makeText(
        requireContext(),
        message,
        duration
    ).show()
}

fun Fragment.finish() {
    findNavController().popBackStack()
}

fun Fragment.dp(value: Float): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        resources.displayMetrics
    ).toInt()
}

val Fragment.screenHeight: Int
    get() = requireActivity().screenHeight

val Fragment.screenWidth: Int
    get() = requireActivity().screenWidth