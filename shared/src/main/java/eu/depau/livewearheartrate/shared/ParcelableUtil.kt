package eu.depau.livewearheartrate.shared

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.parcelableCreator

fun Parcelable.marshall(): ByteArray {
    val parcel = Parcel.obtain()
    try {
        writeToParcel(parcel, 0)
        return parcel.marshall()
    } finally {
        parcel.recycle()
    }
}

inline fun <reified T : Parcelable> unmarshall(parcel: Parcel): T {
    return parcelableCreator<T>().createFromParcel(parcel)
}

inline fun <reified T : Parcelable> unmarshall(data: ByteArray): T {
    val parcel = Parcel.obtain()
    try {
        parcel.unmarshall(data, 0, data.size)
        parcel.setDataPosition(0)
        return unmarshall<T>(parcel)
    } finally {
        parcel.recycle()
    }
}
