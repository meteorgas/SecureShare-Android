package com.mazeppa.secureshare.ui.send

import com.mazeppa.secureshare.data.lan.model.DeviceInfo
import com.mazeppa.secureshare.databinding.ListItemDeviceBinding
import com.mazeppa.secureshare.util.generic_recycler_view.RecyclerListAdapter

class DeviceListAdapter(
    private val onSendClicked: (String) -> Unit
) : RecyclerListAdapter<ListItemDeviceBinding, DeviceInfo>(
    onInflate = ListItemDeviceBinding::inflate,
    onBind = { binding, device, _ ->
        binding.apply {
            textViewDeviceName.text = device.name
            textViewIpAddress.text = device.ipAddress
            buttonSendFiles.setOnClickListener {
                onSendClicked(device.ipAddress)
            }
        }
    }
)