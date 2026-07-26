package com.streamdeck.iptv

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.streamdeck.iptv.data.AccountExpiredException
import com.streamdeck.iptv.data.AppUpdate
import com.streamdeck.iptv.data.AppUpdateManager
import com.streamdeck.iptv.data.BackgroundStatusStore
import com.streamdeck.iptv.data.Category
import com.streamdeck.iptv.data.ContentKind
import com.streamdeck.iptv.data.CredentialStore
import com.streamdeck.iptv.data.Credentials
import com.streamdeck.iptv.data.LibrarySection
import com.streamdeck.iptv.data.LocalLibraryStore
import com.streamdeck.iptv.data.ResumeEntry
import com.streamdeck.iptv.data.StoredSession
import com.streamdeck.iptv.data.StreamItem
import com.streamdeck.iptv.data.XtreamApi
import com.streamdeck.iptv.data.canBeFavorited
import com.streamdeck.iptv.data.canResume
import com.streamdeck.iptv.data.currentEpochSeconds
import com.streamdeck.iptv.data.localLibraryKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

data class IptvUiState(
    val checkingSession: Boolean = true,
    val authenticated: Boolean = false,
    val loading: Boolean = false,
    val openingItem: Boolean = false,
    val error: String? = null,
    val accountUsername: String = "",
    val accountExpiresAtEpochSeconds: Long? = null,
    val accountExpired: Boolean = false,
    val section: LibrarySection = LibrarySection.LIVE,
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val items: List<StreamItem> = emptyList(),
    val favoriteItems: List<StreamItem> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
    val resumeEntries: List<ResumeEntry> = emptyList(),
    val continueWatchingItems: List<StreamItem> = emptyList(),
    val seriesTitle: String? = null,
    val seriesId: String? = null,
    val episodes: List<StreamItem> = emptyList(),
    val searchQueries: Map<String, String> = emptyMap(),
    val playbackUrl: String? = null,
    val playbackSources: List<String> = emptyList(),
    val playbackTitle: String = "",
    val playbackKind: ContentKind? = null,
    val playbackItem: StreamItem? = null,
    val playbackStartPositionMs: Long = 0L,
    val availableUpdate: AppUpdate? = null,
    val updateDownloading: Boolean = false,
    val updateAwaitingPermission: Boolean = false,
    val updateError: String? = null,
    val updateDismissed: Boolean = false,
) {
    val searchKey: String
        get() = seriesId?.let { "episodes:$it" } ?: "section:${section.name}"

    val searchQuery: String
        get() = searchQueries[searchKey].orEmpty()
}

private data class ActiveSession(
    val sessionId: String,
    val credentials: Credentials,
)

class IptvViewModel(application: Application) : AndroidViewModel(application) {
    private val api = XtreamApi()
    private val credentialStore = CredentialStore(application)
    private val localLibraryStore = LocalLibraryStore(application)
    private val updateManager = AppUpdateManager(application)
    private val backgroundStatusStore = BackgroundStatusStore(application)
    private var credentials: Credentials? = null
    private var activeSessionId: String? = null
    private var catalogJob: Job? = null
    private var openJob: Job? = null
    private var loginJob: Job? = null
    private var expirationJob: Job? = null
    private var accountRefreshJob: Job? = null
    private var visibleAccountRefreshJob: Job? = null
    private var updateInstallJob: Job? = null
    private var lastOpenAtElapsedMs: Long = 0L

    var state by mutableStateOf(IptvUiState())
        private set

    init {
        refreshLocalLibraryState()
        backgroundStatusStore.loadAvailableUpdate()?.let { update ->
            state = state.copy(availableUpdate = update)
        }
        checkForUpdates()
        val savedSession = credentialStore.loadSession()
        if (savedSession == null) {
            val expiredNotice = backgroundStatusStore.loadExpiredAccountNotice()
            state = state.copy(
                checkingSession = false,
                error = expiredNotice?.let { "Account expired." },
            )
        } else if (savedSession.expired) {
            credentials = savedSession.credentials
            activeSessionId = savedSession.sessionId
            handleAccountExpired(
                username = savedSession.accountUsername,
                expiresAtEpochSeconds = savedSession.expiresAtEpochSeconds,
                expectedSessionId = savedSession.sessionId,
            )
        } else {
            credentials = savedSession.credentials
            activeSessionId = savedSession.sessionId
            state = state.copy(
                checkingSession = false,
                authenticated = true,
                accountUsername = savedSession.accountUsername,
                accountExpiresAtEpochSeconds = savedSession.expiresAtEpochSeconds,
            )
            scheduleExpiration(savedSession.expiresAtEpochSeconds)
            startAccountRefreshLoop()
            refreshAccountInfo(savedSession)
            loadSection(LibrarySection.LIVE)
        }
    }

    private fun refreshAccountInfo(session: StoredSession) {
        visibleAccountRefreshJob?.cancel()
        visibleAccountRefreshJob = viewModelScope.launch {
            refreshAccountInfoOnce(session)
        }
    }

    private suspend fun refreshAccountInfoOnce(session: StoredSession) {
        try {
            val account = api.authenticate(session.credentials, savedSession = true)
            val updatedSession = credentialStore.updateIfCurrent(session, account)
            if (
                updatedSession == null &&
                activeSessionId == session.sessionId &&
                credentials == session.credentials &&
                credentialStore.loadSession() == null
            ) {
                handleSessionInvalidated(session.sessionId)
                return
            }
            if (
                updatedSession != null &&
                activeSessionId == session.sessionId &&
                credentials == session.credentials
            ) {
                backgroundStatusStore.clearExpiredAccountNotice()
                state = state.copy(
                    accountUsername = account.username,
                    accountExpiresAtEpochSeconds = account.expiresAtEpochSeconds,
                )
                scheduleExpiration(account.expiresAtEpochSeconds)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (error is AccountExpiredException || session.expired) {
                handleAccountExpired(
                    username = session.accountUsername,
                    expiresAtEpochSeconds = session.expiresAtEpochSeconds,
                    expectedSessionId = session.sessionId,
                )
            }
        }
    }

    private fun checkForUpdates() {
        viewModelScope.launch {
            runCatching { updateManager.checkForUpdate() }
                .onSuccess { update ->
                    backgroundStatusStore.saveAvailableUpdate(update)
                    state = state.copy(
                        availableUpdate = update,
                        updateDismissed = false,
                        updateError = null,
                    )
                }
        }
    }

    fun installAvailableUpdate() {
        val update = state.availableUpdate ?: return
        if (state.updateDownloading || updateInstallJob?.isActive == true) return
        state = state.copy(
            updateDownloading = true,
            updateAwaitingPermission = false,
            updateError = null,
        )
        updateInstallJob = viewModelScope.launch {
            try {
                val apk = updateManager.downloadAndVerify(update)
                val installerOpened = updateManager.openInstallerOrSettings(apk)
                state = state.copy(
                    updateDownloading = false,
                    updateAwaitingPermission = !installerOpened,
                    updateDismissed = installerOpened,
                    updateError = if (installerOpened) {
                        null
                    } else {
                        "Allow b33r IPTV to install updates, then return to continue."
                    },
                )
            } catch (error: CancellationException) {
                state = state.copy(updateDownloading = false)
                throw error
            } catch (error: Exception) {
                state = state.copy(
                    updateDownloading = false,
                    updateError = error.message ?: "The update could not be downloaded.",
                )
            }
        }
    }

    fun resumePendingUpdateInstall() {
        if (!state.updateAwaitingPermission) return
        val installerOpened = runCatching { updateManager.resumePendingInstall() }
            .getOrElse { error ->
                state = state.copy(
                    updateError = error.message ?: "The Android installer could not be opened.",
                )
                false
            }
        if (installerOpened) {
            state = state.copy(
                updateAwaitingPermission = false,
                updateDismissed = true,
                updateError = null,
            )
        }
    }

    fun refreshVisibleAccountInfo() {
        val currentCredentials = credentials ?: return
        val currentSessionId = activeSessionId ?: return
        val session = credentialStore.loadSession()
        if (session == null) {
            val expiredNotice = backgroundStatusStore.loadExpiredAccountNotice()
            if (expiredNotice != null) {
                handleAccountExpired(
                    username = expiredNotice.username,
                    expiresAtEpochSeconds = expiredNotice.expiresAtEpochSeconds,
                    expectedSessionId = currentSessionId,
                    storageAlreadyCleared = true,
                )
            } else {
                handleSessionInvalidated(currentSessionId)
            }
            return
        }
        if (session.sessionId != currentSessionId || session.credentials != currentCredentials) return
        if (session.expired) {
            handleAccountExpired(
                username = session.accountUsername,
                expiresAtEpochSeconds = session.expiresAtEpochSeconds,
                expectedSessionId = session.sessionId,
            )
            return
        }
        state = state.copy(
            accountUsername = session.accountUsername,
            accountExpiresAtEpochSeconds = session.expiresAtEpochSeconds,
            accountExpired = false,
        )
        scheduleExpiration(session.expiresAtEpochSeconds)
        refreshAccountInfo(session)
    }

    fun dismissUpdate() {
        if (!state.updateDownloading) {
            state = state.copy(updateDismissed = true)
        }
    }

    fun login(username: String, password: String) {
        if (state.loading || loginJob?.isActive == true) return
        if (username.isBlank() || password.isBlank()) {
            state = state.copy(error = "Enter both username and password.")
            return
        }
        state = state.copy(loading = true, error = null)
        loginJob = viewModelScope.launch {
            val candidate = Credentials(username.trim(), password)
            try {
                val account = api.authenticate(candidate)
                val storedSession = credentialStore.save(candidate, account)
                backgroundStatusStore.clearExpiredAccountNotice()
                credentials = candidate
                activeSessionId = storedSession.sessionId
                state = state.copy(
                    authenticated = true,
                    loading = false,
                    accountUsername = account.username,
                    accountExpiresAtEpochSeconds = account.expiresAtEpochSeconds,
                    accountExpired = false,
                )
                scheduleExpiration(account.expiresAtEpochSeconds)
                startAccountRefreshLoop()
                loadSection(LibrarySection.LIVE)
            } catch (error: CancellationException) {
                state = state.copy(loading = false)
                throw error
            } catch (error: Exception) {
                state = state.copy(
                    loading = false,
                    error = error.message ?: "Login failed.",
                )
            }
        }
    }

    fun logout() {
        catalogJob?.cancel()
        openJob?.cancel()
        loginJob?.cancel()
        expirationJob?.cancel()
        accountRefreshJob?.cancel()
        visibleAccountRefreshJob?.cancel()
        updateInstallJob?.cancel()
        val sessionId = activeSessionId
        if (sessionId == null) {
            credentialStore.clear()
        } else {
            credentialStore.clearIfCurrent(sessionId)
        }
        backgroundStatusStore.clearExpiredAccountNotice()
        credentials = null
        activeSessionId = null
        state = IptvUiState(checkingSession = false)
        refreshLocalLibraryState()
    }

    fun loadSection(section: LibrarySection) {
        if (
            section == state.section &&
            state.seriesTitle == null &&
            (state.loading || state.items.isNotEmpty())
        ) {
            return
        }
        openJob?.cancel()
        if (section == LibrarySection.FAVORITES || section == LibrarySection.RESUME) {
            catalogJob?.cancel()
            refreshLocalLibraryState()
            state = state.copy(
                section = section,
                loading = false,
                openingItem = false,
                error = null,
                categories = emptyList(),
                selectedCategoryId = null,
                items = localItems(section),
                seriesTitle = null,
                seriesId = null,
                episodes = emptyList(),
            )
            return
        }
        val session = usableSession() ?: return
        catalogJob?.cancel()
        state = state.copy(
            section = section,
            loading = true,
            openingItem = false,
            error = null,
            categories = emptyList(),
            selectedCategoryId = null,
            items = emptyList(),
            seriesTitle = null,
            seriesId = null,
            episodes = emptyList(),
        )
        catalogJob = viewModelScope.launch {
            try {
                val categories = api.categories(session.credentials, section)
                coroutineContext.ensureActive()
                if (state.section == section && state.selectedCategoryId == null) {
                    state = state.copy(categories = categories)
                }
                val streams = api.streams(session.credentials, section, null)
                coroutineContext.ensureActive()
                if (state.section == section && state.selectedCategoryId == null) {
                    state = state.copy(
                        loading = false,
                        categories = categories,
                        items = streams,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error is AccountExpiredException) {
                    handleAccountExpired(
                        username = state.accountUsername,
                        expiresAtEpochSeconds = state.accountExpiresAtEpochSeconds,
                        expectedSessionId = session.sessionId,
                    )
                    return@launch
                }
                if (state.section == section) {
                    state = state.copy(
                        loading = false,
                        error = error.message ?: "Unable to load the catalog.",
                    )
                }
            }
        }
    }

    fun selectCategory(categoryId: String?) {
        if (categoryId == state.selectedCategoryId) return
        if (state.section == LibrarySection.FAVORITES || state.section == LibrarySection.RESUME) return
        val session = usableSession() ?: return
        catalogJob?.cancel()
        val section = state.section
        state = state.copy(
            selectedCategoryId = categoryId,
            loading = true,
            error = null,
            items = emptyList(),
        )
        catalogJob = viewModelScope.launch {
            try {
                val streams = api.streams(session.credentials, section, categoryId)
                coroutineContext.ensureActive()
                if (state.section == section && state.selectedCategoryId == categoryId) {
                    state = state.copy(loading = false, items = streams)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error is AccountExpiredException) {
                    handleAccountExpired(
                        username = state.accountUsername,
                        expiresAtEpochSeconds = state.accountExpiresAtEpochSeconds,
                        expectedSessionId = session.sessionId,
                    )
                    return@launch
                }
                if (state.section == section && state.selectedCategoryId == categoryId) {
                    state = state.copy(
                        loading = false,
                        error = error.message ?: "Unable to load this category.",
                    )
                }
            }
        }
    }

    fun open(item: StreamItem) {
        if (
            state.loading ||
            state.openingItem ||
            state.playbackUrl != null ||
            openJob?.isActive == true
        ) {
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastOpenAtElapsedMs < OPEN_ACTION_DEBOUNCE_MS) return
        lastOpenAtElapsedMs = now
        val session = usableSession() ?: return
        val itemToOpen = if (state.section == LibrarySection.RESUME) {
            originalResumeItem(item)
        } else {
            item
        }
        state = state.copy(openingItem = true, error = null)
        if (itemToOpen.kind == ContentKind.SERIES) {
            openJob = viewModelScope.launch {
                try {
                    val episodes = api.episodes(session.credentials, itemToOpen.id)
                    coroutineContext.ensureActive()
                    state = state.copy(
                        openingItem = false,
                        seriesTitle = itemToOpen.name,
                        seriesId = itemToOpen.id,
                        episodes = episodes,
                        error = if (episodes.isEmpty()) {
                            "No episodes were returned for this series."
                        } else {
                            null
                        },
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (error is AccountExpiredException) {
                        handleAccountExpired(
                            username = state.accountUsername,
                            expiresAtEpochSeconds = state.accountExpiresAtEpochSeconds,
                            expectedSessionId = session.sessionId,
                        )
                        return@launch
                    }
                    state = state.copy(
                        openingItem = false,
                        error = error.message ?: "Unable to load episodes.",
                    )
                }
            }
        } else {
            val playbackSources = api.playbackUrls(session.credentials, itemToOpen)
            val firstSource = playbackSources.firstOrNull()
            if (firstSource == null) {
                state = state.copy(
                    openingItem = false,
                    error = "No playable address was returned for this item.",
                )
                return
            }
            state = state.copy(
                openingItem = false,
                playbackUrl = firstSource,
                playbackSources = playbackSources,
                playbackTitle = itemToOpen.name,
                playbackKind = itemToOpen.kind,
                playbackItem = itemToOpen,
                playbackStartPositionMs = if (itemToOpen.canResume()) {
                    localLibraryStore.resumePositionMs(itemToOpen)
                } else {
                    0L
                },
                error = null,
            )
        }
    }

    fun closeSeries() {
        state = state.copy(
            seriesTitle = null,
            seriesId = null,
            episodes = emptyList(),
            error = null,
        )
    }

    fun updateSearchQuery(query: String) {
        state = state.copy(
            searchQueries = state.searchQueries + (state.searchKey to query),
        )
    }

    fun toggleFavorite(item: StreamItem) {
        if (!item.canBeFavorited()) return
        localLibraryStore.toggleFavorite(item)
        refreshLocalLibraryState()
    }

    fun savePlaybackProgress(item: StreamItem, positionMs: Long, durationMs: Long) {
        if (!item.canResume()) return
        localLibraryStore.saveResumeProgress(item, positionMs, durationMs)
    }

    fun closePlayer() {
        if (state.playbackUrl == null) return
        state = state.copy(
            playbackUrl = null,
            playbackSources = emptyList(),
            playbackTitle = "",
            playbackKind = null,
            playbackItem = null,
            playbackStartPositionMs = 0L,
        )
        refreshLocalLibraryState()
    }

    fun clearError() {
        state = state.copy(error = null)
    }

    private fun refreshLocalLibraryState() {
        val favorites = localLibraryStore.favoriteItems()
        val resumeEntries = localLibraryStore.resumeEntries()
        val continueWatchingItems = resumeEntries.map { it.toContinueWatchingItem() }
        state = state.copy(
            favoriteItems = favorites,
            favoriteKeys = favorites.map { it.localLibraryKey() }.toSet(),
            resumeEntries = resumeEntries,
            continueWatchingItems = continueWatchingItems,
            items = when {
                state.seriesTitle != null -> state.items
                state.section == LibrarySection.FAVORITES -> favorites
                state.section == LibrarySection.RESUME -> continueWatchingItems
                else -> state.items
            },
        )
    }

    private fun localItems(section: LibrarySection): List<StreamItem> =
        when (section) {
            LibrarySection.FAVORITES -> state.favoriteItems
            LibrarySection.RESUME -> state.continueWatchingItems
            else -> emptyList()
        }

    private fun originalResumeItem(item: StreamItem): StreamItem =
        state.resumeEntries
            .firstOrNull { it.item.localLibraryKey() == item.localLibraryKey() }
            ?.item
            ?: item

    private fun ResumeEntry.toContinueWatchingItem(): StreamItem {
        val parts = buildList {
            if (item.subtitle.isNotBlank()) add(item.subtitle)
            add("Left off at ${formatPlaybackTimestamp(positionMs)}")
            if (durationMs > 0L) add("of ${formatPlaybackTimestamp(durationMs)}")
        }
        return item.copy(subtitle = parts.joinToString("  •  "))
    }

    private fun formatPlaybackTimestamp(milliseconds: Long): String {
        if (milliseconds <= 0L) return "0:00"
        val totalSeconds = milliseconds / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    private fun usableSession(): ActiveSession? {
        val sessionCredentials = credentials ?: return null
        val sessionId = activeSessionId ?: return null
        if (isExpired(state.accountExpiresAtEpochSeconds)) {
            handleAccountExpired(
                username = state.accountUsername.ifBlank { sessionCredentials.username },
                expiresAtEpochSeconds = state.accountExpiresAtEpochSeconds,
                expectedSessionId = sessionId,
            )
            return null
        }
        return ActiveSession(sessionId, sessionCredentials)
    }

    private fun scheduleExpiration(expiresAtEpochSeconds: Long?) {
        expirationJob?.cancel()
        if (expiresAtEpochSeconds == null) return
        val expectedSessionId = activeSessionId ?: return
        val delayMillis = ((expiresAtEpochSeconds - currentEpochSeconds()) * 1_000L).coerceAtLeast(0L)
        expirationJob = viewModelScope.launch {
            delay(delayMillis + 1_000L)
            if (
                activeSessionId == expectedSessionId &&
                isExpired(expiresAtEpochSeconds)
            ) {
                handleAccountExpired(
                    username = state.accountUsername,
                    expiresAtEpochSeconds = expiresAtEpochSeconds,
                    expectedSessionId = expectedSessionId,
                )
            }
        }
    }

    private fun startAccountRefreshLoop() {
        accountRefreshJob?.cancel()
        accountRefreshJob = viewModelScope.launch {
            while (true) {
                delay(ACCOUNT_REFRESH_INTERVAL_MS)
                val session = credentialStore.loadSession()
                if (session == null) {
                    activeSessionId?.let { currentSessionId ->
                        val expiredNotice = backgroundStatusStore.loadExpiredAccountNotice()
                        if (expiredNotice != null) {
                            handleAccountExpired(
                                username = expiredNotice.username,
                                expiresAtEpochSeconds = expiredNotice.expiresAtEpochSeconds,
                                expectedSessionId = currentSessionId,
                                storageAlreadyCleared = true,
                            )
                        } else {
                            handleSessionInvalidated(currentSessionId)
                        }
                    }
                    continue
                }
                if (
                    activeSessionId == session.sessionId &&
                    credentials == session.credentials
                ) {
                    refreshAccountInfoOnce(session)
                }
            }
        }
    }

    private fun handleAccountExpired(
        username: String,
        expiresAtEpochSeconds: Long?,
        expectedSessionId: String,
        storageAlreadyCleared: Boolean = false,
    ) {
        if (activeSessionId != expectedSessionId) return
        val storedSession = credentialStore.loadSession()
        if (storedSession != null && storedSession.sessionId != expectedSessionId) return
        if (
            !storageAlreadyCleared &&
            storedSession != null &&
            !credentialStore.clearIfCurrent(expectedSessionId)
        ) {
            return
        }
        catalogJob?.cancel()
        openJob?.cancel()
        loginJob?.cancel()
        expirationJob?.cancel()
        accountRefreshJob?.cancel()
        visibleAccountRefreshJob?.cancel()
        backgroundStatusStore.saveAccountExpired(username, expiresAtEpochSeconds)
        credentials = null
        activeSessionId = null
        state = IptvUiState(
            checkingSession = false,
            error = "Account expired.",
            accountUsername = username,
            accountExpiresAtEpochSeconds = expiresAtEpochSeconds,
            accountExpired = true,
            availableUpdate = state.availableUpdate,
        )
        refreshLocalLibraryState()
    }

    private fun handleSessionInvalidated(expectedSessionId: String) {
        if (activeSessionId != expectedSessionId) return
        catalogJob?.cancel()
        openJob?.cancel()
        loginJob?.cancel()
        expirationJob?.cancel()
        accountRefreshJob?.cancel()
        visibleAccountRefreshJob?.cancel()
        credentials = null
        activeSessionId = null
        state = IptvUiState(
            checkingSession = false,
            error = "Saved login is no longer available. Please sign in again.",
            availableUpdate = state.availableUpdate,
        )
        refreshLocalLibraryState()
    }

    private fun isExpired(expiresAtEpochSeconds: Long?): Boolean =
        expiresAtEpochSeconds?.let { it <= currentEpochSeconds() } == true

    private companion object {
        const val ACCOUNT_REFRESH_INTERVAL_MS = 5 * 60 * 1_000L
        const val OPEN_ACTION_DEBOUNCE_MS = 650L
    }
}
