package com.streamdeck.iptv.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.streamdeck.iptv.data.ContentKind
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer as VlcMediaPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

private data class VlcTrackChoice(
    val id: Int,
    val name: String,
)

@Composable
internal fun VlcPlayerScreen(
    sources: List<String>,
    title: String,
    contentKind: ContentKind,
    startPositionMs: Long,
    onProgress: (Long, Long) -> Unit,
    onBack: () -> Unit,
    onUseMedia3: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val responsive = rememberResponsiveLayout()
    var sourceIndex by remember(sources) { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentTime by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var softwareDecode by remember { mutableStateOf(false) }
    var videoOutputCount by remember { mutableIntStateOf(0) }
    var audioTracks by remember { mutableStateOf(emptyList<VlcTrackChoice>()) }
    var subtitleTracks by remember {
        mutableStateOf(listOf(VlcTrackChoice(-1, "Off")))
    }
    var selectedAudioTrack by remember { mutableIntStateOf(-1) }
    var selectedSubtitleTrack by remember { mutableIntStateOf(-1) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlActivity by remember { mutableIntStateOf(0) }
    var trackMenuExpanded by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var playbackRequested by remember { mutableStateOf(true) }
    var lifecycleStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var selectedQuality by remember { mutableIntStateOf(QUALITY_AUTO) }
    var resumePositionForNextAttempt by remember(sources, startPositionMs) {
        mutableLongStateOf(startPositionMs.coerceAtLeast(0L))
    }
    var lastReportedPositionMs by remember(sources) { mutableLongStateOf(Long.MIN_VALUE) }
    var lastReportedDurationMs by remember(sources) { mutableLongStateOf(Long.MIN_VALUE) }
    var queuedFallback by remember(sources) { mutableStateOf<Pair<String, String>?>(null) }
    val fallbackTransitionGate = remember(sources) { SingleFlightGate() }
    val playerScope = rememberCoroutineScope()
    val controlFocusRequester = remember { FocusRequester() }
    val backFocusRequester = remember { FocusRequester() }
    val otherPlayerFocusRequester = remember { FocusRequester() }
    val rewindFocusRequester = remember { FocusRequester() }
    val playFocusRequester = remember { FocusRequester() }
    val forwardFocusRequester = remember { FocusRequester() }
    val qualityFocusRequester = remember { FocusRequester() }
    val audioFocusRequester = remember { FocusRequester() }
    val subtitleFocusRequester = remember { FocusRequester() }
    val isLive = contentKind == ContentKind.LIVE
    val activeUrl = sources[sourceIndex.coerceIn(sources.indices)]
    val audioEnabled = audioTracks.size > 1
    val subtitlesEnabled = subtitleTracks.size > 1
    val playbackAttemptKey = "$sourceIndex:$softwareDecode:$selectedQuality"
    var initialSeekApplied by remember(activeUrl, softwareDecode, selectedQuality, startPositionMs) {
        mutableStateOf(false)
    }
    val qualityChoices = remember {
        listOf(
            VlcTrackChoice(QUALITY_AUTO, "Auto"),
            VlcTrackChoice(720, "720p"),
            VlcTrackChoice(1_080, "1080p"),
            VlcTrackChoice(1_440, "1440p"),
            VlcTrackChoice(2_160, "2160p"),
            VlcTrackChoice(QUALITY_MAX, "Max"),
        )
    }

    val libVlc = remember {
        LibVLC(
            context,
            arrayListOf(
                "--network-caching=8000",
                "--live-caching=8000",
                "--http-reconnect",
                "--no-drop-late-frames",
                "--no-skip-frames",
            ),
        )
    }
    val mediaPlayer = remember(libVlc) { VlcMediaPlayer(libVlc) }

    fun refreshTrackChoices() {
        audioTracks = runCatching {
            mediaPlayer.audioTracks
                .orEmpty()
                .filter { it.id >= 0 }
                .map { VlcTrackChoice(it.id, it.name.ifBlank { "Audio ${it.id}" }) }
        }.getOrDefault(emptyList())
        subtitleTracks = listOf(VlcTrackChoice(-1, "Off")) + runCatching {
            mediaPlayer.spuTracks
                .orEmpty()
                .filter { it.id >= 0 }
                .map { VlcTrackChoice(it.id, it.name.ifBlank { "Subtitle ${it.id}" }) }
        }.getOrDefault(emptyList())
        selectedAudioTrack = runCatching { mediaPlayer.audioTrack }.getOrDefault(-1)
        selectedSubtitleTrack = runCatching { mediaPlayer.spuTrack }.getOrDefault(-1)
    }

    fun captureResumePosition(): Long {
        if (isLive) return 0L
        val playerPosition = runCatching { mediaPlayer.time.coerceAtLeast(0L) }
            .getOrDefault(0L)
        val observedPosition = if (initialSeekApplied || resumePositionForNextAttempt <= 0L) {
            when {
                playerPosition > 0L -> playerPosition
                currentTime > 0L -> currentTime
                else -> resumePositionForNextAttempt
            }
        } else {
            resumePositionForNextAttempt
        }
        if (observedPosition > 0L) {
            resumePositionForNextAttempt = observedPosition
            currentTime = observedPosition
        }
        return resumePositionForNextAttempt.coerceAtLeast(0L)
    }

    fun reportProgressIfChanged(
        positionMs: Long = captureResumePosition(),
        durationMs: Long = duration.coerceAtLeast(0L),
    ) {
        if (
            isLive ||
            positionMs <= 0L ||
            (positionMs == lastReportedPositionMs && durationMs == lastReportedDurationMs)
        ) {
            return
        }
        lastReportedPositionMs = positionMs
        lastReportedDurationMs = durationMs
        onProgress(positionMs, durationMs)
    }

    fun leavePlayer() {
        reportProgressIfChanged()
        onBack()
    }

    fun advanceAutomaticFallback(
        reason: String,
        generation: String = playbackAttemptKey,
    ) {
        if (generation != playbackAttemptKey) return
        if (!lifecycleStarted) {
            queuedFallback = generation to reason
            return
        }
        if (!fallbackTransitionGate.tryAcquire()) {
            queuedFallback = generation to reason
            return
        }
        queuedFallback = null
        playerScope.launch {
            if (!lifecycleStarted || !playbackRequested) {
                fallbackTransitionGate.release()
                return@launch
            }
            reportProgressIfChanged()
            playbackError = reason
            isBuffering = true
            runCatching { mediaPlayer.stop() }
            // Give the current native decoder and surface time to shut down
            // before another playback attempt acquires them.
            delay(450)
            if (!lifecycleStarted || !playbackRequested) {
                queuedFallback = generation to reason
                fallbackTransitionGate.release()
                return@launch
            }
            when {
                !softwareDecode -> softwareDecode = true
                sourceIndex < sources.lastIndex -> {
                    sourceIndex += 1
                    softwareDecode = false
                }
                else -> {
                    reportProgressIfChanged()
                    onUseMedia3()
                }
            }
        }
    }

    fun showControls() {
        controlsVisible = true
        controlActivity += 1
    }

    BackHandler(onBack = ::leavePlayer)

    DisposableEffect(libVlc, mediaPlayer) {
        onDispose {
            runCatching { mediaPlayer.stop() }
            runCatching { mediaPlayer.vlcVout.detachViews() }
            runCatching { mediaPlayer.release() }
            runCatching { libVlc.release() }
        }
    }

    DisposableEffect(mediaPlayer, activeUrl, softwareDecode, selectedQuality, lifecycleOwner) {
        val listener = VlcMediaPlayer.EventListener { event ->
            when (event.type) {
                VlcMediaPlayer.Event.Playing -> {
                    if (!playbackRequested || !lifecycleStarted) {
                        isPlaying = false
                        isBuffering = false
                        runCatching { mediaPlayer.pause() }
                    } else {
                        isPlaying = true
                        isBuffering = false
                        playbackError = null
                        if (
                            !isLive &&
                            !initialSeekApplied &&
                            resumePositionForNextAttempt > 0L
                        ) {
                            mediaPlayer.time = resumePositionForNextAttempt
                            currentTime = resumePositionForNextAttempt
                            initialSeekApplied = true
                        }
                        refreshTrackChoices()
                    }
                }
                VlcMediaPlayer.Event.Buffering -> {
                    isBuffering =
                        lifecycleStarted && playbackRequested && event.buffering < 100f
                }
                VlcMediaPlayer.Event.Paused,
                VlcMediaPlayer.Event.Stopped -> {
                    isPlaying = false
                    isBuffering = false
                }
                VlcMediaPlayer.Event.EndReached -> {
                    reportProgressIfChanged()
                    playbackRequested = false
                    isPlaying = false
                    isBuffering = false
                }
                VlcMediaPlayer.Event.TimeChanged -> if (!isScrubbing) {
                    val eventPosition = max(0L, event.timeChanged)
                    if (eventPosition > 0L) {
                        currentTime = eventPosition
                        if (initialSeekApplied || resumePositionForNextAttempt <= 0L) {
                            resumePositionForNextAttempt = eventPosition
                        }
                    }
                }
                VlcMediaPlayer.Event.LengthChanged -> duration = max(0L, event.lengthChanged)
                VlcMediaPlayer.Event.Vout -> videoOutputCount = event.voutCount
                VlcMediaPlayer.Event.ESAdded,
                VlcMediaPlayer.Event.ESDeleted,
                VlcMediaPlayer.Event.ESSelected -> refreshTrackChoices()
                VlcMediaPlayer.Event.EncounteredError -> {
                    advanceAutomaticFallback(
                        "Playback failed; selecting the next built-in fallback.",
                        playbackAttemptKey,
                    )
                }
            }
        }
        mediaPlayer.setEventListener(listener)
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    lifecycleStarted = true
                    if (playbackRequested && !mediaPlayer.isPlaying) {
                        isBuffering = true
                        mediaPlayer.play()
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    lifecycleStarted = false
                    reportProgressIfChanged()
                    if (mediaPlayer.isPlaying) mediaPlayer.pause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        currentTime = if (isLive) 0L else resumePositionForNextAttempt
        duration = 0L
        isBuffering = lifecycleStarted && playbackRequested
        videoOutputCount = 0
        audioTracks = emptyList()
        subtitleTracks = listOf(VlcTrackChoice(-1, "Off"))
        selectedAudioTrack = -1
        selectedSubtitleTrack = -1
        val media = Media(libVlc, Uri.parse(activeUrl)).apply {
            setHWDecoderEnabled(!softwareDecode, false)
            addOption(":network-caching=8000")
            addOption(":live-caching=8000")
            addOption(":http-reconnect")
            addOption(
                if (selectedQuality == QUALITY_MAX) {
                    ":adaptive-logic=highest"
                } else {
                    ":adaptive-logic=rate"
                },
            )
            if (selectedQuality > 0 && selectedQuality != QUALITY_MAX) {
                addOption(":adaptive-maxheight=$selectedQuality")
            }
        }
        mediaPlayer.media = media
        media.release()
        mediaPlayer.aspectRatio = null
        mediaPlayer.scale = 0f
        if (lifecycleStarted && playbackRequested) {
            mediaPlayer.play()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            reportProgressIfChanged()
            mediaPlayer.setEventListener(null)
            runCatching { mediaPlayer.stop() }
        }
    }

    LaunchedEffect(mediaPlayer, activeUrl, contentKind) {
        while (true) {
            delay(5_000)
            if (!isLive && lifecycleStarted && playbackRequested && mediaPlayer.isPlaying) {
                reportProgressIfChanged()
            }
        }
    }

    LaunchedEffect(activeUrl, softwareDecode, selectedQuality) {
        delay(650)
        fallbackTransitionGate.release()
        val pending = queuedFallback
        queuedFallback = null
        if (pending?.first == playbackAttemptKey) {
            advanceAutomaticFallback(pending.second, pending.first)
        }
        var activeWatchdogMs = 0L
        var startupWatchdogMs = 0L
        while (
            activeWatchdogMs < NO_VIDEO_WATCHDOG_MS &&
            startupWatchdogMs < STARTUP_WATCHDOG_MS
        ) {
            delay(NO_VIDEO_WATCHDOG_POLL_MS)
            if (lifecycleStarted && playbackRequested) {
                startupWatchdogMs += NO_VIDEO_WATCHDOG_POLL_MS
                if (isPlaying) {
                    activeWatchdogMs += NO_VIDEO_WATCHDOG_POLL_MS
                }
            }
        }
        if (lifecycleStarted && playbackRequested && videoOutputCount == 0) {
            advanceAutomaticFallback("No video output detected; selecting the next built-in fallback.")
        }
    }

    LaunchedEffect(lifecycleStarted, playbackAttemptKey) {
        if (lifecycleStarted) {
            val pending = queuedFallback
            queuedFallback = null
            if (pending?.first == playbackAttemptKey) {
                advanceAutomaticFallback(pending.second, pending.first)
            }
        }
    }

    LaunchedEffect(Unit) {
        controlFocusRequester.requestFocus()
    }

    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) {
            controlFocusRequester.requestFocus()
        } else {
            delay(80)
            runCatching { playFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(controlActivity, controlsVisible, isPlaying, trackMenuExpanded) {
        if (controlsVisible && isPlaying && !trackMenuExpanded) {
            delay(5_000)
            controlsVisible = false
        }
    }

    fun openInAnotherPlayer() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(activeUrl), "video/*")
            putExtra(Intent.EXTRA_TITLE, title)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Open with video player"))
        } catch (_: ActivityNotFoundException) {
            playbackError = "No other compatible video player is installed."
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
	            .onPreviewKeyEvent { event ->
	                if (event.type == KeyEventType.KeyDown) {
	                    val consumeToReveal = !controlsVisible
	                    showControls()
	                    if (consumeToReveal) {
	                        runCatching { playFocusRequester.requestFocus() }
	                    }
	                    consumeToReveal
	                } else {
                    false
                }
            }
            .focusRequester(controlFocusRequester)
            .focusable(),
    ) {
        AndroidView(
            factory = { viewContext ->
                SurfaceView(viewContext).also { surface ->
                    surface.keepScreenOn = true
                    surface.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                        val width = right - left
                        val height = bottom - top
                        if (width > 0 && height > 0) {
                            mediaPlayer.vlcVout.setWindowSize(width, height)
                        }
                    }
                    mediaPlayer.vlcVout.setVideoView(surface)
                    mediaPlayer.vlcVout.attachViews()
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { showControls() })
                },
        )

        if (isBuffering) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xCC111111), RoundedCornerShape(12.dp))
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = "Buffering…",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        if (controlsVisible) Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(responsive.playerPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = ::leavePlayer,
                modifier = Modifier
                    .size(responsive.playerButtonSize)
                    .focusRequester(backFocusRequester)
                    .focusProperties {
                        right = otherPlayerFocusRequester
                        down = playFocusRequester
                    },
            ) {
                Icon(
	                    Icons.AutoMirrored.Filled.ArrowBack,
	                    contentDescription = "Back",
	                    tint = Color.White,
	                    modifier = Modifier.size(responsive.playerIconSize),
	                )
	            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
	                    color = Color.White,
	                    fontWeight = FontWeight.SemiBold,
	                    fontSize = responsive.sp(14f),
	                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        if (isLive) append("LIVE • ")
                        append(if (softwareDecode) "VLC SOFTWARE" else "VLC HARDWARE")
                    },
	                    color = MaterialTheme.colorScheme.primary,
	                    fontSize = responsive.sp(10f),
	                )
            }
            if (sources.size > 1) {
                Text(
                    text = "SOURCE ${sourceIndex + 1}/${sources.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            TextButton(
                onClick = {
                    showControls()
                    openInAnotherPlayer()
                },
                modifier = Modifier
                    .height(responsive.dp(44f))
                    .focusRequester(otherPlayerFocusRequester)
                    .focusProperties {
                        left = backFocusRequester
                        down = qualityFocusRequester
                    },
            ) {
                Text("Other player", color = Color.White)
            }
        }

        if (controlsVisible) Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(responsive.playerPadding)
                .background(Color(0xCC111111), RoundedCornerShape(responsive.dp(12f)))
                .padding(horizontal = responsive.dp(12f), vertical = responsive.dp(8f)),
        ) {
            playbackError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!isLive) {
                    IconButton(
                        onClick = {
                            showControls()
                            val target = (captureResumePosition() - 10_000L).coerceAtLeast(0L)
                            mediaPlayer.time = target
                            currentTime = target
                            resumePositionForNextAttempt = target
                            initialSeekApplied = true
                        },
                        modifier = Modifier
                            .size(responsive.playerButtonSize)
                            .focusRequester(rewindFocusRequester)
                            .focusProperties {
                                up = backFocusRequester
                                right = playFocusRequester
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Rewind 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(responsive.playerIconSize),
                        )
                    }
                }
                IconButton(
                    onClick = {
                        showControls()
                        if (playbackRequested) {
                            reportProgressIfChanged()
                            playbackRequested = false
                            isBuffering = false
                            runCatching { mediaPlayer.pause() }
                        } else {
                            playbackRequested = true
                            if (lifecycleStarted) {
                                isBuffering = true
                                mediaPlayer.play()
                            }
                        }
                    },
                    modifier = Modifier
                        .size(responsive.playerButtonSize)
                        .focusRequester(playFocusRequester)
                        .focusProperties {
                            up = backFocusRequester
                            left = if (isLive) backFocusRequester else rewindFocusRequester
                            right = if (isLive) qualityFocusRequester else forwardFocusRequester
                        },
                ) {
                    Icon(
                        imageVector =
                            if (playbackRequested) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackRequested) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(responsive.playerIconSize),
                    )
                }
                if (!isLive) {
                    IconButton(
                        onClick = {
                            showControls()
                            val target = captureResumePosition() + 30_000L
                            val seekTarget = if (duration > 0L) {
                                target.coerceAtMost(duration)
                            } else {
                                target
                            }
                            mediaPlayer.time = seekTarget
                            currentTime = seekTarget
                            resumePositionForNextAttempt = seekTarget
                            initialSeekApplied = true
                        },
                        modifier = Modifier
                            .size(responsive.playerButtonSize)
                            .focusRequester(forwardFocusRequester)
                            .focusProperties {
                                up = backFocusRequester
                                left = playFocusRequester
                                right = qualityFocusRequester
                            },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward30,
                            contentDescription = "Skip forward 30 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(responsive.playerIconSize),
                        )
                    }
                }
                Text(
                    text = if (isLive) {
                        "LIVE"
                    } else {
                        "${formatDuration(currentTime)} / ${formatDuration(duration)}"
                    },
                    color = Color.White,
                    fontSize = responsive.sp(12f),
                )
                Spacer(Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(
                    space = responsive.dp(4f),
                    alignment = Alignment.End,
                ),
            ) {
                TrackMenuButton(
                    label = "Quality",
                    choices = qualityChoices,
                    selectedId = selectedQuality,
                    enabled = true,
                    modifier = Modifier
                        .height(responsive.dp(44f))
                        .focusRequester(qualityFocusRequester)
                        .focusProperties {
                            up = if (isLive) playFocusRequester else forwardFocusRequester
                            left = if (isLive) playFocusRequester else forwardFocusRequester
                            right = when {
                                audioEnabled -> audioFocusRequester
                                subtitlesEnabled -> subtitleFocusRequester
                                else -> otherPlayerFocusRequester
                            }
                    },
                    onInteraction = ::showControls,
                    onExpandedChange = { trackMenuExpanded = it },
                    onSelect = { choice ->
                        if (choice.id != selectedQuality) {
                            captureResumePosition()
                            selectedQuality = choice.id
                        }
                    },
                )
                if (audioEnabled) {
                    TrackMenuButton(
                        label = "Audio",
                        choices = audioTracks,
                        selectedId = selectedAudioTrack,
                        enabled = true,
                        modifier = Modifier
                            .height(responsive.dp(44f))
                            .focusRequester(audioFocusRequester)
                            .focusProperties {
                                up = otherPlayerFocusRequester
                                left = qualityFocusRequester
                                right =
                                    if (subtitlesEnabled) {
                                        subtitleFocusRequester
                                    } else {
                                        otherPlayerFocusRequester
                                    }
                        },
                        onInteraction = ::showControls,
                        onExpandedChange = { trackMenuExpanded = it },
                        onSelect = { choice ->
                            if (mediaPlayer.setAudioTrack(choice.id)) {
                                selectedAudioTrack = choice.id
                            }
                        },
                    )
                }
                if (subtitlesEnabled) {
                    TrackMenuButton(
                        label = "Subtitles",
                        choices = subtitleTracks,
                        selectedId = selectedSubtitleTrack,
                        enabled = true,
                        modifier = Modifier
                            .height(responsive.dp(44f))
                            .focusRequester(subtitleFocusRequester)
                            .focusProperties {
                                up = otherPlayerFocusRequester
                                left =
                                    if (audioEnabled) {
                                        audioFocusRequester
                                    } else {
                                        qualityFocusRequester
                                    }
                                right = otherPlayerFocusRequester
                        },
                        onInteraction = ::showControls,
                        onExpandedChange = { trackMenuExpanded = it },
                        onSelect = { choice ->
                            if (mediaPlayer.setSpuTrack(choice.id)) {
                                selectedSubtitleTrack = choice.id
                            }
                        },
                    )
                }
            }
            if (!isLive) Slider(
                value = if (isScrubbing) {
                    scrubFraction
                } else if (duration > 0L) {
                    (currentTime.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                },
                onValueChange = {
                    showControls()
                    isScrubbing = true
                    scrubFraction = it
                },
                onValueChangeFinished = {
                    if (duration > 0L) {
                        val seekTarget = (duration * scrubFraction).toLong()
                        mediaPlayer.time = seekTarget
                        currentTime = seekTarget
                        resumePositionForNextAttempt = seekTarget
                        initialSeekApplied = true
                    }
                    isScrubbing = false
                },
                enabled = duration > 0L,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TrackMenuButton(
    label: String,
    choices: List<VlcTrackChoice>,
    selectedId: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onInteraction: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (VlcTrackChoice) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
	        TextButton(
	            onClick = {
	                onInteraction()
	                expanded = true
	                onExpandedChange(true)
	            },
	            enabled = enabled,
	            modifier = modifier,
	        ) {
            Text(label)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                onExpandedChange(false)
            },
        ) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (choice.id == selectedId) "✓ ${choice.name}" else choice.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        onInteraction()
                        onSelect(choice)
                        expanded = false
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    if (milliseconds <= 0L) return "0:00"
    val totalSeconds = milliseconds / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private const val QUALITY_AUTO = -1
private const val QUALITY_MAX = Int.MAX_VALUE
private const val NO_VIDEO_WATCHDOG_MS = 11_500L
private const val STARTUP_WATCHDOG_MS = 45_000L
private const val NO_VIDEO_WATCHDOG_POLL_MS = 500L
