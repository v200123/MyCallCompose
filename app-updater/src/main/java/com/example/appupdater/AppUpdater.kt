package com.example.appupdater

import android.app.AlertDialog
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppUpdater private constructor(
    private val activity: ComponentActivity,
    config: UpdateConfig
) {
    private val updateManager = AppUpdateManager(config)
    private var hasCheckedAppUpdate = false
    private var isCheckingForUpdate = false
    private var pendingInstallApkFile: File? = null
    private var pendingForceUpdate = false
    private var activeDialog: AlertDialog? = null

    private val installPermissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val apkFile = pendingInstallApkFile ?: return@registerForActivityResult
            if (updateManager.canRequestPackageInstalls(activity)) {
                updateManager.installApk(activity, apkFile)
                pendingInstallApkFile = null
                pendingForceUpdate = false
            } else if (pendingForceUpdate) {
                showInstallPermissionDialog(apkFile, true)
            } else {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.app_updater_install_permission_denied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    init {
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                activeDialog?.dismiss()
                activeDialog = null
            }
        })
    }

    fun checkAndShow() {
        if (hasCheckedAppUpdate) return
        hasCheckedAppUpdate = true
        checkForUpdate(showLatestVersionMessage = false)
    }

    /** 用户从设置页主动检查更新。 */
    fun checkAndShowUserInitiated() {
        checkForUpdate(showLatestVersionMessage = true)
    }

    private fun checkForUpdate(showLatestVersionMessage: Boolean) {
        if (isCheckingForUpdate) return
        isCheckingForUpdate = true
        activity.lifecycleScope.launch {
            try {
                if (showLatestVersionMessage) {
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.app_updater_checking),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                val updateResult = withContext(Dispatchers.IO) {
                    runCatching { updateManager.checkForUpdate(activity) }
                        .onFailure { Log.e(TAG, "版本检测失败", it) }
                }
                updateResult.onSuccess { result ->
                    if (result?.needUpdate == true) {
                        showUpdateDialog(result)
                    } else if (showLatestVersionMessage) {
                        Toast.makeText(
                            activity,
                            activity.getString(
                                if (result == null) {
                                    R.string.app_updater_check_failed
                                } else {
                                    R.string.app_updater_already_latest
                                }
                            ),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }.onFailure {
                    if (showLatestVersionMessage) {
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.app_updater_check_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } finally {
                isCheckingForUpdate = false
            }
        }
    }

    private fun showUpdateDialog(updateResult: UpdateCheckResult) {
        val dialogMessage = buildString {
            append(
                activity.getString(
                    R.string.app_updater_dialog_version_label,
                    updateResult.latestVersion ?: "--"
                )
            )
            if (!updateResult.updateLog.isNullOrBlank()) {
                append("\n\n")
                append(activity.getString(R.string.app_updater_dialog_log_label))
                append("\n")
                append(updateResult.updateLog)
            }
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(
                if (updateResult.forceUpdate) {
                    activity.getString(R.string.app_updater_dialog_title_force)
                } else {
                    activity.getString(R.string.app_updater_dialog_title)
                }
            )
            .setMessage(dialogMessage)
            .setCancelable(!updateResult.forceUpdate)
            .setPositiveButton(R.string.app_updater_dialog_confirm, null)
            .apply {
                if (!updateResult.forceUpdate) {
                    setNegativeButton(R.string.app_updater_dialog_cancel, null)
                }
            }
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialog.dismiss()
                startUpdateDownload(updateResult)
            }
        }
        trackDialog(dialog)
        dialog.show()
    }

    private fun startUpdateDownload(updateResult: UpdateCheckResult) {
        pendingForceUpdate = updateResult.forceUpdate
        val progressDialog = AlertDialog.Builder(activity)
            .setTitle(R.string.app_updater_downloading_title)
            .setMessage(activity.getString(R.string.app_updater_downloading_message))
            .setCancelable(false)
            .create()
        trackDialog(progressDialog)
        progressDialog.show()

        activity.lifecycleScope.launch {
            val downloadResult = withContext(Dispatchers.IO) {
                runCatching {
                    updateManager.downloadApk(activity, updateResult) { progress ->
                        activity.runOnUiThread {
                            if (progressDialog.isShowing) {
                                progressDialog.setMessage(
                                    activity.getString(
                                        R.string.app_updater_downloading_progress,
                                        progress
                                    )
                                )
                            }
                        }
                    }
                }
            }
            if (progressDialog.isShowing) {
                progressDialog.dismiss()
            }

            downloadResult.onSuccess { apkFile ->
                pendingInstallApkFile = apkFile
                if (updateManager.canRequestPackageInstalls(activity)) {
                    updateManager.installApk(activity, apkFile)
                    pendingInstallApkFile = null
                    pendingForceUpdate = false
                } else {
                    showInstallPermissionDialog(apkFile, updateResult.forceUpdate)
                }
            }.onFailure { throwable ->
                Toast.makeText(
                    activity,
                    activity.getString(
                        R.string.app_updater_download_failed,
                        throwable.message ?: "未知错误"
                    ),
                    Toast.LENGTH_LONG
                ).show()
                if (updateResult.forceUpdate) {
                    showUpdateDialog(updateResult)
                }
            }
        }
    }

    private fun showInstallPermissionDialog(apkFile: File, forceUpdate: Boolean) {
        pendingInstallApkFile = apkFile
        pendingForceUpdate = forceUpdate
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.app_updater_install_permission_title)
            .setMessage(R.string.app_updater_install_permission_message)
            .setCancelable(!forceUpdate)
            .setPositiveButton(R.string.app_updater_install_permission_confirm) { _, _ ->
                installPermissionLauncher.launch(
                    updateManager.createInstallPermissionIntent(activity)
                )
            }
            .apply {
                if (!forceUpdate) {
                    setNegativeButton(R.string.app_updater_dialog_cancel, null)
                }
            }
            .create()
        trackDialog(dialog)
        dialog.show()
    }

    private fun trackDialog(dialog: AlertDialog) {
        activeDialog?.dismiss()
        activeDialog = dialog
        dialog.setOnDismissListener {
            if (activeDialog === dialog) activeDialog = null
        }
    }

    companion object {
        private const val TAG = "AppUpdater"

        @JvmStatic
        fun register(
            activity: ComponentActivity,
            config: UpdateConfig
        ): AppUpdater {
            return AppUpdater(activity, config)
        }
    }
}
