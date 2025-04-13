package eu.depau.livewearheartrate

import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import eu.depau.livewearheartrate.shared.SensorsDTO
import eu.depau.livewearheartrate.shared.unmarshall
import eu.depau.livewearheartrate.ui.theme.LiveWearHeartRateTheme
import kotlinx.coroutines.delay

const val HR_MESSAGE_PATH = "/sensors/heart_rate"
private const val LOG_TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    private val mViewModel by viewModels<AppViewModel>()
    private val mMessageClient by lazy { Wearable.getMessageClient(this) }
    private lateinit var mDataLayerListener: LifecycleDataLayerListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        mDataLayerListener = messageClientListener(mMessageClient, ::onMessageReceived)

        setContent {
            DisposableEffect(Unit) {
                window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose {
                    window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            LiveWearHeartRateTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HeartRate(
                        viewModel = mViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun onMessageReceived(message: MessageEvent) {
        Log.d(LOG_TAG, "Received message from ${message.sourceNodeId}, path: ${message.path}")
        if (message.path == HR_MESSAGE_PATH) {
            val sensorsDTO = unmarshall<SensorsDTO>(message.data)
            Log.d(LOG_TAG, "Received heart rate message: ${sensorsDTO.heartRate}")
            Log.d(LOG_TAG, "Raw data: '${message.data.toHexString()}' (${message.data.size} bytes)")
            mViewModel.acceptSensorReading(sensorsDTO)
        }
    }
}

@Composable
fun HeartRate(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val state by viewModel.state.collectAsState()
        Text(text = "Heart rate", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "${state.lastReading.heartRate.toInt()}",
            style = MaterialTheme.typography.displayLarge,
        )
        Text(
            text = "bpm", style = MaterialTheme.typography.headlineSmall
        )

        var timeAgo by remember { mutableLongStateOf(System.currentTimeMillis() - state.lastReading.timestamp) }
        LaunchedEffect(timeAgo, state.lastReading) {
            delay(100L)
            timeAgo = System.currentTimeMillis() - state.lastReading.timestamp
        }

        Spacer(modifier = Modifier.padding(16.dp))

        val modelProducer = remember { CartesianChartModelProducer() }
        LaunchedEffect(state.heartRateHistory) {
            if (state.heartRateHistory.isEmpty()) return@LaunchedEffect

            modelProducer.runTransaction {
                lineSeries {
                    series(
                        state.heartRateHistory.map { (it.timestamp - System.currentTimeMillis()) / 1000 },
                        state.heartRateHistory.map { it.heartRate },
                    )
                }
            }
        }

        CartesianChartHost(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp),
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    rangeProvider = CartesianLayerRangeProvider.fixed(
                        -KEEP_LAST_SECONDS.toDouble()+5,
                        0.0,
                        50.0,
                        180.0
                    ),
                ),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(),
                decorations = listOf(
                    rememberModerateZoneBox(),
                    rememberVigorousZoneBox(),
                    rememberMaxZoneBox(),
                )
            ),
            modelProducer = modelProducer,
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            animationSpec = null,
        )

        Spacer(modifier = Modifier.padding(16.dp))

        if (state.lastReading.timestamp == 0L) {
            Text(
                text = "No data received yet",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Text(
                text = "Last updated: ${(timeAgo / 100).toDouble() / 10} seconds ago",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp")
@Composable
fun GreetingPreview() {
    val viewModel by remember { mutableStateOf(AppViewModel()) }
    LiveWearHeartRateTheme {
        HeartRate(viewModel)
    }
}