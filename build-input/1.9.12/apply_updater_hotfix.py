from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


view_model_path = Path("app/src/main/java/com/streamdeck/iptv/IptvViewModel.kt")
view_model = view_model_path.read_text()
view_model = replace_once(
    view_model,
    """    val availableUpdate: AppUpdate? = null,
    val updateDownloading: Boolean = false,
    val updateAwaitingPermission: Boolean = false,
    val updateError: String? = null,
    val updateDismissed: Boolean = false,
""",
    """    val availableUpdate: AppUpdate? = null,
    val updateChecking: Boolean = false,
    val updateDownloading: Boolean = false,
    val updateAwaitingPermission: Boolean = false,
    val updateError: String? = null,
    val updateCheckMessage: String? = null,
    val updateDismissed: Boolean = false,
""",
    "update state fields",
)
view_model = replace_once(
    view_model,
    """    private fun checkForUpdates() {
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
""",
    """    private fun checkForUpdates(manual: Boolean = false) {
        if (manual && (state.updateChecking || state.updateDownloading)) return
        if (manual) {
            state = state.copy(
                updateChecking = true,
                updateCheckMessage = null,
                updateError = null,
            )
        }
        viewModelScope.launch {
            runCatching { updateManager.checkForUpdate() }
                .onSuccess { update ->
                    backgroundStatusStore.saveAvailableUpdate(update)
                    state = state.copy(
                        availableUpdate = update,
                        updateChecking = false,
                        updateDismissed = false,
                        updateError = null,
                        updateCheckMessage = if (manual && update == null) {
                            if (BuildConfig.DEBUG) {
                                "This is a preview build. The stable update channel has no newer compatible release."
                            } else {
                                "You're up to date. Version ${BuildConfig.VERSION_NAME} is installed."
                            }
                        } else {
                            null
                        },
                    )
                }
                .onFailure { error ->
                    state = state.copy(
                        updateChecking = false,
                        updateCheckMessage = if (manual) {
                            error.message ?: "Unable to check for updates."
                        } else {
                            state.updateCheckMessage
                        },
                    )
                }
        }
    }

    fun checkForUpdatesManually() {
        checkForUpdates(manual = true)
    }

    fun dismissUpdateCheckMessage() {
        state = state.copy(updateCheckMessage = null)
    }
""",
    "update check implementation",
)
view_model_path.write_text(view_model)

ui_path = Path("app/src/main/java/com/streamdeck/iptv/ui/AppUi.kt")
ui = ui_path.read_text()
ui = replace_once(
    ui,
    "import androidx.compose.material.icons.filled.Movie\n",
    "import androidx.compose.material.icons.filled.Movie\nimport androidx.compose.material.icons.filled.SystemUpdate\n",
    "system update icon import",
)
ui = replace_once(
    ui,
    """                            onBackFromSeries = viewModel::closeSeries,
                            onLogout = viewModel::logout,
                        )
""",
    """                            onBackFromSeries = viewModel::closeSeries,
                            onLogout = viewModel::logout,
                            onCheckForUpdates = viewModel::checkForUpdatesManually,
                        )
""",
    "library update callback",
)
ui = replace_once(
    ui,
    """                    }
            }
        }
    }
}

@Composable
private fun UpdateDialog(
""",
    """                    }
                state.updateCheckMessage
                    ?.takeIf { state.availableUpdate == null && state.playbackUrl == null }
                    ?.let { message ->
                        AlertDialog(
                            onDismissRequest = viewModel::dismissUpdateCheckMessage,
                            title = { Text("App updates") },
                            text = { Text(message) },
                            confirmButton = {
                                TextButton(onClick = viewModel::dismissUpdateCheckMessage) {
                                    Text("OK")
                                }
                            },
                        )
                    }
            }
        }
    }
}

@Composable
private fun UpdateDialog(
""",
    "manual update result dialog",
)
ui = replace_once(
    ui,
    """    onBackFromSeries: () -> Unit,
    onLogout: () -> Unit,
) {
""",
    """    onBackFromSeries: () -> Unit,
    onLogout: () -> Unit,
    onCheckForUpdates: () -> Unit,
) {
""",
    "library screen signature",
)
ui = replace_once(
    ui,
    """                    onBackFromSeries = onBackFromSeries,
                    sectionNavigationFocusRequester = sectionNavigationFocusRequester,
                )
""",
    """                    onBackFromSeries = onBackFromSeries,
                    onCheckForUpdates = onCheckForUpdates,
                    sectionNavigationFocusRequester = sectionNavigationFocusRequester,
                )
""",
    "wide library callback",
)
ui = replace_once(
    ui,
    """                    onBackFromSeries = onBackFromSeries,
                    onLogout = onLogout,
                    sectionNavigationFocusRequester = sectionNavigationFocusRequester,
""",
    """                    onBackFromSeries = onBackFromSeries,
                    onLogout = onLogout,
                    onCheckForUpdates = onCheckForUpdates,
                    sectionNavigationFocusRequester = sectionNavigationFocusRequester,
""",
    "compact library callback",
)
ui = replace_once(
    ui,
    """    onBackFromSeries: () -> Unit,
    sectionNavigationFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onLogout: (() -> Unit)? = null,
) {
""",
    """    onBackFromSeries: () -> Unit,
    onCheckForUpdates: () -> Unit,
    sectionNavigationFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onLogout: (() -> Unit)? = null,
) {
""",
    "library content signature",
)
ui = replace_once(
    ui,
    """            onBackFromSeries = onBackFromSeries,
            onLogout = onLogout,
        )
""",
    """            onBackFromSeries = onBackFromSeries,
            onLogout = onLogout,
            checkingUpdates = state.updateChecking,
            onCheckForUpdates = onCheckForUpdates,
        )
""",
    "top bar update callback",
)
ui = replace_once(
    ui,
    """    responsive: ResponsiveLayout,
    onBackFromSeries: () -> Unit,
    onLogout: (() -> Unit)?,
) {
""",
    """    responsive: ResponsiveLayout,
    onBackFromSeries: () -> Unit,
    onLogout: (() -> Unit)?,
    checkingUpdates: Boolean,
    onCheckForUpdates: () -> Unit,
) {
""",
    "top bar signature",
)
compact_logout = """                        onLogout?.let { logout ->
                            IconButton(onClick = logout) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log out")
                            }
                        }
"""
compact_replacement = """                        IconButton(
                            onClick = onCheckForUpdates,
                            enabled = !checkingUpdates,
                        ) {
                            if (checkingUpdates) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(responsive.dp(20f)),
                                    strokeWidth = responsive.dp(2f),
                                )
                            } else {
                                Icon(Icons.Filled.SystemUpdate, contentDescription = "Check for updates")
                            }
                        }
                        onLogout?.let { logout ->
                            IconButton(onClick = logout) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log out")
                            }
                        }
"""
ui = replace_once(ui, compact_logout, compact_replacement, "compact update button")
wide_logout = """                    onLogout?.let { logout ->
                        IconButton(onClick = logout) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log out")
                        }
                    }
"""
wide_replacement = """                    IconButton(
                        onClick = onCheckForUpdates,
                        enabled = !checkingUpdates,
                    ) {
                        if (checkingUpdates) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(responsive.dp(22f)),
                                strokeWidth = responsive.dp(2f),
                            )
                        } else {
                            Icon(Icons.Filled.SystemUpdate, contentDescription = "Check for updates")
                        }
                    }
                    onLogout?.let { logout ->
                        IconButton(onClick = logout) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log out")
                        }
                    }
"""
ui = replace_once(ui, wide_logout, wide_replacement, "wide update button")
ui_path.write_text(ui)
