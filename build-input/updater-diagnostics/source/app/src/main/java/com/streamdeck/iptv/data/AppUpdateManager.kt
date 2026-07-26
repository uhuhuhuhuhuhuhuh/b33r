package com.streamdeck.iptv.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.streamdeck.iptv.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val releaseDate: String,
    val notes: List<String>,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("versionCode", versionCode)
        .put("versionName", versionName)
        .put("apkUrl", apkUrl)
        .put("sha256", sha256)
        .put("sizeBytes", sizeBytes)
        .put("releaseDate", releaseDate)
        .put("notes", notes)

    companion object {
        fun fromJson(document: JSONObject): AppUpdate = AppUpdate(
            versionCode = document.getInt("versionCode"),
            versionName = document.getString("versionName"),
            apkUrl = document.getString("apkUrl"),
            sha256 = document.getString("sha256").lowercase(),
            sizeBytes = document.optLong("sizeBytes", -1L),
            releaseDate = document.optString("releaseDate"),
            notes = document.optJSONArray("notes")?.let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        add(array.getString(index))
                    }
                }
            }.orEmpty(),
        )
    }
}

class AppUpdateManager(private val context: Context) {
    private var pendingApk: File? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(false)
        .build()

    suspend fun checkForUpdate(): AppUpdate? = withContext(Dispatchers.IO) {
        val cacheBuster = if ("?" in BuildConfig.UPDATE_MANIFEST_URL) "&" else "?"
        val manifestUrl =
            "${BuildConfig.UPDATE_MANIFEST_URL}${cacheBuster}installed=${BuildConfig.VERSION_CODE}"
        val json = getText(manifestUrl)
        val update = AppUpdate.fromJson(JSONObject(json))
        validateUpdate(update)
        update.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
    }

    suspend fun downloadAndVerify(update: AppUpdate): File = withContext(Dispatchers.IO) {
        validateUpdate(update)
        val updateDirectory = File(context.cacheDir, "updates").apply { mkdirs() }
        val finalFile = File(updateDirectory, "StreamDeck-IPTV-${update.versionName}.apk")
        val cachedSizeIsValid = finalFile.isFile &&
            finalFile.length() <= MAX_UPDATE_BYTES &&
            (update.sizeBytes <= 0L || finalFile.length() == update.sizeBytes)
        if (cachedSizeIsValid && sha256(finalFile) == update.sha256) {
            validateApk(finalFile, update)
            pendingApk = finalFile
            return@withContext finalFile
        }

        val temporaryFile = File(updateDirectory, "${finalFile.name}.download")
        temporaryFile.delete()
        try {
            downloadToFile(update, temporaryFile)
            if (temporaryFile.length() > MAX_UPDATE_BYTES) {
                error("The downloaded update is larger than the allowed limit.")
            }
            if (update.sizeBytes > 0 && temporaryFile.length() != update.sizeBytes) {
                error("The downloaded update has an unexpected file size.")
            }
            if (sha256(temporaryFile) != update.sha256) {
                error("The update failed its security checksum.")
            }
            validateApk(temporaryFile, update)
            finalFile.delete()
            if (!temporaryFile.renameTo(finalFile)) {
                error("The verified update could not be prepared for installation.")
            }
            pendingApk = finalFile
            finalFile
        } catch (error: Throwable) {
            temporaryFile.delete()
            throw error
        }
    }

    fun openInstallerOrSettings(apk: File): Boolean {
        pendingApk = apk
        if (!canInstallPackagesFromThisSource()) {
            openInstallPermissionSettings()
            return false
        }
        openInstaller(apk)
        return true
    }

    fun resumePendingInstall(): Boolean {
        val apk = pendingApk?.takeIf(File::isFile) ?: return false
        if (!canInstallPackagesFromThisSource()) return false
        openInstaller(apk)
        return true
    }

    private fun canInstallPackagesFromThisSource(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.INSTALL_NON_MARKET_APPS,
                0,
            ) == 1
        }

    private fun openInstallPermissionSettings() {
        val permissionIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
        context.startActivity(permissionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openInstaller(apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.updates",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        pendingApk = null
    }

    private fun validateUpdate(update: AppUpdate) {
        require(update.versionCode > 0) { "The update manifest has an invalid version code." }
        require(update.versionName.isNotBlank()) { "The update manifest has no version name." }
        require(update.sha256.matches(Regex("[a-f0-9]{64}"))) {
            "The update manifest has an invalid checksum."
        }
        require(update.sizeBytes <= MAX_UPDATE_BYTES) {
            "The update manifest advertises a file larger than the allowed limit."
        }
        val uri = URI(update.apkUrl)
        require(uri.scheme.equals("https", ignoreCase = true)) {
            "Updates must use a secure HTTPS address."
        }
        require(
            uri.host.equals("github.com", ignoreCase = true) ||
                uri.host.equals("download.b33r.top", ignoreCase = true),
        ) {
            "The update points to an untrusted download host."
        }
    }

    private fun validateApk(file: File, update: AppUpdate) {
        val signingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val packageInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, signingFlags)
            ?: error("The downloaded update is not a valid Android package.")
        require(packageInfo.packageName == context.packageName) {
            "The downloaded update belongs to a different app."
        }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        require(versionCode == update.versionCode.toLong()) {
            "The downloaded APK version does not match the update manifest."
        }
        @Suppress("DEPRECATION")
        val installedPackage = context.packageManager.getPackageInfo(context.packageName, signingFlags)
        require(isTrustedUpdateSigner(installedPackage, packageInfo)) {
            "The downloaded APK is not signed by the installed app's trusted key."
        }
    }

    private suspend fun getText(url: String): String {
        val request = request(url, "application/json")
        return execute(request) { response, isActive ->
            val body = response.body ?: error("Update server returned an empty response.")
            val declaredLength = body.contentLength()
            if (declaredLength > MAX_MANIFEST_BYTES) {
                error("The update manifest is larger than the allowed limit.")
            }
            val bytes = body.byteStream().use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    if (!isActive()) throw CancellationException("Update check canceled.")
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_MANIFEST_BYTES) {
                        error("The update manifest is larger than the allowed limit.")
                    }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            String(bytes, Charsets.UTF_8)
        }
    }

    private suspend fun downloadToFile(update: AppUpdate, destination: File) {
        val request = request(update.apkUrl, "application/vnd.android.package-archive")
        execute(request) { response, isActive ->
            val body = response.body ?: error("Update server returned an empty download.")
            val declaredLength = body.contentLength()
            if (declaredLength > MAX_UPDATE_BYTES) {
                error("The update is larger than the allowed limit.")
            }
            if (
                update.sizeBytes > 0L &&
                declaredLength > 0L &&
                declaredLength != update.sizeBytes
            ) {
                error("The update server reported an unexpected file size.")
            }
            var total = 0L
            FileOutputStream(destination).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1_024)
                    while (true) {
                        if (!isActive()) throw CancellationException("Update download canceled.")
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (
                            total > MAX_UPDATE_BYTES ||
                            (update.sizeBytes > 0L && total > update.sizeBytes)
                        ) {
                            error("The update download exceeded its expected size.")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (declaredLength > 0L && total != declaredLength) {
                error("The update download was incomplete.")
            }
        }
    }

    private fun request(url: String, accept: String): Request =
        Request.Builder()
            .url(url)
            .header("Accept", accept)
            .header("Cache-Control", "no-cache")
            .header("User-Agent", "StreamDeck-IPTV/${BuildConfig.VERSION_NAME}")
            .get()
            .build()

    private suspend fun <T> execute(
        request: Request,
        read: (Response, () -> Boolean) -> T,
    ): T = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        IOException("Unable to contact the update server.", e),
                    )
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!continuation.isActive) return
                    try {
                        if (!response.isSuccessful) {
                            if (
                                response.code == 408 ||
                                response.code == 429 ||
                                response.code >= 500
                            ) {
                                throw IOException(
                                    "Update server is temporarily unavailable (HTTP ${response.code}).",
                                )
                            }
                            error("Update server returned HTTP ${response.code}.")
                        }
                        validateResolvedUrl(response)
                        val result = read(response) { continuation.isActive }
                        if (continuation.isActive) continuation.resume(result)
                    } catch (error: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            }
        })
    }

    private fun validateResolvedUrl(response: Response) {
        val url = response.request.url
        require(url.isHttps) { "The update server redirected to an insecure address." }
        val host = url.host.lowercase()
        require(
            host == "github.com" ||
                host == "download.b33r.top" ||
                host.endsWith(".githubusercontent.com"),
        ) {
            "The update server redirected to an untrusted host."
        }
    }

    @Suppress("DEPRECATION")
    private fun isTrustedUpdateSigner(installed: PackageInfo, candidate: PackageInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            val installedSigners = signerDigests(installed.signatures.orEmpty())
            val candidateSigners = signerDigests(candidate.signatures.orEmpty())
            return installedSigners.isNotEmpty() && candidateSigners == installedSigners
        }

        val installedSigningInfo = installed.signingInfo ?: return false
        val candidateSigningInfo = candidate.signingInfo ?: return false
        val installedCurrent = signerDigests(installedSigningInfo.apkContentsSigners.orEmpty())
        val candidateCurrent = signerDigests(candidateSigningInfo.apkContentsSigners.orEmpty())
        if (installedSigningInfo.hasMultipleSigners() || candidateSigningInfo.hasMultipleSigners()) {
            return installedCurrent.isNotEmpty() && candidateCurrent == installedCurrent
        }
        val candidateHistory = signerDigests(candidateSigningInfo.signingCertificateHistory.orEmpty())
        return installedCurrent.isNotEmpty() && candidateHistory.containsAll(installedCurrent)
    }

    private fun signerDigests(signatures: Array<out android.content.pm.Signature>): Set<String> {
        return signatures.mapTo(linkedSetOf()) { signature ->
            val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val MAX_MANIFEST_BYTES = 1L * 1_024L * 1_024L
        const val MAX_UPDATE_BYTES = 250L * 1_024L * 1_024L
    }
}
