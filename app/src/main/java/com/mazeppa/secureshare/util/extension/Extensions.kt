package com.mazeppa.secureshare.util.extension

import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Window
import android.widget.FrameLayout
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Created by Mirkamal on 16 December 2022
 */

fun Number.toDp(metrics: DisplayMetrics) = TypedValue.applyDimension(
    TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), metrics
)

val BottomSheetDialogFragment.bottomSheetBehavior: BottomSheetBehavior<FrameLayout>
    get() = (dialog as BottomSheetDialog).behavior

fun NavController.safeNavigate(directions: NavDirections) {
    try {
        this.navigate(directions)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun Window.getSoftInputMode(): Int {
    return attributes.softInputMode
}