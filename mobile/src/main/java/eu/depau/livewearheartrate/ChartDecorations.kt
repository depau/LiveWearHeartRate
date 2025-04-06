package eu.depau.livewearheartrate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.decoration.HorizontalBox

@Composable
fun rememberModerateZoneBox(): HorizontalBox {
    val fill = fill(Color(0x30658000))
    val shape = rememberShapeComponent(fill = fill)
    return remember {
        HorizontalBox(
            y = { 117.0..140.0 },
            box = shape,
        )
    }
}

@Composable
fun rememberVigorousZoneBox(): HorizontalBox {
    val fill = fill(Color(0x3084701f))
    val shape = rememberShapeComponent(fill = fill)
    return remember {
        HorizontalBox(
            y = { 141.0..170.0 },
            box = shape,
        )
    }
}

@Composable
fun rememberMaxZoneBox(): HorizontalBox {
    val fill = fill(Color(0x30ff0000))
    val shape = rememberShapeComponent(fill = fill)
    return remember {
        HorizontalBox(
            y = { 171.0..180.0 },
            box = shape,
        )
    }
}
