package com.spotpriceplugs.ui

import com.spotpriceplugs.resolveMessages
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.spotpriceplugs.R
import com.spotpriceplugs.domain.Support
import com.spotpriceplugs.domain.clockText
import com.spotpriceplugs.domain.eurosText
import com.spotpriceplugs.domain.tapText

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/** Small explanatory text under a block. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = modifier)
}

@Composable
fun Header(lastRun: Long, lastRunInfo: String, nextRun: Long, savedMonth: Double) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Hint(stringResource(R.string.tagline))
        Spacer(Modifier.height(6.dp))
        Hint(
            if (lastRunInfo.isNotBlank()) {
                stringResource(R.string.last_run_info, tapText(lastRun), resolveMessages(lastRunInfo, LocalContext.current))
            } else {
                stringResource(R.string.last_run, tapText(lastRun))
            }
        )
        Hint(stringResource(R.string.next_run, clockText(nextRun)))
        if (savedMonth >= 0.01) {
            Text(
                stringResource(R.string.saved_30_days, eurosText(savedMonth)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = PlugPalette.Good
            )
        }
    }
}

/** "New version available" banner above all cards. */
@Composable
fun UpdateBanner(version: String, onDownload: () -> Unit, onLater: () -> Unit) {
    SectionCard(title = stringResource(R.string.update_title), container = Color(0xFF13283D)) {
        Text(stringResource(R.string.update_text, version), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onDownload, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.update_download))
            }
            OutlinedButton(onClick = onLater, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.update_later))
            }
        }
    }
}

/**
 * Project support. The main argument is how much the app has already saved:
 * that keeps the request honest and concrete.
 */
@Composable
fun SupportCard(savedMonth: Double) {
    val context = LocalContext.current
    SectionCard(title = stringResource(R.string.support_title)) {
        if (savedMonth >= 0.5) {
            Text(
                stringResource(R.string.support_saved, eurosText(savedMonth)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = PlugPalette.Good
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            stringResource(R.string.support_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { openUrl(context, Support.DONATE_URL) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PlugPalette.Coffee,
                contentColor = PlugPalette.OnCoffee
            )
        ) {
            Text(stringResource(R.string.support_coffee), fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = { openUrl(context, Support.SOURCE_URL) },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.support_github)) }
            TextButton(
                onClick = { openUrl(context, Support.ISSUES_URL) },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.support_bug)) }
        }
    }
}

/** Version, manual update check, language picker (Android 13+). */
@Composable
fun AboutCard(
    version: String,
    checking: Boolean,
    status: String,
    onCheck: () -> Unit
) {
    val context = LocalContext.current
    SectionCard(title = stringResource(R.string.about_title)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.bodyMedium)
                if (status.isNotBlank()) Hint(status)
            }
            OutlinedButton(onClick = onCheck, enabled = !checking) {
                if (checking) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.about_check))
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            TextButton(
                onClick = { openLanguageSettings(context) },
                modifier = Modifier.padding(top = 2.dp)
            ) {
                // Decorative globe: TalkBack reads only the label.
                Text("🌐", modifier = Modifier.clearAndSetSemantics { })
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.about_language))
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }
        Hint(stringResource(R.string.about_note))
    }
}

@Composable
fun PermissionsCard() {
    val context = LocalContext.current
    var exact by remember { mutableStateOf(canExact(context)) }
    var battery by remember { mutableStateOf(batteryIgnored(context)) }
    var notifications by remember { mutableStateOf(notificationsGranted(context)) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notifications = granted }

    // Re-read the states when the user returns from system settings;
    // otherwise the red labels would stay until the app restarts.
    LifecycleResumeEffect(Unit) {
        exact = canExact(context)
        battery = batteryIgnored(context)
        notifications = notificationsGranted(context)
        onPauseOrDispose { }
    }

    val askNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val allGood = exact && battery && (notifications || !askNotifications)

    SectionCard(title = stringResource(R.string.perm_title)) {
        PermissionLine(R.string.perm_exact, exact, R.string.perm_allowed, R.string.perm_denied)
        PermissionLine(R.string.perm_background, battery, R.string.perm_unrestricted, R.string.perm_restricted)
        if (askNotifications) {
            PermissionLine(R.string.perm_notifications, notifications, R.string.perm_allowed, R.string.perm_denied)
        }
        if (!allGood) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!exact) {
                    OutlinedButton(
                        onClick = { openExactSettings(context) },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.perm_btn_alarms)) }
                }
                if (!battery) {
                    OutlinedButton(
                        onClick = { openBatterySettings(context) },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.perm_btn_battery)) }
                }
            }
            if (askNotifications && !notifications) {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.perm_btn_notifications)) }
            }
            Spacer(Modifier.height(8.dp))
            Hint(stringResource(R.string.perm_hint))
        }
    }
}

@Composable
private fun PermissionLine(title: Int, ok: Boolean, yes: Int, no: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            if (ok) "✓" else "✕",
            color = if (ok) PlugPalette.Good else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            // The line text already says allowed/denied; don't read the glyph aloud.
            modifier = Modifier.width(22.dp).clearAndSetSemantics { }
        )
        Text(
            stringResource(R.string.perm_line, stringResource(title), stringResource(if (ok) yes else no)),
            style = MaterialTheme.typography.bodyMedium,
            color = if (ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
        )
    }
}
