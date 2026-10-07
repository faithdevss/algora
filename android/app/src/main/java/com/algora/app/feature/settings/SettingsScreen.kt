package com.algora.app.feature.settings

import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.algora.app.core.share.AppShare
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.data.settings.AccentColor
import com.algora.app.core.data.settings.DEFAULT_DAILY_REMINDER_MINUTE
import com.algora.app.core.data.settings.SettingsRepository
import com.algora.app.core.data.settings.ThemeMode
import com.algora.app.core.data.settings.settingsDataStore
import com.algora.app.core.nav.AppMode
import com.algora.app.core.notify.DailyReminder
import com.algora.app.core.notify.DailyReminderWorker
import com.algora.app.core.notify.StudyReminder
import com.algora.app.core.notify.StudyReminderWorker
import com.algora.app.core.ui.components.CrossPromoApp
import com.algora.app.core.ui.components.CrossPromoRow
import com.algora.app.core.ui.theme.ScreenGutter
import com.algora.app.core.ui.theme.SpaceGrotesk
import com.algora.app.core.ui.theme.accent
import kotlinx.coroutines.launch

// Appearance settings: theme mode (System/Light/Dark) and the accent swatch set from the
// docs/design/Algora.dc.html `accent` prop (#4f46e5, #7c3aed, #0ea5e9, #059669, #db2777), plus an
// Auto option (the default) that hands the accent over to the active DSA/AI mode.
// Both persist to DataStore and are applied by AlgoraTheme at the root.
@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context.settingsDataStore) }
    val scope = rememberCoroutineScope()
    val themeMode by settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    // null = the Auto option: the accent tracks the DSA/AI mode instead of a fixed swatch.
    val accent by settings.accent.collectAsState(initial = null)
    val remindersEnabled by settings.remindersEnabled.collectAsState(initial = true)
    val dailyEnabled by settings.dailyReminderEnabled.collectAsState(initial = true)
    val dailyMinute by settings.dailyReminderMinute.collectAsState(initial = DEFAULT_DAILY_REMINDER_MINUTE)
    var pickingTime by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenGutter),
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary,
                        ),
                    ),
                    RoundedCornerShape(22.dp),
                )
                .padding(18.dp),
        ) {
            // The arrow and the title together are the back control.
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = "Back", role = Role.Button, onClick = onBack)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.size(12.dp))
                Column {
                    Text("Preferences", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    Text(
                        "Settings",
                        color = Color.White,
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    )
                }
            }
        }

        SettingsCard(title = "Theme", subtitle = "How AlgorAI follows light and dark") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.background,
                        RoundedCornerShape(14.dp),
                    )
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    ModeChip(
                        label = when (mode) {
                            ThemeMode.SYSTEM -> "System"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        },
                        selected = mode == themeMode,
                        modifier = Modifier.weight(1f),
                    ) { scope.launch { settings.setThemeMode(mode) } }
                }
            }
        }

        SettingsCard(title = "Accent color", subtitle = "Tints buttons, progress and highlights") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AutoAccentSwatch(selected = accent == null) {
                    scope.launch { settings.setAccent(null) }
                }
                AccentColor.entries.forEach { option ->
                    AccentSwatch(
                        option = option,
                        selected = option == accent,
                    ) { scope.launch { settings.setAccent(option) } }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                accent?.label ?: "Auto — indigo in DSA mode, pink in AI mode",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        }

        SettingsCard(
            title = "Study reminders",
            subtitle = "One nudge after a week away — never more than one a week",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (remindersEnabled) "On" else "Off",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (remindersEnabled) {
                            "You'll hear from AlgorAI only if you go quiet for ${StudyReminder.INACTIVE_DAYS} days."
                        } else {
                            "AlgorAI will never send you a notification."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = remindersEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            settings.setRemindersEnabled(enabled)
                            // Flipping the toggle takes effect now rather than at the next launch.
                            if (enabled) {
                                StudyReminderWorker.schedule(context)
                            } else {
                                StudyReminderWorker.cancel(context)
                            }
                        }
                    },
                )
            }
        }

        SettingsCard(
            title = "Daily reminder",
            subtitle = "One nudge at your time, only on a day you haven't opened the app",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (dailyEnabled) "On" else "Off",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (dailyEnabled) {
                            "Only when cards are due or the drill is unfinished — and never once " +
                                "you've been away more than ${DailyReminder.MAX_GAP_DAYS} days."
                        } else {
                            "No daily nudge. The weekly one above is unaffected."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = dailyEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            settings.setDailyReminderEnabled(enabled)
                            if (enabled) {
                                DailyReminderWorker.schedule(context, dailyMinute, replaceExisting = true)
                            } else {
                                DailyReminderWorker.cancel(context)
                            }
                        }
                    },
                )
            }

            if (dailyEnabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pickingTime = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        "Remind me at",
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        formatMinuteOfDay(dailyMinute),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (pickingTime) {
            ReminderTimeDialog(
                minuteOfDay = dailyMinute,
                onDismiss = { pickingTime = false },
                onConfirm = { minute ->
                    pickingTime = false
                    scope.launch {
                        settings.setDailyReminderMinute(minute)
                        DailyReminderWorker.schedule(context, minute, replaceExisting = true)
                    }
                },
            )
        }

        SettingsCard(title = "Share AlgorAI", subtitle = "Send someone the install link") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { AppShare.share(context) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    "Share the app",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                )
            }
        }

        SettingsCard(title = "More from the developer", subtitle = "Other apps by the AlgorAI developer") {
            CrossPromoRow(CrossPromoApp.Systa)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                title,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.5.sp,
            )
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(11.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun RowScope.AccentSwatch(
    option: AccentColor,
    selected: Boolean,
    onClick: () -> Unit,
) {
    SwatchCircle(
        ring = Color(option.argb),
        fill = listOf(Color(option.argb), Color(option.gradientEnd)),
        selected = selected,
        contentDescription = "${option.label} selected",
        onClick = onClick,
    )
}

/**
 * The Auto option. Painted with both mode accents so the swatch itself says what it does — indigo
 * (DSA) bleeding into pink (AI) — rather than needing the caption underneath to explain it.
 */
@Composable
private fun RowScope.AutoAccentSwatch(selected: Boolean, onClick: () -> Unit) {
    val dsa = Color(AppMode.DSA.accent.argb)
    val ai = Color(AppMode.AI.accent.argb)
    SwatchCircle(
        ring = MaterialTheme.colorScheme.primary,
        fill = listOf(dsa, ai),
        selected = selected,
        contentDescription = "Auto accent selected",
        onClick = onClick,
    )
}

// Swatches share a row and size themselves by weight, so six of them still fit a narrow screen.
@Composable
private fun RowScope.SwatchCircle(
    ring: Color,
    fill: List<Color>,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) {
                    // The deeper accents all but vanish against the dark card, so the ring turns white there.
                    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color.White else ring
                } else Color.Transparent,
                shape = CircleShape,
            )
            .padding(4.dp)
            .background(Brush.linearGradient(fill), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// 24h minute-of-day as the wall-clock string the picker and the settings row both show. Locale is
// deliberately not consulted: the app's own copy is English, and a 12h string here would disagree
// with a 24h system picker on the same screen.
internal fun formatMinuteOfDay(minuteOfDay: Int): String {
    val hour = (minuteOfDay / 60).coerceIn(0, 23)
    val minute = (minuteOfDay % 60).coerceIn(0, 59)
    return "%02d:%02d".format(hour, minute)
}

// Rescheduling on confirm is the point: a time picked tonight has to take effect tonight, which is
// the one case the launch-time KEEP schedule cannot cover.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(
    minuteOfDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = minuteOfDay / 60,
        initialMinute = minuteOfDay % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remind me at") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
