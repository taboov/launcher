package com.mrmannwood.hexlauncher.applist

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteException
import androidx.annotation.WorkerThread
import com.mrmannwood.hexlauncher.DB
import com.mrmannwood.hexlauncher.executors.diskExecutor
import com.mrmannwood.hexlauncher.icon.IconAdapter
import timber.log.Timber
import java.util.concurrent.TimeUnit

object AppListUpdater {

    fun updateAppList(context: Context) {
        diskExecutor.execute {
            updateAppListWithCount(context.applicationContext, 0)
        }
    }

    @WorkerThread
    private fun updateAppListWithCount(context: Context, count: Int) {
        var runAgain = false
        try {
            val installedApps = getInstalledApps(context)
            val appDao = DB.get(context).appDataDao()

            appDao.deleteNotIncluded(installedApps)
            appDao.deleteNotIncludedDecoration(installedApps)

            val appUpdateTimes = appDao.getLastUpdateTimeStamps().associateBy({ it.packageName }, { it.timestamp })
            for (packageName in installedApps) {
                val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
                val lastUpdateTime = appUpdateTimes.getOrElse(packageName, { -1L })
                if (lastUpdateTime >= packageInfo.lastUpdateTime) continue
                Timber.d("Inserting $packageName")

                loadAppDataFromPacman(packageInfo, context.packageManager)?.let { appData ->
                    try {
                        appDao.insert(appData)
                        appDao.insert(AppDataDecoration(appData.packageName))
                    } catch (e: SQLiteException) {
                        Timber.e(e, "An error occurred while writing app to db: $appData")
                    }
                } ?: run {
                    runAgain = true
                    Timber.d("$packageName had a null icon")
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "An error occurred while updating the app database")
        }
        if (runAgain) {
            if (count <= 5) {
                diskExecutor.schedule(
                    { updateAppListWithCount(context, count + 1) },
                    10,
                    TimeUnit.MILLISECONDS
                )
            } else {
                Timber.wtf("Cannot get an icon for at least one app")
            }
        }
    }

    @WorkerThread
    private fun getInstalledApps(context: Context) : List<String> {
        return context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER), 0
        )
            .filter { it.activityInfo.packageName != context.packageName }
            .map { it.activityInfo.packageName }
            .distinct() // apparently there are times where this is necessary
    }

    @WorkerThread
    private fun loadAppDataFromPacman(packageInfo: PackageInfo, pacman: PackageManager) : AppData? {
        val packageName = packageInfo.packageName
        val appInfo = packageInfo.applicationInfo
        val icon = appInfo.loadIcon(pacman)
        return if (IconAdapter.INSTANCE.isRecycled(icon)) {
            null
        } else {
            AppData(
                packageName = packageName,
                label = appInfo.loadLabel(pacman).toString(),
                lastUpdateTime = packageInfo.lastUpdateTime,
                backgroundColor = IconAdapter.INSTANCE.getBackgroundColor(icon),
                category = appInfo.category
            )
        }
    }
}
