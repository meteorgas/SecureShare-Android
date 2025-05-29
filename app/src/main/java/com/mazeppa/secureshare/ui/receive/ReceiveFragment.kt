package com.mazeppa.secureshare.ui.receive

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mazeppa.secureshare.R
import com.mazeppa.secureshare.data.lan.invitation.InvitationServer
import com.mazeppa.secureshare.data.lan.model.IncomingFile
import com.mazeppa.secureshare.data.lan.peer_discovery.PeerDiscovery
import com.mazeppa.secureshare.data.lan.receiver.FileDownloadHandler
import com.mazeppa.secureshare.data.lan.receiver.FileReceiver
import com.mazeppa.secureshare.data.p2p.PinPairingService
import com.mazeppa.secureshare.data.p2p.PinPairingService.acceptInvite
import com.mazeppa.secureshare.data.p2p.PinPairingService.lookupPin
import com.mazeppa.secureshare.data.p2p.ReceiverSession
import com.mazeppa.secureshare.data.p2p.SdpObserverAdapter
import com.mazeppa.secureshare.data.p2p.WebRtcManager
import com.mazeppa.secureshare.data.p2p.WebSocketSignalingClient
import com.mazeppa.secureshare.databinding.FragmentReceiveBinding
import com.mazeppa.secureshare.databinding.ListItemIncomingFileBinding
import com.mazeppa.secureshare.util.FileManager.formatSize
import com.mazeppa.secureshare.util.Utility.getLocalIpAddress
import com.mazeppa.secureshare.util.Utility.getPublicIpAddress
import com.mazeppa.secureshare.util.constant.BASE_URL
import com.mazeppa.secureshare.util.extension.showToast
import com.mazeppa.secureshare.util.generic_recycler_view.RecyclerListAdapter
import kotlinx.coroutines.launch
import org.webrtc.SessionDescription
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.UUID

class ReceiveFragment : Fragment(), FileReceiver.FileReceiverListener {

    companion object {
        private const val TAG = "ReceiveFragment"
    }

    private lateinit var signalingClient: WebSocketSignalingClient
    private var receiverSession: ReceiverSession? = null
    private val selfId = UUID.randomUUID().toString()
    private var outputStream: FileOutputStream? = null
    private lateinit var receivedFile: File


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
        receiverSession?.dispose()
        outputStream?.close()
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
                PinPairingService.generatePin(selfId) { success, pin, peerId ->
                    requireActivity().runOnUiThread {
                        if (!success || pin.isNullOrEmpty() || peerId.isNullOrEmpty()) {
                            Toast.makeText(requireContext(), "Failed to generate PIN", Toast.LENGTH_LONG).show()
                            return@runOnUiThread
                        }
                        // Show the PIN to the user
                        AlertDialog.Builder(requireContext())
                            .setTitle("Share this PIN with sender")
                            .setMessage("PIN: $pin")
                            .setPositiveButton("OK", null)
                            .show()

                        // Start P2P listener
                        startP2PListener(peerId)
                    }
                }

//                val editText = EditText(requireContext()).apply {
//                    hint = "Enter PIN"
//                    inputType = InputType.TYPE_CLASS_NUMBER
//                    layoutParams = ViewGroup.LayoutParams(
//                        ViewGroup.LayoutParams.MATCH_PARENT,
//                        ViewGroup.LayoutParams.WRAP_CONTENT
//                    )
//                }
//
//                AlertDialog.Builder(requireContext())
//                    .setTitle("PIN")
//                    .setMessage("Enter the sender's PIN:")
//                    .setView(editText)
//                    .setPositiveButton("Confirm") { _, _ ->
//                        val pin = editText.text.toString().trim()
//                        if (pin.isNotBlank()) {
//                            val receiverId = "${Build.MODEL}_${UUID.randomUUID()}"
//
//                            lookupPin(pin) { found, senderPeerId, error ->
//                                if (found && senderPeerId != null) {
//                                    Log.i(TAG, "Sender peerId: $senderPeerId")
//                                    startWebRtcConnection(senderPeerId, receiverId)
//
////                                    acceptInvite(pin, receiverId) { accepted, senderIdOrError ->
////                                        if (accepted && senderIdOrError != null) {
////                                            Log.i(TAG, "Invitation accepted. SenderId: $senderIdOrError")
////                                            val senderId = senderIdOrError
////                                            startWebRtcConnection(senderId, receiverId)
////                                        } else {
////                                            showToast("Accept failed: $senderIdOrError")
////                                        }
////                                    }
//
//                                } else {
//                                    showToast("PIN not found: $error")
//                                }
//                            }
//                        } else {
//                            showToast("Invalid PIN")
//                        }
//                    }
//                    .setNegativeButton("Cancel", null)
//                    .show()
            }
        }
    }

    private fun startP2PListener(peerId: String) {
        val wsUrl = "ws://192.168.231.9:5151"
        signalingClient = WebSocketSignalingClient(wsUrl, selfId)

        // Initialize ReceiverSession with callbacks
        receiverSession = ReceiverSession(
            requireContext(),
            signalingClient,
            peerId,
            onConnected = {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Connection established", Toast.LENGTH_SHORT).show()
                }
            },
            onDataReceived = { buffer: ByteBuffer ->
                // Write incoming bytes to file
                if (outputStream == null) {
                    receivedFile = File(
                        requireContext().externalCacheDir,
                        "received_file_${System.currentTimeMillis()}"
                    )
                    outputStream = FileOutputStream(receivedFile)
                }
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                outputStream?.write(bytes)

                // Optionally, update UI with progress here
            }
        )
        receiverSession?.start()

        // Listen for channel closure to finish writing
        // (Session does not currently expose a callback for closure, so we rely on user to stop)
    }

    private fun startWebRtcConnection(senderId: String, receiverId: String) {
        val signalingClient = WebSocketSignalingClient("ws://10.200.13.65:5151", receiverId)

        val peerConnection = WebRtcManager.createPeerConnection { candidate ->
            signalingClient.sendIceCandidate(senderId, candidate)
        }

        signalingClient.onAnswerReceived { sdp ->
            Log.i(TAG, "Answer received from sender")
            peerConnection.setRemoteDescription(
                object : SdpObserverAdapter() {
                    override fun onSetSuccess() {
                        Log.i(TAG, "Remote description set successfully")
                    }
                },
                SessionDescription(SessionDescription.Type.ANSWER, sdp)
            )
        }

        signalingClient.onIceCandidateReceived { candidate ->
            Log.i(TAG, "ICE candidate received")
            peerConnection.addIceCandidate(candidate)
        }

        WebRtcManager.createOffer { offer ->
            signalingClient.sendOffer(senderId, offer)
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