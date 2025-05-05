package com.mazeppa.filebeam

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Environment
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.mazeppa.filebeam.databinding.ActivityMainBinding
import java.io.DataInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.ServerSocket

class MainActivity : AppCompatActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}
    private val startActivityForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {}

    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val PORT: Int = 5050
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setListeners()
    }

    private fun setListeners() {
        binding.apply {
            buttonRequestPermissions.setOnClickListener {
                PermissionHandler.requestPermissions(
                    permissionLauncher = permissionLauncher,
                    startActivityForResult = startActivityForResult,
                    packageName = packageName,
                )
            }

            buttonStart.setOnClickListener(View.OnClickListener { v: View? -> startReceiver() })
        }
    }

    @SuppressLint("SetTextI18n")
    private fun startReceiver() {
        Thread {
            try {
                ServerSocket(PORT).use { serverSocket ->
                    runOnUiThread {
                        binding.textViewStatusText.text = "Waiting for sender on port $PORT..."
                    }

                    val socket = serverSocket.accept()
                    val inputStream = DataInputStream(socket.getInputStream())

                    // 1. Read file name and file size
                    val fileName = inputStream.readUTF()
                    val fileSize = inputStream.readLong()

                    // 2. Prepare file output location
                    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val outputFile = File(downloads, fileName)
                    val outputStream = FileOutputStream(outputFile)

                    // 3. Receive and write file
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (totalBytesRead < fileSize) {
                        bytesRead = inputStream.read(buffer)
                        if (bytesRead == -1) break
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                    }

                    outputStream.close()
                    inputStream.close()
                    socket.close()

                    runOnUiThread {
                        binding.textViewStatusText.text = "File received: ${outputFile.absolutePath}"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    binding.textViewStatusText.text = "Error: ${e.message}"
                }
            }
        }.start()
    }
}