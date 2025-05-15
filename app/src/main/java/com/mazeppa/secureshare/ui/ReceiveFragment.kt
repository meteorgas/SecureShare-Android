package com.mazeppa.secureshare.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.mazeppa.secureshare.data.FileReceiver
import com.mazeppa.secureshare.databinding.FragmentReceiveBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReceiveFragment : Fragment(), FileReceiver.FileReceiverListener {

    private lateinit var fileReceiver: FileReceiver
    private lateinit var binding: FragmentReceiveBinding

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
    }

    @SuppressLint("SetTextI18n")
    private fun setListeners() {
        binding.apply {
            buttonStart.setOnClickListener {
                lifecycleScope.launch {
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