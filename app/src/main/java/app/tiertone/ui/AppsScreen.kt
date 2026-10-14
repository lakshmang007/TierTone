package app.tiertone.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tiertone.core.Lane
import app.tiertone.core.Tier
import app.tiertone.engine.RuleEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(val pkg: String, val label: String)

fun appLabel(context: Context, pkg: String): String = try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
} catch (_: PackageManager.NameNotFoundException) {
    pkg
}

/** Launchable apps plus anything already configured or seen notifying. */
private fun loadApps(context: Context, extra: Collection<String>): List<AppEntry> {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val packages = LinkedHashSet<String>()
    @Suppress("DEPRECATION")
    pm.queryIntentActivities(launcher, 0).forEach { packages += it.activityInfo.packageName }
    packages += extra
    packages -= context.packageName
    return packages.map { AppEntry(it, appLabel(context, it)) }.sortedBy { it.label.lowercase() }
}

private enum class Filter(val label: String) { ALL("All"), SORTED("Sorted"), RECENT("Notified recently") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(model: AppModel) {
    val context = LocalContext.current
    val c = model.config
    val seen = model.stats.seen
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) { loadApps(context, c.appTiers.keys + seen.keys) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(Filter.ALL.ordinal) }

    // Sorted by recent activity, then name; deliberately not by tier, so rows don't jump while you tap.
    val shown = remember(apps, query, filter, c.appTiers, seen) {
        apps.orEmpty()
            .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) || it.pkg.contains(query, ignoreCase = true) }
            .filter {
                when (Filter.entries[filter]) {
                    Filter.ALL -> true
                    Filter.SORTED -> it.pkg in c.appTiers
                    Filter.RECENT -> it.pkg in seen
                }
            }
            .sortedWith(compareByDescending<AppEntry> { seen[it.pkg] ?: 0L }.thenBy { it.label.lowercase() })
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            ScreenTitle(
                "Apps",
                if (c.sortUnlisted) "Apps you don't list use the Normal sound." else "Apps you don't list keep their own sound.",
            )
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                placeholder = { Text("Search apps") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Filter.entries.forEach { f ->
                    FilterChip(selected = filter == f.ordinal, onClick = { filter = f.ordinal }, label = { Text(f.label) })
                }
            }
        }
        when {
            apps == null -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            shown.isEmpty() -> item {
                Text(
                    "No apps here yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
        items(shown, key = { it.pkg }) { app ->
            AppRow(app, c.appTiers[app.pkg], c.sortUnlisted, onTier = { model.setTier(app.pkg, it) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRow(app: AppEntry, tier: Tier?, sortUnlisted: Boolean, onTier: (Tier?) -> Unit) {
    val status = when (tier) {
        null -> if (sortUnlisted) "Not listed · Normal sound" else "Not listed · keeps its own sound"
        Tier.IMPORTANT -> "Important sound, pops up on screen"
        Tier.NORMAL -> "Normal sound"
        Tier.MUTED -> "Silent, still in your notification shade"
    }
    val chat = app.pkg in RuleEngine.CHAT_APPS
    Column(Modifier.padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.pkg, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (chat) "$status · VIPs override" else status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tier != null) {
                IconButton(onClick = { onTier(null) }) { Icon(Icons.Filled.Close, contentDescription = "Remove from list") }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 52.dp, top = 8.dp)) {
            Tier.entries.forEachIndexed { i, t ->
                val color = laneColor(Lane.of(t))
                SegmentedButton(
                    selected = tier == t,
                    onClick = { onTier(if (tier == t) null else t) },
                    shape = SegmentedButtonDefaults.itemShape(i, Tier.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = color.copy(alpha = 0.18f),
                        activeContentColor = color,
                    ),
                    label = { Text(t.label, maxLines = 1) },
                )
            }
        }
    }
}
