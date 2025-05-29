package com.mazeppa.secureshare.util.extension

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.widget.Toast
import androidx.annotation.AnimRes
import androidx.annotation.StringRes
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.navigation.NavDirections
import androidx.navigation.fragment.FragmentNavigator
import androidx.navigation.fragment.findNavController
import com.mazeppa.secureshare.R
import java.io.Serializable
import kotlin.reflect.KClass

fun Fragment.showToast(message: String) {
    requireActivity().runOnUiThread {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}