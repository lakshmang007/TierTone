package app.tiertone.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var model: AppModel

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        model = AppModel(applicationContext)
        setContent { TierToneTheme { Root(model) } }
    }

    override fun onResume() {
        super.onResume()
        // Access may have changed in system settings while we were away.
        model.refresh()
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    APPS("Apps", @Suppress("DEPRECATION") Icons.Filled.List),
    VIPS("VIPs", Icons.Filled.Star),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
private fun Root(model: AppModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        // Live activity counts while the screen is visible; nothing runs in the background.
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(4_000)
                model.tick()
            }
        }
    }
    if (!model.config.onboarded) Onboarding(model) else MainTabs(model)
}

@Composable
private fun MainTabs(model: AppModel) {
    var tab by rememberSaveable { mutableIntStateOf(Tab.HOME.ordinal) }
    BackHandler(enabled = tab != Tab.HOME.ordinal) { tab = Tab.HOME.ordinal }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t.ordinal,
                        onClick = { tab = t.ordinal },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
            when (Tab.entries[tab]) {
                Tab.HOME -> HomeScreen(model, openApps = { tab = Tab.APPS.ordinal }, openVips = { tab = Tab.VIPS.ordinal })
                Tab.APPS -> AppsScreen(model)
                Tab.VIPS -> VipsScreen(model)
                Tab.SETTINGS -> SettingsScreen(model)
            }
        }
    }
}
