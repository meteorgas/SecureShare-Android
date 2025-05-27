package com.mazeppa.secureshare.data.lan

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import fi.iki.elonen.NanoHTTPD
import okhttp3.Response
import org.json.JSONObject
import java.lang.reflect.Method

class InvitationServer(private val context: Context) : NanoHTTPD(5050) {

    override fun serve(session: IHTTPSession): Response {
        return if (session.method == Method.POST && session.uri == "/invite") {
            val map = mutableMapOf<String, String>()
            session.parseBody(map)
            val json = JSONObject(map["postData"])
            val fileName = json.getString("fileName")

            showInviteDialog(fileName)

            newFixedLengthResponse(
                Response.Status.OK,
                MIME_PLAINTEXT,
                "ACCEPTED"
            )
        } else {
            newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "Not Found"
            )
        }
    }

    private fun showInviteDialog(fileName: String) {
        Handler(Looper.getMainLooper()).post {
            AlertDialog.Builder(context)
                .setTitle("Incoming File")
                .setMessage("Do you want to accept file: $fileName?")
                .setPositiveButton("Accept") { _, _ ->
                    // Handle accept
                }
                .setNegativeButton("Decline", null)
                .show()
        }
    }
}