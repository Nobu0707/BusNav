package net.nobu0707.busnav.developer

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.core.okio.OkioStorage
import java.io.File
import kotlinx.coroutines.CoroutineScope
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/** Android's pre-26 File.renameTo cannot replace files on a Windows JVM (SDK_INT=0).
 * Use the supported Okio storage with the same Preferences protobuf on the host. */
fun testPreferenceStore(file: File, scope: CoroutineScope) = PreferenceDataStoreFactory.create(
    storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file.toOkioPath() }, scope = scope,
)
