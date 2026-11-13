package com.spotpriceplugs.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spotpriceplugs.R
import com.spotpriceplugs.data.Updates
import com.spotpriceplugs.domain.Support
import com.spotpriceplugs.domain.Vat
import com.spotpriceplugs.model.DeviceName
import com.spotpriceplugs.model.Plug
import com.spotpriceplugs.net.Scanner
import com.spotpriceplugs.work.Scheduler
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is always dark: keep light system-bar icons even when the
        // system is in light mode (the default "auto" style would make them dark).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        setContent {
            PlugTheme { Screen() }
        }
        Scheduler.schedule(this)
    }
}

@Composable
private fun Screen(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val store = vm.store
    val snackbar = remember { SnackbarHostState() }
    val appVersion = remember { Updates.currentVersion(context) }

    // Input fields survive rotation and app-language changes.
    var prefix by rememberSaveable {
        mutableStateOf(store.subnet.ifBlank { Scanner.localPrefix(context) ?: "" })
    }
    var defaultRegion by rememberSaveable { mutableStateOf(store.defaultRegion) }
    var defaultWatts by rememberSaveable { mutableStateOf(trimWatts(store.defaultWatts)) }
    var vat by rememberSaveable { mutableStateOf(trimVat(store.vat)) }
    var refreshAt by rememberSaveable {
        mutableStateOf("%02d:%02d".format(store.refreshHour, store.refreshMinute))
    }
    var defaultHours by rememberSaveable { mutableStateOf(trimNum(store.defaultHours)) }
    var defaultLimit by rememberSaveable { mutableStateOf(trimNum(store.defaultLimit)) }

    // Short messages at the bottom of the screen, visible wherever the user is.
    // A new message replaces the one on screen.
    LaunchedEffect(vm) {
        vm.messages.collect { message ->
            launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(
                    message.text.resolve(context),
                    withDismissAction = message.long,
                    duration = if (message.long) SnackbarDuration.Long else SnackbarDuration.Short
                )
            }
        }
    }

    // A background refresh may have run while the app was in the background.
    LifecycleResumeEffect(vm) {
        vm.reload()
        vm.pollPower()
        onPauseOrDispose { }
    }

    val plugs = vm.plugs
    val busy = vm.busy
    val scanning = vm.scanning

    val networkCard = @Composable {
        NetworkCard(
            prefix = prefix,
            onPrefix = { value -> prefix = value.filter { it.isDigit() || it == '.' }.take(15) },
            onAuto = {
                prefix = Scanner.localPrefix(context) ?: prefix
                vm.say(
                    if (prefix.isBlank()) text(R.string.msg_wifi_unknown)
                    else text(R.string.msg_network, prefix)
                )
            },
            onScan = { vm.startScan(prefix) },
            scanning = scanning,
            scanned = vm.scanned,
            enabled = !scanning && !busy,
            primary = plugs.isEmpty()
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .consumeWindowInsets(insets)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Header(vm.lastRun, vm.lastRunInfo, vm.nextRun, vm.savedMonth)

            if (vm.updateVersion.isNotBlank()) {
                UpdateBanner(
                    version = vm.updateVersion,
                    onDownload = { openUrl(context, store.updateUrl.ifBlank { Support.RELEASES_URL }) },
                    onLater = { vm.dismissUpdate() }
                )
            }

            // First run: discovery comes first. Afterwards the plugs matter most
            // and the rarely needed network card moves below them.
            if (plugs.isEmpty()) networkCard()

            PlugsCard(
                plugs = plugs,
                busy = busy,
                scanning = scanning,
                progress = vm.progress,
                onRefreshAll = { vm.refresh() }
            ) { plug ->
                val savings = vm.savings[plug.ip]
                PlugCard(
                    plug = plug,
                    busy = busy || scanning,
                    fallbackRegion = vm.defaultRegion,
                    savingsToday = savings?.today ?: 0.0,
                    savingsWeek = savings?.week ?: 0.0,
                    onSave = { region, hours, limit, watts ->
                        vm.savePlug(plug, region, hours, limit, watts)
                    },
                    onDelete = { vm.deletePlug(plug) },
                    onRefresh = { vm.refresh(setOf(plug.ip)) },
                    onPower = { on -> vm.setPower(plug, on) },
                    onTimers = { on -> vm.setTimersEnabled(plug, on) },
                    onManaged = { on -> vm.setManaged(plug, on) },
                    onCopyName = { name ->
                        copyToClipboard(context, name)
                        vm.say(text(R.string.name_copied), long = true)
                    }
                )
            }

            if (plugs.isNotEmpty()) networkCard()

            SettingsCard(
                vat = vat, onVat = { vat = it },
                refreshAt = refreshAt, onRefreshAt = { refreshAt = it.take(5) },
                hours = defaultHours, onHours = { defaultHours = it },
                limit = defaultLimit, onLimit = { defaultLimit = it },
                region = defaultRegion,
                onRegion = { v -> defaultRegion = v.uppercase().filter { it.isLetterOrDigit() }.take(4) },
                watts = defaultWatts, onWatts = { v -> defaultWatts = v.filter { it.isDigit() }.take(6) },
                onSave = {
                    if (vm.saveSettings(vat, refreshAt, defaultHours, defaultLimit, defaultRegion, defaultWatts)) {
                        defaultRegion = store.defaultRegion
                    }
                }
            )

            PermissionsCard()

            SupportCard(vm.savedMonth)

            AboutCard(
                version = appVersion,
                checking = vm.checkingUpdate,
                status = vm.updateStatus?.resolve().orEmpty(),
                onCheck = { vm.checkUpdates(manual = true) }
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PlugsCard(
    plugs: List<Plug>,
    busy: Boolean,
    scanning: Boolean,
    progress: String,
    onRefreshAll: () -> Unit,
    item: @Composable (Plug) -> Unit
) {
    val title = if (plugs.isEmpty()) {
        stringResource(R.string.plugs_title)
    } else {
        stringResource(R.string.plugs_title_count, plugs.size)
    }
    SectionCard(title = title) {
        if (plugs.isEmpty()) {
            Text(
                stringResource(R.string.plugs_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                plugs.forEach { item(it) }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRefreshAll,
                enabled = !busy && !scanning,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.refresh_sending))
                } else {
                    Text(stringResource(R.string.refresh_all))
                }
            }
            if (progress.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Hint(progress)
            }
        }
    }
}

@Composable
private fun NetworkCard(
    prefix: String,
    onPrefix: (String) -> Unit,
    onAuto: () -> Unit,
    onScan: () -> Unit,
    scanning: Boolean,
    scanned: Int,
    enabled: Boolean,
    /** First run: scanning is the main action on screen. */
    primary: Boolean
) {
    SectionCard(title = stringResource(R.string.net_title)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = prefix,
                onValueChange = onPrefix,
                label = { Text(stringResource(R.string.net_subnet)) },
                placeholder = { Text("192.168.1") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = onAuto, enabled = enabled) {
                Text(stringResource(R.string.net_auto))
            }
        }
        Spacer(Modifier.height(10.dp))
        val scanLabel: @Composable () -> Unit = {
            if (scanning) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("$scanned/254")
            } else {
                Text(stringResource(R.string.net_scan))
            }
        }
        val scanModifier = Modifier.fillMaxWidth().height(48.dp)
        if (primary) {
            Button(onClick = onScan, enabled = enabled, modifier = scanModifier) { scanLabel() }
        } else {
            OutlinedButton(onClick = onScan, enabled = enabled, modifier = scanModifier) { scanLabel() }
        }
        if (scanning) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { scanned / 254f }, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        Hint(stringResource(R.string.net_hint))
    }
}

@Composable
private fun SettingsCard(
    vat: String, onVat: (String) -> Unit,
    refreshAt: String, onRefreshAt: (String) -> Unit,
    hours: String, onHours: (String) -> Unit,
    limit: String, onLimit: (String) -> Unit,
    region: String, onRegion: (String) -> Unit,
    watts: String, onWatts: (String) -> Unit,
    onSave: () -> Unit
) {
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    SectionCard(title = stringResource(R.string.settings_title)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = vat, onValueChange = onVat,
                label = { Text(stringResource(R.string.set_vat)) },
                placeholder = {
                    val auto = stringResource(R.string.set_vat_auto)
                    Text(Vat.forRegion(region)?.let { "$auto · ${trimNum(it)}" } ?: auto)
                },
                singleLine = true, keyboardOptions = decimal,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = refreshAt, onValueChange = onRefreshAt,
                label = { Text(stringResource(R.string.set_refresh)) },
                placeholder = { Text("14:05") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = hours, onValueChange = onHours,
                label = { Text(stringResource(R.string.set_hours)) },
                singleLine = true, keyboardOptions = decimal,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = limit, onValueChange = onLimit,
                label = { Text(stringResource(R.string.set_limit)) },
                singleLine = true, keyboardOptions = decimal,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = region, onValueChange = onRegion,
                label = { Text(stringResource(R.string.set_region)) },
                placeholder = { Text("LV") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = watts, onValueChange = onWatts,
                label = { Text(stringResource(R.string.set_watts)) },
                placeholder = { Text(stringResource(R.string.set_watts_none)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.set_save))
        }
        Spacer(Modifier.height(8.dp))
        Hint(stringResource(R.string.set_hint, DeviceName.EXAMPLE))
    }
}
