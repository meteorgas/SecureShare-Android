package com.mazeppa.secureshare.ui.receive

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
import com.mazeppa.secureshare.util.constant.Constants
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

    private var localPeerId: String? = null
    private var remotePeerId: String? = null

    private var signalingClient: WebSocketSignalingClient? = null
    private var receiverSession: ReceiverSession? = null

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

                    MaterialAlertDialogBuilder(requireContext())
                        .setBackground(
                            AppCompatResources.getDrawable(
                                requireContext(),
                                R.drawable.dialog_white_rounded
                            )
                        )
                        .setTitle("Device IP Addresses")
                        .setMessage("Local IP:      $localIp\nPublic IP:     $publicIp")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }

            buttonRemoteConnection.setOnClickListener {
                val editText = EditText(requireContext()).apply {
                    hint = "PIN"
                    inputType = InputType.TYPE_CLASS_NUMBER
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
                    .setTitle("Enter PIN")
                    .setMessage("Enter 6-digit PIN provided by the sender to establish a connection.")
                    .setView(container)
                    .setPositiveButton("OK") { _, _ ->
                        val pin = editText.text.toString().trim()
                        if (pin.length != 6 || !pin.all { it.isDigit() }) {
                            showToast("Please enter a valid 6-digit PIN")
                        } else {
                            lookupPin(pin)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            binding.buttonPickFile.setOnClickListener {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_TITLE, "received_file")
                }
                startActivityForResult(intent, REQUEST_CODE_CREATE_FILE)
            }

            binding.buttonReceiveFile.setOnClickListener {
                startWebRtcReceive()
            }
        }
    }

    private fun lookupPin(pin: String) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val body = JSONObject().put("pin", pin)
                    .toString()
                    .toRequestBody("application/json".toMediaType())

                val req = Request.Builder()
                    .url("${Constants.HTTP_BASE_URL_PROD}/lookup-pin")
                    .post(body)
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("Code ${resp.code}")
                    val json = JSONObject(resp.body!!.string())
                    remotePeerId = json.getString("peerId")
                }

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

        signalingClient = WebSocketSignalingClient(
            serverUrl = Constants.WS_BASE_URL_PROD,
            peerId = lp,
            onOffer = { offer, from -> receiverSession?.onRemoteOffer(offer) },
            onAnswer = { _, _ -> },
            onIceCandidate = { candidate, _ -> receiverSession?.onRemoteIceCandidate(candidate) },
            onOpen = { receiverSession?.start() }
        )

        receiverSession = ReceiverSession(
            requireContext(),
            signalingClient!!,
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