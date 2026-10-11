package com.tinycrap.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tinycrap.app.TinyCrapApp
import com.tinycrap.app.data.ApiException
import com.tinycrap.app.data.DownloadRequest
import com.tinycrap.app.data.DownloadTask
import com.tinycrap.app.data.LibraryItem
import com.tinycrap.app.data.LinkParser
import com.tinycrap.app.data.MediaInfo
import com.tinycrap.app.data.MediaKind
import com.tinycrap.app.data.Platform
import com.tinycrap.app.data.VideoQuality
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AnalyzeState {
    data object Idle : AnalyzeState
    data object Loading : AnalyzeState
    data class Ready(val media: MediaInfo) : AnalyzeState
    data class Failed(val message: String) : AnalyzeState
}

enum class ResultTab { Video, Audio }

data class UiState(
    val input: String = "",
    val platform: Platform? = null,
    val analyze: AnalyzeState = AnalyzeState.Idle,
    val tab: ResultTab = ResultTab.Video,
    val qualityKey: String? = null,
    val container: String? = null,
    val audioFormat: String = "best",     // "best" means "no conversion"; chips only choose converted formats
    val audioBitrate: Int? = null,
    /** The download most recently started from this screen; its progress card is shown on Home. */
    val lastTaskId: String? = null,
)

/** Home screen: paste a link, look at the options, add a download to the queue. */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val tc = app as TinyCrapApp
    private val settings = tc.settings
    private val api = tc.api
    private val downloads = tc.downloads

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    /** Progress of the download started here (null when none, or after it was dismissed). */
    val lastTask: StateFlow<DownloadTask?> = combine(downloads.tasks, _state.map { it.lastTaskId }) { tasks, id ->
        tasks.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The latest three finished files, shown as "Recent" on Home. */
    val recent: StateFlow<List<LibraryItem>> = tc.library.items.map { it.take(3) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var analyzeJob: Job? = null

    // ---- input ----------------------------------------------------------------------------

    fun onInputChange(text: String) {
        _state.update { it.copy(input = text, platform = LinkParser.guessPlatform(text) ?: it.platform) }
    }

    fun onPlatformSelected(platform: Platform) {
        _state.update { it.copy(platform = platform) }
    }

    /** Pasted from the clipboard: clean it up and analyse straight away. */
    fun onPasted(text: String) {
        val url = LinkParser.extractUrl(text) ?: text.trim()
        _state.update { it.copy(input = url, platform = LinkParser.guessPlatform(url) ?: it.platform) }
        submit()
    }

    /** Link shared into the app from Instagram / YouTube / Facebook. */
    fun onSharedText(text: String) = onPasted(text)

    fun clearInput() {
        analyzeJob?.cancel()
        _state.update { it.copy(input = "", platform = null, analyze = AnalyzeState.Idle) }
    }

    // ---- analysis -------------------------------------------------------------------------

    fun submit() {
        val raw = _state.value.input
        val url = LinkParser.extractUrl(raw) ?: raw.trim()
        if (url.isEmpty()) {
            _state.update { it.copy(analyze = AnalyzeState.Failed("Paste a link first.")) }
            return
        }
        analyzeJob?.cancel()
        _state.update {
            it.copy(
                input = url,
                platform = LinkParser.guessPlatform(url) ?: it.platform,
                analyze = AnalyzeState.Loading,
            )
        }
        analyzeJob = viewModelScope.launch {
            try {
                val media = api.analyze(url)
                _state.update { applyDefaults(it, media) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                _state.update { it.copy(analyze = AnalyzeState.Failed(e.message)) }
            } catch (e: Exception) {
                _state.update { it.copy(analyze = AnalyzeState.Failed("Something went wrong. Please try again.")) }
            }
        }
    }

    /** Pre-selects what Settings says: highest wanted quality, preferred container, preferred audio. */
    private fun applyDefaults(s: UiState, media: MediaInfo): UiState {
        val prefs = settings.state.value
        val quality = pickQuality(media.video.qualities, prefs.defaultQuality)
        val containers = quality?.variants?.map { it.container }.orEmpty()
        val container = when {
            prefs.defaultContainer in containers -> prefs.defaultContainer
            media.video.defaultContainer in containers -> media.video.defaultContainer
            else -> containers.firstOrNull()
        }
        val wantedAudio = media.audio.formats.firstOrNull { it.format == prefs.defaultAudio && it.format != "best" }
        return s.copy(
            platform = media.platform,           // the backend decides the platform
            analyze = AnalyzeState.Ready(media),
            tab = if (media.video.available) ResultTab.Video else ResultTab.Audio,
            qualityKey = quality?.key,
            container = container,
            audioFormat = wantedAudio?.format ?: "best",
            audioBitrate = wantedAudio?.defaultBitrate,
        )
    }

    private fun pickQuality(list: List<VideoQuality>, maxHeight: Int): VideoQuality? {
        if (list.isEmpty()) return null
        if (maxHeight <= 0) return list.first()
        return list.firstOrNull { (it.height ?: Int.MAX_VALUE) <= maxHeight } ?: list.last()
    }

    // ---- selections -----------------------------------------------------------------------

    fun selectTab(tab: ResultTab) = _state.update { it.copy(tab = tab) }

    fun selectQuality(key: String) {
        val media = readyMedia() ?: return
        val quality = media.video.qualities.firstOrNull { it.key == key } ?: return
        _state.update {
            val available = quality.variants.map { v -> v.container }
            it.copy(qualityKey = key, container = if (it.container in available) it.container else available.firstOrNull())
        }
    }

    fun selectContainer(container: String) = _state.update { it.copy(container = container) }

    fun selectAudioFormat(format: String) {
        val media = readyMedia() ?: return
        val spec = media.audio.formats.firstOrNull { it.format == format } ?: return
        _state.update { it.copy(audioFormat = format, audioBitrate = spec.defaultBitrate) }
    }

    fun selectAudioBitrate(bitrate: Int) = _state.update { it.copy(audioBitrate = bitrate) }

    private fun readyMedia(): MediaInfo? = (_state.value.analyze as? AnalyzeState.Ready)?.media

    // ---- downloads ------------------------------------------------------------------------

    fun downloadVideo() {
        val s = _state.value
        val media = readyMedia() ?: return
        val quality = media.video.qualities.firstOrNull { it.key == s.qualityKey } ?: return
        val container = s.container ?: return
        val variant = quality.variants.firstOrNull { it.container == container }
        enqueue(
            media,
            DownloadRequest(
                url = media.url, platform = media.platform, title = media.title, uploader = media.uploader,
                thumbnail = media.thumbnail, durationText = media.durationText, kind = MediaKind.Video,
                label = "${quality.label} · ${container.uppercase()}",
                quality = quality.key, container = container, estimatedSize = variant?.size,
            ),
        )
    }

    /** One tap: the original audio stream, no re-encoding. */
    fun downloadBestAudio() {
        val media = readyMedia() ?: return
        val ext = media.audio.formats.firstOrNull { it.format == "best" }?.ext?.uppercase() ?: "Audio"
        enqueue(media, audioRequest(media, format = "best", bitrate = null, label = "Original $ext"))
    }

    fun downloadConvertedAudio() {
        val s = _state.value
        val media = readyMedia() ?: return
        if (s.audioFormat == "best") return
        val spec = media.audio.formats.firstOrNull { it.format == s.audioFormat } ?: return
        val label = listOfNotNull(spec.label, s.audioBitrate?.let { "$it kbps" }).joinToString(" · ")
        enqueue(media, audioRequest(media, s.audioFormat, s.audioBitrate, label))
    }

    private fun audioRequest(media: MediaInfo, format: String, bitrate: Int?, label: String) = DownloadRequest(
        url = media.url, platform = media.platform, title = media.title, uploader = media.uploader,
        thumbnail = media.thumbnail, durationText = media.durationText, kind = MediaKind.Audio,
        label = label, audioFormat = format, bitrate = bitrate,
    )

    private fun enqueue(media: MediaInfo, request: DownloadRequest) {
        val id = downloads.enqueue(request)
        _state.update { it.copy(lastTaskId = id) }
        _messages.tryEmit(UiMessage("Added to Downloaded", viewDownloads = true))
    }

    fun cancelTask(id: String) = downloads.cancel(id)

    fun retryTask(id: String) = downloads.retry(id)

    /** Hides the progress card; a finished file stays in Downloaded. */
    fun dismissTask(id: String) {
        downloads.cancel(id)
        _state.update { if (it.lastTaskId == id) it.copy(lastTaskId = null) else it }
    }
}
