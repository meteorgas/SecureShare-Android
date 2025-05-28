package com.mazeppa.secureshare.ui

import android.os.Bundle
import android.view.LayoutInflater
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.mazeppa.secureshare.R
import com.mazeppa.secureshare.util.base.BaseActivity
import com.mazeppa.secureshare.data.lan.receiver.FileReceiver
import com.mazeppa.secureshare.data.p2p.WebRtcManager
import com.mazeppa.secureshare.databinding.ActivityMainBinding
import org.json.JSONObject

class MainActivity : BaseActivity<ActivityMainBinding>() {

    private val navHostFragment: NavHostFragment by lazy {
        supportFragmentManager.findFragmentById(R.id.fragmentContainerView) as NavHostFragment
    }

    private val navController: NavController by lazy {
        navHostFragment.navController
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.bottomNavigationViewMain.setupWithNavController(navController)
        val rtcManager = WebRtcManager(applicationContext)
        rtcManager.initialize()
    }

    override val onInflate: (LayoutInflater) -> ActivityMainBinding
        get() = ActivityMainBinding::inflate

}