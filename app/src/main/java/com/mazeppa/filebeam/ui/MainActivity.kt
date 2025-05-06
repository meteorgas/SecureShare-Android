package com.mazeppa.filebeam.ui

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.mazeppa.filebeam.R
import com.mazeppa.filebeam.common.base.BaseActivity
import com.mazeppa.filebeam.data.FileReceiver
import com.mazeppa.filebeam.databinding.ActivityMainBinding

class MainActivity :  BaseActivity<ActivityMainBinding>() {

    private lateinit var fileReceiver: FileReceiver

    private val navHostFragment: NavHostFragment by lazy {
        supportFragmentManager.findFragmentById(R.id.fragmentContainerView) as NavHostFragment
    }

    private val navController: NavController by lazy {
        navHostFragment.navController
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fileReceiver = FileReceiver()
        binding.bottomNavigationViewMain.setupWithNavController(navController)
    }

    override val onInflate: (LayoutInflater) -> ActivityMainBinding
        get() = ActivityMainBinding::inflate

}