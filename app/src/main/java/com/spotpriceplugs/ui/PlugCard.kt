package com.spotpriceplugs.ui

import com.spotpriceplugs.resolveMessages
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spotpriceplugs.R
import com.spotpriceplugs.domain.PlanView
import com.spotpriceplugs.domain.clockText
import com.spotpriceplugs.domain.eurosText
import com.spotpriceplugs.domain.minutesText
import com.spotpriceplugs.domain.parsePlan
import com.spotpriceplugs.domain.priceAt
import com.spotpriceplugs.domain.switchText
import com.spotpriceplugs.domain.tapText
import com.spotpriceplugs.model.DeviceName
import com.spotpriceplugs.model.Plug
import com.spotpriceplugs.model.PlugKind
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val SLOT_MS = 15 * 60_000L

@Composable
fun PlugCard(
    plug: Plug,
    busy: Boolean,
    fallbackRegion: String,
    savingsToday: Double,
    savingsWeek: Double,
    onSave: (region: String, hours: Double, limit: Double, watts: Double) -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
    onPower: (Boolean) -> Unit,
    onTimers: (Boolean) -> Unit,
    onManaged: (Boolean) -> Unit,
    onCopyName: (String) -> Unit
) {
    // Saveable: an open editor or delete dialog survives rotation / language change.
    var editing by rememberSaveable(plug.ip) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(plug.ip) { mutableStateOf(false) }
    var region by rememberSaveable(plug.ip) { mutableStateOf("") }
    var hours by rememberSaveable(plug.ip) { mutableStateOf("") }
    var limit by rememberSaveable(plug.ip) { mutableStateOf("") }
    var watts by rememberSaveable(plug.ip) { mutableStateOf("") }
    var regionInvalid by rememberSaveable(plug.ip) { mutableStateOf(false) }

    fun startEditing() {
        // Always start from the saved values, so "Cancel" leaves no stale input behind.
        region = plug.region.ifBlank { fallbackRegion }
        hours = trimNum(plug.hours)
        limit = trimNum(plug.limit)
        watts = trimWatts(plug.watts)
        regionInvalid = false
        editing = true
    }

    // Tick every 30 seconds so the countdown and the "now" line keep moving.
    val now by produceState(System.currentTimeMillis(), plug.planJson) {
        while (true) {
            value = System.currentTimeMillis()
            delay(30_000L)
        }
    }
    val plan = remember(plug.planJson) { parsePlan(plug.planJson) }
    val priceSummary = remember(plan, plug.effectiveWatts) { plan?.priceSummary(plug.effectiveWatts) }
    val onTime = plan?.onTimeText(now)
    val base = remember(plug.name) { DeviceName.baseName(plug.name) }
    val switchInfo = plan?.switchText(now)
    val currentPrice = plan?.priceAt(now)

    val shownRegion = plug.region.ifBlank { fallbackRegion }
    val regionFromSettings = plug.region.isBlank() && fallbackRegion.isNotBlank()
    val title = plug.displayName

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.plug_delete_title)) },
            text = { Text(stringResource(R.string.plug_delete_text, title)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; editing = false; onDelete() }) {
                    Text(stringResource(R.string.plug_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    val kindText = when (plug.kind) {
        PlugKind.TASMOTA, PlugKind.SHELLY_RPC, PlugKind.SHELLY_GEN1, PlugKind.OPENBEKEN ->
            PlugKind.label(plug.kind)
        else -> stringResource(R.string.plug_kind_unknown)
    }

    // Tapping the name / address opens the plug's own web page in the browser.
    val context = LocalContext.current
    val openPage = Modifier
        .clip(RoundedCornerShape(6.dp))
        .clickable(onClickLabel = stringResource(R.string.plug_open_page)) {
            openUrl(context, "http://${plug.ip}")
        }

    // Found on the network but not controlled: no plan, no relay buttons —
    // only the choice to start controlling it or to drop it from the list.
    if (!plug.managed) {
        Card(
            colors = CardDefaults.cardColors(containerColor = PlugPalette.Inner),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Column(modifier = Modifier.fillMaxWidth().then(openPage)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Hint(
                        buildString {
                            append(plug.ip)
                            append(" · $kindText")
                            if (plug.model.isNotBlank()) append(" · ${plug.model}")
                        }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Hint(stringResource(R.string.plug_unmanaged))
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { onManaged(true) }, enabled = !busy) {
                        Text(stringResource(R.string.plug_manage))
                    }
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.plug_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        return
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = PlugPalette.Inner),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).then(openPage)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    val fromSettings = stringResource(R.string.plug_region_from_settings)
                    val kindLabel = kindText
                    Hint(
                        buildString {
                            append(plug.ip)
                            append(" · $kindLabel")
                            if (plug.model.isNotBlank()) append(" · ${plug.model}")
                            if (regionFromSettings) append(" · $fromSettings")
                        }
                    )
                }
                Spacer(Modifier.width(8.dp))
                Chip(
                    shownRegion.ifBlank { "?" },
                    if (shownRegion.isNotBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    description = if (shownRegion.isBlank()) stringResource(R.string.plug_region_missing) else null
                )
                Spacer(Modifier.width(6.dp))
                Chip(
                    stringResource(if (plug.power) R.string.plug_on else R.string.plug_off),
                    if (plug.power) PlugPalette.Good else PlugPalette.Muted
                )
            }

            if (onTime != null) {
                Spacer(Modifier.height(6.dp))
                Text(onTime, style = MaterialTheme.typography.bodyMedium)
            }
            if (priceSummary != null) Text(priceSummary, style = MaterialTheme.typography.bodyMedium)
            if (plug.lastError.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Hint(resolveMessages(plug.lastError, context), color = MaterialTheme.colorScheme.error)
            }
            if (!plug.timersOn) {
                Spacer(Modifier.height(4.dp))
                Hint(stringResource(R.string.plug_paused), color = MaterialTheme.colorScheme.error)
            }

            if (plan != null) PlanStrip(plan, now)

            if (switchInfo != null || currentPrice != null) {
                Spacer(Modifier.height(6.dp))
                val priceText = currentPrice?.let {
                    stringResource(R.string.plug_now_price, "%.3f".format(it))
                }
                Text(
                    listOfNotNull(priceText, switchInfo).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (plug.effectiveWatts > 0.0 && (savingsToday > 0.0 || savingsWeek > 0.0)) {
                Spacer(Modifier.height(2.dp))
                Hint(
                    stringResource(R.string.plug_saved, eurosText(savingsToday), eurosText(savingsWeek)),
                    color = PlugPalette.Good
                )
            }
            if (plug.lastApplied > 0L) {
                Spacer(Modifier.height(2.dp))
                Hint(stringResource(R.string.plug_updated, tapText(plug.lastApplied), clockText(plug.nextAt)))
            }

            if (editing) {
                EditFields(
                    region = region, onRegion = { region = it; regionInvalid = false },
                    hours = hours, onHours = { hours = it; regionInvalid = false },
                    limit = limit, onLimit = { limit = it; regionInvalid = false },
                    watts = watts, onWatts = { watts = it; regionInvalid = false },
                    measuredWatts = plug.autoWatts
                )
                // Live preview: the name that would carry exactly these settings.
                val preview = DeviceName.compose(
                    base,
                    region.trim(),
                    parseNum(hours, plug.hours),
                    parseWatts(watts, plug.watts),
                    parseNum(limit, plug.limit)
                )
                NamePreview(preview, onCopy = { onCopyName(preview) })
                if (regionInvalid) {
                    Spacer(Modifier.height(4.dp))
                    Hint(stringResource(R.string.plug_region_error), color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val code = region.trim().uppercase()
                            if (code.isNotBlank() && !DeviceName.isRegion(code)) {
                                regionInvalid = true
                                return@Button
                            }
                            onSave(
                                code,
                                parseNum(hours, plug.hours),
                                parseNum(limit, plug.limit),
                                parseWatts(watts, plug.watts)
                            )
                            editing = false
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.save)) }
                    OutlinedButton(
                        onClick = { editing = false },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.cancel)) }
                }
                Row {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.plug_delete), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { editing = false; onManaged(false) }, enabled = !busy) {
                        Text(stringResource(R.string.plug_unmanage))
                    }
                }
            } else {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onPower(!plug.power) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(if (plug.power) R.string.plug_turn_off else R.string.plug_turn_on))
                    }
                    OutlinedButton(
                        onClick = { onTimers(!plug.timersOn) },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(if (plug.timersOn) R.string.plug_pause else R.string.plug_resume))
                    }
                }
                val params = stringResource(R.string.plug_params, trimNum(plug.hours), trimNum(plug.limit))
                val wattsText = stringResource(R.string.plug_watts, trimWatts(plug.effectiveWatts))
                // Settings summary with "Copy name" beside it: the name encodes exactly these values.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Hint(
                        if (plug.effectiveWatts > 0.0) "$params · $wattsText" else params,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        onCopyName(DeviceName.compose(base, plug.region, plug.hours, plug.watts, plug.limit))
                    }) { Text(stringResource(R.string.name_copy)) }
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { startEditing() }) { Text(stringResource(R.string.plug_edit)) }
                    TextButton(onClick = onRefresh, enabled = !busy) {
                        Text(stringResource(R.string.plug_recalc))
                    }
                }
            }
        }
    }
}

@Composable
private fun EditFields(
    region: String, onRegion: (String) -> Unit,
    hours: String, onHours: (String) -> Unit,
    limit: String, onLimit: (String) -> Unit,
    watts: String, onWatts: (String) -> Unit,
    /** The plug's own meter reading, shown when the field is empty (0 = none). */
    measuredWatts: Double
) {
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = region,
            onValueChange = { v -> onRegion(v.uppercase().filter { it.isLetterOrDigit() }.take(4)) },
            label = { Text(stringResource(R.string.set_region)) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = hours,
            onValueChange = onHours,
            label = { Text(stringResource(R.string.edit_hours)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            keyboardOptions = decimal
        )
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = limit,
            onValueChange = onLimit,
            label = { Text(stringResource(R.string.edit_limit)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            keyboardOptions = decimal
        )
        OutlinedTextField(
            value = watts,
            onValueChange = { v -> onWatts(v.filter { it.isDigit() }.take(6)) },
            label = { Text(stringResource(R.string.set_watts)) },
            placeholder = {
                Text(
                    if (measuredWatts > 0.0) "≈ ${trimWatts(measuredWatts)}"
                    else stringResource(R.string.edit_unknown)
                )
            },
            singleLine = true,
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}

/** "Name for the plug: Boiler DE 4h max0.20  [Copy]" under the edit fields. */
@Composable
private fun NamePreview(name: String, onCopy: () -> Unit) {
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Hint(stringResource(R.string.name_preview))
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        TextButton(onClick = onCopy) { Text(stringResource(R.string.name_copy)) }
    }
}

@Composable
private fun Chip(text: String, color: Color, description: String? = null) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .then(
                if (description != null) Modifier.clearAndSetSemantics { contentDescription = description }
                else Modifier
            )
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/** Chart strip: colour by price, green = on, white line = now. */
@Composable
private fun PlanStrip(plan: PlanView, now: Long) {
    val zone = ZoneId.systemDefault()
    val start = plan.slots.first().first
    val end = plan.slots.last().first + SLOT_MS
    val span = (end - start).coerceAtLeast(1L)
    val startDay = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
    // Starts of new days become separators on the strip.
    val midnights = remember(plan) {
        plan.slots.map { it.first }.filter { t ->
            val lt = Instant.ofEpochMilli(t).atZone(zone)
            t > start && lt.hour == 0 && lt.minute == 0
        }
    }

    // Spoken summary for TalkBack: the strip itself is only colour.
    val chartDescription = stringResource(
        R.string.plan_chart_desc,
        "%.3f".format(plan.minPrice),
        "%.3f".format(plan.maxPrice),
        minutesText(plan.onMinutes)
    )
    Canvas(
        modifier = Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(4.dp))
            .semantics { contentDescription = chartDescription }
    ) {
        val gap = 1f // thin gap between slots, px
        fun x(t: Long) = (t - start).toFloat() / span * size.width
        plan.slots.forEach { (t, price) ->
            val rel = ((price - plan.minPrice) / (plan.maxPrice - plan.minPrice + 1e-9))
                .toFloat().coerceIn(0f, 1f)
            val color = if (rel < 0.5f) {
                lerp(PlugPalette.Primary, PlugPalette.Middle, rel * 2f)
            } else {
                lerp(PlugPalette.Middle, PlugPalette.Error, (rel - 0.5f) * 2f)
            }
            val x0 = x(t)
            drawRect(color, Offset(x0, 0f), Size((x(t + SLOT_MS) - x0 - gap).coerceAtLeast(0.5f), size.height))
        }
        // On-blocks fill the lower half, so the price stays visible above.
        val half = size.height * 0.45f
        plan.blocks.forEach { (bs, be) ->
            drawRect(
                PlugPalette.Good,
                Offset(x(bs), size.height - half),
                Size((x(be) - x(bs)).coerceAtLeast(2f), half)
            )
        }
        val line = 2.dp.toPx()
        midnights.forEach { t ->
            drawRect(PlugPalette.Background, Offset(x(t) - line / 2, 0f), Size(line, size.height))
        }
        if (now in start..end) {
            drawLine(
                Color.White, Offset(x(now), 0f), Offset(x(now), size.height),
                strokeWidth = line, cap = StrokeCap.Round
            )
        }
    }

    val coverage = ((end - now) / 60_000L).toInt().coerceAtLeast(0)
    val coveredDays = plan.slots
        .map { Instant.ofEpochMilli(it.first).atZone(zone).toLocalDate() }
        .distinct().size
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        LegendDot(PlugPalette.Primary, stringResource(R.string.legend_cheap))
        LegendDot(PlugPalette.Error, stringResource(R.string.legend_expensive))
        LegendDot(PlugPalette.Good, stringResource(R.string.legend_on))
    }
    val days = when {
        coveredDays >= 3 -> pluralStringResource(R.plurals.plan_days, coveredDays, coveredDays)
        coveredDays == 2 -> stringResource(R.string.plan_today_tomorrow)
        startDay == LocalDate.now(zone) -> stringResource(R.string.plan_today)
        else -> stringResource(R.string.plan_one_day)
    }
    Hint(stringResource(R.string.plan_covered, days, minutesText(coverage)))
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(color))
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, end = 12.dp)
    )
}
