package com.mazeppa.secureshare.ui.send

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import com.mazeppa.secureshare.R
import com.mazeppa.secureshare.data.SelectedFile
import com.mazeppa.secureshare.databinding.ListItemFileBinding
import com.mazeppa.secureshare.util.generic_recycler_view.RecyclerListAdapter

class FileListAdapter(
    private val onRemoveClicked: (Uri) -> Unit
) : RecyclerListAdapter<ListItemFileBinding, SelectedFile>(
    onInflate = ListItemFileBinding::inflate,
    onBind = { binding, selectedFile, _ ->
        binding.apply {
            val mime = binding.root.context.contentResolver.getType(selectedFile.uri) ?: ""
            if (mime.startsWith("image/")) {
                try {
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(binding.root.context.contentResolver, selectedFile.uri)
                        ImageDecoder.decodeBitmap(source)
                    } else {
                        BitmapFactory.decodeStream(
                            binding.root.context.contentResolver.openInputStream(selectedFile.uri)
                        )
                    }
                    imageViewFileIcon.setImageBitmap(bitmap)
                } catch (_: Exception) {
                    imageViewFileIcon.setImageResource(R.drawable.ic_file)
                }
            } else {
                imageViewFileIcon.setImageResource(R.drawable.ic_file)
            }

            textViewFileName.text = selectedFile.name
            textViewFileSize.text = selectedFile.size
            buttonRemoveFile.setOnClickListener {
                onRemoveClicked(selectedFile.uri)
            }
        }
    }
)