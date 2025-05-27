package com.mazeppa.secureshare.ui

import android.R.attr.name
import android.R.attr.port
import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Log.i
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mazeppa.secureshare.R
import com.mazeppa.secureshare.data.SelectedFile
import com.mazeppa.secureshare.data.client_server.FileUploader.getFileName
import com.mazeppa.secureshare.data.lan.DeviceInfo
import com.mazeppa.secureshare.data.lan.FileSender
import com.mazeppa.secureshare.data.lan.PeerDiscovery
import com.mazeppa.secureshare.databinding.FragmentSendBinding
import com.mazeppa.secureshare.databinding.ListItemDeviceBinding
import com.mazeppa.secureshare.databinding.ListItemFileBinding
import com.mazeppa.secureshare.util.formatSize
import com.mazeppa.secureshare.util.generic_recycler_view.RecyclerListAdapter
import com.mazeppa.secureshare.util.getFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.IOException
import org.json.JSONObject

class SendFragment : Fragment(), FileSender.FileSenderListener {

    companion object {
        private const val TAG = "SendFragment"
    }

    private lateinit var fileSender: FileSender
    private val selectedFileUris = mutableListOf<Uri>()
    private lateinit var binding: FragmentSendBinding
    private var onRemoveFile: ((Uri) -> Unit)? = null
    private val selectedFilesAdapter by lazy {
        RecyclerListAdapter<ListItemFileBinding, SelectedFile>(
            onInflate = ListItemFileBinding::inflate,
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

    private var onSendFilesClicked: ((String) -> Unit)? = null
    private val devicesAdapter by lazy {
        RecyclerListAdapter<ListItemDeviceBinding, DeviceInfo>(
            onInflate = ListItemDeviceBinding::inflate,
            onBind = { binding, deviceInfo, pos ->
                binding.apply {
                    textViewDeviceName.text = deviceInfo.name
                    textViewIpAddress.text = deviceInfo.ipAddress
                    buttonSendFiles.setOnClickListener {
                        if (selectedFileUris.isEmpty()) {
                            Toast.makeText(
                                context,
                                "No files selected to send",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@setOnClickListener
                        }
                        onSendFilesClicked?.invoke(deviceInfo.ipAddress)
                    }
                    buttonSendFiles.isEnabled = selectedFileUris.isEmpty()
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

    private lateinit var waitingDialog: AlertDialog

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setListeners()

        binding.recyclerViewFiles.adapter = selectedFilesAdapter
        binding.recyclerViewDevices.adapter = devicesAdapter

        discoverDevices()

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
            selectedFilesAdapter.submitList(updatedList)
        }

        onSendFilesClicked = onSendFilesClicked@{ ipAddress ->
            if (ipAddress.isBlank() || selectedFileUris.isEmpty()) {
                Toast.makeText(context, "IP address or files missing", Toast.LENGTH_SHORT)
                    .show()
                return@onSendFilesClicked
            }

            val targetIp = ipAddress
            val targetPort = 5050
            val fileName = selectedFileUris.firstOrNull()?.let { getFileName(requireContext(), it) } ?: return@onSendFilesClicked

            val json = JSONObject()
            json.put("fileName", fileName)

            val requestBody = json.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("http://$targetIp:$targetPort/invite")
                .post(requestBody)
                .build()

            requireActivity().runOnUiThread {
                waitingDialog = AlertDialog.Builder(requireContext())
                    .setTitle("Waiting for Receiver")
                    .setMessage("Waiting for the receiver to accept the file...")
                    .setCancelable(false)
                    .create()
                waitingDialog.show()
            }

            OkHttpClient().newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("Invitation", "Failed to send invite: ${e.message}")
                    requireActivity().runOnUiThread {
                        if (::waitingDialog.isInitialized) waitingDialog.dismiss()
                        Toast.makeText(requireContext(), "Failed to send invite", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        Log.d("Invitation", "Invite accepted. Proceed to send file.")
                        requireActivity().runOnUiThread {
                            if (::waitingDialog.isInitialized) waitingDialog.dismiss()
                            Toast.makeText(
                                requireContext(),
                                "Invite accepted. Proceed to send file.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        lifecycleScope.launch {
                            selectedFileUris.forEach { uri ->
                                Log.i(TAG, "Sending file: ${getFileName(requireContext(), uri)} to $ipAddress")
                                fileSender.sendFile(uri, ipAddress, port, this@SendFragment)
                                delay(1000)
                            }
                        }
                    } else {
                        Log.e("Invitation", "Invite rejected or failed: ${response.code}")
                        requireActivity().runOnUiThread {
                            if (::waitingDialog.isInitialized) waitingDialog.dismiss()
                            Toast.makeText(
                                requireContext(),
                                "Invite rejected or failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            })
        }
    }

    override fun onResume() {
        super.onResume()
        checkAddFileButtonVisibility()
    }

    private fun discoverDevices() {
        val set = mutableSetOf<DeviceInfo>()
        PeerDiscovery.discoverPeers { name, ip ->
            if (name.isNullOrBlank() || ip.isBlank() || name == Build.MODEL) {
                Log.w("PeerDiscovery", "Received empty device name or IP")
                return@discoverPeers
            }
            set.add(DeviceInfo(name.toString(), ip))
            Log.d("PeerDiscovery", "Discovered peer: $name $ip")

            activity?.runOnUiThread {
                devicesAdapter.submitList(set.toList())
            }
        }
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

                    selectedFilesAdapter.submitList(selectedFiles)
                }
            }

            listOf(viewBackground, textViewAddFile).forEach { view ->
                view.setOnClickListener {
                    filePickerLauncher.launch(arrayOf("*/*"))
                }
            }

            buttonRefreshDevices.setOnClickListener {
                discoverDevices()
            }
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