package eu.depau.livewearheartrate.livedata

import androidx.lifecycle.LiveData
import eu.depau.livewearheartrate.shared.SensorsDTO

object SensorLiveData : LiveData<SensorsDTO>() {
    fun updateSensorData(sensorsDTO: SensorsDTO) {
        postValue(sensorsDTO)
    }
}
