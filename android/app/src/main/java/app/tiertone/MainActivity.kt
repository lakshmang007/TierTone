package app.tiertone

import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.getcapacitor.BridgeActivity

/** Hosts the React UI. Runs in the `:ui` process, separate from the always-on listener. */
class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        registerPlugin(TierTonePlugin::class.java)
        super.onCreate(savedInstanceState)
    }
}
