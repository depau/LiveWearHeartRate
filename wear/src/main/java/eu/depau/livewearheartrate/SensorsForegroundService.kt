package eu.depau.livewearheartrate

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.health.services.client.HealthServices
import androidx.health.services.client.HealthServicesClient
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.MeasureClient
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.getCapabilities
import androidx.health.services.client.unregisterMeasureCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.Wearable
import eu.depau.livewearheartrate.livedata.SensorLiveData
import eu.depau.livewearheartrate.livedata.ServiceStatusLiveData
import eu.depau.livewearheartrate.presentation.MainActivity
import eu.depau.livewearheartrate.shared.SensorsDTO
import eu.depau.livewearheartrate.shared.marshall
import eu.depau.livewearheartrate.utils.WakeLockHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean

const val HR_MESSAGE_PATH = "/sensors/heart_rate"

const val FG_SERVICE_CHANNEL = "eu.depau.livewearheartrate.FOREGROUND_SERVICE_CHANNEL"
const val ALERTS_CHANNEL = "eu.depau.livewearheartrate.ALERTS_CHANNEL"
const val FG_SERVICE_ID = 324

private const val LOG_TAG = "SensorsForegroundService"

class SensorsForegroundService : LifecycleService() {
    private var mIsRunning = AtomicBoolean(false)
    private var mShouldStop = false
    private val mMessageClient by lazy { Wearable.getMessageClient(this) }

    private lateinit var mHealthClient: HealthServicesClient
    private lateinit var mWakeLockHelper: WakeLockHelper

    companion object {
        const val ACTION_START = "eu.depau.livewearheartrate.ACTION_START"
        const val ACTION_STOP = "eu.depau.livewearheartrate.ACTION_STOP"

        const val ACTION_SENSOR_DATA = "eu.depau.livewearheartrate.ACTION_SENSOR_DATA"
        const val ACTION_SERVICE_STATUS = "eu.depau.livewearheartrate.ACTION_SERVICE_STATUS"
    }

    @OptIn(ExperimentalStdlibApi::class)
    suspend fun sendHeartRateData(heartRate: Double) {
        val dto = SensorsDTO(heartRate, System.currentTimeMillis())
        SensorLiveData.updateSensorData(dto)

        val parcelData = dto.marshall()

        val nodeClient = Wearable.getNodeClient(this)
        val nodes = nodeClient.connectedNodes.await()

        Log.d(LOG_TAG, "Message raw data: '${parcelData.toHexString()}' (${parcelData.size} bytes)")

        for (node in nodes) {
            mMessageClient.sendMessage(node.id, HR_MESSAGE_PATH, parcelData)
            Log.d(LOG_TAG, "Sent heart rate data: $heartRate to node: ${node.id}")
        }
    }

    val measureHeartRateCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(
            dataType: DeltaDataType<*, *>,
            availability: Availability
        ) {
            if (availability is DataTypeAvailability) {
                when (availability) {
                    DataTypeAvailability.AVAILABLE -> {
                        Log.d(LOG_TAG, "Heart rate data is available")
                    }

                    DataTypeAvailability.UNAVAILABLE -> {
                        Log.d(LOG_TAG, "Heart rate data is unavailable")
                    }

                    DataTypeAvailability.ACQUIRING -> {
                        Log.d(LOG_TAG, "Heart rate data is acquiring")
                    }
                }
            }
        }

        override fun onDataReceived(data: DataPointContainer) {
            val currentHeartRate = data.getData(DataType.HEART_RATE_BPM)
            for (point in currentHeartRate) {
                Log.d(LOG_TAG, "Heart rate: ${point.value} bpm")
                lifecycleScope.launch {
                    sendHeartRateData(point.value.toDouble())
                }
            }
        }
    }

    private suspend fun measurementThread() {
        Toast.makeText(this, "Measurement thread started", Toast.LENGTH_SHORT).show()

        val measureClient = mHealthClient.measureClient
        if (!checkAndNotifyHeartRateSupported(measureClient)) return

        measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, measureHeartRateCallback)

        while (!mShouldStop) {
            delay(1000)
            mWakeLockHelper.bump()
        }

        measureClient.unregisterMeasureCallback(DataType.HEART_RATE_BPM, measureHeartRateCallback)
        mShouldStop = false
    }

    override fun onCreate() {
        super.onCreate()
        mHealthClient = HealthServices.getClient(this)
        mWakeLockHelper = WakeLockHelper(this, PowerManager.SCREEN_DIM_WAKE_LOCK)

        lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(
                source: LifecycleOwner,
                event: Lifecycle.Event
            ) {
                Log.d(LOG_TAG, "Lifecycle event: $event")
            }
        })
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        super.onStartCommand(intent, flags, startId)

        if (intent?.action == ACTION_STOP) {
            mShouldStop = true
            // TODO: ensure service is stopped
            return START_NOT_STICKY

        } else if (intent?.action != ACTION_START) {
            Log.w(LOG_TAG, "Service started with unknown action: ${intent?.action}")
            return START_NOT_STICKY
        }

        require(intent.action == ACTION_START)

        // Don't start the service again if it's already running
        if (!mIsRunning.compareAndSet(false, true)) {
            return START_NOT_STICKY
        }
        ServiceStatusLiveData.setIsRunning(true)

        createNotificationChannels()
        // Create the notification with a button to stop the service
        val notification = Notification.Builder(this, FG_SERVICE_CHANNEL)
            .setContentTitle("Monitoring sensors")
            .setContentText("Monitoring heart rate")
            .setSmallIcon(R.drawable.splash_icon)  // TODO: Use a proper icon
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java).apply {
                        setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("foregroundServiceRunning", mIsRunning.get())
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .addAction(
                Notification.Action.Builder( // TODO: replace deprecated builder
                    R.drawable.splash_icon, // TODO: Use a proper icon
                    "Stop monitoring",
                    PendingIntent.getService(
                        this,
                        0,
                        Intent(this, this::class.java).apply { action = ACTION_STOP },
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                ).build()
            ).build()

        // Make the service a foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(FG_SERVICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(FG_SERVICE_ID, notification)
        }

        // Launch the measurement thread coroutine
        lifecycleScope.launch {
            try {
                mShouldStop = false
                mWakeLockHelper.bump()
                measurementThread()
            } finally {
                mWakeLockHelper.release()
                Toast.makeText(
                    this@SensorsForegroundService,
                    "Stopping service",
                    Toast.LENGTH_SHORT
                ).show()
                ServiceStatusLiveData.setIsRunning(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                mIsRunning.set(false)
                stopSelf()
            }
        }

        return START_STICKY
    }
}


private fun Service.createNotificationChannels() {
    val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.createNotificationChannels(
        listOf(
            NotificationChannel(
                FG_SERVICE_CHANNEL,
                "Sensor monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for background sensor monitoring"
                setShowBadge(false)
            },
            NotificationChannel(
                ALERTS_CHANNEL,
                "Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for status alerts"
                setShowBadge(true)
            }
        )
    )
}

private suspend fun Service.checkAndNotifyHeartRateSupported(measureClient: MeasureClient): Boolean {
    val capabilities = measureClient.getCapabilities()

    if (DataType.HEART_RATE_BPM !in capabilities.supportedDataTypesMeasure) {
        val notification = Notification.Builder(this, ALERTS_CHANNEL)
            .setContentTitle("Heart rate not supported")
            .setContentText("Heart rate measurement is not supported on this device.")
            .setSmallIcon(R.drawable.splash_icon)  // TODO: Use a proper icon
            .setOngoing(false)
            .build()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(0, notification)
        return false
    }

    return true
}
