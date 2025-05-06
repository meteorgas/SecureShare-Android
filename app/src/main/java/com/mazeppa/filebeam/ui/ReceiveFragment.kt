package com.mazeppa.filebeam.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mazeppa.filebeam.data.FileReceiver
import com.mazeppa.filebeam.databinding.FragmentReceiveBinding
import com.mazeppa.filebeam.utils.PermissionHandler
import kotlinx.coroutines.launch

class ReceiveFragment : Fragment(), FileReceiver.FileReceiverListener {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}
    private val startActivityForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {}
    private lateinit var fileReceiver: FileReceiver
    private lateinit var binding: FragmentReceiveBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentReceiveBinding.inflate(inflater, container, false)
        fileReceiver = FileReceiver()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setListeners()
    }

    @SuppressLint("SetTextI18n")
    private fun setListeners() {
        binding.apply {
            buttonRequestPermissions.setOnClickListener {
                PermissionHandler.requestPermissions(
                    permissionLauncher = permissionLauncher,
                    startActivityForResult = startActivityForResult,
                    packageName = binding.root.context.packageName,
                )
            }

            buttonStart.setOnClickListener {
                lifecycleScope.launch {
                    fileReceiver.start(this@ReceiveFragment)
                }
            }
        }
    }

    override fun onStatusUpdate(message: String) {
        binding.textViewStatusText.text = message
    }

    override fun onFileReceived(path: String) {
        binding.textViewStatusText.text = "File received: $path"
    }

    override fun onError(message: String) {
        binding.textViewStatusText.text = "Error: $message"
    }
}