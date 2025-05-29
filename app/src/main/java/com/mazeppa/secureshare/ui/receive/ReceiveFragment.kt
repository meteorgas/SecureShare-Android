package com.mazeppa.secureshare.ui.receive

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mazeppa.secureshare.R
import com.mazeppa.secureshare.data.lan.invitation.InvitationServer
import com.mazeppa.secureshare.data.lan.model.IncomingFile
import com.mazeppa.secureshare.data.lan.peer_discovery.PeerDiscovery
import com.mazeppa.secureshare.data.lan.receiver.FileDownloadHandler
import com.mazeppa.secureshare.data.lan.receiver.FileReceiver
import com.mazeppa.secureshare.data.p2p.ReceiverSession
import com.mazeppa.secureshare.data.p2p.WebSocketSignalingClient
import com.mazeppa.secureshare.databinding.FragmentReceiveBinding
import com.mazeppa.secureshare.databinding.ListItemIncomingFileBinding
import com.mazeppa.secureshare.util.FileManager.formatSize
import com.mazeppa.secureshare.util.Utility.getLocalIpAddress
import com.mazeppa.secureshare.util.Utility.getPublicIpAddress
import com.mazeppa.secureshare.util.constant.BASE_URL
import com.mazeppa.secureshare.util.constant.BASE_URL_2
import com.mazeppa.secureshare.util.extension.showToast
import com.mazeppa.secureshare.util.generic_recycler_view.RecyclerListAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.IOException
import org.json.JSONObject

class ReceiveFragment : Fragment(), FileReceiver.FileReceiverListener {

    companion object {
        private const val TAG = "ReceiveFragment"
        private const val REQUEST_CODE_CREATE_FILE = 2001
    }

    private val httpBaseUrl = "https://$BASE_URL"
    private val wsUrl = "wss://$BASE_URL"

    // Will be set after lookup-pin
    private var localPeerId: String? = null
    private var remotePeerId: String? = null

    private var signalingClient: WebSocketSignalingClient? = null
    private var receiverSession: ReceiverSession? = null

    // Chosen save location
    private var targetFileUri: Uri? = null

    private lateinit var fileReceiver: FileReceiver
    private lateinit var binding: FragmentReceiveBinding
    private var onDownloadFile: ((String, String) -> Unit)? = null
    private val incomingFilesAdapter by lazy {
        RecyclerListAdapter<ListItemIncomingFileBinding, IncomingFile>(
            onInflate = ListItemIncomingFileBinding::inflate,
            onBind = { binding, incomingFile, pos ->
                binding.apply {
                    imageViewFileIcon.setImageResource(R.drawable.ic_file)
                    textViewFileName.text = incomingFile.name
                    textViewFileSize.text = incomingFile.size
                    progressBar.progress = incomingFile.progress
                }
            }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentReceiveBinding.inflate(inflater, container, false)
        fileReceiver = FileReceiver()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setListeners()

        PeerDiscovery.startPeerDiscoveryReceiver()

        val server = InvitationServer.Companion.ensureRunning()
        server.setContextProvider { requireContext() }

        binding.recyclerViewFiles.adapter = incomingFilesAdapter

        onDownloadFile = FileDownloadHandler.createDownloadCallback(requireContext()) { status ->
            binding.textViewStatusText.text = status
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            fileReceiver.start(this@ReceiveFragment)
        }
    }

    override fun onPause() {
        super.onPause()
        fileReceiver.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.i(TAG, "onDestroyView called, stopping file receiver and peer discovery.")
        PeerDiscovery.stopPeerDiscoveryReceiver()
        InvitationServer.Companion.stopServer()
        receiverSession?.close()
        signalingClient?.close()
    }

    @SuppressLint("SetTextI18n")
    private fun setListeners() {
        binding.apply {
            buttonInfoIpAddress.setOnClickListener {
                lifecycleScope.launch {
                    val localIp = getLocalIpAddress()
                    val publicIp = getPublicIpAddress()

                    AlertDialog.Builder(requireContext())
                        .setTitle("Device IP Addresses")
                        .setMessage("Local IP: $localIp\nPublic IP: $publicIp")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }

            buttonRemoteConnection.setOnClickListener {
                val pin = binding.editTextPin.text.toString().trim()
                if (pin.isEmpty()) {
                    showToast("Enter the PIN from sender")
                } else {
                    lookupPin(pin)
                }
            }

            // 2) When user clicks "Pick Save Location", open the create-document picker
            binding.buttonPickFile.setOnClickListener {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_TITLE, "received_file")
                }
                startActivityForResult(intent, REQUEST_CODE_CREATE_FILE)
            }

            // 3) When user clicks "Receive File", kick off WebRTC receive flow
            binding.buttonReceiveFile.setOnClickListener {
                startWebRtcReceive()
            }
        }
    }

    /** Step 1: POST { pin } → /lookup-pin to get the sender’s peerId */
    private fun lookupPin(pin: String) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val body = JSONObject().put("pin", pin)
                    .toString()
                    .toRequestBody("application/json".toMediaType())

                val req = Request.Builder()
                    .url("$httpBaseUrl/lookup-pin")
                    .post(body)
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("Code ${resp.code}")
                    val json = JSONObject(resp.body!!.string())
                    // the server returns { peerId: "..." }
                    remotePeerId = json.getString("peerId")
                }

                // generate our own localPeerId
//                localPeerId = UUID.randomUUID().toString()
                localPeerId = pin

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    showToast("Found sender. Your ID: $localPeerId")
                    // enable the next steps
                    binding.buttonPickFile.isEnabled = true
                    binding.buttonReceiveFile.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    showToast("Lookup failed: ${e.message}")
                }
            }
        }
    }

    /** Step 3: Wire WS + WebRTC and start receiving */
    private fun startWebRtcReceive() {
        val lp = localPeerId
        val rp = remotePeerId
        val uri = targetFileUri

        if (lp == null || rp == null) {
            showToast("Please lookup the PIN first")
            return
        }
        if (uri == null) {
            showToast("Please pick a save location")
            return
        }

        // 3a) Create & connect the WS client
        signalingClient = WebSocketSignalingClient(
            serverUrl = wsUrl,
            peerId = lp,
            onOffer = { offer, from -> receiverSession?.onRemoteOffer(offer) },
            onAnswer = { _, _ -> /* not used on receiver */ },
            onIceCandidate = { candidate, _ -> receiverSession?.onRemoteIceCandidate(candidate) },
            onOpen = { receiverSession?.start() }
        )

        // 3b) Create the ReceiverSession
        receiverSession = ReceiverSession(
            requireContext(),
            signalingClient!!,
            localPeerId = lp,
            remotePeerId = rp,
            targetFileUri = uri,
            onProgress = { bytes ->
                // If you want %, you could track file size separately
                binding.progressBar.progress = (bytes % 100).toInt()
            },
            onComplete = {
                showToast("File received!")
            },
            onError = { ex ->
                showToast("Receive error: ${ex.message}")
            }
        )

        signalingClient?.connect()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CREATE_FILE && resultCode == Activity.RESULT_OK) {
            targetFileUri = data?.data
            binding.textViewFileName.text = targetFileUri?.lastPathSegment ?: "Unknown"
        }
    }

    override fun onStatusUpdate(message: String) {
        updateStatus(message)
    }

    override fun onError(message: String) {
        updateStatus("Error: $message")
    }

    override fun onFileMetadataReceived(name: String, size: Long, mimeType: String) {
        Log.i(TAG, "Incoming file: $name ($size bytes) [$mimeType]")
        lifecycleScope.launch {
            incomingFilesAdapter.submitList(
                incomingFilesAdapter.currentList + IncomingFile(name, formatSize(size), mimeType)
            )
        }
    }

    override fun onFileProgressUpdate(name: String, progress: Int) {
        Log.d(TAG, "$name progress: $progress%")
        val updatedList = incomingFilesAdapter.currentList.map {
            if (it.name == name) it.copy(progress = progress) else it
        }
        lifecycleScope.launch {
            incomingFilesAdapter.submitList(updatedList)
        }
    }

    override fun onFileReceived(path: String) {
        Log.i(TAG, "File received: $path")
    }

    private fun updateStatus(text: String) {
        lifecycleScope.launch {
            binding.textViewStatusText.text = text
        }
    }
}