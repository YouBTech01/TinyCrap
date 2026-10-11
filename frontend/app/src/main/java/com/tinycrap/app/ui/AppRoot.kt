package com.tinycrap.app.ui

import kotlin.math.absoluteValue
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tinycrap.app.TinyCrapApp
import com.tinycrap.app.ui.components.rememberReducedMotion
import com.tinycrap.app.ui.downloads.DownloadsScreen
import com.tinycrap.app.ui.downloads.DownloadsViewModel
import com.tinycrap.app.ui.home.HomeScreen
import com.tinycrap.app.ui.nav.FloatingNavBar
import com.tinycrap.app.ui.settings.SettingsScreen
import com.tinycrap.app.ui.settings.SettingsViewModel
import com.tinycrap.app.ui.splash.SplashOverlay
import com.tinycrap.app.ui.theme.TinyCrapTheme
import com.tinycrap.app.ui.update.UpdateRequiredScreen
import com.tinycrap.app.ui.util.rememberHaptic
import kotlinx.coroutines.launch

private const val HOME = 0
private const val DOWNLOADS = 1
private const val SETTINGS = 2

/** The whole app: three swipeable pages and a floating bar to jump between them. */
@Composable
fun TinyCrapRoot(
    home: MainViewModel,
    requestedTab: Int?,
    onTabHandled: () -> Unit,
) {
    val settingsVm: SettingsViewModel = viewModel()
    val downloadsVm: DownloadsViewModel = viewModel()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()

    TinyCrapTheme(settings) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val pager = rememberPagerState(initialPage = HOME, pageCount = { 3 })
            val snackbar = remember { SnackbarHostState() }
            val activeDownloads by downloadsVm.activeCount.collectAsStateWithLifecycle()
            val tick = rememberHaptic(settings.haptics)
            val reduced = rememberReducedMotion()
            val requiredUpdate by (context.applicationContext as TinyCrapApp).updates.required.collectAsStateWithLifecycle()
            var showSplash by rememberSaveable { mutableStateOf(true) }
            val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val barSpace = PaddingValues(bottom = navInset + 86.dp)

            // The bar's slide is a plain Animatable that is only read while drawing, so hiding or showing it
            // never recomposes the screens (that was a source of stutter while scrolling).
            val barHidden = remember { Animatable(0f) }
            val setBar = remember {
                var shown = true
                fun(show: Boolean) {
                    if (show == shown) return
                    shown = show
                    scope.launch { barHidden.animateTo(if (show) 0f else 1f, spring(dampingRatio = 0.9f, stiffness = 420f)) }
                }
            }
            val barConnection = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (available.y < -10f) setBar(false) else if (available.y > 10f) setBar(true)
                        return Offset.Zero
                    }
                }
            }

            // Stable callbacks: a new lambda per recomposition would make every page recompose when the tab changes.
            val goTo = remember<(Int) -> Unit> { { page -> scope.launch { pager.animateScrollToPage(page) }; Unit } }
            val seeDownloads = remember { { goTo(DOWNLOADS) } }
            val currentTick = androidx.compose.runtime.rememberUpdatedState(tick)
            val onSelectTab = remember<(Int) -> Unit> { { page -> currentTick.value(); setBar(true); goTo(page) } }

            // Ask once for the notification permission (Android 13+), so finished downloads can tell you.
            val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                settingsVm.update { it.copy(notifyDone = granted && it.notifyDone) }
            }
            LaunchedEffect(Unit) {
                val app = context.applicationContext as TinyCrapApp
                if (!settings.askedNotifications) {
                    settingsVm.update { it.copy(askedNotifications = true) }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !app.notifier.canNotify()) {
                        askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            LaunchedEffect(requestedTab) {
                if (requestedTab != null) {
                    pager.animateScrollToPage(requestedTab)
                    onTabHandled()
                }
            }

            // Snackbar messages from the Home and Downloaded screens.
            LaunchedEffect(Unit) {
                launch { home.messages.collect { show(snackbar, it, goTo) } }
                launch { downloadsVm.messages.collect { show(snackbar, it, goTo) } }
            }

            BackHandler(enabled = pager.currentPage != HOME) { goTo(HOME) }

            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize().nestedScroll(barConnection),
                    beyondViewportPageCount = 1,       // only the neighbours are kept ready; composing all three up front made start-up and swipes stutter
                ) { page ->
                    // Pages ease in and out: slight parallax, scale and fade. Only the draw layer changes.
                    Box(
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
                            val distance = offset.absoluteValue.coerceIn(0f, 1f)
                            val s = 1f - 0.05f * distance
                            scaleX = s
                            scaleY = s
                            alpha = 1f - 0.55f * distance
                            // Parallax only while a page is mid-swipe; at rest a neighbouring page (distance 1) must sit exactly off-screen.
                            translationX = size.width * offset * 0.18f * (1f - distance)
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        },
                    ) {
                        when (page) {
                            HOME -> HomeScreen(home, barSpace, onSeeDownloads = seeDownloads)
                            DOWNLOADS -> DownloadsScreen(downloadsVm, visible = pager.currentPage == DOWNLOADS, contentPadding = barSpace)
                            else -> SettingsScreen(settingsVm, barSpace)
                        }
                    }
                }

                SnackbarHost(
                    hostState = snackbar,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 76.dp),
                )

                FloatingNavBar(
                    selected = pager.currentPage,
                    activeDownloads = activeDownloads,
                    onSelect = onSelectTab,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp)
                        .graphicsLayer {
                            // Slides down out of the way while you scroll a list, and comes back when you scroll up.
                            val hidden = barHidden.value
                            translationY = hidden * (size.height + 48.dp.toPx())
                            alpha = 1f - hidden
                        },
                )

                AnimatedVisibility(
                    visible = showSplash && settings.splashAnimation && !reduced,
                    exit = fadeOut(tween(320)) + scaleOut(tween(320), targetScale = 1.08f),
                ) {
                    SplashOverlay(onFinished = { showSplash = false })
                }

                // A required update covers everything and cannot be dismissed.
                requiredUpdate?.let { UpdateRequiredScreen(it) }
            }
        }
    }
}

private suspend fun show(host: SnackbarHostState, message: UiMessage, goTo: (Int) -> Unit) {
    val result = host.showSnackbar(
        message = message.text,
        actionLabel = if (message.viewDownloads) "View" else null,
        withDismissAction = !message.viewDownloads,
    )
    if (result == SnackbarResult.ActionPerformed && message.viewDownloads) goTo(DOWNLOADS)
}
