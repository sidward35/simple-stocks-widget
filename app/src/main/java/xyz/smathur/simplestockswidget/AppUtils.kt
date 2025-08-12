package xyz.smathur.simplestockswidget

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo

object AppUtils {

    fun getInstalledApps(context: Context): List<AppInfo> {
        val packageManager = context.packageManager
        val installedApps = mutableListOf<AppInfo>()

        // Add default option (this stock app)
        installedApps.add(
            AppInfo(
                packageName = context.packageName,
                appName = "Simple Stocks Widget (Default)",
                icon = context.packageManager.getApplicationIcon(context.packageName)
            )
        )

        // Get all installed packages
        val packages = packageManager.getInstalledPackages(0)

        for (packageInfo in packages) {
            // Skip our own app (already added as default)
            if (packageInfo.packageName == context.packageName) {
                continue
            }

            // Only include apps that can be launched (have a launch intent)
            val launchIntent = packageManager.getLaunchIntentForPackage(packageInfo.packageName)
            if (launchIntent != null) {
                try {
                    val applicationInfo = packageInfo.applicationInfo
                    if (applicationInfo == null) continue

                    // Only skip apps that are clearly system components users shouldn't launch
                    if (shouldHideApp(packageInfo.packageName)) {
                        continue
                    }

                    val appName = packageManager.getApplicationLabel(applicationInfo).toString()
                    val icon = packageManager.getApplicationIcon(applicationInfo)

                    installedApps.add(
                        AppInfo(
                            packageName = packageInfo.packageName,
                            appName = appName,
                            icon = icon
                        )
                    )
                } catch (e: Exception) {
                    // Skip apps we can't get info for
                    continue
                }
            }
        }

        // Sort alphabetically (but keep default first)
        val defaultApp = installedApps.first()
        val sortedApps = installedApps.drop(1).sortedBy { it.appName }

        return listOf(defaultApp) + sortedApps
    }

    fun launchApp(context: Context, packageName: String): Boolean {
        return try {
            if (packageName == context.packageName) {
                // Launch our own main activity
                val intent = Intent(context, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                // Launch the selected app
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun shouldHideApp(packageName: String): Boolean {
        // Only hide truly problematic system apps that users shouldn't launch
        val hiddenPackages = setOf(
            "android",
            "com.android.systemui",
            "com.android.shell",
            "com.android.bluetooth"
        )
        return hiddenPackages.any { packageName.startsWith(it) }
    }

    private fun isSystemApp(applicationInfo: ApplicationInfo): Boolean {
        return applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
    }
}