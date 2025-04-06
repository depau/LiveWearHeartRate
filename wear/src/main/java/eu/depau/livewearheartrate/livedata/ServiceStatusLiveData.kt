package eu.depau.livewearheartrate.livedata

import androidx.lifecycle.LiveData

object ServiceStatusLiveData : LiveData<Boolean>() {
    fun setIsRunning(isRunning: Boolean) {
        postValue(isRunning)
    }
}
