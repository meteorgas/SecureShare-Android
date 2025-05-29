package com.mazeppa.secureshare.ui.send

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mazeppa.secureshare.R
import com.mazeppa.secureshare.data.OutgoingFile
import com.mazeppa.secureshare.data.client_server.FileUploader.getFileName
import com.mazeppa.secureshare.data.lan.invitation.InvitationSender
import com.mazeppa.secureshare.data.lan.model.DeviceInfo
import com.mazeppa.secureshare.data.lan.peer_discovery.PeerDiscovery
import com.mazeppa.secureshare.data.lan.sender.FileSender
import com.mazeppa.secureshare.data.p2p.SenderSession
import com.mazeppa.secureshare.data.p2p.WebSocketSignalingClient
import com.mazeppa.secureshare.databinding.FragmentSendBinding
import com.mazeppa.secureshare.util.FileManager
import com.mazeppa.secureshare.util.FileManager.formatSize
import com.mazeppa.secureshare.util.FileManager.getFileSize
import com.mazeppa.secureshare.util.constant.Constants
import com.mazeppa.secureshare.util.extension.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException
import org.json.JSONObject

class SendFragment : Fragment(), FileSender.FileSenderListener {

    companion object {
        private const val TAG = "SendFragment"
    }

    private var localPeerId: String? = null
    private var currentPin: String? = null

    private var signalingClient: WebSocketSignalingClient? = null
    private var senderSession: SenderSession? = null

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
            else showToast("No files selected to send")
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
                hint = "IP address"
                inputType = InputType.TYPE_CLASS_PHONE
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(40, 30, 40, 30)
                background =
                    ContextCompat.getDrawable(requireContext(), R.drawable.bg_edittext_material)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val container = FrameLayout(requireContext()).apply {
                val margin = resources.getDimensionPixelSize(R.dimen.dialog_margin_horizontal)
                setPadding(margin, 0, margin, 0)
                addView(editText)
            }

            MaterialAlertDialogBuilder(requireContext())
                .setBackground(
                    AppCompatResources.getDrawable(
                        requireContext(),
                        R.drawable.dialog_white_rounded
                    )
                )
                .setTitle("Manual Target")
                .setMessage("Enter the receiver's IP address")
                .setView(container)
                .setPositiveButton("Send") { _, _ ->
                    val ip = editText.text.toString().trim()
                    if (ip.isNotEmpty()) {
                        if (outgoingFiles.isNotEmpty()) {
                            sendInvitation(ip)
                        } else {
                            showToast("No files selected")
                        }
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Please enter a valid IP",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        buttonRemoteConnection.setOnClickListener {
            generatePinAndShow()
        }

        buttonSend.setOnClickListener {
            startWebRtcFileTransfer()
        }
    }

    private fun generatePinAndShow() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val body = "{}".toRequestBody("application/json".toMediaType())
                val req = Request.Builder()
                    .url("${Constants.HTTP_BASE_URL_PROD}/generate-pin")
                    .post(body)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("Unexpected code $resp")
                    val json = JSONObject(resp.body!!.string())
                    currentPin = json.getString("pin")
                    localPeerId = json.getString("peerId")
                    Log.i(TAG, "Generated PIN: $currentPin, Peer ID: $localPeerId")
                }

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    MaterialAlertDialogBuilder(requireContext())
                        .setBackground(
                            AppCompatResources.getDrawable(
                                requireContext(),
                                R.drawable.dialog_white_rounded
                            )
                        )
                        .setTitle("Your PIN")
                        .setMessage("Share this PIN with your friend:\n\n$currentPin")
                        .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    showToast("Failed to generate PIN: ${e.message}")
                }
            }
        }
    }

    private fun startWebRtcFileTransfer() {
        val pin = currentPin
        val peerId = localPeerId
        val uri = outgoingFiles.firstOrNull()?.uri

        if (pin == null || peerId == null) {
            showToast("Please generate a PIN first")
            return
        }
        if (uri == null) {
            showToast("Please pick a file first")
            return
        }

        val remotePeerId = pin

        signalingClient = WebSocketSignalingClient(
            serverUrl = Constants.WS_BASE_URL_PROD,
            peerId = peerId,
            onOffer = { _, _ -> },
            onAnswer = { answer, from ->
                senderSession?.onRemoteAnswer(answer)
            },
            onIceCandidate = { candidate, from ->
                senderSession?.onRemoteIceCandidate(candidate)
            },
            onOpen = {
                senderSession?.start()
            }
        )

        senderSession = SenderSession(
            requireContext(),
            signalingClient!!,
            remotePeerId = remotePeerId,
            fileUri = uri,
            onProgress = { pct ->
                binding.progressBar.progress = pct
            },
            onComplete = {
                showToast("Transfer complete!")
            },
            onError = { ex ->
                showToast("Transfer error: ${ex.message}")
            }
        )

        signalingClient?.connect()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        senderSession?.close()
        signalingClient?.close()
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
            showToast("IP address or files missing")
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
                showToast("Invite accepted. Sending files...")
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
                showToast("Invite rejected by receiver")
            },
            onError = { message ->
                dismissWaitingDialog()
                showToast(message)
            }
        )
    }

    private fun showWaitingDialog() {
        waitingDialog = MaterialAlertDialogBuilder(requireContext())
            .setBackground(
                AppCompatResources.getDrawable(
                    requireContext(),
                    R.drawable.dialog_white_rounded
                )
            )
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