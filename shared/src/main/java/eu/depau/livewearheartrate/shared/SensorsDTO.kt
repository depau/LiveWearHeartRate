package eu.depau.livewearheartrate.shared;


import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class SensorsDTO(
    val heartRate: Double,
    val timestamp: Long,
) : Parcelable
