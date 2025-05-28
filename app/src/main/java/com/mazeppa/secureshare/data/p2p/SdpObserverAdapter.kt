package com.mazeppa.secureshare.data.p2p

import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

abstract class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(sessionDescription: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}