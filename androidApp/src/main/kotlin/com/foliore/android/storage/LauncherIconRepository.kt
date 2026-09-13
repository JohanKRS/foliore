package com.foliore.android.storage

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LauncherIcon(val label: String) {
    DAWN("Dawn"), DAY("Day"), DUSK("Dusk"), NIGHT("Night");

    val aliasName: String get() = "com.foliore.android.launcher.${label}Icon"
}

/** PackageManager persists the choice per device/user, including across app updates.
 * Reading it directly avoids a second preference getting out of sync with the launcher.
 */
class LauncherIconRepository internal constructor(private val components: LauncherIconComponents) {
    constructor(context: Context) : this(AndroidLauncherIconComponents(context))

    private val _icon = MutableStateFlow(currentIcon())
    val icon: StateFlow<LauncherIcon> = _icon.asStateFlow()

    init {
        // Repair an interrupted pre-Android 13 switch when the app is opened again.
        if (enabledIcons().size != 1) select(_icon.value)
    }

    fun select(icon: LauncherIcon): Boolean {
        val previous = enabledIcons()
        if (previous == listOf(icon)) {
            _icon.value = icon
            return true
        }
        return try {
            applySelection(icon)
            _icon.value = currentIcon()
            enabledIcons() == listOf(icon)
        } catch (_: RuntimeException) {
            // On older Android versions a failed second operation may leave two
            // aliases enabled. Restore the previous entry before removing the new one.
            if (!components.supportsAtomicChanges) {
                runCatching { applySelection(previous.firstOrNull() ?: LauncherIcon.DAY) }
            }
            _icon.value = currentIcon()
            false
        }
    }

    private fun applySelection(icon: LauncherIcon) {
        if (components.supportsAtomicChanges) {
            components.setAll(icon)
        } else {
            // Always enable first: even if the process stops between calls, the
            // user retains a launcher entry. MainActivity and share aliases stay enabled.
            components.setEnabled(icon, true)
            LauncherIcon.entries.filter { it != icon }.forEach {
                components.setEnabled(it, false)
            }
        }
    }

    private fun enabledIcons() = LauncherIcon.entries.filter(components::isEnabled)
    private fun currentIcon() = enabledIcons().firstOrNull() ?: LauncherIcon.DAY
}

internal interface LauncherIconComponents {
    val supportsAtomicChanges: Boolean
    fun isEnabled(icon: LauncherIcon): Boolean
    fun setEnabled(icon: LauncherIcon, enabled: Boolean)
    fun setAll(selected: LauncherIcon)
}

private class AndroidLauncherIconComponents(context: Context) : LauncherIconComponents {
    private val packageName = context.packageName
    private val packageManager = context.packageManager
    override val supportsAtomicChanges get() = Build.VERSION.SDK_INT >= 33
    private fun component(icon: LauncherIcon) = ComponentName(packageName, icon.aliasName)

    override fun isEnabled(icon: LauncherIcon): Boolean =
        when (packageManager.getComponentEnabledSetting(component(icon))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == LauncherIcon.DAY
            else -> false
        }

    override fun setEnabled(icon: LauncherIcon, enabled: Boolean) {
        packageManager.setComponentEnabledSetting(
            component(icon), state(enabled), PackageManager.DONT_KILL_APP,
        )
    }

    override fun setAll(selected: LauncherIcon) {
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.setComponentEnabledSettings(
                LauncherIcon.entries.map {
                    PackageManager.ComponentEnabledSetting(
                        component(it), state(it == selected), PackageManager.DONT_KILL_APP,
                    )
                },
            )
        } else {
            error("Atomic launcher updates require Android 13")
        }
    }

    private fun state(enabled: Boolean) = if (enabled) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    } else {
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }
}
