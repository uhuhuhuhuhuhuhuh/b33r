package com.streamdeck.iptv.ui

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import kotlin.math.roundToInt

internal data class ResponsiveScaleProfile(
    val isTelevision: Boolean,
    val uiScale: Float,
    val textScale: Float,
    val shortestWidthDp: Int,
)

internal data class ResponsiveLayout(
    val isTelevision: Boolean,
    val uiScale: Float,
    val textScale: Float,
    val pagePadding: Dp,
    val gridCardWidth: Dp,
    val gridSpacing: Dp,
    val railWidth: Dp,
    val loginMaxWidth: Dp,
    val playerPadding: Dp,
    val playerButtonSize: Dp,
    val playerIconSize: Dp,
)

private val LocalResponsiveScaleProfile = compositionLocalOf {
    ResponsiveScaleProfile(
        isTelevision = false,
        uiScale = 1f,
        textScale = 1f,
        shortestWidthDp = 360,
    )
}

/**
 * Scales the entire Compose tree, including Material components, typography,
 * focus targets, menus, dialogs, and controls. This avoids the partial scaling
 * that results from multiplying only a handful of individual dp/sp values.
 */
@Composable
internal fun ProvideResponsiveScaling(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val baseDensity = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val windowShortSidePx = min(windowSize.width, windowSize.height).takeIf { it > 0 }
        ?: min(
            context.resources.displayMetrics.widthPixels,
            context.resources.displayMetrics.heightPixels,
        )
    val shortestWidthDp = (windowShortSidePx / baseDensity.density)
        .roundToInt()
        .coerceAtLeast(1)
    val physicalShortSidePx = remember(
        context,
        windowShortSidePx,
        configuration.orientation,
    ) {
        physicalShortSidePixels(context, windowShortSidePx)
    }
    val television = remember(context, configuration.uiMode) {
        isTelevisionDevice(context, configuration)
    }
    val profile = remember(
        television,
        shortestWidthDp,
        physicalShortSidePx,
        windowShortSidePx,
    ) {
        calculateResponsiveScaleProfile(
            isTelevision = television,
            shortestWidthDp = shortestWidthDp,
            physicalShortSidePx = physicalShortSidePx,
            windowShortSidePx = windowShortSidePx,
        )
    }
    val scaledDensity = remember(
        baseDensity.density,
        baseDensity.fontScale,
        profile.uiScale,
        profile.textScale,
    ) {
        Density(
            density = baseDensity.density * profile.uiScale,
            fontScale = baseDensity.fontScale * (profile.textScale / profile.uiScale),
        )
    }

    CompositionLocalProvider(
        LocalResponsiveScaleProfile provides profile,
        LocalDensity provides scaledDensity,
        content = content,
    )
}

internal fun calculateResponsiveScaleProfile(
    isTelevision: Boolean,
    shortestWidthDp: Int,
    physicalShortSidePx: Int,
    windowShortSidePx: Int = physicalShortSidePx,
): ResponsiveScaleProfile {
    // A television may advertise a 4K physical mode while Android renders this
    // app into a smaller compatibility window. Size controls for the actual
    // render target so a 1080p app window is not scaled as though it were 4K.
    val effectiveTelevisionShortSidePx = min(
        physicalShortSidePx.coerceAtLeast(1),
        windowShortSidePx.coerceAtLeast(1),
    )
    val uiScale = if (isTelevision) {
        when {
            effectiveTelevisionShortSidePx >= 1_800 -> 1.38f
            effectiveTelevisionShortSidePx >= 1_000 -> 1.25f
            effectiveTelevisionShortSidePx >= 700 -> 1.12f
            else -> 1.04f
        }
    } else {
        when {
            shortestWidthDp >= 840 -> 1.14f
            shortestWidthDp >= 600 -> 1.08f
            shortestWidthDp < 340 -> 0.92f
            else -> 1.00f
        }
    }
    val textScale = if (isTelevision) {
        (uiScale * 1.08f).coerceAtMost(1.48f)
    } else {
        when {
            shortestWidthDp >= 840 -> 1.16f
            shortestWidthDp >= 600 -> 1.10f
            shortestWidthDp < 340 -> 0.96f
            else -> 1.02f
        }
    }
    return ResponsiveScaleProfile(
        isTelevision = isTelevision,
        uiScale = uiScale,
        textScale = textScale,
        shortestWidthDp = shortestWidthDp,
    )
}

@Composable
internal fun rememberResponsiveLayout(): ResponsiveLayout {
    val profile = LocalResponsiveScaleProfile.current
    val isTablet = !profile.isTelevision && profile.shortestWidthDp >= 600
    return remember(profile.isTelevision, profile.uiScale, profile.textScale, isTablet) {
        ResponsiveLayout(
            isTelevision = profile.isTelevision,
            uiScale = profile.uiScale,
            textScale = profile.textScale,
            pagePadding = if (profile.isTelevision) 20.dp else 16.dp,
            gridCardWidth = when {
                profile.isTelevision -> 178.dp
                isTablet -> 172.dp
                else -> 142.dp
            },
            gridSpacing = if (profile.isTelevision) 18.dp else 14.dp,
            railWidth = if (profile.isTelevision) 108.dp else 104.dp,
            loginMaxWidth = if (profile.isTelevision) 520.dp else 460.dp,
            playerPadding = if (profile.isTelevision) 12.dp else 10.dp,
            playerButtonSize = if (profile.isTelevision) 50.dp else 44.dp,
            playerIconSize = if (profile.isTelevision) 34.dp else 30.dp,
        )
    }
}

internal fun shouldUseNavigationRail(
    isTelevision: Boolean,
    widthDp: Float,
    heightDp: Float,
): Boolean {
    val hasEnoughHeight = heightDp >= if (isTelevision) 480f else 520f
    val hasEnoughWidth = widthDp >= if (isTelevision) 640f else 720f
    val tabletOrTelevision = isTelevision || min(widthDp, heightDp) >= 600f
    return tabletOrTelevision && hasEnoughWidth && hasEnoughHeight
}

internal fun scaledDensityDpi(baseDensityDpi: Int, scale: Float): Int =
    (baseDensityDpi.coerceAtLeast(1) * scale.coerceIn(0.85f, 1.60f))
        .roundToInt()
        .coerceAtLeast(1)

internal fun createScaledAndroidViewContext(
    context: Context,
    uiScale: Float,
    textScale: Float,
): Context {
    if (uiScale == 1f && textScale == 1f) return context
    val configuration = Configuration(context.resources.configuration).apply {
        densityDpi = scaledDensityDpi(context.resources.displayMetrics.densityDpi, uiScale)
        fontScale = context.resources.configuration.fontScale * (textScale / uiScale)
    }
    return context.createConfigurationContext(configuration)
}

internal fun ResponsiveLayout.dp(value: Float): Dp = value.dp

internal fun ResponsiveLayout.sp(value: Float): TextUnit = value.sp

private fun isTelevisionDevice(
    context: Context,
    configuration: Configuration,
): Boolean {
    val uiModeIsTelevision =
        configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION
    val packageManager = context.packageManager
    return uiModeIsTelevision ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
}

@Suppress("DEPRECATION")
private fun physicalShortSidePixels(context: Context, windowShortSidePx: Int): Int {
    var shortSide = windowShortSidePx
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val mode = windowManager?.defaultDisplay?.mode
        if (mode != null) {
            shortSide = maxOf(shortSide, min(mode.physicalWidth, mode.physicalHeight))
        }
    }
    return shortSide.coerceAtLeast(1)
}
