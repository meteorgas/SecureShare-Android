package com.mazeppa.secureshare.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mazeppa.secureshare.data.client_server.SharedFile
import com.mazeppa.secureshare.data.lan.FileDownloadHandler
import com.mazeppa.secureshare.data.lan.FileReceiver
import com.mazeppa.secureshare.data.lan.InvitationServer
import com.mazeppa.secureshare.data.lan.PeerDiscovery
import com.mazeppa.secureshare.databinding.FragmentReceiveBinding
import com.mazeppa.secureshare.databinding.ListReceivedItemBinding
import com.mazeppa.secureshare.util.generic_recycler_view.RecyclerListAdapter
import kotlinx.coroutines.launch

class ReceiveFragment : Fragment(), FileReceiver.FileReceiverListener {

    companion object {
        private const val TAG = "ReceiveFragment"
    }

    private lateinit var fileReceiver: FileReceiver
    private lateinit var binding: FragmentReceiveBinding
    private var onDownloadFile: ((String, String) -> Unit)? = null
    private val adapter by lazy {
        RecyclerListAdapter<ListReceivedItemBinding, SharedFile>(
            onInflate = ListReceivedItemBinding::inflate,
            onBind = { binding, sharedFile, pos ->
                binding.apply {
                    textViewFileName.text = sharedFile.name
                    buttonDownloadFile.setOnClickListener {
                        onDownloadFile?.invoke(sharedFile.url, sharedFile.name)
                    }
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

        val server = InvitationServer.ensureRunning()
        server.setContextProvider { requireContext() }

        binding.recyclerView.adapter = adapter

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
        InvitationServer.stopServer()
    }

    @SuppressLint("SetTextI18n")
    private fun setListeners() {
        binding.apply {
        }
    }

    override fun onStatusUpdate(message: String) {
        updateStatus(message)
    }

    override fun onFileReceived(path: String) {
        updateStatus("File received: $path")
    }

    override fun onError(message: String) {
        updateStatus("Error: $message")
    }

    private fun updateStatus(text: String) {
        lifecycleScope.launch {
            binding.textViewStatusText.text = text
        }
    }
}