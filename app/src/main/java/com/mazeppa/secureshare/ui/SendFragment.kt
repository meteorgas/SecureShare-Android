package com.mazeppa.secureshare.ui

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mazeppa.secureshare.R
import com.mazeppa.secureshare.common.constant.BASE_URL
import com.mazeppa.secureshare.data.lan.FileSender
import com.mazeppa.secureshare.data.SelectedFile
import com.mazeppa.secureshare.data.client_server.FileUploader.uploadFileToServer
import com.mazeppa.secureshare.data.p2p.SocketManager
import com.mazeppa.secureshare.databinding.FragmentSendBinding
import com.mazeppa.secureshare.databinding.ListItemBinding
import com.mazeppa.secureshare.utils.generic_recycler_view.RecyclerListAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
                                    val source = ImageDecoder.createSource(contentResolver, selectedFile.uri)
                                    val bitmap = ImageDecoder.decodeBitmap(source)
                                    imageViewFileIcon.setImageBitmap(bitmap)
                                } catch (_: Exception) {
                                    imageViewFileIcon.setImageResource(R.drawable.ic_file)
                                }
                            } else {
                                try {
                                    val inputStream = contentResolver.openInputStream(selectedFile.uri)
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
        binding.recyclerView.adapter = adapter
        onRemoveFile = { uri ->
            selectedFileUris.remove(uri)

            val updatedList = selectedFileUris.map { uri ->
                SelectedFile(
                    name = getFileName(uri),
                    size = formatSize(getFileSize(uri)),
                    uri = uri
                )
            }
            adapter.submitList(updatedList)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.apply {
            buttonSend.isEnabled = selectedFileUris.isNotEmpty()
        }
    }

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
                    val fileSizes = uris.map { formatSize(getFileSize(it)) }
                    val selectedFiles = uris.mapIndexed { index, uri ->
                        SelectedFile(
                            name = filenames[index],
                            size = fileSizes[index],
                            uri = uri
                        )
                    }

                    buttonSend.isEnabled = true
                    adapter.submitList(selectedFiles)

                    binding.textViewStatusText.text = "Selected Files:"
                }
            }

//            SocketManager.connect(userId = "android-1234")

            binding.buttonDiscoverNearbyDevices.setOnClickListener {
                Log.i(TAG, "button DiscoverNearbyDevices clicked")
                SocketManager.connect(userId = "android-1234")
                SocketManager.discoverPeer("mac-5678")
            }

//            buttonDiscoverNearbyDevices.setOnClickListener {
//                PeerDiscovery.discoverPeers { address ->
//                    lifecycleScope.launch {
//                        binding.textViewStatusText.text = "Discovered peer: $address"
//                    }
//                }
//            }

            buttonChooseFile.setOnClickListener {
                filePickerLauncher.launch(arrayOf("*/*"))
            }

            buttonSend.setOnClickListener {
//                val ip = editTextIpAddress.text.toString()
//                val port = 5050

//                if (ip.isBlank() || selectedFileUris.isEmpty()) {
//                    Toast.makeText(context, "IP address or files missing", Toast.LENGTH_SHORT)
//                        .show()
//                    return@setOnClickListener
//                }

                lifecycleScope.launch {
                    selectedFileUris.forEach { uri ->
                        uploadFileToServer(requireContext(), uri, "$BASE_URL/upload") { success, message ->
                            requireActivity().runOnUiThread {
                                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                            }
                        }
//                        fileSender.sendFile(uri, ip, port, this@SendFragment)
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
//        binding.textViewSpeed.text = "Speed: ${formatSize(speedBytesPerSec.toLong())}/s"
//        binding.textViewEstimatedTime.text = "Time left: ${formatTime(remainingSec)}"
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
//                binding.textViewSpeed.text = ""
//                binding.textViewEstimatedTime.text = ""
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