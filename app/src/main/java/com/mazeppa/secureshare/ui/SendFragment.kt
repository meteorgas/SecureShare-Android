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
import kotlinx.coroutines.launch

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

    @SuppressLint("SetTextI18n")
    private fun setListeners() {
        binding.apply {
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
                    Toast.makeText(
                        binding.root.context,
                        "IP address or file is missing",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                lifecycleScope.launch {
                    fileSender.sendFile(uri, ip, port, this@SendFragment)
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        val cursor = binding.root.context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst()) it.getString(nameIndex) else "Unknown"
        } ?: "Unknown"
    }

    override fun onStatusUpdate(message: String) {
        binding.textViewStatusText.text = message
    }

    override fun onProgressUpdate(progress: Int) {
        binding.progressBar.progress = progress
    }

    override fun onComplete() {
        binding.textViewStatusText.text = "File sent successfully!"
        binding.progressBar.progress = 0
    }

    override fun onError(message: String) {
        binding.textViewStatusText.text = "Error: $message"
    }
}