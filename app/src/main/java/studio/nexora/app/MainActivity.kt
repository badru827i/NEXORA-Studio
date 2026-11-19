package studio.nexora.app

import android.content.*
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat

private val Bg = Color(0xFFF5F5F2)
private val Paper = Color.White
private val Ink = Color(0xFF20231F)
private val Grey = Color(0xFF777D76)
private val Edge = Color(0xFFE7E9E4)
private val Green = Color(0xFF416B55)
private val SoftGreen = Color(0xFFEAF1EC)
private val PreviewBg = Color(0xFF292D2A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NexoraApp() }
    }
}
private data class Asset(val uri: Uri, val name: String)
private enum class Screen(val label: String) { Editor("Edit"), Media("Media"), Projects("Projects"), Devices("Devices"), ThreeD("3D") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NexoraApp() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(Screen.Editor) }
    var project by remember { mutableStateOf("Untitled project") }
    val assets = remember { mutableStateListOf<Asset>() }
    var tool by remember { mutableStateOf("Trim") }
    var speed by remember { mutableFloatStateOf(1f) }
    var trimIn by remember { mutableFloatStateOf(0f) }
    var trimOut by remember { mutableFloatStateOf(100f) }
    var splitCount by remember { mutableIntStateOf(0) }
    var exportDialog by remember { mutableStateOf(false) }
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
                            Screen.ThreeD -> Icons.Default.ViewInAr
                        }
                        NavigationBarItem(selected = screen == item, onClick = { screen = item }, icon = { Icon(icon, item.label) }, label = { Text(item.label, fontSize = 10.sp) })
                    }
                }
            }
        ) { padding ->
            when(screen) {
                Screen.Editor -> EditorScreen(
                    Modifier.padding(padding), assets, tool, { tool = it }, speed, { speed = safeSpeed(it) },
                    trimIn, { trimIn = safeTrimStart(it, trimOut) }, trimOut, { trimOut = safeTrimEnd(it, trimIn) },
                    splitCount, { splitCount++ }, { picker.launch(arrayOf("video/*", "image/*", "audio/*")) }
                )
                Screen.Media -> MediaScreen(Modifier.padding(padding), assets) { picker.launch(arrayOf("video/*", "image/*", "audio/*")) }
                Screen.Projects -> ProjectsScreen(Modifier.padding(padding), project) {
                    project = "Untitled project"; assets.clear(); tool = "Trim"; speed = 1f
                    trimIn = 0f; trimOut = 100f; splitCount = 0; screen = Screen.Editor
                }
                Screen.Devices -> DevicesScreen(Modifier.padding(padding))
                Screen.ThreeD -> ThreeDScreen(Modifier.padding(padding))
            }
        }
    }
    if (exportDialog) AlertDialog(
        onDismissRequest = { exportDialog = false }, title = { Text("Export project") },
        text = { Text("The editing workspace is ready. Media3 preview and final video rendering are the next implementation step; this first foundation does not yet export a finished video.") },
        confirmButton = { TextButton(onClick = { exportDialog = false }) { Text("Close") } }
    )
    if (renameDialog) AlertDialog(
        onDismissRequest = { renameDialog = false }, title = { Text("Project name") },
        text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { project = newName.ifBlank { "Untitled project" }; renameDialog = false }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { renameDialog = false }) { Text("Cancel") } }
    )
}

@Composable
private fun EditorScreen(
    modifier: Modifier, assets: List<Asset>, tool: String, selectTool: (String) -> Unit,
    speed: Float, changeSpeed: (Float) -> Unit, trimIn: Float, changeIn: (Float) -> Unit,
    trimOut: Float, changeOut: (Float) -> Unit, splitCount: Int, split: () -> Unit, onImport: () -> Unit
) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Workspace", color = Grey, fontSize = 13.sp)
                Row { Icon(Icons.Default.Undo, null, tint = Grey); Spacer(Modifier.width(10.dp)); Icon(Icons.Default.Redo, null, tint = Grey) }
            }
            Card(Modifier.fillMaxWidth().aspectRatio(16f/9f), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = PreviewBg)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Movie, null, tint = Color(0xFFD7DED8), modifier = Modifier.size(34.dp))
                        Text(if (assets.isEmpty()) "Your preview appears here" else assets.first().name, color = Paper, fontSize = 13.sp)
                        Text(if (assets.isEmpty()) "Import media to start" else assets.size.toString() + " media item(s)", color = Color(0xFFBEC7BF), fontSize = 11.sp)
                    }
                    Icon(Icons.Default.PlayCircle, null, tint = Paper, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).size(34.dp))
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
                    Text(if (assets.isEmpty()) "No clip yet · import media to populate timeline" else "Clip 1 · " + (splitCount + 1) + " segment(s)", color = Grey, fontSize = 11.sp)
                }
            }
        }
        item {
            Text("Editing tools", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            val tools = listOf(
                "Trim" to Icons.Default.ContentCut, "Split" to Icons.Default.CallSplit,
                "Crop" to Icons.Default.Crop, "Speed" to Icons.Default.Speed,
                "Audio" to Icons.Default.GraphicEq, "Text" to Icons.Default.TextFields,
                "Subtitles" to Icons.Default.Subtitles, "Adjust" to Icons.Default.Tune
            )
            LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxWidth().height(174.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = false) {
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
                        "Speed" -> {
                            Text(String.format("%.2f", speed) + "×", color = Green, fontWeight = FontWeight.Bold)
                            Slider(speed, changeSpeed, valueRange = 0.25f..3f)
                        }
                        "Split" -> {
                            Text("Place a split marker at the playhead.", color = Grey, fontSize = 12.sp)
                            OutlinedButton(onClick = split) { Icon(Icons.Default.CallSplit, null); Text("Add split marker") }
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
            Button(onClick = import) { Icon(Icons.Default.Add, null); Text("Import") }
        }
        if (assets.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, null, tint = Green, modifier = Modifier.size(42.dp))
                    Text("Your media starts here", fontWeight = FontWeight.SemiBold)
                    Text("Import video, images or audio.", color = Grey, fontSize = 12.sp)
                    OutlinedButton(onClick = import) { Text("Import media") }
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
private fun ProjectsScreen(modifier: Modifier, project: String, newProject: () -> Unit) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("Projects", fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("Your creative work", color = Grey, fontSize = 12.sp) }
            Button(onClick = newProject) { Icon(Icons.Default.Add, null); Text("New") }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Paper)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, tint = Green, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column { Text(project, fontWeight = FontWeight.SemiBold); Text("Current local session", color = Grey, fontSize = 11.sp) }
            }
        }
        Text("Project autosave and reopen are next milestones.", color = Grey, fontSize = 12.sp)
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

@Composable
private fun ThreeDPage(modifier: Modifier) {
    var shape by remember { mutableStateOf("Cube") }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("3D workspace", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text("A simple space for modelling and scene setup.", color = Grey, fontSize = 12.sp)
        Card(Modifier.fillMaxWidth().height(220.dp), colors = CardDefaults.cardColors(containerColor = PreviewBg)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ViewInAr, null, tint = Color(0xFFDCE5DD), modifier = Modifier.size(44.dp))
                    Text(shape + " workspace", color = Paper)
                    Text("3D viewport module", color = Color(0xFFBEC7BF), fontSize = 11.sp)
                }
            }
        }
        Text("Add primitive", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Cube","Sphere","Cylinder").forEach { value -> OutlinedButton(onClick = { shape = value }) { Text(value) } }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Paper)) {
            Column(Modifier.padding(14.dp)) {
                Text("Scene tools", fontWeight = FontWeight.SemiBold)
                Text("Move · Rotate · Scale · Camera · Material", color = Grey, fontSize = 12.sp)
                Text("The real-time viewport and mesh export engine are later modules.", color = Grey, fontSize = 11.sp)
            }
        }
    }
}
