package com.burnsubtitle.data.pref

import android.content.Context
import android.net.Uri
import com.burnsubtitle.data.saf.SafDocumentQuery
import com.burnsubtitle.data.saf.SafUriPermissions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class FolderSetting(
    val uri: Uri,
    val displayName: String,
)

@Singleton
class AppPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val safPermissions: SafUriPermissions,
    private val safQuery: SafDocumentQuery,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _outputFolder = MutableStateFlow(loadFolder(KEY_OUTPUT_FOLDER_URI, KEY_OUTPUT_FOLDER_NAME))
    val outputFolder: StateFlow<FolderSetting?> = _outputFolder.asStateFlow()

    private val _errorLogsFolder = MutableStateFlow(loadFolder(KEY_ERROR_LOGS_FOLDER_URI, KEY_ERROR_LOGS_FOLDER_NAME))
    val errorLogsFolder: StateFlow<FolderSetting?> = _errorLogsFolder.asStateFlow()

    fun setOutputFolder(uri: Uri?) {
        val previous = _outputFolder.value?.uri
        if (uri != null) {
            safPermissions.takePersistableReadWrite(uri)
            val name = safQuery.queryFolderDisplayName(uri)
            prefs.edit()
                .putString(KEY_OUTPUT_FOLDER_URI, uri.toString())
                .putString(KEY_OUTPUT_FOLDER_NAME, name)
                .apply()
            _outputFolder.value = FolderSetting(uri, name)
            if (previous != null && previous != uri) {
                safPermissions.releasePersistableReadWrite(previous)
            }
        } else {
            prefs.edit()
                .remove(KEY_OUTPUT_FOLDER_URI)
                .remove(KEY_OUTPUT_FOLDER_NAME)
                .apply()
            _outputFolder.value = null
            if (previous != null) {
                safPermissions.releasePersistableReadWrite(previous)
            }
        }
    }

    fun setErrorLogsFolder(uri: Uri?) {
        val previous = _errorLogsFolder.value?.uri
        if (uri != null) {
            safPermissions.takePersistableReadWrite(uri)
            val name = safQuery.queryFolderDisplayName(uri)
            prefs.edit()
                .putString(KEY_ERROR_LOGS_FOLDER_URI, uri.toString())
                .putString(KEY_ERROR_LOGS_FOLDER_NAME, name)
                .apply()
            _errorLogsFolder.value = FolderSetting(uri, name)
            if (previous != null && previous != uri) {
                safPermissions.releasePersistableReadWrite(previous)
            }
        } else {
            prefs.edit()
                .remove(KEY_ERROR_LOGS_FOLDER_URI)
                .remove(KEY_ERROR_LOGS_FOLDER_NAME)
                .apply()
            _errorLogsFolder.value = null
            if (previous != null) {
                safPermissions.releasePersistableReadWrite(previous)
            }
        }
    }

    fun getOutputFolderUri(): Uri? = _outputFolder.value?.uri

    fun getErrorLogsFolderUri(): Uri? = _errorLogsFolder.value?.uri

    private fun loadFolder(keyUri: String, keyName: String): FolderSetting? {
        val uriStr = prefs.getString(keyUri, null) ?: return null
        val uri = runCatching { Uri.parse(uriStr) }.getOrNull() ?: return null
        if (!safPermissions.isPersistedPermissionValid(uri, write = true)) {
            prefs.edit().remove(keyUri).remove(keyName).apply()
            return null
        }
        val name = prefs.getString(keyName, null) ?: safQuery.queryFolderDisplayName(uri)
        return FolderSetting(uri, name)
    }

    companion object {
        private const val PREFS_NAME = "burn_subtitle_prefs"
        private const val KEY_OUTPUT_FOLDER_URI = "output_folder_uri"
        private const val KEY_OUTPUT_FOLDER_NAME = "output_folder_name"
        private const val KEY_ERROR_LOGS_FOLDER_URI = "error_logs_folder_uri"
        private const val KEY_ERROR_LOGS_FOLDER_NAME = "error_logs_folder_name"
    }
}
