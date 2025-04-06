/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package eu.depau.livewearheartrate.presentation

import android.Manifest.permission
import android.app.ComponentCaller
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TimeTextDefaults
import androidx.wear.compose.material.curvedText
import androidx.wear.tooling.preview.devices.WearDevices
import eu.depau.livewearheartrate.SensorsForegroundService
import eu.depau.livewearheartrate.livedata.SensorLiveData
import eu.depau.livewearheartrate.livedata.ServiceStatusLiveData
import eu.depau.livewearheartrate.presentation.theme.LiveWearHeartRateTheme
import eu.depau.livewearheartrate.shared.SensorsDTO
import eu.depau.livewearheartrate.shared.broadcastReceiver
import kotlinx.coroutines.launch

private const val LOG_TAG = "MainActivity"

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private val PERMISSIONS = listOf(
    permission.POST_NOTIFICATIONS,
    permission.BODY_SENSORS,
    permission.BODY_SENSORS_BACKGROUND,
)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private val PERMISSIONS_DESC = mapOf(
    permission.POST_NOTIFICATIONS to "notifications",
    permission.BODY_SENSORS to "sensors (all the time)",
    permission.BODY_SENSORS_BACKGROUND to "sensors (all the time)",
)

class MainActivity : ComponentActivity() {
    private val mViewModel: AppViewModel by viewModels()

    private val mServiceStatusObserver = object : Observer<Boolean> {
        override fun onChanged(value: Boolean) {
            mViewModel.setForegroundServiceRunning(value)
        }
    }

    private val mSensorDataObserver = object : Observer<SensorsDTO> {
        override fun onChanged(value: SensorsDTO) {
            mViewModel.setForegroundServiceRunning(true)
            mViewModel.setHeartRate(value.heartRate)
        }
    }

    private val mBatteryBroadcastReceiver = broadcastReceiver { intent ->
        val batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        mViewModel.setBatteryLevel(batteryLevel)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val missingPermissions = PERMISSIONS
            .filter { result[it] == false }
            .map { PERMISSIONS_DESC[it] }
            .toSet()

        if (!missingPermissions.isEmpty()) {
            Toast.makeText(this, "Please grant $missingPermissions permissions", Toast.LENGTH_LONG)
                .show()
            openAppSettings()
        } else {
            mViewModel.setHasAllPermissions(true)
        }
    }

    override fun onNewIntent(intent: Intent, caller: ComponentCaller) {
        super.onNewIntent(intent, caller)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.hasExtra("foregroundServiceRunning")) {
            mViewModel.setForegroundServiceRunning(
                intent.getBooleanExtra(
                    "foregroundServiceRunning",
                    false
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(mBatteryBroadcastReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(mBatteryBroadcastReceiver)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        mViewModel.setHasAllPermissions(hasAllPermissions())

        val bm = getSystemService(BatteryManager::class.java)
        val batteryLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        mViewModel.setBatteryLevel(batteryLevel)

        ServiceStatusLiveData.observe(this, mServiceStatusObserver)
        SensorLiveData.observe(this, mSensorDataObserver)

        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            WearApp(
                viewModel = mViewModel,
                performAction = ::toggleSensorService
            )
        }
    }

    private fun toggleSensorService() {
        if (hasAllPermissions()) {
            val serviceRunning = mViewModel.state.value.isForegroundServiceRunning
            if (!serviceRunning) {
                startService(
                    Intent(this, SensorsForegroundService::class.java).apply {
                        action = SensorsForegroundService.ACTION_START
                    }
                )
                mViewModel.setForegroundServiceRunning(true)
            } else {
                startService(
                    Intent(this, SensorsForegroundService::class.java).apply {
                        action = SensorsForegroundService.ACTION_STOP
                    }
                )
                mViewModel.setForegroundServiceRunning(false)
            }
            mViewModel.setForegroundServiceRunning(!serviceRunning)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestAllPermissions()
            } else {
                require(false) { "Build version is lower than Tiramisu" }
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
    }

    fun hasAllPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PERMISSIONS.all { perm ->
                ContextCompat.checkSelfPermission(this, perm) == PERMISSION_GRANTED
            }
        } else {
            return true
        }
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun requestAllPermissions() {
        lifecycleScope.launch {
            val allGranted = PERMISSIONS.all { perm ->
                ContextCompat.checkSelfPermission(this@MainActivity, perm) == PERMISSION_GRANTED
            }

            if (!allGranted) {
                Toast.makeText(
                    this@MainActivity,
                    "This app needs notification and sensor permissions, including background access.",
                    Toast.LENGTH_LONG
                ).show()

                permissionLauncher.launch(PERMISSIONS.toTypedArray())
            }
        }
    }
}

@Composable
fun WearApp(viewModel: AppViewModel, performAction: () -> Unit) {
    LiveWearHeartRateTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            val state by viewModel.state.collectAsState()

            val trailingTextStyle =
                TimeTextDefaults.timeTextStyle(color = MaterialTheme.colors.primary)
            TimeText(
                endLinearContent = {
                    Text(
                        text = "${state.batteryLevel} %",
                        style = trailingTextStyle
                    )
                },
                endCurvedContent = {
                    curvedText(
                        text = "${state.batteryLevel} %",
                        style = CurvedTextStyle(trailingTextStyle)
                    )
                },
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "${state.heartRate.toInt()} bpm",
                    style = MaterialTheme.typography.title1,
                )

                Button(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    onClick = performAction
                ) {
                    Text(
                        text = if (!state.hasAllPermissions) {
                            "Request permissions"
                        } else if (state.isForegroundServiceRunning) {
                            "Stop service"
                        } else {
                            "Start service"
                        },
                        textAlign = TextAlign.Center
                    )

                }
            }
        }
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    val viewModel = remember { AppViewModel() }
    WearApp(viewModel) {}
}
