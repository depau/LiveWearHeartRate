package eu.depau.livewearheartrate.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppState(
    val hasAllPermissions: Boolean = false,
    val isForegroundServiceRunning: Boolean = false,
    val heartRate: Double = 0.0,
    val batteryLevel: Int = -1,
)

class AppViewModel : ViewModel() {
    private val _state = MutableStateFlow(AppState())
    val state = _state.asStateFlow()

    fun setHasAllPermissions(hasAllPermissions: Boolean) {
        _state.value = _state.value.copy(hasAllPermissions = hasAllPermissions)
    }

    fun setForegroundServiceRunning(isForegroundServiceRunning: Boolean) {
        _state.value = _state.value.copy(isForegroundServiceRunning = isForegroundServiceRunning)
    }

    fun setHeartRate(heartRate: Double) {
        _state.value = _state.value.copy(
            heartRate = heartRate,
        )
    }

    fun setBatteryLevel(batteryLevel: Int) {
        _state.value = _state.value.copy(
            batteryLevel = batteryLevel,
        )
    }
}