package eu.depau.livewearheartrate

import androidx.lifecycle.ViewModel
import eu.depau.livewearheartrate.shared.SensorsDTO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val KEEP_LAST_SECONDS = 60

data class AppState(
    val lastReading: SensorsDTO = SensorsDTO(0.0, 0L),
    val heartRateHistory: List<SensorsDTO> = emptyList(),
)

class AppViewModel : ViewModel() {
    private val _state = MutableStateFlow(AppState())
    val state = _state.asStateFlow()

    fun acceptSensorReading(sensorsDTO: SensorsDTO) {
        _state.value =
            _state.value.copy(
                lastReading = sensorsDTO,
                heartRateHistory = (_state.value.heartRateHistory + sensorsDTO).filter {
                    it.timestamp > System.currentTimeMillis() - KEEP_LAST_SECONDS * 1000
                },
            )
    }
}
