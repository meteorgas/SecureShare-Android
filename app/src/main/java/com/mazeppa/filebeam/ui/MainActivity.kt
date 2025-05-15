package com.mazeppa.filebeam.ui

import android.os.Bundle
import android.view.LayoutInflater
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.mazeppa.filebeam.R
import com.mazeppa.filebeam.common.base.BaseActivity
import com.mazeppa.filebeam.data.FileReceiver
import com.mazeppa.filebeam.databinding.ActivityMainBinding
import org.json.JSONObject

class MainActivity : BaseActivity<ActivityMainBinding>() {

    private lateinit var fileReceiver: FileReceiver

    private val navHostFragment: NavHostFragment by lazy {
        supportFragmentManager.findFragmentById(R.id.fragmentContainerView) as NavHostFragment
    }

    private val navController: NavController by lazy {
        navHostFragment.navController
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val json = """{"title": "My App", "version": 3}"""
        val jsonObject = JSONObject(json)
        val title = jsonObject.getString("title")  // Parser lets you use "My App"

        fileReceiver = FileReceiver()
        binding.bottomNavigationViewMain.setupWithNavController(navController)
    }

    override val onInflate: (LayoutInflater) -> ActivityMainBinding
        get() = ActivityMainBinding::inflate

}