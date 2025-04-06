package eu.depau.livewearheartrate.utils

import android.content.Context
import android.os.PowerManager

private const val WAKELOCK_TIMEOUT = 60 * 1000L

class WakeLockHelper(context: Context, levelAndFlags: Int = PowerManager.PARTIAL_WAKE_LOCK) {
    private val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wakeLock = pm.newWakeLock(
        levelAndFlags,
        "LiveWear:WakeLock"
    ).apply {
        setReferenceCounted(false)
    }

    fun bump() {
        wakeLock.acquire(WAKELOCK_TIMEOUT)
    }

    fun release() {
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }
}
