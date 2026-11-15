package com.lumenchord.pianoweave.ui.screens.pianoroll.settings

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SignalCellularAlt1Bar
import androidx.compose.material.icons.filled.SignalCellularAlt2Bar
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lumenchord.pianoweave.midi.BleMidiPeripheral
import com.lumenchord.pianoweave.midi.BluetoothMidi
import com.lumenchord.pianoweave.midi.BluetoothMidiPermissions
import com.lumenchord.pianoweave.midi.MidiInputManager
import com.lumenchord.pianoweave.ui.components.SettingToggleRow
import com.lumenchord.pianoweave.ui.theme.LocalAppTheme
import com.lumenchord.pianoweave.ui.viewmodel.PianoWeaveViewModel
import kotlinx.coroutines.delay

/**
 * The "MIDI & Bluetooth" settings page: what's connected, one Bluetooth list (keyboards to connect to, and
 * a computer connected to the phone), and what happens to the notes.
 *
 * Opening the page searches for keyboards (which reveals nothing about the phone). The phone becomes findable
 * for computers only on "Connect a computer", for a short time (see [BleMidiPeripheral]). A connection stays
 * after the page is closed.
 */
@Composable
internal fun MidiSettingsSection(viewModel: PianoWeaveViewModel, context: Context) {
    val accent = LocalAppTheme.current.primaryColor
    val gate = rememberBluetoothGate(context)
    var ready by remember { mutableStateOf(isBluetoothReady(context)) }
    val hasBle = remember { BluetoothMidi.hasBluetoothLe(context) }

    fun goLive() {
        ready = true
        if (!hasBluetoothConnection()) BluetoothMidi.startScan(context)
    }

    // Back from the system's Bluetooth or permission screens: pick up where things stand now
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val nowReady = isBluetoothReady(context)
        if (hasBle && nowReady) goLive() else ready = nowReady
    }
    // Bluetooth switched off and on while the page is open (from quick settings, or by the system)
    LaunchedEffect(BluetoothMidi.bluetoothRestarts) {
        if (BluetoothMidi.bluetoothRestarts > 0 && hasBle && isBluetoothReady(context)) goLive()
    }
    DisposableEffect(Unit) {
        onDispose {
            BluetoothMidi.stopScan()
            BleMidiPeripheral.setDiscoverable(context, false)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        ConnectionCard(accent)

        if (!hasBle) {
            Hint("This phone has no Bluetooth MIDI. USB keyboards still work.")
        } else {
            SettingsGroup("Bluetooth") {
                SettingToggleRow(
                    title = "Bluetooth MIDI",
                    subtitle = "Connect BLE MIDI keyboards and computers",
                    checked = BluetoothMidi.isEnabled,
                    onCheckedChange = { enabled -> BluetoothMidi.setEnabled(context, enabled) },
                    accent = accent
                )
                if (BluetoothMidi.isEnabled) {
                    if (!ready) {
                        TurnOnCard(accent) { gate.run(BluetoothMidiPermissions.forAll(), ::goLive) }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Devices",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                SearchButton(accent) { BluetoothMidi.startScan(context) }
                            }
                            BluetoothList(context, accent)
                        }
                        if (BleMidiPeripheral.connectedCentralName == null) ConnectComputerRow(context, accent)
                    }
                    gate.problem?.let { problem ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(problem, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                )
                            }) { Text("App settings", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }

        SettingsGroup("Sound") {
            SettingToggleRow(
                title = "Keyboard sound",
                subtitle = "Play keyboard notes through the app",
                checked = viewModel.isKeyboardSoundEnabled,
                onCheckedChange = { viewModel.setKeyboardSoundEnabled(context, it) },
                accent = accent
            )
            SettingToggleRow(
                title = "Send the song",
                subtitle = "Play the song on the keyboard or computer too",
                checked = viewModel.isMidiOutputEnabled,
                onCheckedChange = { viewModel.setMidiOutputEnabled(context, it) },
                accent = accent
            )
        }
    }
}

private fun isBluetoothReady(context: Context): Boolean =
    BluetoothMidiPermissions.has(context, BluetoothMidiPermissions.forAll()) && BluetoothMidi.isBluetoothOn(context)

private fun hasBluetoothConnection(): Boolean =
    BluetoothMidi.isKeyboardConnected() || BleMidiPeripheral.connectedCentralName != null

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
}

/** What's connected right now, at the top of the page: a keyboard, a computer, or nothing yet. */
@Composable
private fun ConnectionCard(accent: Color) {
    val name = MidiInputManager.connectedName
    val transport = MidiInputManager.connectedTransport
    val computer = BleMidiPeripheral.connectedCentralName
    val connected = name != null || computer != null
    val icon = when {
        name == null && computer != null -> Icons.Default.Computer
        transport == MidiInputManager.Transport.Usb -> Icons.Default.Usb
        transport == MidiInputManager.Transport.Bluetooth -> Icons.Default.Bluetooth
        else -> Icons.Default.Piano
    }
    val title = name ?: computer ?: "Nothing connected"
    val subtitle = when {
        name != null && computer != null -> "Keyboard and computer connected"
        name != null -> if (transport == MidiInputManager.Transport.Usb) "Keyboard · USB" else if (transport == MidiInputManager.Transport.Bluetooth) "Keyboard · Bluetooth" else "Keyboard"
        computer != null -> "Computer · Bluetooth"
        else -> "Plug in a USB keyboard or use Bluetooth below"
    }
    val border by animateColorAsState(if (connected) accent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.tertiaryContainer, label = "cardBorder")

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (connected) accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(accent.copy(alpha = if (connected) 0.18f else 0.08f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (connected) accent else accent.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            if (connected) LiveDot(accent)
        }
    }
}

/** A softly pulsing dot: this connection is live. */
@Composable
private fun LiveDot(color: Color, size: Int = 9) {
    val pulse by rememberInfiniteTransition(label = "live").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "liveAlpha"
    )
    Box(modifier = Modifier.size(size.dp).alpha(pulse).background(color, CircleShape))
}

/** "Searching…" with a spinner while it runs; a small refresh button otherwise. */
@Composable
private fun SearchButton(accent: Color, onSearch: () -> Unit) {
    if (BluetoothMidi.isScanning) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = accent)
            Text("Searching", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        TextButton(onClick = onSearch, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), modifier = Modifier.height(28.dp)) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Search", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

/** Bluetooth permission missing, or Bluetooth off: one button sorts out both. */
@Composable
private fun TurnOnCard(accent: Color, onTurnOn: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Hint("Connect a Bluetooth keyboard, or let a computer connect to this phone.")
            Button(
                onClick = onTurnOn,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = if (LocalAppTheme.current.isLightAccent) Color.Black else Color.White
                ),
                shape = RoundedCornerShape(percent = 50)
            ) {
                Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Turn on Bluetooth MIDI", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

/** Keyboards found nearby and a connected computer, in one list, plus how a computer connects. */
@Composable
private fun BluetoothList(context: Context, accent: Color) {
    val computer = BleMidiPeripheral.connectedCentralName
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (computer != null) {
            DeviceRow(
                icon = Icons.Default.Computer,
                name = computer,
                status = "Connected · computer",
                accent = accent,
                isConnected = true,
                onDisconnect = BleMidiPeripheral::disconnectComputer
            )
        }
        BluetoothMidi.found.forEach { keyboard ->
            val isConnecting = BluetoothMidi.connectingAddress == keyboard.address
            val isConnected = BluetoothMidi.connectedAddress == keyboard.address && BluetoothMidi.isKeyboardConnected()
            DeviceRow(
                icon = Icons.Default.Piano,
                name = keyboard.name,
                status = when {
                    isConnected -> "Connected · keyboard"
                    isConnecting -> "Connecting…"
                    else -> "Tap to connect"
                },
                accent = accent,
                isConnected = isConnected,
                isConnecting = isConnecting,
                rssi = keyboard.rssi,
                onClick = { BluetoothMidi.connect(context, keyboard.address) },
                onDisconnect = { BluetoothMidi.disconnect(context) }
            )
        }
        if (!hasBluetoothConnection() &&
            BluetoothMidi.found.isEmpty() &&
            !BluetoothMidi.isScanning &&
            BluetoothMidi.connectingAddress == null
        ) {
            Hint(BluetoothMidi.message ?: "No keyboards nearby. Turn on the keyboard's Bluetooth and search again.")
        } else if (BluetoothMidi.connectingAddress == null && !hasBluetoothConnection()) {
            BluetoothMidi.message?.let { Hint(it) }
        }
    }
}

/**
 * "Connect a computer", kept low-key under the list: most people only connect keyboards, and it isn't a device
 * row. Tapping it makes the phone findable for [BleMidiPeripheral.VISIBLE_MS]; the line then shows the time
 * left and Stop, in the same height so nothing jumps. Not shown while a computer is connected (it's in the
 * list then).
 */
@Composable
private fun ConnectComputerRow(context: Context, accent: Color) {
    val until = BleMidiPeripheral.visibleUntil
    val problem = BleMidiPeripheral.problem
    val visible = until > 0L
    val secondsLeft by produceState(0L, until) {
        while (until > 0L) {
            value = ((until - SystemClock.elapsedRealtime()) / 1000).coerceAtLeast(0)
            delay(1000)
        }
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (visible) {
                LiveDot(accent, size = 7)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Waiting for a computer · %d:%02d".format(secondsLeft / 60, secondsLeft % 60),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { BleMidiPeripheral.setDiscoverable(context, false) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) { Text("Stop", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            } else {
                Text("Using a computer?", fontSize = 12.sp, color = muted, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = { BleMidiPeripheral.setDiscoverable(context, true) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.Computer, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Connect", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        if (visible) Hint("Choose this phone in the computer's Bluetooth MIDI list.")
        else problem?.let { Text(it, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun DeviceRow(
    icon: ImageVector,
    name: String,
    status: String,
    accent: Color,
    isConnected: Boolean,
    isConnecting: Boolean = false,
    rssi: Int = 0,
    onClick: () -> Unit = {},
    onDisconnect: () -> Unit
) {
    Surface(
        onClick = { if (!isConnected && !isConnecting) onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (isConnected) accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, if (isConnected) accent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                Text(status, fontSize = 11.sp, maxLines = 1, color = if (isConnected) accent else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                isConnecting -> CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp, color = accent)
                isConnected -> TextButton(onClick = onDisconnect, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Disconnect", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                rssi != 0 -> Icon(
                    signalIcon(rssi),
                    contentDescription = "Signal",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp).size(16.dp)
                )
            }
        }
    }
}

private fun signalIcon(rssi: Int): ImageVector = when {
    rssi > -65 -> Icons.Default.SignalCellularAlt
    rssi > -80 -> Icons.Default.SignalCellularAlt2Bar
    else -> Icons.Default.SignalCellularAlt1Bar
}

/**
 * Runs an action that needs Bluetooth: asks for the permissions it needs, then to turn Bluetooth on, and
 * only then runs it. If permission is refused, [problem] says so (with a way to app settings).
 */
private class BluetoothGate(
    private val context: Context,
    private val askPermissions: (Array<String>) -> Unit,
    private val askBluetoothOn: () -> Unit
) {
    var pending: (() -> Unit)? = null
    var problem by mutableStateOf<String?>(null)

    fun run(permissions: Array<String>, action: () -> Unit) {
        problem = null
        if (!BluetoothMidiPermissions.has(context, permissions)) {
            pending = action
            askPermissions(permissions)
        } else if (!BluetoothMidi.isBluetoothOn(context)) {
            pending = action
            askBluetoothOn()
        } else {
            action()
        }
    }

    fun onPermissions(granted: Boolean) {
        if (!granted) {
            pending = null
            problem = "Bluetooth permission is off for Piano Weave."
            return
        }
        val action = pending ?: return
        if (BluetoothMidi.isBluetoothOn(context)) { pending = null; action() } else askBluetoothOn()
    }

    fun onBluetoothResult() {
        val action = pending
        pending = null
        if (action != null && BluetoothMidi.isBluetoothOn(context)) action()
    }
}

@Composable
private fun rememberBluetoothGate(context: Context): BluetoothGate {
    lateinit var gate: BluetoothGate
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        gate.onPermissions(result.values.all { it })
    }
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK || BluetoothMidi.isBluetoothOn(context)) gate.onBluetoothResult()
        else gate.pending = null
    }
    gate = remember {
        BluetoothGate(
            context = context,
            askPermissions = { permissionLauncher.launch(it) },
            askBluetoothOn = { runCatching { enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) } }
        )
    }
    return gate
}
