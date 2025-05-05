package com.mazeppa.filebeam.ui

import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mazeppa.filebeam.data.FileReceiver
import com.mazeppa.filebeam.databinding.ActivityMainBinding
import com.mazeppa.filebeam.utils.PermissionHandler
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), FileReceiver.FileReceiverListener {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}
    private val startActivityForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {}

    private lateinit var binding: ActivityMainBinding
    private lateinit var fileReceiver: FileReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        fileReceiver = FileReceiver()
        setContentView(binding.root)
        setListeners()
    }

    private fun setListeners() {
        binding.apply {
            buttonRequestPermissions.setOnClickListener {
                PermissionHandler.requestPermissions(
                    permissionLauncher = permissionLauncher,
                    startActivityForResult = startActivityForResult,
                    packageName = packageName,
                )
            }

            buttonStart.setOnClickListener {
                lifecycleScope.launch {
                    fileReceiver.start(this@MainActivity)
                }
            }
        }
    }

    override fun onStatusUpdate(message: String) {
        runOnUiThread {
            binding.textViewStatusText.text = message
        }
    }

    override fun onFileReceived(path: String) {
        runOnUiThread {
            binding.textViewStatusText.text = "File received: $path"
        }
    }

    override fun onError(message: String) {
        runOnUiThread {
            binding.textViewStatusText.text = "Error: $message"
        }
    }
}