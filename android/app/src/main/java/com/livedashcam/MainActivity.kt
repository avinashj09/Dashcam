package com.livedashcam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private val Ink = Color(0xFF07111D)
private val Panel = Color(0xFF101F30)
private val Teal = Color(0xFF38E2D0)
private val Muted = Color(0xFF9BB0C6)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LiveDashcamApp() }
    }

    @Composable
    private fun LiveDashcamApp() {
        var host by remember { mutableStateOf("192.168.169.1") }
        var port by remember { mutableStateOf("2222") }
        var path by remember { mutableStateOf("/streamtype=0") }
        var tab by remember { mutableStateOf("LIVE") }
        var status by remember { mutableStateOf("Not tested") }
        var diagnostic by remember { mutableStateOf("Connect your phone to the dashcam Wi-Fi, then test the connection.") }
        var streamUrl by remember { mutableStateOf("rtsp://192.168.169.1:2222/streamtype=0") }
        var player by remember { mutableStateOf<ExoPlayer?>(null) }
        var playing by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }

        DisposableEffect(Unit) { onDispose { player?.release() } }

        MaterialTheme(colorScheme = darkColorScheme(primary = Teal, background = Ink, surface = Panel, onBackground = Color.White, onSurface = Color.White)) {
            Column(Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState()).padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("LIVE DASHCAM", color = Teal, fontSize = 12.sp, letterSpacing = 2.sp)
                        Text("Your drive. Your control.", color = Color.White, fontSize = 25.sp)
                    }
                    Surface(color = Panel, shape = RoundedCornerShape(14.dp)) {
                        Text("●  $status", color = if (status == "Connected") Teal else Muted, modifier = Modifier.padding(10.dp), fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    TabButton("LIVE", tab == "LIVE", Modifier.weight(1f)) { tab = "LIVE" }
                    TabButton("STORAGE", tab == "STORAGE", Modifier.weight(1f)) { tab = "STORAGE" }
                    TabButton("SETUP", tab == "SETUP", Modifier.weight(1f)) { tab = "SETUP" }
                }
                Spacer(Modifier.height(16.dp))
                when (tab) {
                    "LIVE" -> {
                        Surface(color = Color(0xFF02070C), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(220.dp)) {
                            if (playing && player != null) {
                                AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player } }, modifier = Modifier.fillMaxSize())
                            } else {
                                Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.PlayArrow, null, tint = Teal, modifier = Modifier.size(54.dp))
                                    Text("LIVE PREVIEW", color = Color.White, fontSize = 16.sp)
                                    Text("Connect to Oncord Wi-Fi to test stream", color = Muted, fontSize = 12.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = {
                                val url = "rtsp://$host:$port$path"
                                streamUrl = url
                                try {
                                    player?.release()
                                    player = ExoPlayer.Builder(this@MainActivity).build().also {
                                        it.setMediaItem(MediaItem.fromUri(url))
                                        it.prepare()
                                        it.playWhenReady = true
                                        it.addListener(object : Player.Listener {
                                            override fun onPlaybackStateChanged(playbackState: Int) {
                                                if (playbackState == Player.STATE_READY) { status = "Connected"; playing = true }
                                            }
                                            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                                status = "Stream error"
                                                diagnostic = "RTSP playback failed: ${error.errorCodeName}. Verify camera protocol/path and Wi-Fi."
                                                playing = false
                                            }
                                        })
                                    }
                                    playing = true
                                    diagnostic = "Connecting to $url"
                                } catch (e: Exception) { diagnostic = e.message ?: "Player could not start"; status = "Error" }
                            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Ink)) { Text("Start Live") }
                            OutlinedButton(onClick = { player?.stop(); playing = false; status = "Stopped" }, modifier = Modifier.weight(1f)) { Text("Stop") }
                        }
                        Spacer(Modifier.height(12.dp))
                        InfoCard("Connection", "Stream: $streamUrl\n$diagnostic")
                    }
                    "STORAGE" -> {
                        InfoCard("Saved recordings", "This prototype does not yet list or download SD-card recordings. First confirm the camera's file-list protocol; live-stream availability alone does not prove SD-card access.")
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            busy = true
                            lifecycleScope.launch {
                                diagnostic = probeStorage(host)
                                busy = false
                            }
                        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Ink), enabled = !busy) {
                            Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "Checking camera…" else "Check SD-card endpoints")
                        }
                        Spacer(Modifier.height(12.dp))
                        InfoCard("Protocol test", diagnostic)
                    }
                    else -> {
                        InfoCard("Camera settings", "Edit the address, RTSP port and path here. These are diagnostic settings, not proof that the camera supports each protocol.")
                        Spacer(Modifier.height(8.dp))
                        EditField("Camera IP / hostname", host) { host = it }
                        EditField("RTSP port", port) { port = it.filter(Char::isDigit).take(5) }
                        EditField("Stream path", path) { path = if (it.startsWith("/")) it else "/$it" }
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            busy = true
                            lifecycleScope.launch {
                                diagnostic = withContext(Dispatchers.IO) {
                                    val a = probe(host, "/app/getmediainfo")
                                    val b = probe(host, "/app/getsdinfo")
                                    "GET /app/getmediainfo:\n$a\n\nGET /app/getsdinfo:\n$b"
                                }
                                busy = false
                                status = if (diagnostic.contains("\"result\":0")) "Responding" else "Check Wi-Fi"
                            }
                        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Ink), enabled = !busy) { Text(if (busy) "Testing…" else "Test camera endpoints") }
                        Spacer(Modifier.height(12.dp))
                        InfoCard("Result", diagnostic)
                        InfoCard("Cloud backup", "Google Drive upload is not implemented in this prototype. It needs OAuth setup and secure background upload plus device testing.")
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("LiveDashcam • Prototype build 0.1.0", color = Muted, fontSize = 11.sp)
            }
        }
    }

    @Composable private fun TabButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
        Surface(onClick = onClick, color = if (selected) Teal else Panel, shape = RoundedCornerShape(14.dp), modifier = modifier) {
            Text(label, color = if (selected) Ink else Color.White, modifier = Modifier.padding(vertical = 14.dp), fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }

    @Composable private fun InfoCard(title: String, body: String) {
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(title, color = Teal, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(body, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }

    @Composable private fun EditField(label: String, value: String, onValueChange: (String) -> Unit) {
        Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
            Text(label, color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(5.dp))
            Surface(color = Panel, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                BasicTextField(value, onValueChange, modifier = Modifier.fillMaxWidth().padding(14.dp), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp))
            }
        }
    }

    private suspend fun probeStorage(host: String): String = withContext(Dispatchers.IO) {
        val paths = listOf("/app/getsdinfo", "/app/getfilelist", "/app/getmediafilelist", "/app/getmediainfo")
        paths.joinToString("\n\n") { p -> "GET $p:\n" + probe(host, p) }
    }

    private fun probe(host: String, endpoint: String): String {
        return try {
            val conn = (URL("http://$host$endpoint").openConnection() as HttpURLConnection)
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            conn.requestMethod = "GET"
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            "HTTP $code: ${body.take(1200)}"
        } catch (e: Exception) { "${e.javaClass.simpleName}: ${e.message}" }
    }
}
