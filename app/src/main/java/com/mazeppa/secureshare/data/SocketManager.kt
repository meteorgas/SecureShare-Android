package com.mazeppa.secureshare.data

//object SocketManager {
//
//    private const val TAG = "SocketManager"
//    private lateinit var socket: Socket
//
//    fun connect(userId: String) {
//        try {
//            val opts = IO.Options().apply {
//                reconnection = true
//                forceNew = true
//                transports = arrayOf("websocket") // skip polling to avoid error
//            }
//
//            val socket = IO.socket("http://192.168.57.167:5050", opts) // use correct IP!
//
//            socket.on(Socket.EVENT_CONNECT) {
//                Log.d("Socket", "Connected")
//                socket.emit("register", userId)
//            }
//
//            socket.on("peer-found") { args ->
//                val peerId = args[0] as String
//                Log.d("Socket", "Found peer: $peerId")
//            }
//
//            socket.on(Socket.EVENT_DISCONNECT) {
//                Log.d("Socket", "Disconnected")
//            }
//
//            socket.on(Socket.EVENT_CONNECT_ERROR) { args ->
//                Log.e("Socket", "❌ Connection Error: ${args.joinToString()}")
//            }
//
//            socket.connect()
//
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }
//
//    fun discoverPeer(peerId: String) {
//        if (!::socket.isInitialized) {
//            Log.e("SocketManager", "Socket not initialized. Call connect() first.")
//            return
//        }
//        socket.emit("discover", peerId)
//    }
//
//    fun disconnect() {
//        if (::socket.isInitialized) {
//            socket.disconnect()
//        }
//    }
//}