package com.mazeppa.secureshare.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mazeppa.secureshare.data.lan.PeerDiscovery
import com.mazeppa.secureshare.data.client_server.FileDownloader
import com.mazeppa.secureshare.data.client_server.SharedFile
import com.mazeppa.secureshare.data.lan.FileReceiver
import com.mazeppa.secureshare.data.lan.InvitationServer
import com.mazeppa.secureshare.databinding.FragmentReceiveBinding
import com.mazeppa.secureshare.databinding.ListReceivedItemBinding
import com.mazeppa.secureshare.util.generic_recycler_view.RecyclerListAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.lang.reflect.Method

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
//                    textViewFileSize.text = selectedFile.size
                    buttonDownloadFile.setOnClickListener {
                        onDownloadFile?.invoke(sharedFile.url, sharedFile.name)
                    }
                }
            }
        )
    }

    private var invitationServer: InvitationServer? = null

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

        if (invitationServer == null) {
            invitationServer = InvitationServer(requireContext())
            try {
                invitationServer?.start()
                Log.d("ReceiveFragment", "InvitationServer started.")
            } catch (e: IOException) {
                Log.e("ReceiveFragment", "Failed to start server: ${e.message}", e)
            }
        } else {
            Log.d("ReceiveFragment", "InvitationServer already running.")
        }

        binding.recyclerView.adapter = adapter
        onDownloadFile = { url, name ->
            Log.i(TAG, "Downloading file from URL: $url")
            lifecycleScope.launch {
                FileDownloader.downloadFile(requireContext(), url, name) { success, message ->
                    Log.i(TAG, "Download result: $success, message: $message")
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.i(TAG, "onDestroyView called, stopping file receiver and peer discovery.")
        PeerDiscovery.stopPeerDiscoveryReceiver()
        invitationServer?.stop()
        invitationServer = null
        Log.d("ReceiveFragment", "InvitationServer stopped.")
    }

    @SuppressLint("SetTextI18n")
    private fun setListeners() {
        binding.apply {
            buttonStart.setOnClickListener {
                lifecycleScope.launch {
//                    FileFetcher.fetchFileList(BASE_URL) { files, error ->
//                        requireActivity().runOnUiThread {
//                            if (files != null) {
//                                // Pass to your RecyclerView adapter
//                                Log.i(TAG, "Files fetched: ${files}")
//                                adapter.submitList(files)
//                            } else {
//                                Toast.makeText(
//                                    requireContext(),
//                                    error ?: "Unknown error",
//                                    Toast.LENGTH_SHORT
//                                ).show()
//                            }
//                        }
//                    }
                    fileReceiver.start(this@ReceiveFragment)
                }
            }
        }
    }

    override fun onStatusUpdate(message: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                binding.textViewStatusText.text = message
            }
        }
    }

    override fun onFileReceived(path: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                binding.textViewStatusText.text = "File received: $path"
            }
        }
    }

    override fun onError(message: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.Main) {
                binding.textViewStatusText.text = "Error: $message"
            }
        }
    }
}