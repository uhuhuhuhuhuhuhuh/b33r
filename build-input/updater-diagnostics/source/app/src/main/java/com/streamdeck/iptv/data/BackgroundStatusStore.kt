package com.streamdeck.iptv.data

import android.content.Context
import com.streamdeck.iptv.BuildConfig
import org.json.JSONObject

data class ExpiredAccountNotice(
    val username: String,
    val expiresAtEpochSeconds: Long?,
)

class BackgroundStatusStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    fun saveAvailableUpdate(update: AppUpdate?) {
        preferences.edit().apply {
            if (update == null) {
                remove(KEY_AVAILABLE_UPDATE)
            } else {
                putString(KEY_AVAILABLE_UPDATE, update.toJson().toString())
            }
        }.apply()
    }

    fun loadAvailableUpdate(): AppUpdate? {
        val raw = preferences.getString(KEY_AVAILABLE_UPDATE, null) ?: return null
        return runCatching { AppUpdate.fromJson(JSONObject(raw)) }
            .getOrNull()
            ?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
    }

    fun hasNotifiedUpdate(versionCode: Int): Boolean =
        preferences.getInt(KEY_NOTIFIED_UPDATE_VERSION, -1) == versionCode

    fun markUpdateNotified(versionCode: Int) {
        preferences.edit().putInt(KEY_NOTIFIED_UPDATE_VERSION, versionCode).apply()
    }

    fun saveAccountExpired(username: String, expiresAtEpochSeconds: Long?) {
        preferences.edit()
            .putString(KEY_EXPIRED_USERNAME, username)
            .apply {
                if (expiresAtEpochSeconds == null) {
                    remove(KEY_EXPIRED_AT)
                } else {
                    putLong(KEY_EXPIRED_AT, expiresAtEpochSeconds)
                }
            }
            .apply()
    }

    fun loadExpiredAccountNotice(): ExpiredAccountNotice? {
        val username = preferences.getString(KEY_EXPIRED_USERNAME, null) ?: return null
        val expiresAt = if (preferences.contains(KEY_EXPIRED_AT)) {
            preferences.getLong(KEY_EXPIRED_AT, 0L).takeIf { it > 0L }
        } else {
            null
        }
        return ExpiredAccountNotice(username, expiresAt)
    }

    fun clearExpiredAccountNotice() {
        preferences.edit()
            .remove(KEY_EXPIRED_USERNAME)
            .remove(KEY_EXPIRED_AT)
            .remove(KEY_NOTIFIED_EXPIRED_AT)
            .apply()
    }

    fun hasNotifiedExpired(expiresAtEpochSeconds: Long?): Boolean =
        preferences.getLong(KEY_NOTIFIED_EXPIRED_AT, Long.MIN_VALUE) ==
            (expiresAtEpochSeconds ?: 0L)

    fun markExpiredNotified(expiresAtEpochSeconds: Long?) {
        preferences.edit()
            .putLong(KEY_NOTIFIED_EXPIRED_AT, expiresAtEpochSeconds ?: 0L)
            .apply()
    }

    private companion object {
        const val PREFERENCES = "background_status"
        const val KEY_AVAILABLE_UPDATE = "available_update"
        const val KEY_NOTIFIED_UPDATE_VERSION = "notified_update_version"
        const val KEY_EXPIRED_USERNAME = "expired_username"
        const val KEY_EXPIRED_AT = "expired_at"
        const val KEY_NOTIFIED_EXPIRED_AT = "notified_expired_at"
    }
}
