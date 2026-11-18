package com.xpulse.tracker

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

private val INR: NumberFormat = NumberFormat.getInstance(Locale("en", "IN"))
private fun rupee(v: Int): String = "₹" + INR.format(v)

private val DropGreen = Color(0xFF0B6E4F)
private val RiseRed = Color(0xFFB3261E)

sealed interface UiState {
    data object Loading : UiState
    data class Error(val message: String) : UiState
    data class Loaded(val points: List<PricePoint>) : UiState
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }

    // Ask for notification permission once (Android 13+).
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored; worker handles absence gracefully */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            PriceScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceScreen() {
    var state by remember { mutableStateOf<UiState>(UiState.Loading) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            state = UiState.Loading
            state = try {
                UiState.Loaded(withContext(Dispatchers.IO) { PriceRepository.fetchHistory() })
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Could not load prices")
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Xpulse Price Tracker") }) }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val s = state) {
                is UiState.Loading -> CenterBox { CircularProgressIndicator() }
                is UiState.Error -> ErrorCard(s.message) { load() }
                is UiState.Loaded ->
                    if (s.points.isEmpty()) {
                        ErrorCard("No price data yet. The cloud job writes the first entry on its next run.") { load() }
                    } else {
                        LoadedContent(s.points) { load() }
                    }
            }
        }
    }
}

@Composable
private fun LoadedContent(points: List<PricePoint>, onRefresh: () -> Unit) {
    val current = points.last()
    val previous = points.getOrNull(points.size - 2)
    val delta = previous?.let { current.onRoad - it.onRoad }

    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Hero Xpulse 200 4V", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            val sub = listOf(current.variant, current.city).filter { it.isNotBlank() }.joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(8.dp))
            Text("On-road price", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Text(rupee(current.onRoad), fontWeight = FontWeight.Bold, fontSize = 34.sp)

            if (delta != null && delta != 0) {
                val down = delta < 0
                Text(
                    (if (down) "▼ " else "▲ ") + rupee(kotlin.math.abs(delta)) +
                        if (down) "  since last check" else "  since last check",
                    color = if (down) DropGreen else RiseRed,
                    fontWeight = FontWeight.Medium,
                )
            } else if (delta == 0) {
                Text("No change since last check", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "Ex-showroom ${rupee(current.exShowroom)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
    }

    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("On-road price history", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            PriceChart(points, Modifier.fillMaxWidth().height(200.dp))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(points.first().date, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(points.last().date, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    val lowest = points.minByOrNull { it.onRoad }!!
    val highest = points.maxByOrNull { it.onRoad }!!
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Stat("Lowest seen", rupee(lowest.onRoad), lowest.date)
            Stat("Highest seen", rupee(highest.onRoad), highest.date)
            Stat("Days tracked", points.size.toString(), "")
        }
    }

    Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Check now") }

    Text(
        "Checked automatically every few hours. Prices from BikeWale & BikeDekho; " +
            "confirm the final on-road quote with your Hero dealer before booking.",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Stat(label: String, value: String, sub: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        if (sub.isNotBlank()) Text(sub, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PriceChart(points: List<PricePoint>, modifier: Modifier) {
    if (points.size < 2) {
        Text(
            "Need at least 2 days of data to draw the graph — check back tomorrow.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        return
    }
    val values = points.map { it.onRoad }
    val minV = values.min()
    val maxV = values.max()
    val range = (maxV - minV).coerceAtLeast(1)
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier) {
        val padX = 10f
        val padY = 14f
        val cw = size.width - padX * 2
        val ch = size.height - padY * 2

        fun px(i: Int) = padX + cw * i / (points.size - 1)
        fun py(v: Int) = padY + ch * (1f - (v - minV).toFloat() / range)

        // baseline at the lowest value
        drawLine(gridColor, Offset(padX, py(minV)), Offset(size.width - padX, py(minV)), strokeWidth = 1.5f)

        val path = Path()
        points.forEachIndexed { i, p ->
            val x = px(i); val y = py(p.onRoad)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path,
            color = lineColor,
            style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        points.forEachIndexed { i, p ->
            drawCircle(lineColor, radius = 5f, center = Offset(px(i), py(p.onRoad)))
        }
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Couldn't load prices", fontWeight = FontWeight.Bold)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Text(
                "If this is the first run: check you edited RAW_HISTORY_URL in Config.kt with your " +
                    "GitHub username, and that the repo is public.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}
