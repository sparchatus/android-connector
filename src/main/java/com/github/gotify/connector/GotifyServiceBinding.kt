package com.github.gotify.connector

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import androidx.core.os.bundleOf

/**
 * This class is used to register to gotify
 */

private const val gotify_package = "com.github.gotify"
private const val messenger_service = "$gotify_package.service.GotifyRegisterService"

data class Registration(val senderUid: Int, val url: String, val token: String)

interface GotifyBindingHandler {
    fun onConnected(service: GotifyServiceBinding)
    fun onRegistered(service: GotifyServiceBinding, registration: Registration)
    fun onUnregistered(service: GotifyServiceBinding)
}

class GotifyServiceBinding(var context: Context, var bindingHandler: GotifyBindingHandler){
    /** Messenger for communicating with service.  */
    private var messengerToGotify: Messenger? = null
    /** To known if it if bound to the service */
    private var isBound = false
    private var waitingForInfo = false

    /**
     * Handler of incoming messages from service.
     */
    private class ReplyHandler(var service: GotifyServiceBinding) : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                TYPE_CLIENT_STARTED -> {
                    logi("Received MSG_START from service")
                    service.bindingHandler.onConnected(service)
                }
                TYPE_REGISTERED_CLIENT -> {
                    if(service.waitingForInfo) {
                        service.waitingForInfo = false
                        val url = msg.data?.getString("url").toString()
                        val token = msg.data?.getString("token").toString()
                        logi("new url: $url")
                        logi("new token: $token")
                        service.bindingHandler.onRegistered(service,
                                Registration(msg.sendingUid,url,token))
                    }
                }
                TYPE_UNREGISTERED_CLIENT -> {
                    logi("App is unregistered")
                    service.bindingHandler.onUnregistered(service)
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    private val replyMessenger = Messenger(ReplyHandler(this))

    /**
     * Class for interacting with the main interface of the service.
     */
    private val connectionToGotify: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            className: ComponentName,
            service: IBinder
        ) {
            messengerToGotify = Messenger(service)
            try {
                // Tell the service we have started
                val msg = Message.obtain(null,
                    TYPE_CLIENT_STARTED, 0, 0)
                msg.replyTo = replyMessenger
                messengerToGotify!!.send(msg)
            } catch (e: RemoteException) {
                // There is nothing special we need to do if the service
                // has crashed.
                logw("The remote service has crashed when the service has been connected")
            }
            isBound = true
            logi("Remote service connected")
        }

        override fun onServiceDisconnected(className: ComponentName) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            messengerToGotify = null
            unbindRemoteService()
            logi("Remote service disconnected")
        }
    }

    fun bindRemoteService() {
        val intent = Intent()
        intent.component = ComponentName(gotify_package , messenger_service)
        context.bindService(intent, connectionToGotify, Context.BIND_AUTO_CREATE)
    }

    fun unbindRemoteService() {
        if (isBound) {
            // Detach our existing connection.
            context.unbindService(connectionToGotify)
            isBound = false
        }
    }

    fun registerApp(serviceName: String){
        if(!isBound){
            logw("Trying to register app without being bound to gotify registration service")
            return
        }
        try {
            val msg = Message.obtain(null,
                TYPE_REGISTER_CLIENT, 0, 0)
            msg.replyTo = replyMessenger
            msg.data = bundleOf("package" to context.packageName, "service" to serviceName)
            waitingForInfo = true
            messengerToGotify!!.send(msg)
        } catch (e: RemoteException) {
            waitingForInfo = false
            // There is nothing special we need to do if the service
            // has crashed.
            logw("The remote service has crashed during registration")
        }
    }

    fun unregisterApp(){
        if(!isBound){
            logw("Trying to unregister app without being bound to gotify registration service")
            return
        }
        try {
            val msg = Message.obtain(null,
                TYPE_UNREGISTER_CLIENT, 0, 0)
            msg.replyTo = replyMessenger
            msg.data = bundleOf("package" to context.packageName)
            messengerToGotify!!.send(msg)
        } catch (e: RemoteException) {
            // There is nothing special we need to do if the service
            // has crashed.
            logw("The remote service has crashed during unregistration")
        }
    }
}