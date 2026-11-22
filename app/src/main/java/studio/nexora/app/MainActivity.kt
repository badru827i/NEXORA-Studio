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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.media3.effect.HslAdjustment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import kotlin.math.roundToInt

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
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(0f) }
    var lightness by remember { mutableFloatStateOf(0f) }
    var blendMode by remember { mutableStateOf("Normal") }
    var blendOpacity by remember { mutableFloatStateOf(1f) }
    var speedCurve by remember { mutableStateOf(listOf(1f, 1f, 1f, 1f)) }
    var blendCurve by remember { mutableStateOf(listOf(1f, 1f, 1f, 1f)) }
    var keyframeInterpolation by remember { mutableStateOf("Ease In-Out") }
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
                    outputPreset, { outputPreset = safeOutputPreset(it) },
                    hue, { hue = it.coerceIn(-180f, 180f) }, saturation, { saturation = it.coerceIn(-100f, 100f) },
                    lightness, { lightness = it.coerceIn(-100f, 100f) }, blendMode, { blendMode = safeBlendMode(it) },
                    blendOpacity, { blendOpacity = it.coerceIn(0f, 1f) },
                    speedCurve, { speedCurve = safeCurve(it, 0.25f, 3f) },
                    blendCurve, { blendCurve = safeCurve(it, 0f, 1f) },
                    keyframeInterpolation, { keyframeInterpolation = safeInterpolation(it) }
                )
                Screen.Media -> MediaScreen(Modifier.padding(padding), assets) { picker.launch(arrayOf("video/*", "image/*", "audio/*")) }
                Screen.Projects -> ProjectsScreen(
                    Modifier.padding(padding), project, savedProjects,
                    onSave = {
                        val document = org.json.JSONObject().apply {
                            put("tool", tool); put("speed", speed.toDouble())
                            put("trimIn", trimIn.toDouble()); put("trimOut", trimOut.toDouble()); put("splitCount", splitCount)
                            put("cropZoom", cropZoom.toDouble()); put("rotationDegrees", rotationDegrees); put("outputPreset", outputPreset)
                            put("hue", hue.toDouble()); put("saturation", saturation.toDouble()); put("lightness", lightness.toDouble())
                            put("blendMode", blendMode); put("blendOpacity", blendOpacity.toDouble())
                            put("speedCurve", org.json.JSONArray().apply { speedCurve.forEach { put(it.toDouble()) } })
                            put("blendCurve", org.json.JSONArray().apply { blendCurve.forEach { put(it.toDouble()) } })
                            put("keyframeInterpolation", keyframeInterpolation)
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
                            hue = doc.optDouble("hue", 0.0).toFloat().coerceIn(-180f, 180f)
                            saturation = doc.optDouble("saturation", 0.0).toFloat().coerceIn(-100f, 100f)
                            lightness = doc.optDouble("lightness", 0.0).toFloat().coerceIn(-100f, 100f)
                            blendMode = safeBlendMode(doc.optString("blendMode", "Normal"))
                            blendOpacity = doc.optDouble("blendOpacity", 1.0).toFloat().coerceIn(0f, 1f)
                            speedCurve = readCurve(doc.optJSONArray("speedCurve"), 1f, 0.25f, 3f)
                            blendCurve = readCurve(doc.optJSONArray("blendCurve"), 1f, 0f, 1f)
                            keyframeInterpolation = safeInterpolation(doc.optString("keyframeInterpolation", "Ease In-Out"))
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
                        trimIn = 0f; trimOut = 100f; splitCount = 0; cropZoom = 1f; rotationDegrees = 0; outputPreset = "Original"; hue = 0f; saturation = 0f; lightness = 0f
                        blendMode = "Normal"; blendOpacity = 1f; speedCurve = listOf(1f, 1f, 1f, 1f)
                        blendCurve = listOf(1f, 1f, 1f, 1f); keyframeInterpolation = "Ease In-Out"; screen = Screen.Editor
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
                        exporter.export(selected.uri, trimIn, trimOut, speed, cropZoom, rotationDegrees, outputPreset, hue, saturation, lightness, speedCurve, object : MediaExportManager.Callback {
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
    hue: Float, changeHue: (Float) -> Unit, saturation: Float, changeSaturation: (Float) -> Unit,
    lightness: Float, changeLightness: (Float) -> Unit, blendMode: String, changeBlendMode: (String) -> Unit,
    blendOpacity: Float, changeBlendOpacity: (Float) -> Unit,
    speedCurve: List<Float>, changeSpeedCurve: (List<Float>) -> Unit,
    blendCurve: List<Float>, changeBlendCurve: (List<Float>) -> Unit,
    keyframeInterpolation: String, changeKeyframeInterpolation: (String) -> Unit
) {
    val context = LocalContext.current
    val activeVideoEffects = remember(cropZoom, rotationDegrees, outputPreset, hue, saturation, lightness) {
        buildVideoEffects(cropZoom, rotationDegrees, outputPreset) + HslAdjustment.Builder()
            .adjustHue(hue).adjustSaturation(saturation).adjustLightness(lightness).build()
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
                Row { Icon(Icons.AutoMirrored.Filled.Undo, null, tint = Grey); Spacer(Modifier.width(10.dp)); Icon(Icons.AutoMirrored.Filled.Redo, null, tint = Grey) }
            }
            Card(Modifier.fillMaxWidth().aspectRatio(16f/9f), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = PreviewBg)) {
                if (previewUri != null) {
                    Box(Modifier.fillMaxSize()) {
                        MediaPreviewPlayer(uri = previewUri, speed = speed, speedCurve = speedCurve, modifier = Modifier.fillMaxSize(), videoEffects = activeVideoEffects, volume = audioVolume)
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Timeline", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("00:00 / 00:15", color = Grey, fontSize = 12.sp)
            }
            Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(15.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("00:00","00:05","00:10","00:15").forEach { Text(it, color = Grey, fontSize = 10.sp) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VideoFile, null, tint = Green)
                        Spacer(Modifier.width(8.dp))
                        Row(Modifier.weight(1f).height(40.dp).background(SoftGreen, RoundedCornerShape(8.dp)).border(1.dp, Color(0xFFD5E3D9), RoundedCornerShape(8.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(8) { Box(Modifier.weight(1f).fillMaxHeight().background(if (it % 2 == 0) Color(0xFFD1E0D5) else Color(0xFFE2EBE4), RoundedCornerShape(4.dp))) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, null, tint = Grey)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f).height(22.dp).background(Color(0xFFF0F1EF), RoundedCornerShape(7.dp))) {
                            Box(Modifier.fillMaxWidth(0.65f).fillMaxHeight().background(Color(0xFFE1E7E1), RoundedCornerShape(7.dp)))
                        }
                    }
                    trackingResult?.let { result ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GpsFixed, null, tint = Green)
                            Spacer(Modifier.width(8.dp))
                            Text("Motion track · ${result.points.size} keyframes", color = Green, fontSize = 11.sp)
                        }
                        Row(
                            Modifier.fillMaxWidth().height(22.dp).background(SoftGreen, RoundedCornerShape(7.dp)).padding(horizontal = 5.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            result.points.take(80).forEach {
                                Box(Modifier.size(5.dp).background(Green, CircleShape))
                            }
                        }
                    }
                    Text(if (assets.isEmpty()) "No clip yet · import media to populate timeline" else "Clip 1 · " + (splitCount + 1) + " segment(s)", color = Grey, fontSize = 11.sp)
                }
            }
        }
        item {
            Text("Editing tools", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            val tools = listOf(
                "Trim" to Icons.Default.ContentCut, "Split" to Icons.AutoMirrored.Filled.CallSplit,
                "Crop" to Icons.Default.Crop, "Rotate" to Icons.Default.RotateRight, "Resize" to Icons.Default.AspectRatio, "Speed" to Icons.Default.Speed,
                "Audio" to Icons.Default.GraphicEq, "Text" to Icons.Default.TextFields,
                "Subtitles" to Icons.Default.Subtitles, "Color" to Icons.Default.Tune,
                "Blend" to Icons.Default.Layers, "Keyframes" to Icons.Default.Timeline,
                "Tracking" to Icons.Default.GpsFixed
            )
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxWidth().height(300.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = false) {
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
                            Text("Speed ramp · ${String.format("%.2f", speed)}× base", color = Green, fontWeight = FontWeight.Bold)
                            Slider(speed, { value -> changeSpeed(value); changeSpeedCurve(List(4) { value }) }, valueRange = 0.25f..3f)
                            Text("Drag the two inner points to shape the slow-motion ramp.", color = Grey, fontSize = 11.sp)
                            CurveEditor("Speed curve", speedCurve, 0.25f..3f, "0.25×", "3.0×", changeSpeedCurve)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Slow" to 0.5f, "Normal" to 1f, "Fast" to 2f).forEach { (label, value) ->
                                    OutlinedButton(onClick = { changeSpeed(value); changeSpeedCurve(List(4) { value }) }) { Text(label) }
                                }
                            }
                        }
                        "Color" -> {
                            Text("HSL color grading", color = Green, fontWeight = FontWeight.SemiBold)
                            Text("Hue · ${hue.toInt()}°", color = Grey, fontSize = 12.sp)
                            Slider(hue, { changeHue(it.coerceIn(-180f, 180f)) }, valueRange = -180f..180f)
                            Text("Saturation · ${saturation.toInt()}", color = Grey, fontSize = 12.sp)
                            Slider(saturation, { changeSaturation(it.coerceIn(-100f, 100f)) }, valueRange = -100f..100f)
                            Text("Lightness · ${lightness.toInt()}", color = Grey, fontSize = 12.sp)
                            Slider(lightness, { changeLightness(it.coerceIn(-100f, 100f)) }, valueRange = -100f..100f)
                            TextButton(onClick = { changeHue(0f); changeSaturation(0f); changeLightness(0f) }) { Text("Reset HSL") }
                            Text("HSL is applied to the preview and video export.", color = Grey, fontSize = 11.sp)
                        }
                        "Blend" -> {
                            Text("Blend mode", color = Green, fontWeight = FontWeight.SemiBold)
                            val blendModes = listOf("Normal", "Multiply", "Screen", "Overlay", "Soft Light")
                            blendModes.chunked(3).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    row.forEach { mode -> FilterChip(selected = blendMode == mode, onClick = { changeBlendMode(mode) }, label = { Text(mode, fontSize = 11.sp) }) }
                                }
                            }
                            Text("Opacity · ${(blendOpacity * 100).toInt()}%", color = Grey, fontSize = 12.sp)
                            Slider(blendOpacity, { changeBlendOpacity(it.coerceIn(0f, 1f)) }, valueRange = 0f..1f)
                            CurveEditor("Blend opacity graph", blendCurve, 0f..1f, "0%", "100%", changeBlendCurve)
                            Text("Blend settings are saved with the project. True clip-to-clip compositing needs an overlay track in the next timeline milestone.", color = Grey, fontSize = 11.sp)
                        }
                        "Keyframes" -> {
                            Text("Keyframe interpolation", color = Green, fontWeight = FontWeight.SemiBold)
                            listOf("Linear", "Ease In", "Ease Out", "Ease In-Out", "Bezier").forEach { mode ->
                                FilterChip(selected = keyframeInterpolation == mode, onClick = { changeKeyframeInterpolation(mode) }, label = { Text(mode) })
                            }
                            Text("The editable speed and blend graphs use draggable control points. Bezier handles and per-property keyframe tracks are a follow-on engine task.", color = Grey, fontSize = 11.sp)
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
                            Text("This control changes preview playback only. Track mixing, fades and final-export gain are the next audio-engine stage.", color = Grey, fontSize = 11.sp)
                        }
                        "Tracking" -> {
                            val supportedMode = trackingMode == "Object" || trackingMode == "Point"
                            Text("Motion Tracker", fontWeight = FontWeight.SemiBold, color = Green)
                            Text("Tap the preview to place the target box. Lock it, then analyze the imported clip locally.", color = Grey, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Object", "Point", "Face", "Camera").forEach { mode ->
                                    FilterChip(selected = trackingMode == mode, onClick = {
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
                        else -> Text(when(tool) {
                            "Crop" -> "Crop, resize and rotation controls will be added to the transform panel."
                            "Audio" -> "Audio volume, fades and waveform controls will be added to the mixer."
                            "Text" -> "Text overlays and title templates are planned."
                            "Subtitles" -> "Subtitle tracks and timing tools are planned."
                            else -> "Color, exposure and look controls are planned."
                        }, color = Grey, fontSize = 12.sp)
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
