package eu.depau.livewearheartrate

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent

private val LOG_TAG = "DataLayerListener"

class LifecycleDataLayerListener(
    private val lifecycle: Lifecycle,
    val messageClient: MessageClient,
    private val onMessage: (message: MessageEvent) -> Unit,
) : MessageClient.OnMessageReceivedListener,
    DefaultLifecycleObserver {

    override fun onMessageReceived(message: MessageEvent) {
        try {
            onMessage(message)
        } catch (e: IllegalStateException) {
            Log.e(LOG_TAG, "Error while processing message: ${message.path}", e)
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        messageClient.removeListener(this)
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        messageClient.addListener(this)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        messageClient.removeListener(this)
        lifecycle.removeObserver(this)
    }
}

fun LifecycleOwner.messageClientListener(
    messageClient: MessageClient,
    onMessage: (message: MessageEvent) -> Unit,
): LifecycleDataLayerListener {
    val listener = LifecycleDataLayerListener(
        lifecycle, messageClient, onMessage
    )
    messageClient.addListener(listener)
    lifecycle.addObserver(listener)
    return listener
}