package com.tinycrap.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.tinycrap.app.data.Notifier
import com.tinycrap.app.ui.MainViewModel
import com.tinycrap.app.ui.TinyCrapRoot

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    /** 0 = Home, 1 = Downloaded, 2 = Settings; set by notifications and shared links. */
    private var requestedTab by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            TinyCrapRoot(viewModel, requestedTab, onTabHandled = { requestedTab = null })
        }
    }

    override fun onStart() {
        super.onStart()
        // Every time the app comes to the front, ask whether a newer version is required.
        lifecycleScope.launch { (application as TinyCrapApp).updates.refresh() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when {
            // A link shared from Instagram / YouTube / Facebook opens straight into analysis.
            intent?.action == Intent.ACTION_SEND && intent.type == "text/plain" -> {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let(viewModel::onSharedText)
                requestedTab = 0
            }
            // Tapping a download notification opens the Downloaded tab.
            intent?.getStringExtra(Notifier.EXTRA_TAB) == "downloads" -> requestedTab = 1
        }
    }
}
