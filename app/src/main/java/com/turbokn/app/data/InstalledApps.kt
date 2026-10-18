package com.turbokn.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable

data class AppEntry(
    val packageName: String,
    val label: String,
)

object InstalledApps {

    fun launchable(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
        return resolved
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Resolves a single package to an entry, or null when it is not installed. */
    fun entryFor(context: Context, pkg: String): AppEntry? {
        val pm = context.packageManager
        return try {
            val info = pm.getApplicationInfo(pkg, 0)
            AppEntry(pkg, pm.getApplicationLabel(info).toString())
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    /**
     * Loads one icon on demand. Resolving every installed icon up front would
     * allocate hundreds of drawables for a list that shows a few at a time.
     */
    fun loadIcon(context: Context, pkg: String): Drawable? = try {
        context.packageManager.getApplicationIcon(pkg)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
