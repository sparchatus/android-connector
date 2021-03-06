package com.github.gotify.connector

import android.os.*
import com.google.gson.Gson

/**
 * This handler is used to receive notifications
 * from gotify (once registered)
 */

open class GotifyServiceHandler(looper: Looper) : Handler(looper) {

    override fun handleMessage(msg: Message) {
        if (!this.isTrusted(msg.sendingUid)) {
            logw("Message received from untrusted ID (${msg.sendingUid})")
            return;
        }
        when (msg.what) {
            TYPE_CHANGED_URL -> onUrlChange(msg.data.getString("changedUrl").orEmpty())
            TYPE_MESSAGE -> onMessage(Gson().fromJson(
                    msg.data?.getString("json"),
                    GotifyMessage::class.java))
            else -> super.handleMessage(msg)
        }
    }

    open fun onMessage(message: GotifyMessage){}

    open fun onUrlChange(url: String) {}

    open fun isTrusted(uid: Int): Boolean {
        /** You  need to override this function
         * to control gotify uid
         */
        return false
    }
}