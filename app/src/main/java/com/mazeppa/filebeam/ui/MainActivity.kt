package com.mazeppa.filebeam.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mazeppa.filebeam.data.FileReceiver
import com.mazeppa.filebeam.data.FileSender
import com.mazeppa.filebeam.databinding.ActivityMainBinding
import com.mazeppa.filebeam.utils.PermissionHandler
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), FileReceiver.FileReceiverListener, FileSender.FileSenderListener {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}
    private val startActivityForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {}

    private lateinit var binding: ActivityMainBinding
    private lateinit var fileReceiver: FileReceiver
    private lateinit var fileSender: FileSender
    private var selectedFileUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        fileReceiver = FileReceiver()
        fileSender = FileSender(this@MainActivity)
        setContentView(binding.root)
        setListeners()
    }

    @SuppressLint("SetTextI18n")
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


            val filePickerLauncher = registerForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                uri?.let {
                    selectedFileUri = it
                    textViewStatusText.text = "Selected: ${getFileName(it)}"
                }
            }

            buttonChooseFile.setOnClickListener {
                filePickerLauncher.launch("*/*")
            }

            buttonSend.setOnClickListener {
                val ip = editTextIpAddress.text.toString()
                val port = 5050 // or make it user-defined
                val uri = selectedFileUri

                if (ip.isBlank() || uri == null) {
                    Toast.makeText(this@MainActivity, "IP address or file is missing", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                lifecycleScope.launch {
                    fileSender.sendFile(uri, ip, port, this@MainActivity)
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst()) it.getString(nameIndex) else "Unknown"
        } ?: "Unknown"
    }

    override fun onStatusUpdate(message: String) {
        runOnUiThread {
            binding.textViewStatusText.text = message
        }
    }

    override fun onProgressUpdate(progress: Int) {
        runOnUiThread { binding.progressBar.progress = progress }
    }

    override fun onComplete() {
        runOnUiThread {
            binding.textViewStatusText.text = "File sent successfully!"
            binding.progressBar.progress = 0
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