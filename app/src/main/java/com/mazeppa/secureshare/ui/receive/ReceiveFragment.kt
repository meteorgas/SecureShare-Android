package com.mazeppa.secureshare.ui.receive

import android.annotation.SuppressLint
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
import com.mazeppa.secureshare.databinding.FragmentReceiveBinding
import com.mazeppa.secureshare.databinding.ListItemIncomingFileBinding
import com.mazeppa.secureshare.util.FileManager.formatSize
import com.mazeppa.secureshare.util.Utility.getLocalIpAddress
import com.mazeppa.secureshare.util.Utility.getPublicIpAddress
import com.mazeppa.secureshare.util.generic_recycler_view.RecyclerListAdapter
import kotlinx.coroutines.launch

class ReceiveFragment : Fragment(), FileReceiver.FileReceiverListener {

    companion object {
        private const val TAG = "ReceiveFragment"
    }

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