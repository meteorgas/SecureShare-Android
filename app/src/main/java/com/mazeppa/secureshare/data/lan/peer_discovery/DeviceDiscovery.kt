package com.mazeppa.secureshare.data.lan.peer_discovery

import android.content.Context
import android.os.Build
import com.mazeppa.secureshare.data.lan.model.DeviceInfo

object DeviceDiscovery {
    fun discover(context: Context, onFound: (DeviceInfo) -> Unit) {
        val seen = mutableSetOf<String>()
        PeerDiscovery.discoverPeers { name, ip ->
            if (!name.isNullOrBlank() && ip.isNotBlank() && name != Build.MODEL && seen.add(ip)) {
                onFound(DeviceInfo(name, ip))
            }
        }
    }
}