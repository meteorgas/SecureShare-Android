package com.mazeppa.secureshare.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mazeppa.secureshare.data.FileSender
import com.mazeppa.secureshare.databinding.FragmentSendBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SendFragment : Fragment(), FileSender.FileSenderListener {

    private lateinit var fileSender: FileSender
    private var selectedFileUri: Uri? = null
    private lateinit var binding: FragmentSendBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSendBinding.inflate(inflater, container, false)
        fileSender = FileSender(inflater.context)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setListeners()
    }

    private val selectedFileUris = mutableListOf<Uri>()

    @SuppressLint("SetTextI18n")
    private fun setListeners() {
        binding.apply {
            progressBar.progress = 0

            val filePickerLauncher = registerForActivityResult(
                ActivityResultContracts.OpenMultipleDocuments()
            ) { uris: List<Uri> ->
                selectedFileUris.clear()
                selectedFileUris.addAll(uris)

                if (uris.isNotEmpty()) {
                    val filenames = uris.map { getFileName(it) }
                    binding.textViewStatusText.text = "Selected: ${filenames.joinToString(", ")}"
                }
            }

            buttonChooseFile.setOnClickListener {
                filePickerLauncher.launch(arrayOf("*/*"))
            }

            buttonSend.setOnClickListener {
                val ip = editTextIpAddress.text.toString()
                val port = 5050

                if (ip.isBlank() || selectedFileUris.isEmpty()) {
                    Toast.makeText(context, "IP address or files missing", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }

                lifecycleScope.launch {
                    selectedFileUris.forEach { uri ->
                        fileSender.sendFile(uri, ip, port, this@SendFragment)
                        delay(1000) // small delay between files (optional)
                    }
                }
            }
        }
    }

    private fun getFileSize(uri: Uri): Long {
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            if (it.moveToFirst()) it.getLong(sizeIndex) else 0L
        } ?: 0L
    }

    private fun formatSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1) String.format("%.2f MB", mb) else String.format("%.1f KB", kb)
    }

    private fun getFileName(uri: Uri): String {
        val cursor = binding.root.context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst()) it.getString(nameIndex) else "Unknown"
        } ?: "Unknown"
    }

    override fun onStatusUpdate(message: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                binding.textViewStatusText.text = message
            }
        }
    }

    override fun onProgressUpdate(progress: Int) {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                binding.progressBar.progress = progress
            }
        }
    }

    override fun onTransferStatsUpdate(speedBytesPerSec: Double, remainingSec: Double) {
        binding.textViewSpeed.text = "Speed: ${formatSize(speedBytesPerSec.toLong())}/s"
        binding.textViewEstimatedTime.text = "Time left: ${formatTime(remainingSec)}"
    }

    private fun formatTime(seconds: Double): String {
        val mins = (seconds / 60).toInt()
        val secs = (seconds % 60).toInt()
        return if (mins > 0) "$mins min $secs sec" else "$secs sec"
    }

    override fun onComplete() {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                binding.textViewStatusText.text = "File sent successfully!"
                binding.progressBar.progress = 0
                binding.textViewSpeed.text = ""
                binding.textViewEstimatedTime.text = ""
            }
        }
    }

    override fun onError(message: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                binding.progressBar.progress = 0
                binding.textViewStatusText.text = "Error: $message"
            }
        }
    }
}