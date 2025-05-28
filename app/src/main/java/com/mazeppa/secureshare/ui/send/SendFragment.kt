package com.mazeppa.secureshare.ui.send

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mazeppa.secureshare.data.client_server.FileUploader.getFileName
import com.mazeppa.secureshare.data.lan.invitation.InvitationSender
import com.mazeppa.secureshare.data.lan.model.DeviceInfo
import com.mazeppa.secureshare.data.lan.peer_discovery.PeerDiscovery
import com.mazeppa.secureshare.data.lan.sender.FileSender
import com.mazeppa.secureshare.databinding.FragmentSendBinding
import com.mazeppa.secureshare.util.FileManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SendFragment : Fragment(), FileSender.FileSenderListener {

    companion object {
        private const val TAG = "SendFragment"
    }

    private lateinit var fileSender: FileSender
    private lateinit var waitingDialog: AlertDialog
    private lateinit var binding: FragmentSendBinding
    private val selectedFileUris = mutableListOf<Uri>()

    private var onRemoveFile: ((Uri) -> Unit)? = null
    private var onSendFilesClicked: ((String) -> Unit)? = null

    private val selectedFilesAdapter by lazy {
        FileListAdapter { uri ->
            selectedFileUris.remove(uri)
            refreshFileList()
        }
    }

    private val devicesAdapter by lazy {
        DeviceListAdapter { ip ->
            if (selectedFileUris.isNotEmpty()) sendInvitation(ip)
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
        binding.recyclerViewFiles.adapter = selectedFilesAdapter
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
    private fun setListeners() = binding.apply {
        val filePickerLauncher =
            registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                selectedFileUris.clear()
                selectedFileUris.addAll(uris)
                refreshFileList()
            }

        listOf(viewBackground, textViewAddFile).forEach {
            it.setOnClickListener { filePickerLauncher.launch(arrayOf("*/*")) }
        }

        buttonRefreshDevices.setOnClickListener { discoverDevices() }
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
        selectedFileUris.remove(uri)
        refreshFileList()
    }

    private fun refreshFileList() {
        checkAddFileButtonVisibility()
        val updatedList = FileManager.mapUrisToFiles(requireContext(), selectedFileUris)
        selectedFilesAdapter.submitList(updatedList)
    }

    private fun sendInvitation(ipAddress: String) {
        if (ipAddress.isBlank() || selectedFileUris.isEmpty()) {
            Toast.makeText(requireContext(), "IP address or files missing", Toast.LENGTH_SHORT)
                .show()
            return
        }

        val fileName = selectedFileUris.firstOrNull()?.let {
            getFileName(requireContext(), it)
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
                    selectedFileUris.forEach { uri ->
                        Log.i(
                            TAG,
                            "Sending file: ${getFileName(requireContext(), uri)} to $ipAddress"
                        )
                        fileSender.sendFile(uri, ipAddress, 5051, this@SendFragment)
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
        val visible = selectedFileUris.isEmpty()
        viewBackground.visibility = if (visible) View.VISIBLE else View.GONE
        textViewAddFile.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun showToast(message: String) {
        requireActivity().runOnUiThread {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStatusUpdate(message: String) {}
    override fun onProgressUpdate(progress: Int) {}
    override fun onTransferStatsUpdate(speedBytesPerSec: Double, remainingSec: Double) {}
    override fun onComplete() {}
    override fun onError(message: String) {}
}