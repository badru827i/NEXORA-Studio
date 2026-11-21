package studio.nexora.app

import android.content.*
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

private val Bg = Color(0xFFF5F5F2)
private val Paper = Color.White
private val Ink = Color(0xFF20231F)
private val Grey = Color(0xFF777D76)
private val Edge = Color(0xFFE7E9E4)
private val Green = Color(0xFF416B55)
private val SoftGreen = Color(0xFFEAF1EC)
private val PreviewBg = Color(0xFF292D2A)

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NexoraApp() }
    }
}
private data class Asset(val uri: Uri, val name: String)
private enum class Screen(val label: String) { Editor("Edit"), Media("Media"), Projects("Projects"), Devices("Devices") }

@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@Composable
private fun NexoraApp() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(Screen.Editor) }
    var project by remember { mutableStateOf("Untitled project") }
    val assets = remember { mutableStateListOf<Asset>() }
    val projectDb = remember { ProjectDatabase(context) }
    var activeProjectId by remember { mutableStateOf<Long?>(null) }
    var savedProjects by remember { mutableStateOf(projectDb.list()) }
    var tool by remember { mutableStateOf("Trim") }
    var speed by remember { mutableFloatStateOf(1f) }
    var trimIn by remember { mutableFloatStateOf(0f) }
    var trimOut by remember { mutableFloatStateOf(100f) }
    var splitCount by remember { mutableIntStateOf(0) }
    var cropZoom by remember { mutableFloatStateOf(1f) }
    var rotationDegrees by remember { mutableIntStateOf(0) }
    var outputPreset by remember { mutableStateOf("Original") }
    var timeline by remember { mutableStateOf(TimelineDocument()) }
    var exportAudioGain by remember { mutableFloatStateOf(1f) }
    var exportFadeInMs by remember { mutableLongStateOf(0L) }
    var exportFadeOutMs by remember { mutableLongStateOf(0L) }
    var exportDialog by remember { mutableStateOf(false) }
    var exportRunning by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf("Choose a video and start export.") }
    val exporter = remember { MediaExportManager(context) }
    val videoAsset = assets.firstOrNull { context.contentResolver.getType(it.uri)?.startsWith("video/") == true }
    var renameDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf(project) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { list ->
        list.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            if (assets.none { it.uri == uri }) assets.add(Asset(uri, uri.lastPathSegment?.substringAfterLast('/') ?: "Media"))
        }
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Green, background = Bg, surface = Paper, onSurface = Ink)) {
        Scaffold(
            containerColor = Bg,
            topBar = {
                TopAppBar(
                    title = { Column {
                        Text("NEXORA STUDIO", fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.5.sp)
                        Text(project, fontSize = 11.sp, color = Grey, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } },
                    actions = {
                        IconButton(onClick = { newName = project; renameDialog = true }) { Icon(Icons.Default.Edit, "Rename", tint = Grey) }
                        TextButton(onClick = { exportDialog = true }) { Icon(Icons.Default.FileDownload, null); Text("Export") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Paper) {
                    Screen.values().forEach { item ->
                        val icon = when(item) {
                            Screen.Editor -> Icons.Default.Tune
                            Screen.Media -> Icons.Default.Image
                            Screen.Projects -> Icons.Default.Folder
                            Screen.Devices -> Icons.Default.Devices
                        }
                        NavigationBarItem(selected = screen == item, onClick = { screen = item }, icon = { Icon(icon, item.label) }, label = { Text(item.label, fontSize = 10.sp) })
                    }
                }
            }
        ) { padding ->
            when(screen) {
                Screen.Editor -> EditorScreen(
                    Modifier.padding(padding), assets, videoAsset?.uri, tool, { tool = it }, speed, { speed = safeSpeed(it) },
                    trimIn, { trimIn = safeTrimStart(it, trimOut) }, trimOut, { trimOut = safeTrimEnd(it, trimIn) },
                    splitCount, { splitCount++ }, { picker.launch(arrayOf("video/*", "image/*", "audio/*")) },
                    cropZoom, { cropZoom = safeCropZoom(it) }, rotationDegrees, { rotationDegrees = safeRotationDegrees(it) },
                    outputPreset, { outputPreset = safeOutputPreset(it) }, timeline, { timeline = it }
                )
                Screen.Media -> MediaScreen(Modifier.padding(padding), assets) { picker.launch(arrayOf("video/*", "image/*", "audio/*")) }
                Screen.Projects -> ProjectsScreen(
                    Modifier.padding(padding), project, savedProjects,
                    onSave = {
                        val document = org.json.JSONObject().apply {
                            put("tool", tool); put("speed", speed.toDouble())
                            put("trimIn", trimIn.toDouble()); put("trimOut", trimOut.toDouble()); put("splitCount", splitCount)
                            put("cropZoom", cropZoom.toDouble()); put("rotationDegrees", rotationDegrees); put("outputPreset", outputPreset)
                            put("timeline", timeline.toJson())
                            put("exportAudioGain", exportAudioGain.toDouble())
                            put("exportFadeInMs", exportFadeInMs)
                            put("exportFadeOutMs", exportFadeOutMs)
                            put("assets", org.json.JSONArray().apply {
                                assets.forEach { asset ->
                                    put(org.json.JSONObject().put("uri", asset.uri.toString()).put("name", asset.name))
                                }
                            })
                        }.toString()
                        activeProjectId = projectDb.save(activeProjectId, project, document)
                        savedProjects = projectDb.list()
                    },
                    onOpen = { saved ->
                        activeProjectId = saved.id
                        project = saved.name
                        assets.clear()
                        runCatching {
                            val doc = org.json.JSONObject(saved.document)
                            val array = doc.optJSONArray("assets") ?: org.json.JSONArray()
                            for (i in 0 until array.length()) {
                                val item = array.optJSONObject(i) ?: continue
                                assets.add(Asset(Uri.parse(item.optString("uri")), item.optString("name", "Media")))
                            }
                            tool = doc.optString("tool", "Trim")
                            speed = safeSpeed(doc.optDouble("speed", 1.0).toFloat())
                            trimIn = doc.optDouble("trimIn", 0.0).toFloat()
                            trimOut = doc.optDouble("trimOut", 100.0).toFloat()
                            splitCount = doc.optInt("splitCount", 0).coerceAtLeast(0)
                            cropZoom = safeCropZoom(doc.optDouble("cropZoom", 1.0).toFloat())
                            rotationDegrees = safeRotationDegrees(doc.optInt("rotationDegrees", 0))
                            outputPreset = safeOutputPreset(doc.optString("outputPreset", "Original"))
                            timeline = TimelineDocument.fromJson(doc.optString("timeline", ""))
                            exportAudioGain = doc.optDouble("exportAudioGain", 1.0).toFloat().coerceIn(0f, 1f)
                            exportFadeInMs = doc.optLong("exportFadeInMs", 0L).coerceIn(0L, 5_000L)
                            exportFadeOutMs = doc.optLong("exportFadeOutMs", 0L).coerceIn(0L, 5_000L)
                        }
                        screen = Screen.Editor
                    },
                    onDelete = { id ->
                        projectDb.delete(id)
                        if (activeProjectId == id) activeProjectId = null
                        savedProjects = projectDb.list()
                    },
                    onNew = {
                        activeProjectId = null
                        project = "Untitled project"; assets.clear(); tool = "Trim"; speed = 1f
                        trimIn = 0f; trimOut = 100f; splitCount = 0; cropZoom = 1f; rotationDegrees = 0; outputPreset = "Original"
                        timeline = TimelineDocument(); exportAudioGain = 1f; exportFadeInMs = 0L; exportFadeOutMs = 0L; screen = Screen.Editor
                    }
                )
                Screen.Devices -> DevicesScreen(Modifier.padding(padding))
            }
        }
    }
    if (exportDialog) AlertDialog(
        onDismissRequest = { if (!exportRunning) exportDialog = false },
        title = { Text("Export video") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (videoAsset == null) "Import a video first. Image and audio files cannot be exported with this single-clip video workflow." else "MP4 · H.264 video · AAC audio · 30 fps")
                Text("Export volume · ${(exportAudioGain * 100).toInt()}%", color = Green, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Slider(value = exportAudioGain, onValueChange = { exportAudioGain = it.coerceIn(0f, 1f) }, valueRange = 0f..1f)
                Text("Fade in · ${exportFadeInMs / 1000f}s", color = Grey, fontSize = 11.sp)
                Slider(value = exportFadeInMs / 1000f, onValueChange = { exportFadeInMs = (it * 1000f).toLong() }, valueRange = 0f..5f)
                Text("Fade out · ${exportFadeOutMs / 1000f}s", color = Grey, fontSize = 11.sp)
                Slider(value = exportFadeOutMs / 1000f, onValueChange = { exportFadeOutMs = (it * 1000f).toLong() }, valueRange = 0f..5f)
                Text(exportMessage, color = Grey, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(
                enabled = videoAsset != null && !exportRunning,
                onClick = {
                    val selected = videoAsset
                    if (selected != null) {
                        exportRunning = true
                        exportMessage = "Preparing video..."
                        exporter.export(selected.uri, trimIn, trimOut, speed, cropZoom, rotationDegrees, outputPreset, exportAudioGain, exportFadeInMs, exportFadeOutMs, timeline, object : MediaExportManager.Callback {
                            override fun onComplete(outputFile: java.io.File, publicUri: Uri?) {
                                exportRunning = false
                                exportMessage = if (publicUri != null) {
                                    "Saved to Movies / NEXORA Studio"
                                } else {
                                    "Export complete. App file: " + outputFile.absolutePath
                                }
                            }
                            override fun onError(message: String) {
                                exportRunning = false
                                exportMessage = "Export failed: " + message
                            }
                        })
                    }
                }
            ) { Text(if (exportRunning) "Exporting..." else "Start export") }
        },
        dismissButton = {
            TextButton(enabled = !exportRunning, onClick = { exportDialog = false }) { Text("Close") }
        }
    )
    if (renameDialog) AlertDialog(
        onDismissRequest = { renameDialog = false }, title = { Text("Project name") },
        text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { project = newName.ifBlank { "Untitled project" }; renameDialog = false }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { renameDialog = false }) { Text("Cancel") } }
    )
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@Composable
private fun EditorScreen(
    modifier: Modifier, assets: List<Asset>, previewUri: Uri?, tool: String, selectTool: (String) -> Unit,
    speed: Float, changeSpeed: (Float) -> Unit, trimIn: Float, changeIn: (Float) -> Unit,
    trimOut: Float, changeOut: (Float) -> Unit, splitCount: Int, split: () -> Unit, onImport: () -> Unit,
    cropZoom: Float, changeCropZoom: (Float) -> Unit, rotationDegrees: Int, changeRotationDegrees: (Int) -> Unit,
    outputPreset: String, changeOutputPreset: (String) -> Unit,
    timeline: TimelineDocument, changeTimeline: (TimelineDocument) -> Unit
) {
    val context = LocalContext.current
    var selectedPreviewClip by remember { mutableStateOf<TimelineClip?>(null) }
    val selectedPreviewVideoClip = selectedPreviewClip?.takeIf { clip ->
        !clip.assetUri.startsWith("text://") &&
            runCatching { context.contentResolver.getType(Uri.parse(clip.assetUri))?.startsWith("video/") == true }.getOrDefault(false)
    }
    val activePreviewUri = selectedPreviewVideoClip?.let { Uri.parse(it.assetUri) } ?: previewUri
    val activeVideoEffects = remember(cropZoom, rotationDegrees, outputPreset, selectedPreviewVideoClip, speed) {
        if (selectedPreviewVideoClip != null) {
            buildKeyframedVideoEffects(selectedPreviewVideoClip!!, cropZoom, rotationDegrees, outputPreset, speed)
        } else {
            buildVideoEffects(cropZoom, rotationDegrees, outputPreset)
        }
    }
    var audioVolume by remember { mutableFloatStateOf(1f) }
    var trackingMode by remember { mutableStateOf("Object") }
    var targetLocked by remember { mutableStateOf(false) }
    var targetCenterX by remember { mutableFloatStateOf(0.5f) }
    var targetCenterY by remember { mutableFloatStateOf(0.5f) }
    var trackingMessage by remember { mutableStateOf("Tap the video preview to place the tracking area.") }
    var trackingRunning by remember { mutableStateOf(false) }
    var trackingProgress by remember { mutableFloatStateOf(0f) }
    var trackingResult by remember { mutableStateOf<MotionTrackingResult?>(null) }
    val trackingExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    DisposableEffect(trackingExecutor) {
        onDispose { trackingExecutor.shutdownNow() }
    }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Workspace", color = Grey, fontSize = 13.sp)
                Text("Import media, arrange clips, then export.", color = Grey, fontSize = 11.sp)
            }
            Card(Modifier.fillMaxWidth().aspectRatio(16f/9f), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = PreviewBg)) {
                if (activePreviewUri != null) {
                    Box(Modifier.fillMaxSize()) {
                        MediaPreviewPlayer(
                            uri = activePreviewUri,
                            speed = speed,
                            modifier = Modifier.fillMaxSize(),
                            videoEffects = activeVideoEffects,
                            volume = audioVolume,
                            animationClip = selectedPreviewVideoClip
                        )
                        if (tool == "Tracking") {
                            val regionSize = if (trackingMode == "Point") 0.10f else 0.18f
                            BoxWithConstraints(
                                Modifier.fillMaxSize().pointerInput(trackingMode) {
                                    detectTapGestures { offset ->
                                        targetCenterX = (offset.x / size.width).coerceIn(0.08f, 0.92f)
                                        targetCenterY = (offset.y / size.height).coerceIn(0.08f, 0.92f)
                                        targetLocked = false
                                        trackingResult = null
                                        trackingMessage = "Tracking area moved. Lock it to start analysis."
                                    }
                                }
                            ) {
                                val markerWidth = maxWidth * regionSize
                                val markerHeight = maxHeight * regionSize
                                Box(
                                    Modifier.offset(
                                        x = maxWidth * targetCenterX - markerWidth / 2,
                                        y = maxHeight * targetCenterY - markerHeight / 2
                                    ).size(markerWidth, markerHeight)
                                        .border(2.dp, Green, RoundedCornerShape(7.dp))
                                )
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Movie, null, tint = Color(0xFFD7DED8), modifier = Modifier.size(34.dp))
                            Text("Import a video to preview", color = Paper, fontSize = 13.sp)
                            Text("Your clips will play here", color = Color(0xFFBEC7BF), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        item {
            TimelineEditorPanel(
                document = timeline,
                assets = assets,
                onDocumentChange = changeTimeline,
                onSelectedClipChange = { selectedPreviewClip = it }
            )
        }
        item {
            Text("Editing tools", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            val tools = listOf(
                "Trim" to Icons.Default.ContentCut,
                "Crop" to Icons.Default.Crop, "Rotate" to Icons.Default.RotateRight, "Resize" to Icons.Default.AspectRatio, "Speed" to Icons.Default.Speed,
                "Audio" to Icons.Default.GraphicEq, "Text" to Icons.Default.TextFields,
                "Subtitles" to Icons.Default.Subtitles, "Adjust" to Icons.Default.Tune,
                "Tracking" to Icons.Default.GpsFixed
            )
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxWidth().height(220.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = false) {
                items(tools.size) { i -> ToolTile(tools[i].first, tools[i].second, tools[i].first == tool) { selectTool(tools[i].first) } }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(15.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(tool + " controls", fontWeight = FontWeight.SemiBold)
                    when(tool) {
                        "Trim" -> {
                            Text("In point: " + trimIn.toInt() + "%", color = Grey, fontSize = 12.sp)
                            Slider(trimIn, changeIn, valueRange = 0f..99f)
                            Text("Out point: " + trimOut.toInt() + "%", color = Grey, fontSize = 12.sp)
                            Slider(trimOut, changeOut, valueRange = 1f..100f)
                        }
                        "Crop" -> {
                            Text("Center crop · ${String.format("%.2f", cropZoom)}×", color = Green, fontWeight = FontWeight.SemiBold)
                            Slider(value = cropZoom, onValueChange = changeCropZoom, valueRange = 1f..2.5f)
                            Text("Zoom in to crop more of the frame.", color = Grey, fontSize = 11.sp)
                            TextButton(onClick = { changeCropZoom(1f) }) { Text("Reset crop") }
                        }
                        "Rotate" -> {
                            Text("Rotation · ${rotationDegrees}°", color = Green, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(0, 90, 180, 270).forEach { degrees ->
                                    FilterChip(selected = rotationDegrees == degrees, onClick = { changeRotationDegrees(degrees) }, label = { Text("${degrees}°") })
                                }
                            }
                        }
                        "Resize" -> {
                            Text("Export resolution", color = Grey, fontSize = 12.sp)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("Original", "480p", "720p", "1080p").forEach { preset ->
                                    FilterChip(selected = outputPreset == preset, onClick = { changeOutputPreset(preset) }, label = { Text(preset) })
                                }
                            }
                            Text("Resolution is applied during export. Smaller output can reduce file size.", color = Grey, fontSize = 11.sp)
                        }
                        "Speed" -> {
                            Text(String.format("%.2f", speed) + "×", color = Green, fontWeight = FontWeight.Bold)
                            Slider(speed, changeSpeed, valueRange = 0.25f..3f)
                        }
                        "Split" -> {
                            Text("Place a split marker at the playhead.", color = Grey, fontSize = 12.sp)
                            OutlinedButton(onClick = split) { Icon(Icons.AutoMirrored.Filled.CallSplit, null); Text("Add split marker") }
                        }
                        "Audio" -> {
                            Text("Preview mixer", color = Green, fontWeight = FontWeight.SemiBold)
                            Text("Adjust the source clip playback level while editing.", color = Grey, fontSize = 12.sp)
                            Text("Volume · ${(audioVolume * 100).toInt()}%", color = Grey, fontSize = 12.sp)
                            Slider(
                                value = audioVolume,
                                onValueChange = { audioVolume = it.coerceIn(0f, 1f) },
                                valueRange = 0f..1f
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { audioVolume = 0f }) {
                                    Icon(Icons.Default.VolumeOff, null)
                                    Text("Mute")
                                }
                                OutlinedButton(onClick = { audioVolume = 1f }) {
                                    Icon(Icons.Default.VolumeUp, null)
                                    Text("Reset")
                                }
                            }
                            Text("Preview volume affects playback only. Clip and track mixing, mute and export fades are available in the timeline and export controls.", color = Grey, fontSize = 11.sp)
                        }
                        "Tracking" -> {
                            val supportedMode = trackingMode == "Object" || trackingMode == "Point"
                            Text("Motion Tracker", fontWeight = FontWeight.SemiBold, color = Green)
                            Text("Tap the preview to place the target box. Lock it, then analyze the imported clip locally.", color = Grey, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Object", "Point", "Face", "Camera").forEach { mode ->
                                    FilterChip(selected = trackingMode == mode, enabled = mode == "Object" || mode == "Point", onClick = {
                                        trackingMode = mode
                                        targetLocked = false
                                        trackingResult = null
                                        trackingMessage = if (mode == "Object" || mode == "Point")
                                            mode + " tracking ready. Tap the preview to position the target."
                                        else mode + " tracking is planned; automatic tracking currently supports Object and Point."
                                    }, label = { Text(mode, fontSize = 11.sp) })
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.GpsFixed, null, tint = if (targetLocked) Green else Grey)
                                Column(Modifier.weight(1f)) {
                                    Text(if (targetLocked) "Target locked" else "Target not locked", fontWeight = FontWeight.Medium)
                                    Text(trackingMessage, color = Grey, fontSize = 11.sp)
                                }
                                OutlinedButton(onClick = {
                                    targetLocked = !targetLocked
                                    trackingMessage = if (targetLocked) "Target locked at ${(targetCenterX * 100).toInt()}%, ${(targetCenterY * 100).toInt()}%." else "Target unlocked."
                                }, enabled = previewUri != null) { Text(if (targetLocked) "Unlock" else "Lock") }
                            }
                            if (trackingRunning) {
                                LinearProgressIndicator(progress = { trackingProgress }, modifier = Modifier.fillMaxWidth())
                                Text("Analyzing frames: ${(trackingProgress * 100).toInt()}%", color = Grey, fontSize = 11.sp)
                            }
                            Button(
                                enabled = previewUri != null && targetLocked && !trackingRunning && supportedMode,
                                onClick = {
                                    val selectedUri = previewUri ?: return@Button
                                    trackingRunning = true
                                    trackingProgress = 0f
                                    trackingResult = null
                                    trackingMessage = "Decoding video frames..."
                                    val regionSize = if (trackingMode == "Point") 0.10f else 0.18f
                                    trackingExecutor.execute {
                                        runCatching {
                                            MotionTrackingEngine.trackVideo(
                                                context,
                                                selectedUri,
                                                TrackingRegion(targetCenterX, targetCenterY, regionSize, regionSize)
                                            ) { completed, total ->
                                                mainHandler.post { trackingProgress = completed.toFloat() / total.coerceAtLeast(1) }
                                            }
                                        }.onSuccess { result ->
                                            mainHandler.post {
                                                trackingResult = result
                                                trackingRunning = false
                                                trackingProgress = 1f
                                                trackingMessage = result.message
                                            }
                                        }.onFailure { error ->
                                            mainHandler.post {
                                                trackingRunning = false
                                                trackingMessage = error.message ?: "Tracking failed."
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.GpsFixed, null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (trackingRunning) "Tracking..." else "Track video automatically")
                            }
                            trackingResult?.let { result ->
                                Text(
                                    "${result.points.size} keyframes · ${(result.averageConfidence * 100).toInt()}% average confidence" +
                                        (result.lostAtMs?.let { " · lost at ${it} ms" } ?: ""),
                                    color = Green,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text("Backend: ${result.backend} · Frames decoded: ${result.framesAnalyzed}", color = Grey, fontSize = 11.sp)
                            }
                            Text(
                                if (!supportedMode) "Face and camera tracking are future modes. Automatic patch tracking is currently available for Object and Point."
                                else "Runs offline. GPU compute is used when GLES 3.1 compute works on this device; otherwise it falls back to CPU. Face and 3D camera solving are separate upgrades.",
                                color = Grey,
                                fontSize = 11.sp
                            )
                        }
                        else -> Text("Choose a clip or add a track in the timeline to continue.", color = Grey, fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Text("Add media") }
                Button(onClick = { selectTool("Trim") }, modifier = Modifier.weight(1f)) { Text("Edit clip") }
            }
        }
    }
}

@Composable
private fun TimelineEditorPanel(
    document: TimelineDocument,
    assets: List<Asset>,
    onDocumentChange: (TimelineDocument) -> Unit,
    onSelectedClipChange: (TimelineClip?) -> Unit
) {
    var selectedTrackId by remember(document.tracks.firstOrNull()?.id) {
        mutableStateOf(document.tracks.firstOrNull()?.id.orEmpty())
    }
    var selectedClipId by remember { mutableStateOf<String?>(null) }
    var pixelsPerSecond by remember { mutableFloatStateOf(120f) }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val context = LocalContext.current
    val videoAsset = assets.firstOrNull { context.contentResolver.getType(it.uri)?.startsWith("video/") == true }
    val audioAsset = assets.firstOrNull { context.contentResolver.getType(it.uri)?.startsWith("audio/") == true }
    val selectedTrack = document.tracks.firstOrNull { it.id == selectedTrackId }
    val selectedClip = selectedTrack?.clips?.firstOrNull { it.id == selectedClipId }
    LaunchedEffect(selectedClip) { onSelectedClipChange(selectedClip) }
    var subtitleImportMessage by remember { mutableStateOf("") }
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val track = document.tracks.firstOrNull { it.id == selectedTrackId && it.type == TimelineTrackType.TEXT }
        if (uri != null && track != null) {
            val imported = runCatching {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { parseSrt(it.readText()) }
                    ?: emptyList()
            }
            imported.onSuccess { cues ->
                if (cues.isEmpty()) {
                    subtitleImportMessage = "No valid SRT subtitle cues found."
                } else {
                    onDocumentChange(document.addSubtitleCues(track.id, cues))
                    subtitleImportMessage = "Imported " + cues.size + " subtitle cues."
                }
            }.onFailure { subtitleImportMessage = "Could not read this subtitle file." }
        }
    }

    fun updateSelectedClip(transform: (TimelineClip) -> TimelineClip) {
        val track = selectedTrack ?: return
        val clip = selectedClip ?: return
        onDocumentChange(document.copy(tracks = document.tracks.map { current ->
            if (current.id != track.id) current else current.copy(
                clips = current.clips.map { item -> if (item.id == clip.id) transform(item) else item }
            )
        }))
    }

    fun updateSelectedTextClip(transform: (TimelineClip) -> TimelineClip) =
        updateSelectedClip(transform)

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Timeline", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text("Duration ${document.durationMs / 1000}s", color = Grey, fontSize = 12.sp)
        }
        Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(15.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Zoom", color = Grey, fontSize = 11.sp)
                    Slider(value = pixelsPerSecond, onValueChange = { pixelsPerSecond = it }, valueRange = 60f..220f, modifier = Modifier.weight(1f))
                    Text("${pixelsPerSecond.toInt()} px/s", color = Grey, fontSize = 10.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TimelineTrackType.values().forEach { type ->
                        OutlinedButton(
                            onClick = {
                                val updated = document.addTrack(type)
                                onDocumentChange(updated)
                                selectedTrackId = updated.tracks.last().id
                                selectedClipId = null
                            },
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp)
                        ) { Text("+ ${type.name.lowercase().replaceFirstChar { it.uppercase() }}", fontSize = 10.sp) }
                    }
                }
                if (selectedTrack?.type in setOf(TimelineTrackType.VIDEO, TimelineTrackType.AUDIO) && selectedClip != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAF7))) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("Clip audio mixer", fontWeight = FontWeight.SemiBold, color = Green)
                            Text("Volume · ${(selectedClip.volume * 100).toInt()}%", color = Grey, fontSize = 11.sp)
                            Slider(
                                value = selectedClip.volume.coerceIn(0f, 1f),
                                onValueChange = { volume -> updateSelectedClip { it.copy(volume = volume.coerceIn(0f, 1f)) } },
                                valueRange = 0f..1f
                            )
                            FilterChip(
                                selected = selectedClip.muted,
                                onClick = { updateSelectedClip { it.copy(muted = !it.muted) } },
                                label = { Text(if (selectedClip.muted) "Clip muted" else "Mute clip") },
                                leadingIcon = { Icon(if (selectedClip.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, null) }
                            )
                            Text("These settings are applied to the final timeline export.", color = Grey, fontSize = 10.sp)
                        }
                    }
                }
                if (selectedClip != null && selectedTrack?.locked == false) {
                    var keyframeProperty by remember(selectedClip.id) { mutableStateOf(KeyframeProperty.POSITION_X) }
                    var keyframeEasing by remember(selectedClip.id) { mutableStateOf(KeyframeEasing.LINEAR) }
                    var keyframeTimeMs by remember(selectedClip.id) { mutableLongStateOf(0L) }
                    var keyframeValue by remember(selectedClip.id) { mutableFloatStateOf(0f) }
                    var propertyMenu by remember { mutableStateOf(false) }
                    var easingMenu by remember { mutableStateOf(false) }
                    val propertyOptions = KeyframeProperty.values()
                    val matchingKeyframes = selectedClip.keyframes.filter { it.property == keyframeProperty }.sortedBy { it.timeMs }
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAF7))) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Keyframe editor", fontWeight = FontWeight.SemiBold, color = Green)
                                Text("${selectedClip.keyframes.size} diamonds", color = Grey, fontSize = 10.sp)
                            }
                            Box {
                                OutlinedButton(onClick = { propertyMenu = true }) {
                                    Text(keyframeProperty.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() })
                                    Icon(Icons.Default.ArrowDropDown, null)
                                }
                                DropdownMenu(expanded = propertyMenu, onDismissRequest = { propertyMenu = false }) {
                                    propertyOptions.forEach { property ->
                                        DropdownMenuItem(
                                            text = { Text(property.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) },
                                            onClick = {
                                                keyframeProperty = property
                                                matchingKeyframes.firstOrNull()?.let {
                                                    keyframeTimeMs = it.timeMs
                                                    keyframeValue = it.value
                                                    keyframeEasing = it.easing
                                                }
                                                propertyMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                            Text("Keyframe time · ${(keyframeTimeMs / 1000f)}s", color = Grey, fontSize = 11.sp)
                            Slider(
                                value = keyframeTimeMs.toFloat().coerceIn(0f, selectedClip.durationMs.toFloat().coerceAtLeast(1f)),
                                onValueChange = { keyframeTimeMs = it.toLong() },
                                valueRange = 0f..selectedClip.durationMs.toFloat().coerceAtLeast(1f)
                            )
                            Text("Value · ${"%.3f".format(keyframeValue)}", color = Grey, fontSize = 11.sp)
                            Slider(value = keyframeValue.coerceIn(-100f, 100f), onValueChange = { keyframeValue = it }, valueRange = -100f..100f)
                            Box {
                                OutlinedButton(onClick = { easingMenu = true }) {
                                    Text("Easing · ${keyframeEasing.name.replace('_', ' ').lowercase()}")
                                    Icon(Icons.Default.ArrowDropDown, null)
                                }
                                DropdownMenu(expanded = easingMenu, onDismissRequest = { easingMenu = false }) {
                                    KeyframeEasing.values().forEach { easing ->
                                        DropdownMenuItem(
                                            text = { Text(easing.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) },
                                            onClick = { keyframeEasing = easing; easingMenu = false }
                                        )
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = {
                                        onDocumentChange(document.setKeyframe(
                                            selectedTrack.id, selectedClip.id,
                                            AnimationKeyframe(keyframeProperty, keyframeTimeMs, keyframeValue, keyframeEasing)
                                        ))
                                    }
                                ) { Icon(Icons.Default.Add, null); Text("Add / update") }
                                OutlinedButton(
                                    enabled = selectedClip.keyframes.any { it.property == keyframeProperty && it.timeMs == keyframeTimeMs },
                                    onClick = {
                                        onDocumentChange(document.removeKeyframe(selectedTrack.id, selectedClip.id, keyframeProperty, keyframeTimeMs))
                                    }
                                ) { Icon(Icons.Default.DeleteOutline, null); Text("Delete") }
                            }
                            if (matchingKeyframes.isNotEmpty()) {
                                Text("Diamonds · tap to select a keyframe", color = Grey, fontSize = 10.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    matchingKeyframes.forEach { marker ->
                                        FilterChip(
                                            selected = marker.timeMs == keyframeTimeMs,
                                            onClick = {
                                                keyframeTimeMs = marker.timeMs
                                                keyframeValue = marker.value
                                                keyframeEasing = marker.easing
                                            },
                                            label = { Text("◆ ${marker.timeMs / 1000f}s", fontSize = 10.sp) }
                                        )
                                    }
                                }
                            }
                            Text("Transform keyframes animate the selected video in preview and timeline export.", color = Grey, fontSize = 10.sp)
                        }
                    }
                }
                if (selectedTrack?.type == TimelineTrackType.TEXT && selectedClip != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAF7))) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Text & subtitle editor", fontWeight = FontWeight.SemiBold, color = Green)
                            OutlinedTextField(
                                value = selectedClip.label,
                                onValueChange = { value -> updateSelectedTextClip { it.copy(label = value.take(240)) } },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Text content") },
                                minLines = 1,
                                maxLines = 3
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("TITLE", "SUBTITLE").forEach { role ->
                                    FilterChip(
                                        selected = selectedClip.textRole == role,
                                        onClick = {
                                            updateSelectedTextClip {
                                                it.copy(
                                                    textRole = role,
                                                    textSizeScale = if (role == "SUBTITLE") 0.042f else 0.06f,
                                                    textPosition = if (role == "SUBTITLE") "BOTTOM" else it.textPosition
                                                )
                                            }
                                        },
                                        label = { Text(if (role == "TITLE") "Title" else "Subtitle") }
                                    )
                                }
                                FilterChip(
                                    selected = selectedClip.textBold,
                                    onClick = { updateSelectedTextClip { it.copy(textBold = !it.textBold) } },
                                    label = { Text("Bold") }
                                )
                            }
                            Text("Font size · " + (selectedClip.textSizeScale * 100).toInt() + "% of frame", color = Grey, fontSize = 11.sp)
                            Slider(
                                value = selectedClip.textSizeScale,
                                onValueChange = { size -> updateSelectedTextClip { it.copy(textSizeScale = size) } },
                                valueRange = 0.025f..0.12f
                            )
                            Text("Text color", color = Grey, fontSize = 11.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    "White" to 0xFFFFFFFF.toInt(),
                                    "Yellow" to 0xFFFFD54F.toInt(),
                                    "Black" to 0xFF171717.toInt()
                                ).forEach { (name, colorValue) ->
                                    FilterChip(
                                        selected = selectedClip.textColor == colorValue,
                                        onClick = { updateSelectedTextClip { it.copy(textColor = colorValue) } },
                                        label = { Text(name) }
                                    )
                                }
                            }
                            Text("Position", color = Grey, fontSize = 11.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("TOP" to "Top", "CENTER" to "Center", "BOTTOM" to "Bottom").forEach { (value, label) ->
                                    FilterChip(
                                        selected = selectedClip.textPosition == value,
                                        onClick = { updateSelectedTextClip { it.copy(textPosition = value) } },
                                        label = { Text(label) }
                                    )
                                }
                            }
                            FilterChip(
                                selected = selectedClip.textBackgroundColor != 0,
                                onClick = {
                                    updateSelectedTextClip {
                                        it.copy(textBackgroundColor = if (it.textBackgroundColor == 0) 0x99000000.toInt() else 0)
                                    }
                                },
                                label = { Text("Background") }
                            )
                            Text(
                                "Timing: " + (selectedClip.startMs / 1000f) + "s – " + (selectedClip.endMs / 1000f) + "s. Drag or trim this clip on the Text track.",
                                color = Grey,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                document.tracks.forEach { track ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { selectedTrackId = track.id; selectedClipId = null },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text(track.name + if (track.locked) " · Locked" else "", color = if (selectedTrackId == track.id) Green else Ink, fontSize = 12.sp)
                            }
                            Spacer(Modifier.weight(1f))
                            Text("${track.clips.size} clips", color = Grey, fontSize = 10.sp)
                            if (track.type != TimelineTrackType.TEXT) {
                                IconButton(
                                    onClick = {
                                        onDocumentChange(document.copy(tracks = document.tracks.map {
                                            if (it.id == track.id) it.copy(muted = !it.muted) else it
                                        }))
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        if (track.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        if (track.muted) "Unmute track" else "Mute track",
                                        tint = if (track.muted) Green else Grey,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            IconButton(onClick = {
                                onDocumentChange(document.copy(tracks = document.tracks.map {
                                    if (it.id == track.id) it.copy(locked = !it.locked) else it
                                }))
                            }, modifier = Modifier.size(30.dp)) {
                                Icon(if (track.locked) Icons.Default.Lock else Icons.Default.LockOpen, "Toggle track lock", tint = Grey, modifier = Modifier.size(16.dp))
                            }
                        }
                        if (track.clips.isEmpty()) {
                            Box(Modifier.fillMaxWidth().height(34.dp).background(Color(0xFFF3F4F1), RoundedCornerShape(7.dp)).padding(8.dp)) {
                                Text("Empty track", color = Grey, fontSize = 10.sp)
                            }
                        } else {
                            val timelineWidth = (document.durationMs / 1000f * pixelsPerSecond + 48f).coerceAtLeast(320f)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Box(
                                    Modifier
                                        .width(timelineWidth.dp)
                                        .height(58.dp)
                                        .background(Color(0xFFF7F8F6), RoundedCornerShape(7.dp))
                                ) {
                                    track.clips.forEach { clip ->
                                        val isSelected = selectedClipId == clip.id && selectedTrackId == track.id
                                        val clipWidth = (clip.durationMs / 1000f * pixelsPerSecond).coerceAtLeast(48f)
                                        Surface(
                                            onClick = { selectedTrackId = track.id; selectedClipId = clip.id },
                                            modifier = Modifier
                                                .offset(x = (clip.startMs / 1000f * pixelsPerSecond).dp, y = 3.dp)
                                                .width(clipWidth.dp)
                                                .height(52.dp)
                                                .then(
                                                    if (track.locked) Modifier else Modifier.pointerInput(clip.id, track.id, pixelsPerSecond) {
                                                        var totalDragPx = 0f
                                                        detectDragGestures(
                                                            onDragStart = { totalDragPx = 0f },
                                                            onDragCancel = { totalDragPx = 0f },
                                                            onDragEnd = { totalDragPx = 0f }
                                                        ) { change, dragAmount ->
                                                            change.consume()
                                                            totalDragPx += dragAmount.x
                                                            val deltaMs = (totalDragPx / density / pixelsPerSecond * 1000f).toLong()
                                                            selectedTrackId = track.id
                                                            selectedClipId = clip.id
                                                            onDocumentChange(document.moveClipMagnetic(track.id, clip.id, clip.startMs + deltaMs, 150L))
                                                        }
                                                    }
                                                ),
                                            shape = RoundedCornerShape(7.dp),
                                            color = if (isSelected) SoftGreen else Color(0xFFF0F1EF),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Green else Edge)
                                        ) {
                                            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    Modifier
                                                        .width(12.dp)
                                                        .fillMaxHeight()
                                                        .background(if (isSelected) Green.copy(alpha = 0.22f) else Color(0xFFE1E5E0))
                                                        .then(if (track.locked) Modifier else Modifier.pointerInput(clip.id, pixelsPerSecond) {
                                                            var totalDragPx = 0f
                                                            detectDragGestures(onDragStart = { totalDragPx = 0f }) { change, dragAmount ->
                                                                change.consume()
                                                                totalDragPx += dragAmount.x
                                                                val deltaMs = (totalDragPx / density / pixelsPerSecond * 1000f).toLong()
                                                                selectedTrackId = track.id
                                                                selectedClipId = clip.id
                                                                onDocumentChange(document.trimClipStart(track.id, clip.id, clip.startMs + deltaMs))
                                                            }
                                                        })
                                                )
                                                Column(Modifier.weight(1f).padding(horizontal = 5.dp)) {
                                                    Text(clip.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                                    Text("${clip.startMs / 1000f}s · ${clip.durationMs / 1000f}s", color = Grey, fontSize = 9.sp)
                                                }
                                                Box(
                                                    Modifier
                                                        .width(12.dp)
                                                        .fillMaxHeight()
                                                        .background(if (isSelected) Green.copy(alpha = 0.22f) else Color(0xFFE1E5E0))
                                                        .then(if (track.locked) Modifier else Modifier.pointerInput(clip.id, pixelsPerSecond) {
                                                            var totalDragPx = 0f
                                                            detectDragGestures(onDragStart = { totalDragPx = 0f }) { change, dragAmount ->
                                                                change.consume()
                                                                totalDragPx += dragAmount.x
                                                                val deltaMs = (totalDragPx / density / pixelsPerSecond * 1000f).toLong()
                                                                selectedTrackId = track.id
                                                                selectedClipId = clip.id
                                                                onDocumentChange(document.trimClipEnd(track.id, clip.id, clip.endMs + deltaMs))
                                                            }
                                                        })
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        enabled = selectedTrack != null && !selectedTrack.locked,
                        onClick = {
                            val track = selectedTrack ?: return@OutlinedButton
                            val asset = when (track.type) {
                                TimelineTrackType.AUDIO -> audioAsset
                                TimelineTrackType.VIDEO -> videoAsset
                                TimelineTrackType.TEXT -> null
                            }
                            if (track.type == TimelineTrackType.TEXT || asset != null) {
                                val clip = TimelineClip(
                                    assetUri = asset?.uri?.toString() ?: "text://title",
                                    label = if (track.type == TimelineTrackType.TEXT) "New title" else asset?.name ?: "Clip",
                                    startMs = document.durationMs,
                                    durationMs = 5_000L,
                                    textRole = "TITLE"
                                )
                                val updated = document.addClip(track.id, clip)
                                onDocumentChange(updated)
                                selectedClipId = clip.id
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp)
                    ) { Text("+ Add clip", fontSize = 10.sp) }
                    if (selectedTrack?.type == TimelineTrackType.TEXT) {
                        OutlinedButton(
                            enabled = !selectedTrack.locked,
                            onClick = {
                                val track = selectedTrack
                                if (track != null) {
                                    val clip = TimelineClip(
                                        assetUri = "text://subtitle",
                                        label = "New subtitle",
                                        startMs = document.durationMs,
                                        durationMs = 3_000L,
                                        textRole = "SUBTITLE",
                                        textSizeScale = 0.042f,
                                        textPosition = "BOTTOM"
                                    )
                                    onDocumentChange(document.addClip(track.id, clip))
                                    selectedClipId = clip.id
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp)
                        ) { Text("+ Subtitle", fontSize = 10.sp) }
                        OutlinedButton(
                            enabled = !selectedTrack.locked,
                            onClick = { subtitlePicker.launch(arrayOf("*/*")) },
                            contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp)
                        ) { Text("Import SRT", fontSize = 10.sp) }
                    }
                    OutlinedButton(
                        enabled = selectedClip != null && selectedTrack?.locked == false,
                        onClick = {
                            val clip = selectedClip ?: return@OutlinedButton
                            val track = selectedTrack ?: return@OutlinedButton
                            onDocumentChange(document.splitClip(track.id, clip.id, clip.startMs + clip.durationMs / 2))
                        },
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp)
                    ) { Text("Split", fontSize = 10.sp) }
                    OutlinedButton(
                        enabled = selectedClip != null && selectedTrack?.locked == false,
                        onClick = {
                            val clip = selectedClip ?: return@OutlinedButton
                            val track = selectedTrack ?: return@OutlinedButton
                            onDocumentChange(document.moveClipMagnetic(track.id, clip.id, clip.startMs + 500L, 300L))
                        },
                        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp)
                    ) { Text("Magnetic move", fontSize = 10.sp) }
                    IconButton(
                        enabled = selectedClip != null && selectedTrack?.locked == false,
                        onClick = {
                            val clip = selectedClip ?: return@IconButton
                            val track = selectedTrack ?: return@IconButton
                            onDocumentChange(document.rippleRemoveClip(track.id, clip.id))
                            selectedClipId = null
                        }
                    ) { Icon(Icons.Default.DeleteOutline, "Ripple delete clip", tint = Grey) }
                }
                if (subtitleImportMessage.isNotBlank()) {
                    Text(subtitleImportMessage, color = Green, fontSize = 10.sp)
                }
                Text("Drag klip untuk alih posisi. Tarik pemegang kiri/kanan untuk trim. Zoom mengubah skala masa.", color = Grey, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ToolTile(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(12.dp), color = if (active) SoftGreen else Paper, border = androidx.compose.foundation.BorderStroke(1.dp, if (active) Color(0xFFBFD1C4) else Edge)) {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (active) Green else Grey, modifier = Modifier.size(20.dp))
            Text(label, fontSize = 10.sp, color = Ink)
        }
    }
}

@Composable
private fun MediaScreen(modifier: Modifier, assets: List<Asset>, onImport: () -> Unit) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("Media library", fontSize = 21.sp, fontWeight = FontWeight.Bold); Text(assets.size.toString() + " items", color = Grey, fontSize = 12.sp) }
            Button(onClick = onImport) { Icon(Icons.Default.Add, null); Text("Import") }
        }
        if (assets.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, null, tint = Green, modifier = Modifier.size(42.dp))
                    Text("Your media starts here", fontWeight = FontWeight.SemiBold)
                    Text("Import video, images or audio.", color = Grey, fontSize = 12.sp)
                    OutlinedButton(onClick = onImport) { Text("Import media") }
                }
            }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(assets) { asset ->
                Card(colors = CardDefaults.cardColors(containerColor = Paper)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PermMedia, null, tint = Green)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(asset.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(asset.uri.toString(), color = Grey, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectsScreen(
    modifier: Modifier,
    project: String,
    projects: List<SavedProject>,
    onSave: () -> Unit,
    onOpen: (SavedProject) -> Unit,
    onDelete: (Long) -> Unit,
    onNew: () -> Unit
) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("Projects", fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("Local SQLite library · ${projects.size} saved", color = Grey, fontSize = 12.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onSave) { Text("Save") }
                Button(onClick = onNew) { Icon(Icons.Default.Add, null); Text("New") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Paper)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, tint = Green, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column { Text(project, fontWeight = FontWeight.SemiBold); Text("Current editing session", color = Grey, fontSize = 11.sp) }
            }
        }
        if (projects.isEmpty()) {
            Text("Save this project to keep it on this phone.", color = Grey, fontSize = 12.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(projects, key = { it.id }) { item ->
                    Card(colors = CardDefaults.cardColors(containerColor = Paper)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FolderOpen, null, tint = Green)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.Medium)
                                Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.updatedAt)), color = Grey, fontSize = 10.sp)
                            }
                            TextButton(onClick = { onOpen(item) }) { Text("Open") }
                            IconButton(onClick = { onDelete(item.id) }) { Icon(Icons.Default.DeleteOutline, "Delete project", tint = Grey) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DevicesScreen(modifier: Modifier) {
    val context = LocalContext.current
    val usb = remember { context.getSystemService(Context.USB_SERVICE) as UsbManager }
    var count by remember { mutableIntStateOf(usb.deviceList.size) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { count = usb.deviceList.size }
        }
        val filter = IntentFilter().apply { addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED); addAction(UsbManager.ACTION_USB_DEVICE_DETACHED) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Devices", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text("Hardware connections and local render workers.", color = Grey, fontSize = 12.sp)
        val capabilities = remember { DeviceCapabilityDetector.detect(context) }
        var cache by remember { mutableStateOf(CacheManager.report(context)) }
        Card(colors = CardDefaults.cardColors(containerColor = Paper)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Universal SoC & hardware", fontWeight = FontWeight.SemiBold)
                Text("${capabilities.socVendor} · ${capabilities.socName}", color = Green, fontSize = 12.sp)
                Text("${capabilities.manufacturer} ${capabilities.model} · ${capabilities.cpuCores} CPU cores · ${capabilities.cpuAbi}", color = Grey, fontSize = 11.sp)
                Text("RAM ${capabilities.availableRamMb} / ${capabilities.totalRamMb} MB · Vulkan ${if (capabilities.vulkanAvailable) "available" else "not exposed"} · GLES ${capabilities.glesVersion}", color = Grey, fontSize = 11.sp)
                Text("Neural API: ${if (capabilities.neuralApiAvailable) "advertised" else "not advertised"} · Thermal: ${capabilities.thermalStatus}", color = Grey, fontSize = 11.sp)
                Text("Note: NPU availability still requires a compatible vendor runtime and driver.", color = Grey, fontSize = 10.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Paper)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cache", fontWeight = FontWeight.SemiBold)
                Text("${cache.fileCount} files · ${cache.bytesUsed / (1024L * 1024L)} MB used · ${capabilities.freeStorageMb} MB free", color = Grey, fontSize = 11.sp)
                OutlinedButton(onClick = { CacheManager.clearTemporary(context); cache = CacheManager.report(context) }, modifier = Modifier.fillMaxWidth()) { Text("Clear temporary cache") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Paper)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Usb, null, tint = Green)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("USB / OTG", fontWeight = FontWeight.SemiBold)
                        Text(if (count == 0) "No USB device detected" else count.toString() + " device(s) detected", color = Grey, fontSize = 12.sp)
                    }
                    Text(if (count > 0) "Connected" else "Ready", color = Green, fontSize = 11.sp)
                }
                Text("OTG detection only identifies attached USB devices. A second phone needs the NEXORA Worker companion and a supported data protocol before it can render.", color = Grey, fontSize = 12.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Paper)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Hub, null, tint = Green); Spacer(Modifier.width(8.dp)); Text("Local Render Farm", fontWeight = FontWeight.SemiBold) }
                Text("Worker discovery, pairing, job scheduling and chunk rendering are a separate local-network module.", color = Grey, fontSize = 12.sp)
                Text("Designed for local operation without paid cloud.", color = Green, fontSize = 12.sp)
            }
        }
    }
}

private fun safeCropZoom(value: Float): Float = if (value.isFinite()) value.coerceIn(1f, 2.5f) else 1f
private fun safeOutputPreset(value: String): String = if (value in listOf("Original", "480p", "720p", "1080p")) value else "Original"
