package com.tinycrap.app.ui.settings

import com.tinycrap.app.ui.components.enterOnce
import com.tinycrap.app.ui.components.Motion
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinycrap.app.data.SaveMode
import com.tinycrap.app.data.SettingsState
import com.tinycrap.app.data.ThemeMode
import com.tinycrap.app.ui.theme.ColorPresets

@Composable
fun SettingsScreen(vm: SettingsViewModel, contentPadding: PaddingValues) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val check by vm.check.collectAsStateWithLifecycle()
    val history by vm.historyCount.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var locationDialog by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }

    // Re-check the system notification switch when coming back from Android's own settings.
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val systemAllowsNotifications = remember(resumeTick, s.notifyDone) { vm.notifier.canNotify() }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.update { it.copy(notifyDone = granted) }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: Exception) { /* picker already granted it for this session */ }
            vm.update { it.copy(saveMode = SaveMode.Custom, saveTree = uri.toString()) }
        }
    }

    Box(modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Settings") },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // ---- Appearance --------------------------------------------------------------
                item {
                    Group("Appearance", Modifier.enterOnce("set-0", 0)) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            RowTitle(Icons.Rounded.SettingsBrightness, "Theme", "Light, dark, or follow the system")
                            val modes = listOf(ThemeMode.System to "System", ThemeMode.Light to "Light", ThemeMode.Dark to "Dark")
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                modes.forEachIndexed { index, (mode, label) ->
                                    SegmentedButton(
                                        selected = s.theme == mode,
                                        onClick = { vm.update { it.copy(theme = mode) } },
                                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                                    ) { Text(label) }
                                }
                            }
                        }
                        Divider()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            SwitchRow(
                                Icons.Rounded.AutoAwesome, "Dynamic colour", "Use colours from your wallpaper",
                                s.dynamicColor, onChange = { v -> vm.update { it.copy(dynamicColor = v) } },
                            )
                            Divider()
                        }
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp).alpha(if (s.dynamicColor) 0.45f else 1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RowTitle(
                                Icons.Rounded.Palette, "Colour preset",
                                "",
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                val dark = com.tinycrap.app.ui.theme.isDarkTheme(s)
                                ColorPresets.all.forEach { preset ->
                                    val scheme = if (dark) preset.dark else preset.light
                                    val selected = !s.dynamicColor && s.preset == preset.id
                                    val swatchScale = animateFloatAsState(
                                        if (selected) 1.1f else 1f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "swatch",
                                    )
                                    val checkAlpha = animateFloatAsState(
                                        if (selected) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 600f), label = "check",
                                    )
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .graphicsLayer { scaleX = swatchScale.value; scaleY = swatchScale.value }
                                                .clip(CircleShape)
                                                .background(scheme.primaryContainer)
                                                .selectable(
                                                    selected = selected, enabled = !s.dynamicColor, role = Role.RadioButton,
                                                    onClick = { vm.update { it.copy(preset = preset.id) } },
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(scheme.primary), contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Rounded.Check, contentDescription = if (selected) "Selected" else null, tint = scheme.onPrimary,
                                                    modifier = Modifier.size(20.dp).graphicsLayer {
                                                        alpha = checkAlpha.value.coerceIn(0f, 1f); scaleX = checkAlpha.value; scaleY = checkAlpha.value
                                                    },
                                                )
                                            }
                                        }
                                        Text(preset.name, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                        Divider()
                        SwitchRow(
                            Icons.Rounded.DarkMode, "Pure black", "True black background in dark mode (saves battery on OLED)",
                            s.amoled, onChange = { v -> vm.update { it.copy(amoled = v) } },
                        )
                        Divider()
                        SwitchRow(
                            Icons.Rounded.Animation, "Opening animation", "Play the crab animation when Tiny Crap starts",
                            s.splashAnimation, onChange = { v -> vm.update { it.copy(splashAnimation = v) } },
                        )
                        Divider()
                        SwitchRow(
                            Icons.Rounded.Vibration, "Haptic feedback", "A light tick when you switch tabs",
                            s.haptics, onChange = { v -> vm.update { it.copy(haptics = v) } },
                        )
                    }
                }

                // ---- Downloads ---------------------------------------------------------------
                item {
                    Group("Downloads", Modifier.enterOnce("set-1", 1)) {
                        ClickRow(
                            Icons.Rounded.Folder, "Save location", vm.locationLabel(s),
                            onClick = { locationDialog = true },
                        )
                        Divider()
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            RowTitle(
                                Icons.Rounded.Layers, "Maximum downloads: ${s.maxConcurrent}",
                                "",
                            )
                            Slider(
                                value = s.maxConcurrent.toFloat(),
                                onValueChange = { v -> vm.update { it.copy(maxConcurrent = v.toInt().coerceIn(1, 5)) } },
                                valueRange = 1f..5f,
                                steps = 3,
                            )
                        }
                        Divider()
                        SwitchRow(
                            Icons.Rounded.Wifi, "Wi-Fi only", "Don't download on mobile data",
                            s.wifiOnly, onChange = { v -> vm.update { it.copy(wifiOnly = v) } },
                        )
                        Divider()
                        ChipsRow(
                            Icons.Rounded.Speed, "Default video quality", "Highest quality picked for you",
                            options = listOf(0 to "Best", 2160 to "4K", 1440 to "1440p", 1080 to "1080p", 720 to "720p", 480 to "480p", 360 to "360p"),
                            selected = s.defaultQuality, onSelect = { v -> vm.update { it.copy(defaultQuality = v) } },
                        )
                        Divider()
                        ChipsRow(
                            Icons.Rounded.Download, "Default video format", "Used when the video offers a choice",
                            options = listOf("mp4" to "MP4", "webm" to "WebM"),
                            selected = s.defaultContainer, onSelect = { v -> vm.update { it.copy(defaultContainer = v) } },
                        )
                        Divider()
                        ChipsRow(
                            Icons.Rounded.Download, "Default audio", "Original keeps the source quality",
                            options = listOf("best" to "Original", "mp3" to "MP3", "m4a" to "M4A", "opus" to "Opus", "flac" to "FLAC", "wav" to "WAV"),
                            selected = s.defaultAudio, onSelect = { v -> vm.update { it.copy(defaultAudio = v) } },
                        )
                    }
                }

                // ---- Notifications -----------------------------------------------------------
                item {
                    Group("Notifications", Modifier.enterOnce("set-2", 2)) {
                        SwitchRow(
                            Icons.Rounded.NotificationsActive, "Notify when finished", "Tell me when a download is ready or fails",
                            s.notifyDone,
                            onChange = { v ->
                                if (v && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !vm.notifier.canNotify()) {
                                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    vm.update { it.copy(notifyDone = v) }
                                }
                            },
                        )
                        Divider()
                        SwitchRow(
                            Icons.Rounded.Notifications, "Show progress", "Progress bar in the notification while downloading",
                            s.showProgress, onChange = { v -> vm.update { it.copy(showProgress = v) } },
                        )
                        if (!systemAllowsNotifications) {
                            Divider()
                            ClickRow(
                                Icons.Rounded.Info, "Notifications are off",
                                "Tap to allow",
                                onClick = { openNotificationSettings(context) },
                            )
                        }
                    }
                }

                // ---- Server ------------------------------------------------------------------
                item { ServerGroup(vm, s, check) }

                // ---- Storage & about ----------------------------------------------------------
                item {
                    Group("Storage", Modifier.enterOnce("set-4", 4)) {
                        ClickRow(
                            Icons.Rounded.Delete, "Clear download history",
                            if (history.isEmpty()) "Empty" else "${history.size} item${if (history.size == 1) "" else "s"}",
                            enabled = history.isNotEmpty(),
                            onClick = { clearDialog = true },
                        )
                    }
                }
                item {
                    Group("About", Modifier.enterOnce("set-5", 5)) {
                        val version = remember {
                            try {
                                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
                            } catch (e: Exception) { "" }
                        }
                        RowTitleBlock(Icons.Rounded.Info, "Tiny Crap", "Version $version")
                    }
                }
            }
        }
    }

    if (locationDialog) {
        AlertDialog(
            onDismissRequest = { locationDialog = false },
            title = { Text("Save location") },
            text = {
                Column(modifier = Modifier.selectableGroup()) {
                    LocationOption("Default", "Videos in Movies / Tiny Crap, music in Music / Tiny Crap", s.saveMode == SaveMode.Default) {
                        vm.update { it.copy(saveMode = SaveMode.Default) }; locationDialog = false
                    }
                    LocationOption("Downloads folder", "Download / Tiny Crap", s.saveMode == SaveMode.Downloads) {
                        vm.update { it.copy(saveMode = SaveMode.Downloads) }; locationDialog = false
                    }
                    LocationOption(
                        "Choose a folder…",
                        if (s.saveTree.isNotBlank()) vm.treeLabel(Uri.parse(s.saveTree)) else "Pick any folder on your phone or SD card",
                        s.saveMode == SaveMode.Custom,
                    ) { locationDialog = false; folderPicker.launch(null) }
                }
            },
            confirmButton = { TextButton(onClick = { locationDialog = false }) { Text("Close") } },
        )
    }
    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text("Clear download history?") },
            text = { Text("The list will be emptied. The files stay on your phone.") },
            confirmButton = { TextButton(onClick = { vm.clearHistory(); clearDialog = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Cancel") } },
        )
    }
}

// ---- server section --------------------------------------------------------------------------------

@Composable
private fun ServerGroup(vm: SettingsViewModel, s: SettingsState, check: ServerCheck) {
    var url by remember(s.serverUrl) { mutableStateOf(s.serverUrl) }
    var key by remember(s.apiKey) { mutableStateOf(s.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    Group("Server", Modifier.enterOnce("set-3", 3)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            RowTitle(Icons.Rounded.CloudQueue, "Backend address", "")
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("Server address") },
            )
            OutlinedTextField(
                value = key, onValueChange = { key = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text("API key") },
                leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") } },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(
                    onClick = { vm.testServer(url, key) },
                    enabled = check !is ServerCheck.Testing,
                    modifier = Modifier.weight(1f).height(44.dp),
                ) { Text(if (check is ServerCheck.Testing) "Testing…" else "Test connection") }
                Button(
                    onClick = { vm.saveServer(url, key) },
                    enabled = url.trim() != s.serverUrl || key.trim() != s.apiKey,
                    modifier = Modifier.weight(1f).height(44.dp),
                ) { Text("Save") }
            }
            (check as? ServerCheck.Result)?.let { r ->
                Text(
                    r.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (r.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ---- building blocks ----------------------------------------------------------------------------------

@Composable
private fun Group(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

@Composable
private fun IconBubble(icon: ImageVector) {
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun RowTitle(icon: ImageVector, title: String, summary: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBubble(icon)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RowTitleBlock(icon: ImageVector, title: String, body: String) {
    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        IconBubble(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ClickRow(icon: ImageVector, title: String, summary: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun <T> ChipsRow(
    icon: ImageVector, title: String, summary: String,
    options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RowTitle(icon, title, summary)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (value, label) ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun LocationOption(title: String, summary: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        // No settings screen to open on this device.
    }
}
