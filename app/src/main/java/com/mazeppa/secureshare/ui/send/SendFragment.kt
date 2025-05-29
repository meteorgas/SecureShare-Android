package com.mazeppa.secureshare.ui.send

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
import com.mazeppa.secureshare.data.p2p.PinPairingService
import com.mazeppa.secureshare.data.p2p.SenderSession
import com.mazeppa.secureshare.data.p2p.WebSocketSignalingClient
import com.mazeppa.secureshare.databinding.FragmentSendBinding
import com.mazeppa.secureshare.util.FileManager
import com.mazeppa.secureshare.util.FileManager.formatSize
import com.mazeppa.secureshare.util.FileManager.getFileSize
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
import java.util.UUID

class SendFragment : Fragment(), FileSender.FileSenderListener {

    companion object {
        private const val TAG = "SendFragment"
    }

    // Replace with your real server URL
    private val httpBaseUrl = "http://192.168.231.9:5151"
    private val wsUrl = "ws://192.168.231.9:5151"

    // These will be set once we have a PIN
    private var localPeerId: String? = null
    private var currentPin: String? = null

    // Our WebRTC helpers
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
                            Toast.makeText(
                                requireContext(),
                                "No files selected",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(requireContext(), "Invalid IP address", Toast.LENGTH_SHORT)
                            .show()
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
                    .url("$httpBaseUrl/generate-pin")
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
//                    binding.textViewPin.text = "PIN: $currentPin"
                    Toast.makeText(requireContext(),
                        "Share this PIN with your friend to receive the connection",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(),
                        "Failed to generate PIN: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun startWebRtcFileTransfer() {
        val pin = currentPin
        val peerId = localPeerId
        val uri = outgoingFiles.firstOrNull()?.uri

        if (pin == null || peerId == null) {
            Toast.makeText(requireContext(), "Please generate a PIN first", Toast.LENGTH_SHORT).show()
            return
        }
        if (uri == null) {
            Toast.makeText(requireContext(), "Please pick a file first", Toast.LENGTH_SHORT).show()
            return
        }

        // 4) Instantiate the WebSocket client
        signalingClient = WebSocketSignalingClient(
            wsUrl,
            peerId,
            onOffer = { _, _ -> /* not used in sender */ },
            onAnswer = { answer, from ->
                senderSession?.onRemoteAnswer(answer)
            },
            onIceCandidate = { candidate, from ->
                senderSession?.onRemoteIceCandidate(candidate)
            }
        ).also { it.connect() }

        // 5) We still need the RECEIVER’s peerId to tell our sender who to talk to.
        //    In this simple flow, we’ll just *reuse* the same PIN as the “to” field.
        //    (On the receiver side, we’ll look up this same PIN and use it as remotePeerId.)
        //
        //    If you’d rather exchange a second UUID for the receiver, you can add
        //    an EditText to gather it here and pass that in instead of `pin`.
        val remotePeerId = pin

        // 6) Create and start the SenderSession
        senderSession = SenderSession(
            requireContext(),
            signalingClient!!,
            localPeerId = peerId,
            remotePeerId = remotePeerId,
            fileUri = uri,
            onProgress = { pct ->
                binding.progressBar.progress = pct
            },
            onComplete = {
                Toast.makeText(requireContext(), "Transfer complete!", Toast.LENGTH_LONG).show()
            },
            onError = { ex ->
                Toast.makeText(requireContext(),
                    "Transfer error: ${ex.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        ).also { it.start() }
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