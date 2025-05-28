package com.mazeppa.secureshare.ui.send

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.mazeppa.secureshare.R
import com.mazeppa.secureshare.data.OutgoingFile
import com.mazeppa.secureshare.databinding.ListItemOutgoingFileBinding
import com.mazeppa.secureshare.util.generic_recycler_view.RecyclerListAdapter

class FileListAdapter(
    private val onRemoveClicked: (OutgoingFile) -> Unit
) : RecyclerListAdapter<ListItemOutgoingFileBinding, OutgoingFile>(
    onInflate = ListItemOutgoingFileBinding::inflate,
    onBind = { binding, outgoingFile, _ ->
        binding.apply {
            val mime = binding.root.context.contentResolver.getType(outgoingFile.uri) ?: ""
            if (mime.startsWith("image/")) {
                try {
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(
                            binding.root.context.contentResolver,
                            outgoingFile.uri
                        )
                        ImageDecoder.decodeBitmap(source)
                    } else {
                        BitmapFactory.decodeStream(
                            binding.root.context.contentResolver.openInputStream(outgoingFile.uri)
                        )
                    }
                    imageViewFileIcon.setImageBitmap(bitmap)
                } catch (_: Exception) {
                    imageViewFileIcon.setImageResource(R.drawable.ic_file)
                }
            } else {
                imageViewFileIcon.setImageResource(R.drawable.ic_file)
            }

            textViewFileName.text = outgoingFile.name
            textViewFileSize.text = outgoingFile.size
            progressBar.progress = outgoingFile.progress
            buttonRemoveFile.setOnClickListener {
                onRemoveClicked(outgoingFile)
            }
        }
    }
)