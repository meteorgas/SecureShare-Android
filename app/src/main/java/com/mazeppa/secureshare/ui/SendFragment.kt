package com.mazeppa.secureshare.ui

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mazeppa.secureshare.R
import com.mazeppa.secureshare.data.SelectedFile
import com.mazeppa.secureshare.data.client_server.FileUploader.getFileName
import com.mazeppa.secureshare.data.lan.FileSender
import com.mazeppa.secureshare.databinding.FragmentSendBinding
import com.mazeppa.secureshare.databinding.ListItemBinding
import com.mazeppa.secureshare.util.formatSize
import com.mazeppa.secureshare.util.generic_recycler_view.RecyclerListAdapter
import com.mazeppa.secureshare.util.getFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SendFragment : Fragment(), FileSender.FileSenderListener {

    companion object {
        private const val TAG = "SendFragment"
    }

    private lateinit var fileSender: FileSender
    private val selectedFileUris = mutableListOf<Uri>()
    private lateinit var binding: FragmentSendBinding
    private var onRemoveFile: ((Uri) -> Unit)? = null
    private val adapter by lazy {
        RecyclerListAdapter<ListItemBinding, SelectedFile>(
            onInflate = ListItemBinding::inflate,
            onBind = { binding, selectedFile, pos ->
                binding.apply {
                    context?.apply {
                        val mime = contentResolver.getType(selectedFile.uri) ?: ""
                        if (mime.startsWith("image/")) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                try {
                                    val source =
                                        ImageDecoder.createSource(contentResolver, selectedFile.uri)
                                    val bitmap = ImageDecoder.decodeBitmap(source)
                                    imageViewFileIcon.setImageBitmap(bitmap)
                                } catch (_: Exception) {
                                    imageViewFileIcon.setImageResource(R.drawable.ic_file)
                                }
                            } else {
                                try {
                                    val inputStream =
                                        contentResolver.openInputStream(selectedFile.uri)
                                    val bitmap = BitmapFactory.decodeStream(inputStream)
                                    imageViewFileIcon.setImageBitmap(bitmap)
                                } catch (_: Exception) {
                                    imageViewFileIcon.setImageResource(R.drawable.ic_file)
                                }
                            }
                        } else {
                            imageViewFileIcon.setImageResource(R.drawable.ic_file)
                        }
                    }

                    textViewFileName.text = selectedFile.name
                    textViewFileSize.text = selectedFile.size
                    buttonRemoveFile.setOnClickListener {
                        onRemoveFile?.invoke(selectedFile.uri)
                    }
                }
            }
        )
    }

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
        binding.recyclerViewFiles.adapter = adapter
        onRemoveFile = { uri ->
            selectedFileUris.remove(uri)

            checkAddFileButtonVisibility()
            val updatedList = selectedFileUris.map { uri ->
                SelectedFile(
                    name = getFileName(requireContext(), uri),
                    size = formatSize(getFileSize(requireContext(), uri)),
                    uri = uri
                )
            }
            adapter.submitList(updatedList)
        }
    }

    override fun onResume() {
        super.onResume()
        checkAddFileButtonVisibility()
//        binding.apply {
//            buttonSend.isEnabled = selectedFileUris.isNotEmpty()
//        }
    }

    private fun checkAddFileButtonVisibility() {
        binding.apply {
            if (selectedFileUris.isEmpty()) {
                viewBackground.visibility = View.VISIBLE
                textViewAddFile.visibility = View.VISIBLE
            } else {
                viewBackground.visibility = View.GONE
                textViewAddFile.visibility = View.GONE
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setListeners() {
        binding.apply {
            val filePickerLauncher = registerForActivityResult(
                ActivityResultContracts.OpenMultipleDocuments()
            ) { uris: List<Uri> ->
                selectedFileUris.clear()
                selectedFileUris.addAll(uris)

                if (uris.isNotEmpty()) {
                    val filenames = uris.map { getFileName(requireContext(), it) }
                    val fileSizes = uris.map { formatSize(getFileSize(requireContext(), it)) }
                    val selectedFiles = uris.mapIndexed { index, uri ->
                        SelectedFile(
                            name = filenames[index],
                            size = fileSizes[index],
                            uri = uri
                        )
                    }

//                    buttonSend.isEnabled = true
                    adapter.submitList(selectedFiles)
                }
            }

            viewBackground.setOnClickListener {
                filePickerLauncher.launch(arrayOf("*/*"))
            }
            textViewAddFile.setOnClickListener {
                filePickerLauncher.launch(arrayOf("*/*"))
            }
//            listOf(viewBackground, textViewAddFile).forEach { view ->
//                view.setOnClickListener {
//                    filePickerLauncher.launch(arrayOf("*/*"))
//                }
//            }

//            binding.buttonDiscoverNearbyDevices.setOnClickListener {
//                Log.i(TAG, "button DiscoverNearbyDevices clicked")
//                SocketManager.connect(userId = "android-1234")
//                SocketManager.discoverPeer("mac-5678")
//            }
//
//            buttonSend.setOnClickListener {
////                val ip = editTextIpAddress.text.toString()
////                val port = 5050
//
////                if (ip.isBlank() || selectedFileUris.isEmpty()) {
////                    Toast.makeText(context, "IP address or files missing", Toast.LENGTH_SHORT)
////                        .show()
////                    return@setOnClickListener
////                }
//
//                lifecycleScope.launch {
//                    selectedFileUris.forEach { uri ->
//                        uploadFileToServer(
//                            requireContext(),
//                            uri,
//                            "$BASE_URL/upload"
//                        ) { success, message ->
//                            requireActivity().runOnUiThread {
//                                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
//                            }
//                        }
////                        fileSender.sendFile(uri, ip, port, this@SendFragment)
//                        delay(1000) // small delay between files (optional)
//                    }
//                }
//            }
        }
    }


    override fun onStatusUpdate(message: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
            }
        }
    }

    override fun onProgressUpdate(progress: Int) {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
            }
        }
    }

    override fun onTransferStatsUpdate(speedBytesPerSec: Double, remainingSec: Double) {

    }

    override fun onComplete() {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
            }
        }
    }

    override fun onError(message: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
            }
        }
    }
}