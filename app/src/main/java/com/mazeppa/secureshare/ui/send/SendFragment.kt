package com.mazeppa.secureshare.ui.send

import android.R.attr.name
import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mazeppa.secureshare.data.OutgoingFile
import com.mazeppa.secureshare.data.client_server.FileUploader.getFileName
import com.mazeppa.secureshare.data.lan.invitation.InvitationSender
import com.mazeppa.secureshare.data.lan.model.DeviceInfo
import com.mazeppa.secureshare.data.lan.peer_discovery.PeerDiscovery
import com.mazeppa.secureshare.data.lan.sender.FileSender
import com.mazeppa.secureshare.databinding.FragmentSendBinding
import com.mazeppa.secureshare.util.FileManager
import com.mazeppa.secureshare.util.FileManager.formatSize
import com.mazeppa.secureshare.util.FileManager.getFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SendFragment : Fragment(), FileSender.FileSenderListener {

    companion object {
        private const val TAG = "SendFragment"
    }

    private lateinit var fileSender: FileSender
    private lateinit var waitingDialog: AlertDialog
    private lateinit var binding: FragmentSendBinding
    private val outgoingFiles = mutableListOf<OutgoingFile>()

    private var onRemoveFile: ((Uri) -> Unit)? = null
    private var onSendFilesClicked: ((String) -> Unit)? = null

    private val outgoingFilesAdapter by lazy {
        FileListAdapter { outgoingFile ->
            outgoingFiles.remove(outgoingFile)
            refreshFileList()
        }
    }

    private val devicesAdapter by lazy {
        DeviceListAdapter { ip ->
            if (outgoingFiles.isNotEmpty()) sendInvitation(ip)
            else Toast.makeText(requireContext(), "No files selected to send", Toast.LENGTH_SHORT)
                .show()
        }
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
        binding.recyclerViewFiles.adapter = outgoingFilesAdapter
        binding.recyclerViewDevices.adapter = devicesAdapter
        discoverDevices()

        onRemoveFile = ::handleRemoveFile
        onSendFilesClicked = ::sendInvitation
    }

    override fun onResume() {
        super.onResume()
        toggleAddFileVisibility()
    }

    private fun checkAddFileButtonVisibility() {
        binding.apply {
            if (outgoingFiles.isEmpty()) {
                viewBackground.visibility = View.VISIBLE
                textViewAddFile.visibility = View.VISIBLE
            } else {
                viewBackground.visibility = View.GONE
                textViewAddFile.visibility = View.GONE
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setListeners() = binding.apply {
        val filePickerLauncher =
            registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                val selected = uris.map {
                    OutgoingFile(
                        name = getFileName(requireContext(), it),
                        size = formatSize(getFileSize(requireContext(), it)),
                        uri = it
                    )
                }
                outgoingFiles.clear()
                outgoingFiles.addAll(selected)
                refreshFileList()
            }

        listOf(viewBackground, textViewAddFile).forEach {
            it.setOnClickListener { filePickerLauncher.launch(arrayOf("*/*")) }
        }

        buttonRefreshDevices.setOnClickListener { discoverDevices() }

        buttonAddIpAddress.setOnClickListener {
            val editText = EditText(requireContext()).apply {
                hint = "Enter IP address"
                inputType = InputType.TYPE_CLASS_TEXT
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Manual Target")
                .setMessage("Enter the receiver's IP address:")
                .setView(editText)
                .setPositiveButton("Send") { _, _ ->
                    val ip = editText.text.toString().trim()
                    if (ip.isNotBlank()) {
                        if (outgoingFiles.isNotEmpty()) {
                            sendInvitation(ip)
                        } else {
                            Toast.makeText(requireContext(), "No files selected", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), "Invalid IP address", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun discoverDevices() {
        val set = mutableSetOf<DeviceInfo>()
        PeerDiscovery.discoverPeers { name, ip ->
            if (!name.isNullOrBlank() && ip.isNotBlank() && name != Build.MODEL) {
                set.add(DeviceInfo(name, ip))
                requireActivity().runOnUiThread { devicesAdapter.submitList(set.toList()) }
            }
        }
    }

    private fun handleRemoveFile(uri: Uri) {
        outgoingFiles.removeIf { it.uri == uri }
        refreshFileList()
    }

    private fun refreshFileList() {
        checkAddFileButtonVisibility()
        val updatedList = FileManager.mapUrisToFiles(requireContext(), outgoingFiles.map { it.uri })
        outgoingFilesAdapter.submitList(updatedList)
    }

    private fun sendInvitation(ipAddress: String) {
        if (ipAddress.isBlank() || outgoingFiles.isEmpty()) {
            Toast.makeText(requireContext(), "IP address or files missing", Toast.LENGTH_SHORT)
                .show()
            return
        }

        val fileName = outgoingFiles.firstOrNull()?.let {
            getFileName(requireContext(), it.uri)
        } ?: return

        showWaitingDialog()

        InvitationSender.send(
            ip = ipAddress,
            fileName = fileName,
            onAccepted = {
                dismissWaitingDialog()
                Toast.makeText(
                    requireContext(),
                    "Invite accepted. Sending files...",
                    Toast.LENGTH_SHORT
                ).show()
                lifecycleScope.launch {
                    outgoingFiles.forEach { file ->
                        Log.i(
                            TAG,
                            "Sending file: ${getFileName(requireContext(), file.uri)} to $ipAddress"
                        )
                        fileSender.sendFile(file.uri, ipAddress, 5051, this@SendFragment)
                        delay(1000)
                    }
                }
            },
            onRejected = {
                dismissWaitingDialog()
                Toast.makeText(requireContext(), "Invite rejected by receiver", Toast.LENGTH_SHORT)
                    .show()
            },
            onError = { message ->
                dismissWaitingDialog()
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showWaitingDialog() {
        waitingDialog = AlertDialog.Builder(requireContext())
            .setTitle("Waiting for Receiver")
            .setMessage("Waiting for the receiver to accept the file...")
            .setCancelable(false)
            .create()
        waitingDialog.show()
    }

    private fun dismissWaitingDialog() {
        if (::waitingDialog.isInitialized) waitingDialog.dismiss()
    }

    private fun toggleAddFileVisibility() = binding.apply {
        val visible = outgoingFiles.isEmpty()
        viewBackground.visibility = if (visible) View.VISIBLE else View.GONE
        textViewAddFile.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun showToast(message: String) {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStatusUpdate(message: String) {}

    override fun onProgressUpdate(fileName: String, progress: Int) {
        Log.i(TAG, "Progress update: $progress%")
        lifecycleScope.launch(Dispatchers.Main) {
            val updatedList = outgoingFilesAdapter.currentList.map {
                if (it.name == fileName) it.copy(progress = progress) else it
            }
            outgoingFilesAdapter.submitList(updatedList)
        }
    }

    override fun onTransferStatsUpdate(speedBytesPerSec: Double, remainingSec: Double) {}
    override fun onComplete() {
        lifecycleScope.launch(Dispatchers.Main) {
            val index = outgoingFiles.indexOfFirst { it.progress < 100 }
            if (index != -1) {
                outgoingFiles[index].progress = 100
                outgoingFilesAdapter.notifyItemChanged(index)
            }
        }
    }

    override fun onError(message: String) {}
}