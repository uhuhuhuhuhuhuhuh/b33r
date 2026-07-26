package com.streamdeck.iptv.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class BackgroundRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val updateManager = AppUpdateManager(applicationContext)
        val credentialStore = CredentialStore(applicationContext)
        val statusStore = BackgroundStatusStore(applicationContext)
        val notifications = BackgroundNotificationHelper(applicationContext)
        var retryNetworkWork = false

        val session = credentialStore.loadSession()
        if (session?.expired == true) {
            expireSession(
                sessionId = session.sessionId,
                username = session.accountUsername,
                expiresAtEpochSeconds = session.expiresAtEpochSeconds,
                credentialStore = credentialStore,
                statusStore = statusStore,
                notifications = notifications,
            )
        }

        try {
            val update = updateManager.checkForUpdate()
            statusStore.saveAvailableUpdate(update)
            if (update != null && !statusStore.hasNotifiedUpdate(update.versionCode)) {
                if (notifications.notifyUpdate(update)) {
                    statusStore.markUpdateNotified(update.versionCode)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            retryNetworkWork = error is IOException
        }

        if (session == null || session.expired) {
            return if (retryNetworkWork) Result.retry() else Result.success()
        }

        try {
            val account = XtreamApi().authenticate(session.credentials, savedSession = true)
            val updated = credentialStore.updateIfCurrent(session, account)
            if (updated != null) {
                statusStore.clearExpiredAccountNotice()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (error is AccountExpiredException || session.expired) {
                expireSession(
                    sessionId = session.sessionId,
                    username = session.accountUsername,
                    expiresAtEpochSeconds = session.expiresAtEpochSeconds,
                    credentialStore = credentialStore,
                    statusStore = statusStore,
                    notifications = notifications,
                )
            } else if (error is XtreamNetworkException) {
                retryNetworkWork = true
            }
        }

        return if (retryNetworkWork) Result.retry() else Result.success()
    }

    private fun expireSession(
        sessionId: String,
        username: String,
        expiresAtEpochSeconds: Long?,
        credentialStore: CredentialStore,
        statusStore: BackgroundStatusStore,
        notifications: BackgroundNotificationHelper,
    ) {
        if (!credentialStore.clearIfCurrent(sessionId)) {
            return
        }
        statusStore.saveAccountExpired(username, expiresAtEpochSeconds)
        if (!statusStore.hasNotifiedExpired(expiresAtEpochSeconds)) {
            if (notifications.notifyExpired(username)) {
                statusStore.markExpiredNotified(expiresAtEpochSeconds)
            }
        }
    }
}

object BackgroundRefreshScheduler {
    private const val PERIODIC_WORK = "background_refresh"
    private const val IMMEDIATE_WORK = "background_refresh_once"

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val periodic = PeriodicWorkRequestBuilder<BackgroundRefreshWorker>(
            6,
            TimeUnit.HOURS,
        )
            .build()

        val workManager = WorkManager.getInstance(appContext)
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic,
        )
        // The foreground view model already performs this refresh at launch.
        // Removing the old one-time job prevents duplicate update/auth traffic
        // while the first catalog is loading on memory-constrained TV boxes.
        workManager.cancelUniqueWork(IMMEDIATE_WORK)
    }
}
