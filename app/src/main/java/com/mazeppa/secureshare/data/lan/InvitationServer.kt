package com.mazeppa.secureshare.data.lan

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import okio.IOException
import org.json.JSONObject
import java.util.concurrent.CountDownLatch

class InvitationServer : NanoHTTPD(5050) {

    companion object {
        private const val TAG = "InvitationServer"
        private var instance: InvitationServer? = null

        fun ensureRunning(): InvitationServer {
            if (instance == null) {
                instance = InvitationServer()
                try {
                    instance!!.start()
                    Log.d(TAG, "Server started.")
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to start server: ${e.message}", e)
                }
            } else {
                Log.d(TAG, "Already running.")
            }
            return instance!!
        }

        fun stopServer() {
            instance?.stop()
            instance = null
            Log.d(TAG, "Stopped.")
        }
    }

    private var contextProvider: (() -> Context)? = null

    fun setContextProvider(provider: () -> Context) {
        this.contextProvider = provider
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.POST && session.uri == "/invite") {
            val map = mutableMapOf<String, String>()
            session.parseBody(map)
            val json = JSONObject(map["postData"] ?: "")
            val fileName = json.getString("fileName")

            val latch = CountDownLatch(1)
            var accepted = false

            contextProvider?.invoke()?.let { context ->
                showInviteDialog(
                    context = context,
                    fileName = fileName,
                    onAccepted = {
                        accepted = true
                        latch.countDown()
                    },
                    onDeclined = {
                        accepted = false
                        latch.countDown()
                    }
                )
            }

            latch.await() // Wait until dialog is responded

            return if (accepted) {
                newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "ACCEPTED")
            } else {
                newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "DECLINED")
            }
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
    }

    private fun showInviteDialog(
        context: Context,
        fileName: String,
        onAccepted: () -> Unit,
        onDeclined: () -> Unit
    ) {
        Handler(Looper.getMainLooper()).post {
            AlertDialog.Builder(context)
                .setTitle("Incoming File")
                .setMessage("Do you want to accept file: $fileName?")
                .setPositiveButton("Accept") { _, _ -> onAccepted() }
                .setNegativeButton("Decline") { _, _ -> onDeclined() }
                .setCancelable(false)
                .show()
        }
    }
}