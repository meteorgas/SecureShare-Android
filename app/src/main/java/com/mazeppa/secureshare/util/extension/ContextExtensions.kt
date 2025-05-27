package com.mazeppa.secureshare.util.extension

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.TypedValue
import android.view.inputmethod.InputMethodManager
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes

/**
 * Created by Mirkamal on 16 December 2022
 */

@ColorInt
fun Context.getThemedColor(@AttrRes res: Int): Int {
    val typedValue = TypedValue()
    theme?.resolveAttribute(res, typedValue, true)
    return typedValue.data
}

@DrawableRes
fun Context.getThemedDrawable(@AttrRes res: Int): Int {
    val themedValue = TypedValue()
    theme?.resolveAttribute(res, themedValue, true)
    return themedValue.resourceId
}

fun Context.showKeyboard() {
    val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
}

/**
 * @return [Boolean] whether any type of internet (Wi-Fi, Cellular, or Ethernet), is available
 */
fun Context.isInternetAvailable(): Boolean {
    val connectivityManager =
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

    return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
        else -> false
    }
}

fun Context.pxToDp(pixels: Int): Float {
    val density = resources.displayMetrics.densityDpi
    return pixels.toFloat() / (density / 160f)
}