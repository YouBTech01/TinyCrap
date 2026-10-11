package com.tinycrap.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinycrap.app.data.Notifier
import com.tinycrap.app.ui.MainViewModel
import com.tinycrap.app.ui.share.QuickDownloadContent
import com.tinycrap.app.ui.update.UpdateRequiredContent
import com.tinycrap.app.ui.update.openLink
import androidx.lifecycle.lifecycleScope
import com.tinycrap.app.ui.theme.TinyCrapTheme
import kotlinx.coroutines.launch

/**
 * The "Tiny Crap" entry in other apps' Share menu. It opens as a small sheet on top of
 * Instagram / YouTube / Facebook, shows every quality and format, and starts the download
 * in the background, so the main app never has to open.
 */
class ShareActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)

        val app = application as TinyCrapApp
        setContent {
            val settings by app.settings.state.collectAsStateWithLifecycle()
            TinyCrapTheme(settings) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val scope = rememberCoroutineScope()
                fun close() {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { finish() }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    ModalBottomSheet(
                        onDismissRequest = { finish() },
                        sheetState = sheetState,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        val required by app.updates.required.collectAsStateWithLifecycle()
                        val update = required
                        if (update != null) {
                            // Downloads stay locked until the newer version is installed.
                            UpdateRequiredContent(update) { openLink(this@ShareActivity, update.link) }
                        } else {
                            QuickDownloadContent(
                                vm = viewModel,
                                onClose = ::close,
                                onOpenApp = {
                                    startActivity(
                                        Intent(this@ShareActivity, MainActivity::class.java)
                                            .putExtra(Notifier.EXTRA_TAB, "downloads")
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                    finish()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { (application as TinyCrapApp).updates.refresh() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            viewModel.onSharedText(text)
        }
    }
}
