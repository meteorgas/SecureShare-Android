package com.mazeppa.secureshare.util.extension

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.annotation.AnimRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.mazeppa.secureshare.BuildConfig
import java.io.File
import kotlin.reflect.KClass

/**
 * Created by Rashad Musayev on 1 September 2023, 14:14
 */
@Suppress("DEPRECATION")
fun <A : Activity> Activity.startActivity(
    cls: KClass<A>,
    finishCurrent: Boolean = false,
    @AnimRes enterAnim: Int? = null,
    @AnimRes exitAnim: Int? = null,
    arguments: Bundle? = null
) {
    startActivity(
        Intent(this, cls.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (arguments != null) {
                putExtras(arguments)
            }
        }
    )
    if (exitAnim != null && enterAnim != null) {
        overridePendingTransition(enterAnim, exitAnim)
    }
    if (finishCurrent) finish()
}

val Activity.screenHeight: Int
    get() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        return displayMetrics.heightPixels
    }

val Activity.screenWidth: Int
    get() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        return displayMetrics.widthPixels
    }

/**
 * Configures System bars (navigation and status bar) for edge-to-edge screen
 *
 * @param root root view of the activity
 * @param lightNavigationBar defines whether navigation bar should be light or dark. Default is true,
 * means navigation bar is light by default
 * @param lightStatusBar defines whether status bar should be light or dark. Default is true,
 * means status bar is light by default
 * @param topMarginExists defines whether there should be m argin from the top with height of status bar
 * @param bottomMarginExists defines whether there should be margin from the bottom with height of navigation bar
 */
@Suppress("DEPRECATION")
fun Activity.configureSystemBars(
    root: View,
    edgeToEdge: Boolean = false,
    lightSystemBar: Boolean = true,
    topMarginExists: Boolean = true,
    bottomMarginExists: Boolean = true,
) {
    //Step 1: Lays out content behind system bars
    WindowCompat.setDecorFitsSystemWindows(window, !edgeToEdge)

    //Step 2: Change the color of system bars
//    Although code below is written in documentation, it does not work for some android versions
//    val windowInsetsController = ViewCompat.getWindowInsetsController(window.decorView)
//
//    windowInsetsController?.isAppearanceLightNavigationBars = lightNavigationBar
//    windowInsetsController?.isAppearanceLightStatusBars = lightStatusBar

    window.decorView.systemUiVisibility =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (lightSystemBar) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
        } else {
            if (lightSystemBar) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0
        }

//    window.navigationBarColor = applicationContext.getColor(R.color.colorBlue600)

//    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//        window.insetsController?.setSystemBarsAppearance(
//            0,
//            0
//        )
//    } else {
//        window.decorView.systemUiVisibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
//        } else {
//            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
//        }
//    }

    if (edgeToEdge) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply the insets as a margin to the view. Here the system is setting
            // only the bottom, left, and right dimensions, but apply whichever insets are
            // appropriate to your layout. You can also update the view padding
            // if that's more appropriate.
            view.updatePadding(
                top = if (topMarginExists) insets.top else 0,
                bottom = if (bottomMarginExists) insets.bottom else 0
            )

            // Return CONSUMED if you don't want want the window insets to keep being
            // passed down to descendant views.
            WindowInsetsCompat.CONSUMED
        }
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
    }
}

/**
 * Returns whether app is in night or light mode
 */
//TODO: Don't forget to uncomment to enable dark mode
fun Activity.isLightMode(): Boolean {
    return true/*when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
        Configuration.UI_MODE_NIGHT_NO -> true
        Configuration.UI_MODE_NIGHT_YES -> false
        else -> {
            false
        }
    }*/
}