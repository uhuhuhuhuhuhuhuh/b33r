@file:androidx.media3.common.util.UnstableApi

package com.streamdeck.iptv.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.streamdeck.iptv.IptvUiState
import com.streamdeck.iptv.IptvViewModel
import com.streamdeck.iptv.R
import com.streamdeck.iptv.data.AppUpdate
import com.streamdeck.iptv.data.ContentKind
import com.streamdeck.iptv.data.LibrarySection
import com.streamdeck.iptv.data.StreamItem
import com.streamdeck.iptv.data.canBeFavorited
import com.streamdeck.iptv.data.localLibraryKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StreamDeckApp(viewModel: IptvViewModel) {
    ProvideResponsiveScaling {
        StreamDeckTheme {
            val state = viewModel.state
            Box(Modifier.fillMaxSize()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when {
                        state.checkingSession -> LoadingScreen()
                        !state.authenticated -> LoginScreen(
                            loading = state.loading,
                            error = state.error,
                            onLogin = viewModel::login,
                            onEdit = viewModel::clearError,
                        )
                        state.playbackUrl != null -> {
                            val playbackItem = state.playbackItem
                            PlayerScreen(
                                sources = state.playbackSources.ifEmpty { listOf(state.playbackUrl) },
                                title = state.playbackTitle,
                                contentKind = state.playbackKind ?: ContentKind.MOVIE,
                                startPositionMs = state.playbackStartPositionMs,
                                onProgress = { positionMs, durationMs ->
                                    playbackItem?.let { item ->
                                        viewModel.savePlaybackProgress(item, positionMs, durationMs)
                                    }
                                },
                                onBack = viewModel::closePlayer,
                            )
                        }
                        else -> LibraryScreen(
                            state = state,
                            onSection = viewModel::loadSection,
                            onCategory = viewModel::selectCategory,
                            onSearchQuery = viewModel::updateSearchQuery,
                            onItem = viewModel::open,
                            onFavorite = viewModel::toggleFavorite,
                            onBackFromSeries = viewModel::closeSeries,
                            onLogout = viewModel::logout,
                        )
                    }
                }
                state.availableUpdate
                    ?.takeUnless { state.updateDismissed || state.playbackUrl != null }
                    ?.let { update ->
                        UpdateDialog(
                            update = update,
                            downloading = state.updateDownloading,
                            awaitingPermission = state.updateAwaitingPermission,
                            error = state.updateError,
                            onInstall = viewModel::installAvailableUpdate,
                            onDismiss = viewModel::dismissUpdate,
                        )
                    }
            }
        }
    }
}

@Composable
private fun UpdateDialog(
    update: AppUpdate,
    downloading: Boolean,
    awaitingPermission: Boolean,
    error: String?,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val updateFocusRequester = remember { FocusRequester() }
    val laterFocusRequester = remember { FocusRequester() }
    LaunchedEffect(downloading, awaitingPermission) {
        if (!downloading) {
            repeat(5) {
                delay(120)
                val focused = runCatching { updateFocusRequester.requestFocus() }
                    .getOrDefault(false)
                if (focused) return@LaunchedEffect
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("Update ${update.versionName} available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (update.releaseDate.isNotBlank()) {
                    Text(
                        text = "Released ${update.releaseDate}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (update.notes.isNotEmpty()) {
                    Text(update.notes.joinToString(separator = "\n") { "• $it" })
                } else {
                    Text("A newer version of b33r IPTV is ready.")
                }
                if (downloading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Downloading and verifying the update…")
                }
                if (awaitingPermission) {
                    Text("Android needs permission to install updates from this app.")
                }
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onInstall,
                enabled = !downloading,
                modifier = Modifier
                    .focusRequester(updateFocusRequester)
                    .focusProperties { left = laterFocusRequester },
            ) {
                Text(
                    when {
                        downloading -> "Downloading"
                        awaitingPermission -> "Open permission"
                        else -> "Update now"
                    },
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !downloading,
                modifier = Modifier
                    .focusRequester(laterFocusRequester)
                    .focusProperties { right = updateFocusRequester },
            ) {
                Text("Later")
            }
        },
    )
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

private fun opensTvKeyboard(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
    val keyCode = event.nativeKeyEvent.keyCode
    return event.type == KeyEventType.KeyDown &&
        (
            keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
                keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
            )
}

private fun tvFocusDirection(event: androidx.compose.ui.input.key.KeyEvent): FocusDirection? {
    if (event.type != KeyEventType.KeyDown) return null
    return when (event.nativeKeyEvent.keyCode) {
        AndroidKeyEvent.KEYCODE_DPAD_UP -> FocusDirection.Up
        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> FocusDirection.Down
        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> FocusDirection.Left
        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> FocusDirection.Right
        else -> null
    }
}

@Composable
private fun LoginScreen(
    loading: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit,
    onEdit: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var usernameEditing by remember { mutableStateOf(false) }
    var passwordEditing by remember { mutableStateOf(false) }
    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val responsive = rememberResponsiveLayout()
    val isTelevision = responsive.isTelevision
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val submit = {
        usernameEditing = false
        passwordEditing = false
        softwareKeyboardController?.hide()
        onLogin(username, password)
    }

    LaunchedEffect(usernameEditing) {
        if (isTelevision && usernameEditing) {
            usernameFocusRequester.requestFocus()
            delay(50)
            softwareKeyboardController?.show()
        }
    }
    LaunchedEffect(passwordEditing) {
        if (isTelevision && passwordEditing) {
            passwordFocusRequester.requestFocus()
            delay(50)
            softwareKeyboardController?.show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2B1709),
                        Color(0xFF160B05),
                        Color(0xFF080301),
                    ),
                ),
            )
            .padding(WindowInsets.safeDrawing.asPaddingValues())
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = responsive.dp(24f),
                vertical = responsive.dp(16f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = responsive.loginMaxWidth)
                .fillMaxWidth(0.92f),
            shape = RoundedCornerShape(responsive.dp(26f)),
            color = Color(0xF21A0F08),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)),
            tonalElevation = 8.dp,
            shadowElevation = 18.dp,
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = responsive.dp(28f),
                    vertical = responsive.dp(30f),
                ),
                verticalArrangement = Arrangement.spacedBy(responsive.dp(16f)),
            ) {
                Surface(
                    modifier = Modifier.size(responsive.dp(54f)),
                    shape = RoundedCornerShape(responsive.dp(16f)),
                    color = Color(0xFF2A170A),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                    ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
	                            text = "b33r",
	                            color = MaterialTheme.colorScheme.primary,
	                            fontWeight = FontWeight.Black,
	                            fontSize = responsive.sp(14f),
	                            letterSpacing = 0.2.sp,
	                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
	                        text = "B33R IPTV",
	                        color = MaterialTheme.colorScheme.primary,
	                        fontWeight = FontWeight.Bold,
	                        fontSize = responsive.sp(12f),
	                        letterSpacing = 2.4.sp,
                    )
                    Text(
                        text = "Your entertainment is on tap",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Live TV, movies, and series with premium multi-engine playback.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        onEdit()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(usernameFocusRequester)
                        .onFocusChanged {
                            if (!it.isFocused) usernameEditing = false
                        }
                        .onPreviewKeyEvent { event ->
                            if (isTelevision && opensTvKeyboard(event)) {
                                usernameEditing = true
                                softwareKeyboardController?.show()
                                true
                            } else {
                                false
                            }
                        },
                    enabled = !loading,
                    readOnly = isTelevision && !usernameEditing,
                    singleLine = true,
	                    shape = RoundedCornerShape(responsive.dp(14f)),
                    label = { Text("Username") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color(0xFF5B3918),
                        focusedContainerColor = Color(0xFF100804),
                        unfocusedContainerColor = Color(0xFF100804),
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = {
                            usernameEditing = false
                            passwordEditing = true
                        },
                    ),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        onEdit()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocusRequester)
                        .onFocusChanged {
                            if (!it.isFocused) passwordEditing = false
                        }
                        .onPreviewKeyEvent { event ->
                            if (isTelevision && opensTvKeyboard(event)) {
                                passwordEditing = true
                                softwareKeyboardController?.show()
                                true
                            } else {
                                false
                            }
                        },
                    enabled = !loading,
                    readOnly = isTelevision && !passwordEditing,
                    singleLine = true,
	                    shape = RoundedCornerShape(responsive.dp(14f)),
                    label = { Text("Password") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color(0xFF5B3918),
                        focusedContainerColor = Color(0xFF100804),
                        unfocusedContainerColor = Color(0xFF100804),
                    ),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                Button(
	                    onClick = submit,
	                    modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = responsive.dp(54f)),
	                    enabled = !loading,
	                    shape = RoundedCornerShape(responsive.dp(14f)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
	                    if (loading) {
	                        CircularProgressIndicator(
	                            modifier = Modifier.size(responsive.dp(22f)),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(
                            text = "ENTER B33R",
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.4.sp,
                        )
                    }
                }
                error?.let {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.24f),
                        ),
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

private enum class TvKeyboardField {
    USERNAME,
    PASSWORD,
}

@Composable
private fun TvLoginField(
    label: String,
    value: String,
    masked: Boolean,
    enabled: Boolean,
    onOpenKeyboard: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onOpenKeyboard),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (focused) 3.dp else 1.dp,
            color = if (focused) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                color = if (focused) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = when {
                    value.isEmpty() -> "Press OK to type"
                    masked -> "•".repeat(value.length)
                    else -> value
                },
                color = if (value.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TvSearchField(
    value: String,
    placeholder: String,
    onOpenKeyboard: () -> Unit,
    onClear: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .height(64.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onOpenKeyboard),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (focused) 3.dp else 1.dp,
            color = if (focused) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = if (focused) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = value.ifEmpty { "$placeholder — press OK to type" },
                modifier = Modifier.weight(1f),
                color = if (value.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        }
    }
}

@Composable
private fun TvKeyboardDialog(
    title: String,
    value: String,
    masked: Boolean,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    var uppercase by remember { mutableStateOf(false) }
    var symbols by remember { mutableStateOf(false) }
    val alphaRows = listOf(
        "1234567890".map(Char::toString),
        "qwertyuiop".map(Char::toString),
        "asdfghjkl".map(Char::toString),
        "zxcvbnm".map(Char::toString),
    )
    val symbolRows = listOf(
        "1234567890".map(Char::toString),
        listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")"),
        listOf("_", "-", "+", "=", "[", "]", "{", "}", "<", ">"),
        listOf(".", ",", ":", ";", "/", "?", "\\", "|", "'", "\""),
    )
    val rows = if (symbols) {
        symbolRows
    } else if (uppercase) {
        alphaRows.map { row -> row.map(String::uppercase) }
    } else {
        alphaRows
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = when {
                        value.isEmpty() -> " "
                        masked -> "•".repeat(value.length)
                        else -> value
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.forEach { key ->
                            TvKeyboardKey(
                                label = key,
                                modifier = Modifier.weight(1f),
                                onClick = { onValueChange(value + key) },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TvKeyboardKey(
                        label = if (uppercase) "abc" else "ABC",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            symbols = false
                            uppercase = !uppercase
                        },
                    )
                    TvKeyboardKey(
                        label = if (symbols) "ABC" else "#+=",
                        modifier = Modifier.weight(1f),
                        onClick = { symbols = !symbols },
                    )
                    TvKeyboardKey(
                        label = "SPACE",
                        modifier = Modifier.weight(2f),
                        onClick = { onValueChange("$value ") },
                    )
                    TvKeyboardKey(
                        label = "⌫",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (value.isNotEmpty()) onValueChange(value.dropLast(1))
                        },
                    )
                    TvKeyboardKey(
                        label = "CLEAR",
                        modifier = Modifier.weight(1f),
                        onClick = { onValueChange("") },
                    )
                    TvKeyboardKey(
                        label = "DONE",
                        modifier = Modifier.weight(1.3f),
                        emphasized = true,
                        onClick = onDone,
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.TvKeyboardKey(
    label: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> MaterialTheme.colorScheme.primary
        emphasized -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(
        modifier = modifier
            .height(46.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        color = background,
        shape = RoundedCornerShape(7.dp),
        border = if (focused) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.onPrimary)
        } else {
            null
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = if (focused) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun LibraryScreen(
    state: IptvUiState,
    onSection: (LibrarySection) -> Unit,
    onCategory: (String?) -> Unit,
    onSearchQuery: (String) -> Unit,
    onItem: (StreamItem) -> Unit,
    onFavorite: (StreamItem) -> Unit,
    onBackFromSeries: () -> Unit,
    onLogout: () -> Unit,
) {
    BackHandler(enabled = state.seriesTitle != null, onBack = onBackFromSeries)
    val responsive = rememberResponsiveLayout()
    val sectionNavigationFocusRequester = remember { FocusRequester() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = shouldUseNavigationRail(
            isTelevision = responsive.isTelevision,
            widthDp = maxWidth.value,
            heightDp = maxHeight.value,
        )
        if (wide) {
            Row(Modifier.fillMaxSize().padding(WindowInsets.safeDrawing.asPaddingValues())) {
                SectionRail(
                    selected = state.section,
                    onSection = onSection,
                    onLogout = onLogout,
                    responsive = responsive,
                    selectedItemFocusRequester = sectionNavigationFocusRequester,
                )
                LibraryContent(
                    state = state,
                    wide = true,
                    onSection = onSection,
                    onCategory = onCategory,
                    onSearchQuery = onSearchQuery,
                    onItem = onItem,
                    onFavorite = onFavorite,
                    onBackFromSeries = onBackFromSeries,
                    sectionNavigationFocusRequester = sectionNavigationFocusRequester,
                )
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(WindowInsets.statusBars.asPaddingValues()),
            ) {
                LibraryContent(
                    state = state,
                    wide = false,
                    onSection = onSection,
                    onCategory = onCategory,
                    onSearchQuery = onSearchQuery,
                    onItem = onItem,
                    onFavorite = onFavorite,
                    onBackFromSeries = onBackFromSeries,
                    onLogout = onLogout,
                    sectionNavigationFocusRequester = sectionNavigationFocusRequester,
                    modifier = Modifier.weight(1f),
                )
                SectionBar(
                    selected = state.section,
                    onSection = onSection,
                    responsive = responsive,
                    selectedItemFocusRequester = sectionNavigationFocusRequester,
                )
            }
        }
    }
}

@Composable
private fun LibraryContent(
    state: IptvUiState,
    wide: Boolean,
    onSection: (LibrarySection) -> Unit,
    onCategory: (String?) -> Unit,
    onSearchQuery: (String) -> Unit,
    onItem: (StreamItem) -> Unit,
    onFavorite: (StreamItem) -> Unit,
    onBackFromSeries: () -> Unit,
    sectionNavigationFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onLogout: (() -> Unit)? = null,
) {
    var searchEditing by remember(state.section, state.seriesTitle) {
        mutableStateOf(false)
    }
    val searchFocusRequester = remember { FocusRequester() }
    val firstResultFocusRequester = remember { FocusRequester() }
    val firstCategoryFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val responsive = rememberResponsiveLayout()
    val isTelevision = responsive.isTelevision
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val searchPlaceholder = when {
        state.seriesTitle != null -> "Search episodes"
        state.section == LibrarySection.LIVE -> "Search live channels"
        state.section == LibrarySection.MOVIES -> "Search movies"
        state.section == LibrarySection.SERIES -> "Search series"
        state.section == LibrarySection.FAVORITES -> "Search favorites"
        else -> "Search continue watching"
    }
    val sectionItems = if (state.seriesTitle == null) state.items else state.episodes
    val visibleItems = remember(sectionItems, state.searchQuery) {
        val query = state.searchQuery.trim()
        if (query.isEmpty()) {
            sectionItems
        } else {
            sectionItems.filter { item ->
                item.name.contains(query, ignoreCase = true) ||
                    item.subtitle.contains(query, ignoreCase = true)
            }
        }
    }
    LaunchedEffect(searchEditing) {
        if (isTelevision && searchEditing) {
            searchFocusRequester.requestFocus()
            delay(50)
            softwareKeyboardController?.show()
        }
    }
    fun leaveSearch(direction: FocusDirection): Boolean {
        searchEditing = false
        softwareKeyboardController?.hide()
        val preferredTarget = when (direction) {
            FocusDirection.Down, FocusDirection.Right ->
                if (visibleItems.isNotEmpty()) {
                    firstResultFocusRequester
                } else {
                    sectionNavigationFocusRequester
                }
            FocusDirection.Up ->
                if (state.seriesTitle == null && state.categories.isNotEmpty()) {
                    firstCategoryFocusRequester
                } else {
                    sectionNavigationFocusRequester
                }
            FocusDirection.Left -> sectionNavigationFocusRequester
            else -> null
        }
        val explicitlyMoved = preferredTarget?.let { requester ->
            runCatching { requester.requestFocus() }.getOrDefault(false)
        } ?: false
        return explicitlyMoved || focusManager.moveFocus(direction)
    }

    Column(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF321B09),
                        Color(0xFF170B04),
                        Color(0xFF080301),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        SmartersTopBar(
            state = state,
            wide = wide,
            responsive = responsive,
            onBackFromSeries = onBackFromSeries,
            onLogout = onLogout,
        )

        if (state.seriesTitle == null) {
            SmartersLaunchRow(
                selected = state.section,
                visibleItemCount = visibleItems.size,
                responsive = responsive,
                enabled = !state.loading && !state.openingItem,
                onSection = onSection,
            )
            Spacer(Modifier.height(responsive.dp(14f)))
            SmartersSectionHeader(
                sectionTitle = state.section.title,
                itemCount = visibleItems.size,
                wide = wide,
                responsive = responsive,
            )
            Spacer(Modifier.height(responsive.dp(10f)))
        }

	        if (state.seriesTitle == null && state.categories.isNotEmpty()) {
	            LazyRow(
	                contentPadding = androidx.compose.foundation.layout.PaddingValues(
	                    horizontal = responsive.pagePadding,
	                ),
	                horizontalArrangement = Arrangement.spacedBy(responsive.dp(10f)),
	            ) {
                item {
                    FilterChip(
                        modifier = Modifier.focusRequester(firstCategoryFocusRequester),
                        selected = state.selectedCategoryId == null,
                        onClick = { onCategory(null) },
                        enabled = !state.loading && !state.openingItem,
                        label = { Text("All") },
                        colors = libraryFilterChipColors(),
                    )
                }
                lazyItemsIndexed(
                    items = state.categories,
                    key = { index, category -> "${category.id}-$index" },
                ) { _, category ->
                    FilterChip(
                        selected = state.selectedCategoryId == category.id,
                        onClick = { onCategory(category.id) },
                        enabled = !state.loading && !state.openingItem,
                        label = {
                            Text(
                                text = category.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        colors = libraryFilterChipColors(),
                    )
                }
            }
	            Spacer(Modifier.height(responsive.dp(12f)))
	        }

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchQuery,
	            modifier = Modifier
	                .padding(horizontal = responsive.pagePadding)
	                .widthIn(max = responsive.dp(if (responsive.isTelevision) 680f else 560f))
                .fillMaxWidth()
                .focusRequester(searchFocusRequester)
                .focusProperties {
                    left = sectionNavigationFocusRequester
                    up = if (state.seriesTitle == null && state.categories.isNotEmpty()) {
                        firstCategoryFocusRequester
                    } else {
                        sectionNavigationFocusRequester
                    }
                    down = if (visibleItems.isNotEmpty()) {
                        firstResultFocusRequester
                    } else {
                        sectionNavigationFocusRequester
                    }
                    right = if (visibleItems.isNotEmpty()) {
                        firstResultFocusRequester
                    } else {
                        sectionNavigationFocusRequester
                    }
                }
                .onFocusChanged {
                    if (!it.isFocused) searchEditing = false
                }
                .onPreviewKeyEvent { event ->
                    val direction = if (isTelevision) tvFocusDirection(event) else null
                    when {
                        direction != null -> {
                            leaveSearch(direction)
                        }
                        isTelevision && opensTvKeyboard(event) -> {
                            searchEditing = true
                            softwareKeyboardController?.show()
                            true
                        }
                        else -> false
                    }
                },
            readOnly = isTelevision && !searchEditing,
            singleLine = true,
	            shape = RoundedCornerShape(responsive.dp(14f)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color(0xFF5B3918),
                focusedContainerColor = Color(0xE61A0F08),
                unfocusedContainerColor = Color(0xE61A0F08),
            ),
            placeholder = { Text(searchPlaceholder) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = if (state.searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { onSearchQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    searchEditing = false
                    softwareKeyboardController?.hide()
                },
            ),
        )
	        Spacer(Modifier.height(responsive.dp(12f)))

        state.error?.let { message ->
            Text(
                text = message,
	                modifier = Modifier.padding(
	                    horizontal = responsive.pagePadding,
	                    vertical = responsive.dp(8f),
	                ),
                color = MaterialTheme.colorScheme.error,
            )
        }

        Box(Modifier.fillMaxSize()) {
            if (visibleItems.isNotEmpty()) {
                LazyVerticalGrid(
	                    columns = GridCells.Adaptive(responsive.gridCardWidth),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
	                        start = responsive.pagePadding,
	                        end = responsive.pagePadding,
	                        top = responsive.dp(4f),
	                        bottom = responsive.dp(24f),
	                    ),
	                    horizontalArrangement = Arrangement.spacedBy(responsive.gridSpacing),
	                    verticalArrangement = Arrangement.spacedBy(responsive.dp(22f)),
                ) {
                    itemsIndexed(
                        items = visibleItems,
                        key = { index, item -> "${item.kind}-${item.id}-$index" },
                    ) { index, item ->
                        ContentCard(
	                            item = item,
	                            modifier = if (index == 0) {
	                                Modifier.focusRequester(firstResultFocusRequester)
	                            } else {
	                                Modifier
	                            },
                            responsive = responsive,
                            enabled = !state.openingItem,
                            isFavorite = state.favoriteKeys.contains(item.localLibraryKey()),
                            onFavorite = if (item.canBeFavorited()) {
                                { onFavorite(item) }
                            } else {
                                null
                            },
                            onClick = { onItem(item) },
                        )
                    }
                }
            } else if (!state.loading && state.error == null) {
                Text(
                    text = if (state.searchQuery.isBlank()) {
                        "Nothing found in this section."
                    } else {
                        "No matches for “${state.searchQuery.trim()}”."
                    },
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.loading || state.openingItem) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun SmartersSectionHeader(
    sectionTitle: String,
    itemCount: Int,
    wide: Boolean,
    responsive: ResponsiveLayout,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = responsive.pagePadding),
    ) {
        val stacked = maxWidth < responsive.dp(360f)
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(responsive.dp(2f))) {
                Text(
                    text = "NOW POURING",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = responsive.sp(9f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = sectionTitle,
                    fontSize = responsive.sp(if (wide) 24f else 20f),
                    lineHeight = responsive.sp(if (wide) 28f else 24f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$itemCount items",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(responsive.dp(12f)),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "NOW POURING",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = responsive.sp(9f),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = sectionTitle,
                        fontSize = responsive.sp(if (wide) 24f else 20f),
                        lineHeight = responsive.sp(if (wide) 28f else 24f),
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "$itemCount items",
                    modifier = Modifier.widthIn(max = responsive.dp(130f)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SmartersTopBar(
    state: IptvUiState,
    wide: Boolean,
    responsive: ResponsiveLayout,
    onBackFromSeries: () -> Unit,
    onLogout: (() -> Unit)?,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = responsive.pagePadding,
                end = responsive.pagePadding,
                top = responsive.dp(14f),
                bottom = responsive.dp(12f),
            ),
        shape = RoundedCornerShape(responsive.dp(18f)),
        color = Color(0xF5150A05),
        border = BorderStroke(1.dp, Color(0xFF5E3816)),
        shadowElevation = responsive.dp(10f),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < responsive.dp(if (wide) 680f else 540f)
            val veryCompact = maxWidth < responsive.dp(380f)
            if (compact) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = responsive.dp(if (veryCompact) 10f else 12f),
                            vertical = responsive.dp(10f),
                        ),
                    verticalArrangement = Arrangement.spacedBy(responsive.dp(8f)),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.seriesTitle != null) {
                            IconButton(onClick = onBackFromSeries) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                        Surface(
                            modifier = Modifier.size(responsive.dp(if (veryCompact) 38f else 42f)),
                            shape = RoundedCornerShape(responsive.dp(13f)),
                            color = MaterialTheme.colorScheme.primary,
                            shadowElevation = responsive.dp(4f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "b33r",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Black,
                                    fontSize = responsive.sp(if (veryCompact) 10f else 11f),
                                    maxLines = 1,
                                )
                            }
                        }
                        Spacer(Modifier.width(responsive.dp(10f)))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "B33R SMART HUB",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = responsive.sp(8f),
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.4.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = state.seriesTitle ?: "Premium IPTV Player",
                                fontSize = responsive.sp(if (veryCompact) 16f else 18f),
                                lineHeight = responsive.sp(if (veryCompact) 19f else 22f),
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        onLogout?.let { logout ->
                            IconButton(onClick = logout) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log out")
                            }
                        }
                    }
                    if (state.accountUsername.isNotBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(responsive.dp(10f)),
                            color = Color(0xFF241307),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
                            ),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = responsive.dp(10f),
                                        vertical = responsive.dp(6f),
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(responsive.dp(8f)),
                            ) {
                                Text(
                                    text = state.accountUsername,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = formatAccountExpiration(state.accountExpiresAtEpochSeconds),
                                    modifier = Modifier.widthIn(max = responsive.dp(170f)),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = responsive.dp(14f),
                            vertical = responsive.dp(11f),
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.seriesTitle != null) {
                        IconButton(onClick = onBackFromSeries) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                    Surface(
                        modifier = Modifier.size(responsive.dp(if (wide) 50f else 44f)),
                        shape = RoundedCornerShape(responsive.dp(14f)),
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = responsive.dp(5f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "b33r",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Black,
                                fontSize = responsive.sp(12f),
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(Modifier.width(responsive.dp(12f)))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "B33R SMART HUB",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = responsive.sp(9f),
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.2.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = state.seriesTitle ?: "Premium IPTV Player",
                            style = if (wide) {
                                MaterialTheme.typography.headlineSmall
                            } else {
                                MaterialTheme.typography.titleLarge
                            },
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (wide && state.seriesTitle == null) {
                            Text(
                                text = "Live TV • Movies • Series • Multi-engine playback",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = responsive.sp(10f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (state.accountUsername.isNotBlank()) {
                        Surface(
                            modifier = Modifier
                                .padding(start = responsive.dp(8f))
                                .widthIn(max = responsive.dp(if (wide) 190f else 130f)),
                            shape = RoundedCornerShape(responsive.dp(12f)),
                            color = Color(0xFF241307),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.32f),
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    horizontal = responsive.dp(11f),
                                    vertical = responsive.dp(7f),
                                ),
                                horizontalAlignment = Alignment.End,
                            ) {
                                Text(
                                    text = state.accountUsername,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    text = formatAccountExpiration(state.accountExpiresAtEpochSeconds),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                    onLogout?.let { logout ->
                        IconButton(onClick = logout) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log out")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartersLaunchRow(
    selected: LibrarySection,
    visibleItemCount: Int,
    responsive: ResponsiveLayout,
    enabled: Boolean,
    onSection: (LibrarySection) -> Unit,
) {
    val sections = listOf(
        LibrarySection.LIVE,
        LibrarySection.MOVIES,
        LibrarySection.SERIES,
        LibrarySection.FAVORITES,
        LibrarySection.RESUME,
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < responsive.dp(520f)
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = responsive.pagePadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(responsive.dp(if (compact) 9f else 12f)),
        ) {
            items(sections, key = { it.name }) { section ->
                SmartersLaunchCard(
                    section = section,
                    selected = selected == section,
                    count = if (selected == section) visibleItemCount else null,
                    responsive = responsive,
                    compact = compact,
                    enabled = enabled,
                    onClick = { onSection(section) },
                )
            }
        }
    }
}

@Composable
private fun SmartersLaunchCard(
    section: LibrarySection,
    selected: Boolean,
    count: Int?,
    responsive: ResponsiveLayout,
    compact: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.055f else 1f,
        label = "launchCardScale",
    )
    val accent = when (section) {
        LibrarySection.LIVE -> Color(0xFFFFB02E)
        LibrarySection.MOVIES -> Color(0xFFFF8B2C)
        LibrarySection.SERIES -> Color(0xFFE7B354)
        LibrarySection.FAVORITES -> Color(0xFFFFC85A)
        LibrarySection.RESUME -> Color(0xFFD99535)
    }
    val subtitle = when (section) {
        LibrarySection.LIVE -> "Channels on tap"
        LibrarySection.MOVIES -> "On-demand pours"
        LibrarySection.SERIES -> "Binge the whole keg"
        LibrarySection.FAVORITES -> "Your saved picks"
        LibrarySection.RESUME -> "Keep watching"
    }
    val cardWidth = when {
        compact -> 140f
        responsive.isTelevision -> 188f
        else -> 156f
    }
    val cardHeight = when {
        compact -> 96f
        responsive.isTelevision -> 116f
        else -> 102f
    }
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .width(responsive.dp(cardWidth))
            .height(responsive.dp(cardHeight))
            .scale(scale)
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(responsive.dp(if (compact) 15f else 18f)),
        border = BorderStroke(
            width = if (focused) 3.dp else 1.dp,
            color = if (focused || selected) accent else Color(0xFF563316),
        ),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            accent.copy(alpha = if (selected) 0.38f else 0.22f),
                            Color(0xFF2A1608),
                            Color(0xFF130804),
                        ),
                    ),
                )
                .padding(responsive.dp(if (compact) 11f else 14f)),
        ) {
            Icon(
                imageVector = section.icon(),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(
                    responsive.dp(
                        when {
                            compact -> 24f
                            responsive.isTelevision -> 34f
                            else -> 28f
                        },
                    ),
                ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = section.title,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = responsive.sp(
                        when {
                            compact -> 13f
                            responsive.isTelevision -> 16f
                            else -> 14f
                        },
                    ),
                    lineHeight = responsive.sp(if (compact) 15f else 18f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compact || count != null) {
                    Text(
                        text = count?.let { "$it loaded" } ?: subtitle,
                        color = Color(0xFFD5C2AE),
                        fontSize = responsive.sp(if (compact) 8f else 9f),
                        lineHeight = responsive.sp(if (compact) 10f else 12f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected && !compact) {
                Text(
                    text = "OPEN",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(accent, RoundedCornerShape(responsive.dp(6f)))
                        .padding(
                            horizontal = responsive.dp(6f),
                            vertical = responsive.dp(3f),
                        ),
                    color = Color(0xFF170B04),
                    fontSize = responsive.sp(8f),
                    lineHeight = responsive.sp(10f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun libraryFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color(0xFF1B0E06),
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
    selectedLabelColor = MaterialTheme.colorScheme.primary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
)

private fun formatAccountExpiration(expiresAtEpochSeconds: Long?): String {
    if (expiresAtEpochSeconds == null) return "Never expires"
    val safeSeconds = expiresAtEpochSeconds.coerceAtMost(Long.MAX_VALUE / 1_000L)
    val formatted = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        .format(Date(safeSeconds * 1_000L))
    if (expiresAtEpochSeconds <= System.currentTimeMillis() / 1_000L) {
        return "Expired $formatted"
    }
    return "Expires $formatted"
}

@Composable
private fun ContentCard(
    item: StreamItem,
    modifier: Modifier = Modifier,
    responsive: ResponsiveLayout,
    enabled: Boolean,
    isFavorite: Boolean,
    onFavorite: (() -> Unit)?,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.055f else 1f, label = "cardScale")
    val container by animateColorAsState(
        if (focused) Color(0xFF4A2B10) else Color(0xFF1B0E06),
        label = "cardColor",
    )
    val posterRatio = if (item.kind == ContentKind.LIVE) 16f / 9f else 2f / 3f
    val context = LocalContext.current
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(responsive.dp(16f)),
        colors = CardDefaults.cardColors(containerColor = container),
        border = if (focused) {
            BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, Color(0xFF4A2A10))
        },
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(posterRatio)
                    .background(Color(0xFF241307)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.kind.icon(),
                    contentDescription = null,
	                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
	                    modifier = Modifier.size(
	                        responsive.dp(if (item.kind == ContentKind.LIVE) 38f else 46f),
	                    ),
	                )
                if (item.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "${item.name} artwork",
                        contentScale = if (item.kind == ContentKind.LIVE) ContentScale.Fit else ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (item.kind == ContentKind.LIVE) {
                    Text(
                        text = "LIVE",
	                        modifier = Modifier
	                            .align(Alignment.TopEnd)
	                            .padding(responsive.dp(8f))
	                            .background(
	                                MaterialTheme.colorScheme.primary,
	                                RoundedCornerShape(responsive.dp(4f)),
	                            )
	                            .padding(
	                                horizontal = responsive.dp(6f),
	                                vertical = responsive.dp(3f),
	                            ),
	                        color = MaterialTheme.colorScheme.onPrimary,
	                        fontSize = responsive.sp(9f),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                    )
                }
                onFavorite?.let { favorite ->
	                    Surface(
	                        modifier = Modifier
	                            .align(Alignment.TopStart)
	                            .padding(responsive.dp(7f))
	                            .size(responsive.dp(38f)),
	                        shape = RoundedCornerShape(responsive.dp(19f)),
                        color = Color(0xCC120804),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                        ),
                    ) {
                        IconButton(onClick = favorite) {
                            Icon(
                                imageVector = if (isFavorite) {
                                    Icons.Default.Favorite
                                } else {
                                    Icons.Default.FavoriteBorder
                                },
                                contentDescription = if (isFavorite) {
                                    "Remove favorite"
                                } else {
                                    "Add favorite"
                                },
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
	            Column(
	                Modifier.padding(
	                    horizontal = responsive.dp(12f),
	                    vertical = responsive.dp(11f),
	                ),
	            ) {
	                Text(
	                    text = item.name,
	                    fontWeight = FontWeight.SemiBold,
	                    maxLines = 2,
	                    overflow = TextOverflow.Ellipsis,
	                    fontSize = responsive.sp(14f),
	                    lineHeight = responsive.sp(18f),
	                )
	                if (item.subtitle.isNotBlank()) {
	                    Spacer(Modifier.height(responsive.dp(4f)))
	                    Text(
	                        text = item.subtitle.uppercase(),
	                        color = MaterialTheme.colorScheme.onSurfaceVariant,
	                        fontSize = responsive.sp(10f),
                        letterSpacing = 0.8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun ContentKind.icon(): ImageVector = when (this) {
    ContentKind.LIVE -> Icons.Default.LiveTv
    ContentKind.MOVIE -> Icons.Default.Movie
    ContentKind.SERIES, ContentKind.EPISODE -> Icons.Default.VideoLibrary
}

@Composable
private fun SectionRail(
    selected: LibrarySection,
    onSection: (LibrarySection) -> Unit,
    onLogout: () -> Unit,
    responsive: ResponsiveLayout,
    selectedItemFocusRequester: FocusRequester,
) {
    NavigationRail(
        modifier = Modifier
            .fillMaxHeight()
            .padding(responsive.dp(10f))
            .width(responsive.railWidth)
            .clip(RoundedCornerShape(responsive.dp(22f))),
        containerColor = Color(0xF20F0703),
        header = {
            Surface(
                modifier = Modifier.padding(vertical = responsive.dp(18f))
                    .size(responsive.dp(48f)),
                shape = RoundedCornerShape(responsive.dp(15f)),
                color = Color(0xFF2A170A),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
	                        text = "b33r",
	                        color = MaterialTheme.colorScheme.primary,
	                        fontWeight = FontWeight.Black,
	                        fontSize = responsive.sp(12f),
                    )
                }
            }
        },
    ) {
        LibrarySection.entries.forEach { section ->
            NavigationRailItem(
                modifier = if (selected == section) {
                    Modifier.focusRequester(selectedItemFocusRequester)
                } else {
                    Modifier
                },
                selected = selected == section,
                onClick = { onSection(section) },
                icon = { Icon(section.icon(), contentDescription = null) },
                label = {
                    Text(
                        text = section.shortTitle(),
                        fontSize = responsive.sp(9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
        Spacer(Modifier.weight(1f))
        NavigationRailItem(
            selected = false,
            onClick = onLogout,
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
            label = {
                Text(
                    text = "Logout",
                    fontSize = responsive.sp(9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            colors = NavigationRailItemDefaults.colors(
                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Spacer(Modifier.height(responsive.dp(12f)))
    }
}

@Composable
private fun SectionBar(
    selected: LibrarySection,
    onSection: (LibrarySection) -> Unit,
    responsive: ResponsiveLayout,
    selectedItemFocusRequester: FocusRequester,
) {
    NavigationBar(
        modifier = Modifier
            .padding(WindowInsets.navigationBars.asPaddingValues())
            .padding(horizontal = responsive.dp(10f), vertical = responsive.dp(8f))
            .clip(RoundedCornerShape(responsive.dp(20f))),
        containerColor = Color(0xFA0F0703),
    ) {
        LibrarySection.entries.forEach { section ->
            NavigationBarItem(
                modifier = if (selected == section) {
                    Modifier.focusRequester(selectedItemFocusRequester)
                } else {
                    Modifier
                },
                selected = selected == section,
                onClick = { onSection(section) },
                icon = { Icon(section.icon(), contentDescription = null) },
                label = {
                    Text(
                        text = section.shortTitle(),
                        fontSize = responsive.sp(9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private fun LibrarySection.icon(): ImageVector = when (this) {
    LibrarySection.LIVE -> Icons.Default.LiveTv
    LibrarySection.MOVIES -> Icons.Default.Movie
    LibrarySection.SERIES -> Icons.Default.VideoLibrary
    LibrarySection.FAVORITES -> Icons.Default.Favorite
    LibrarySection.RESUME -> Icons.Default.History
}

private fun LibrarySection.shortTitle(): String = when (this) {
    LibrarySection.LIVE -> "Live"
    LibrarySection.MOVIES -> "Movies"
    LibrarySection.SERIES -> "Series"
    LibrarySection.FAVORITES -> "Favs"
    LibrarySection.RESUME -> "Continue"
}

private data class PlaybackAttempt(
    val sourceIndex: Int,
    val useTextureView: Boolean,
)

private data class VideoQualityOption(
    val label: String,
    val maxHeight: Int?,
    val forceHighest: Boolean,
)

private val videoQualityOptions = listOf(
    VideoQualityOption("Auto", null, false),
    VideoQualityOption("720p", 720, true),
    VideoQualityOption("1080p", 1_080, true),
    VideoQualityOption("1440p", 1_440, true),
    VideoQualityOption("2160p", 2_160, true),
    VideoQualityOption("Max", null, true),
)

private enum class PlaybackEngine {
    VLC,
    MEDIA3,
}

@Composable
private fun PlayerScreen(
    sources: List<String>,
    title: String,
    contentKind: ContentKind,
    startPositionMs: Long,
    onProgress: (Long, Long) -> Unit,
    onBack: () -> Unit,
) {
    var engine by remember(sources) { mutableStateOf(PlaybackEngine.VLC) }
    var vlcExhausted by remember(sources) { mutableStateOf(false) }
    var latestPositionMs by remember(sources) { mutableLongStateOf(startPositionMs) }
    var engineStartPositionMs by remember(sources) { mutableLongStateOf(startPositionMs) }
    val reportProgress: (Long, Long) -> Unit = { positionMs, durationMs ->
        if (contentKind != ContentKind.LIVE && positionMs > 0L) {
            latestPositionMs = positionMs
        }
        onProgress(positionMs, durationMs)
    }
    if (engine == PlaybackEngine.VLC) {
        VlcPlayerScreen(
            sources = sources,
            title = title,
            contentKind = contentKind,
            startPositionMs = engineStartPositionMs,
            onProgress = reportProgress,
            onBack = onBack,
            onUseMedia3 = {
                engineStartPositionMs = latestPositionMs
                vlcExhausted = true
                engine = PlaybackEngine.MEDIA3
            },
        )
    } else {
        Media3PlayerScreen(
            sources = sources,
            title = title,
            contentKind = contentKind,
            startPositionMs = engineStartPositionMs,
            onProgress = reportProgress,
            onBack = onBack,
            canUseVlc = !vlcExhausted,
            onUseVlc = {
                engineStartPositionMs = latestPositionMs
                engine = PlaybackEngine.VLC
            },
        )
    }
}

@Composable
private fun Media3PlayerScreen(
    sources: List<String>,
    title: String,
    contentKind: ContentKind,
    startPositionMs: Long,
    onProgress: (Long, Long) -> Unit,
    onBack: () -> Unit,
    canUseVlc: Boolean,
    onUseVlc: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val responsive = rememberResponsiveLayout()
    val attempts = remember(sources) {
        sources.flatMapIndexed { sourceIndex, _ ->
            listOf(
                PlaybackAttempt(sourceIndex, useTextureView = false),
                PlaybackAttempt(sourceIndex, useTextureView = true),
            )
        }
    }
    var attemptIndex by remember(sources) { mutableIntStateOf(0) }
    var playerError by remember(sources) { mutableStateOf<String?>(null) }
    var externalError by remember(sources) { mutableStateOf<String?>(null) }
    var controlsVisible by remember(sources) { mutableStateOf(true) }
    var isBuffering by remember(sources) { mutableStateOf(true) }
    var selectedQualityIndex by remember(sources) { mutableIntStateOf(0) }
    var fallbackResumePositionMs by remember(sources) { mutableLongStateOf(startPositionMs) }
    var lifecycleStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    var resumeAfterLifecycleStop by remember(sources) { mutableStateOf(true) }
    var queuedFallback by remember(sources) { mutableStateOf<Pair<Int, String>?>(null) }
    var media3View by remember { mutableStateOf<PlayerView?>(null) }
    var media3MenuExpanded by remember(sources) { mutableStateOf(false) }
    val fallbackTransitionGate = remember(sources) { SingleFlightGate() }
    val playerScope = rememberCoroutineScope()
    val mediaBackFocusRequester = remember { FocusRequester() }
    val mediaQualityFocusRequester = remember { FocusRequester() }
    val mediaOtherPlayerFocusRequester = remember { FocusRequester() }
    val playerAttemptIndex = attemptIndex
    val attempt = attempts[attemptIndex.coerceIn(attempts.indices)]
    val activeUrl = sources[attempt.sourceIndex]
    val player = remember(activeUrl, attempt.useTextureView) {
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000,
                60_000,
                2_500,
                5_000,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
            .apply {
            if (contentKind != ContentKind.LIVE && fallbackResumePositionMs > 0L) {
                setMediaItem(createMediaItem(activeUrl), fallbackResumePositionMs)
            } else {
                setMediaItem(createMediaItem(activeUrl))
            }
            prepare()
            playWhenReady = lifecycleStarted && resumeAfterLifecycleStop
            setHandleAudioBecomingNoisy(true)
        }
    }

    fun reportCurrentProgress() {
        if (contentKind != ContentKind.LIVE && player.currentPosition > 0L) {
            onProgress(player.currentPosition, player.duration.coerceAtLeast(0L))
        }
    }

    fun leaveMedia3Player() {
        reportCurrentProgress()
        onBack()
    }

    BackHandler(onBack = ::leaveMedia3Player)

    fun advanceMedia3Fallback(reason: String, generation: Int = playerAttemptIndex) {
        if (generation != attemptIndex) return
        if (!lifecycleStarted) {
            queuedFallback = generation to reason
            return
        }
        if (!fallbackTransitionGate.tryAcquire()) {
            queuedFallback = generation to reason
            return
        }
        queuedFallback = null
        resumeAfterLifecycleStop = player.playWhenReady
        if (contentKind != ContentKind.LIVE && player.currentPosition > 0L) {
            fallbackResumePositionMs = player.currentPosition
            reportCurrentProgress()
        }
        playerError = reason
        runCatching { player.stop() }
        playerScope.launch {
            // ExoPlayer releases its active codecs on stop. Waiting here keeps
            // old Fire TV boxes from having two native decoders starting at once.
            delay(450)
            when {
                attemptIndex < attempts.lastIndex -> attemptIndex += 1
                canUseVlc -> onUseVlc()
                else -> {
                    playerError = "All built-in playback engines failed for this stream."
                    fallbackTransitionGate.release()
                }
            }
        }
    }

    DisposableEffect(player, lifecycleOwner) {
        val playerListener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onRenderedFirstFrame() {
                playerError = null
                isBuffering = false
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                advanceMedia3Fallback(
                    "Playback failed; trying the next built-in fallback.",
                    playerAttemptIndex,
                )
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    lifecycleStarted = true
                    if (resumeAfterLifecycleStop) player.play()
                }
                Lifecycle.Event.ON_STOP -> {
                    lifecycleStarted = false
                    resumeAfterLifecycleStop = player.playWhenReady
                    reportCurrentProgress()
                    player.pause()
                }
                else -> Unit
            }
        }
        player.addListener(playerListener)
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            if (contentKind != ContentKind.LIVE) {
                onProgress(player.currentPosition.coerceAtLeast(0L), player.duration.coerceAtLeast(0L))
            }
            player.removeListener(playerListener)
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    LaunchedEffect(player, contentKind) {
        while (true) {
            delay(5_000)
            if (lifecycleStarted && player.isPlaying) {
                reportCurrentProgress()
            }
        }
    }

    LaunchedEffect(player, playerAttemptIndex) {
        delay(650)
        fallbackTransitionGate.release()
        val pending = queuedFallback
        queuedFallback = null
        if (pending?.first == attemptIndex) {
            advanceMedia3Fallback(pending.second, pending.first)
        }
    }

    LaunchedEffect(lifecycleStarted, playerAttemptIndex) {
        if (lifecycleStarted) {
            val pending = queuedFallback
            queuedFallback = null
            if (pending?.first == attemptIndex) {
                advanceMedia3Fallback(pending.second, pending.first)
            }
        }
    }

    LaunchedEffect(player, selectedQualityIndex) {
        val quality = videoQualityOptions[selectedQualityIndex]
        val parameters = player.trackSelectionParameters.buildUpon()
            .setForceHighestSupportedBitrate(quality.forceHighest)
            .apply {
                if (quality.maxHeight == null) {
                    clearVideoSizeConstraints()
                } else {
                    setMaxVideoSize(Int.MAX_VALUE, quality.maxHeight)
                }
            }
            .build()
        player.trackSelectionParameters = parameters
    }

    LaunchedEffect(player, attemptIndex) {
        delay(10_000)
        if (
            player.isPlaying &&
            player.currentPosition > 1_000 &&
            player.videoSize.width == 0 &&
            attemptIndex < attempts.lastIndex
        ) {
            advanceMedia3Fallback(
                "No video frames detected; trying the next built-in fallback.",
                playerAttemptIndex,
            )
        } else if (
            player.isPlaying &&
            player.currentPosition > 1_000 &&
            player.videoSize.width == 0
        ) {
            if (canUseVlc) {
                advanceMedia3Fallback(
                    "No video frames detected; switching to the next built-in player.",
                    playerAttemptIndex,
                )
            } else {
                playerError = "All built-in playback engines failed to produce video."
            }
        }
    }

    fun openInAnotherPlayer() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(activeUrl), "video/*")
            putExtra(Intent.EXTRA_TITLE, title)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Open with video player"))
            externalError = null
        } catch (_: ActivityNotFoundException) {
            externalError = "No other compatible video player is installed."
        }
    }

    fun keepMedia3ControlsVisible() {
        media3View?.showController()
        controlsVisible = true
    }

    LaunchedEffect(media3MenuExpanded, media3View) {
        media3View?.let { view ->
            view.controllerShowTimeoutMs = if (media3MenuExpanded) 0 else 5_000
            view.showController()
            controlsVisible = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        key(attemptIndex) {
            AndroidView(
                factory = { viewContext ->
                    val layout = if (attempt.useTextureView) {
                        R.layout.player_texture
                    } else {
                        R.layout.player_surface
                    }
                    val scaledViewContext = createScaledAndroidViewContext(
                        context = viewContext,
                        uiScale = responsive.uiScale,
                        textScale = responsive.textScale,
                    )
                    (
                        LayoutInflater.from(scaledViewContext)
                            .inflate(layout, null, false) as PlayerView
                        ).apply {
                        if (!attempt.useTextureView) {
                            setEnableComposeSurfaceSyncWorkaround(true)
                        }
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowSubtitleButton(true)
                        setShowRewindButton(contentKind != ContentKind.LIVE)
                        setShowFastForwardButton(contentKind != ContentKind.LIVE)
                        controllerShowTimeoutMs = 5_000
                        controllerAutoShow = true
                        controllerHideOnTouch = true
                        setControllerVisibilityListener(
                            PlayerView.ControllerVisibilityListener { visibility ->
                                if (visibility != View.VISIBLE && media3MenuExpanded) {
                                    controlsVisible = true
                                    post { showController() }
                                } else {
                                    controlsVisible = visibility == View.VISIBLE
                                }
                                if (visibility != View.VISIBLE && !media3MenuExpanded) {
                                    post { requestFocus() }
                                }
                            },
                        )
                        setOnKeyListener { _, keyCode, keyEvent ->
                            val wakeKey = keyCode == AndroidKeyEvent.KEYCODE_DPAD_UP ||
                                keyCode == AndroidKeyEvent.KEYCODE_DPAD_DOWN ||
                                keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT ||
                                keyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT ||
                                keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                                keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
                                keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER ||
                                keyCode == AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                            if (
                                keyEvent.action == AndroidKeyEvent.ACTION_DOWN &&
                                wakeKey &&
                                !isControllerFullyVisible
                            ) {
                                showController()
                                controlsVisible = true
                                true
                            } else {
                                false
                            }
                        }
                        this.player = player
                        media3View = this
                        post {
                            requestFocus()
                            showController()
                        }
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = { view ->
                    view.player = player
                },
                onRelease = { view ->
                    view.player = null
                    view.setOnKeyListener(null)
                    if (media3View === view) media3View = null
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (controlsVisible) Row(
	            modifier = Modifier
	                .fillMaxWidth()
	                .padding(WindowInsets.safeDrawing.asPaddingValues())
	                .padding(responsive.playerPadding),
	            verticalAlignment = Alignment.CenterVertically,
	        ) {
	            IconButton(
	                onClick = ::leaveMedia3Player,
	                modifier = Modifier
	                    .size(responsive.playerButtonSize)
	                    .focusRequester(mediaBackFocusRequester)
	                    .focusProperties { right = mediaQualityFocusRequester },
            ) {
                Icon(
	                    Icons.AutoMirrored.Filled.ArrowBack,
	                    contentDescription = "Back",
	                    tint = Color.White,
	                    modifier = Modifier.size(responsive.playerIconSize),
	                )
	            }
            Text(
                text = title,
	                modifier = Modifier.weight(1f),
	                color = Color.White,
	                fontWeight = FontWeight.SemiBold,
	                fontSize = responsive.sp(14f),
	                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (contentKind == ContentKind.LIVE) {
                Text(
                    text = "LIVE",
	                    color = MaterialTheme.colorScheme.primary,
	                    fontWeight = FontWeight.Bold,
	                    fontSize = responsive.sp(12f),
	                    modifier = Modifier.padding(horizontal = responsive.dp(12f)),
	                )
	            }
	            Media3QualityMenu(
	                selectedIndex = selectedQualityIndex,
	                onSelect = { selectedQualityIndex = it },
	                onInteraction = ::keepMedia3ControlsVisible,
	                onExpandedChange = { media3MenuExpanded = it },
	                modifier = Modifier
	                    .height(responsive.dp(44f))
	                    .focusRequester(mediaQualityFocusRequester)
                    .focusProperties {
                        left = mediaBackFocusRequester
                        right = mediaOtherPlayerFocusRequester
                    },
	            )
	            TextButton(
	                onClick = {
	                    keepMedia3ControlsVisible()
	                    openInAnotherPlayer()
	                },
	                modifier = Modifier
	                    .height(responsive.dp(44f))
	                    .focusRequester(mediaOtherPlayerFocusRequester)
                    .focusProperties { left = mediaQualityFocusRequester },
            ) {
                Text(
                    text = if (responsive.isTelevision) "Other player" else "Open",
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }
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
        val statusMessage = externalError ?: playerError
        statusMessage?.let { message ->
            Text(
                text = message,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(WindowInsets.safeDrawing.asPaddingValues())
                    .padding(16.dp)
                    .background(Color(0xDD111111), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Media3QualityMenu(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onInteraction: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = {
                onInteraction()
                expanded = true
                onExpandedChange(true)
            },
            modifier = modifier,
        ) {
            Text(
                text = "Quality: ${videoQualityOptions[selectedIndex].label}",
                color = Color.White,
                maxLines = 1,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                onExpandedChange(false)
            },
        ) {
            videoQualityOptions.forEachIndexed { index, quality ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (index == selectedIndex) {
                                "✓ ${quality.label}"
                            } else {
                                quality.label
                            },
                        )
                    },
                    onClick = {
                        onInteraction()
                        onSelect(index)
                        expanded = false
                        onExpandedChange(false)
                    },
                )
            }
        }
    }
}

private fun createMediaItem(url: String): MediaItem {
    val normalized = url.lowercase()
    val mimeType = when {
        ".m3u8" in normalized || "format=m3u8" in normalized || "type=m3u8" in normalized ->
            MimeTypes.APPLICATION_M3U8
        ".mpd" in normalized || "format=mpd" in normalized || "type=mpd" in normalized ->
            MimeTypes.APPLICATION_MPD
        ".ism/manifest" in normalized || normalized.endsWith(".ism") ->
            MimeTypes.APPLICATION_SS
        else -> null
    }
    return MediaItem.Builder()
        .setUri(url)
        .apply { mimeType?.let { setMimeType(it) } }
        .build()
}
