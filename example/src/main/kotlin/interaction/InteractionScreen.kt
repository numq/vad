package interaction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import capturing.CapturingService
import com.github.numq.vad.VoiceActivityDetection
import device.Device
import device.DeviceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import picker.ModePicker
import playback.PlaybackService

@Composable
fun InteractionScreen(
    deviceService: DeviceService,
    vad: VoiceActivityDetection,
    capturingService: CapturingService,
    playbackService: PlaybackService,
    handleThrowable: (Throwable) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope { Dispatchers.Default }

    var deviceJob by remember { mutableStateOf<Job?>(null) }

    var capturingJob by remember { mutableStateOf<Job?>(null) }

    var mode by remember { mutableStateOf(vad.mode) }

    val capturingDevices = remember { mutableStateListOf<Device>() }

    var selectedCapturingDevice by remember { mutableStateOf<Device?>(null) }

    var refreshRequested by remember { mutableStateOf(true) }

    var isSpeechDetected by remember { mutableStateOf(false) }

    LaunchedEffect(refreshRequested) {
        deviceJob?.cancel()
        deviceJob = null

        if (refreshRequested) {
            deviceJob = coroutineScope.launch {
                deviceService.listCapturingDevices().onSuccess { devices ->
                    if (devices != capturingDevices) {
                        capturingDevices.clear()
                        capturingDevices.addAll(devices)

                        if (selectedCapturingDevice !in capturingDevices) {
                            selectedCapturingDevice = null
                        }
                    }
                }.onFailure(handleThrowable)

                refreshRequested = false
            }
        }
    }

    LaunchedEffect(mode, selectedCapturingDevice) {
        isSpeechDetected = false

        capturingJob?.cancel()
        capturingJob = null

        if (vad.mode != mode) {
            vad.changeMode(mode).onFailure(handleThrowable)
        }

        capturingJob = when (val device = selectedCapturingDevice) {
            null -> return@LaunchedEffect

            else -> coroutineScope.launch {
                val chunkSize = device.sampleRate / 1_000 * device.channels

                capturingService.capture(device = device, chunkSize = chunkSize).catch {
                    handleThrowable(it)
                }.collect { pcmBytes ->
                    isSpeechDetected = vad.detect(
                        pcmBytes = pcmBytes,
                        sampleRate = device.sampleRate,
                        channels = device.channels
                    ).onFailure(handleThrowable).getOrNull() == true

                    if (isSpeechDetected) {
                        playbackService.write(pcmBytes = pcmBytes).onFailure(handleThrowable)
                    } else {
                        playbackService.play().onFailure(handleThrowable)
                    }
                }
            }
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            ModePicker(
                modifier = Modifier.fillMaxWidth(),
                currentMode = mode
            ) { changedMode ->
                vad.changeMode(changedMode).onSuccess {
                    mode = vad.mode
                }.onFailure(handleThrowable)
            }

            if (isSpeechDetected) {
                Text("Speech", color = Color.Green, modifier = Modifier.padding(8.dp))
            } else {
                Text("Silence", color = Color.Red, modifier = Modifier.padding(8.dp))
            }

            Divider(modifier = Modifier.fillMaxWidth())

            Card(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Capturing devices", modifier = Modifier.padding(8.dp))
                        when (refreshRequested) {
                            true -> IconButton(onClick = {
                                refreshRequested = false
                            }) {
                                Icon(Icons.Default.Cancel, null)
                            }

                            false -> IconButton(onClick = {
                                refreshRequested = true
                            }) {
                                Icon(Icons.Default.Refresh, null)
                            }
                        }
                    }
                    when {
                        refreshRequested -> Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }

                        else -> LazyColumn(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(space = 8.dp, alignment = Alignment.Top),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            items(capturingDevices, key = { it.name }) { device ->
                                Card(
                                    modifier = Modifier.fillMaxWidth()
                                        .alpha(alpha = if (device == selectedCapturingDevice) .5f else 1f).clickable {
                                            selectedCapturingDevice = device.takeIf { it != selectedCapturingDevice }
                                        }) {
                                    Text(device.name, modifier = Modifier.padding(8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}