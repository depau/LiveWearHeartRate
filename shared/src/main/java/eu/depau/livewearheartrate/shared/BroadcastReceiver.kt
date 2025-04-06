package eu.depau.livewearheartrate.shared

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

inline fun broadcastReceiver(crossinline onReceive: (context: Context, intent: Intent) -> Unit) =
    object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            onReceive(context, intent)
        }
    }

inline fun broadcastReceiver(crossinline onReceive: (intent: Intent) -> Unit) =
    object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            onReceive(intent)
        }
    }