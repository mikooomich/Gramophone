/*
 *     Copyright (C) 2024 Akane Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.ui.fragments.settings

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.media3.common.util.Log
import androidx.preference.Preference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.akanework.gramophone.BuildConfig
import org.akanework.gramophone.R
import org.akanework.gramophone.db.AppDatabase
import org.akanework.gramophone.db.GramophoneDatabase
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.utils.Flags
import org.akanework.gramophone.ui.fragments.BasePreferenceFragment
import org.akanework.gramophone.ui.fragments.BaseSettingsActivity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlin.system.exitProcess

class ExperimentalSettingsActivity : BaseSettingsActivity(
    R.string.settings_experimental_settings,
    { ExperimentalSettingsFragment() })

class ExperimentalSettingsFragment : BasePreferenceFragment() {
    @Inject
    lateinit var database: GramophoneDatabase
    private lateinit var e: Exception

    private val backupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            runCatching {
                requireContext().applicationContext.contentResolver.openOutputStream(uri)?.use {
                    ZipOutputStream(it.buffered()).use { outputStream ->
                        outputStream.setLevel(Deflater.BEST_COMPRESSION)
                        runBlocking(Dispatchers.IO) {
                            database.checkpoint()
                        }
                        FileInputStream(database.openHelper.writableDatabase.path).use { inputStream ->
                            outputStream.putNextEntry(ZipEntry(AppDatabase.DB_NAME))
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
            }.onSuccess {
                Toast.makeText(context, "Success", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Failled", Toast.LENGTH_SHORT).show()
                it.printStackTrace()
            }
        }
    }

    private val restoreLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val TAG = "restoreLauncher"
                requireContext().applicationContext.contentResolver.openInputStream(uri)?.use {
                    ZipInputStream(it).use { inputStream ->
                        var entry = inputStream.nextEntry
                        while (entry != null) {
                            when (entry.name) {

                                AppDatabase.DB_NAME -> {
                                    Log.i(TAG, "Starting database restore")
                                    runBlocking(Dispatchers.IO) {
                                        database.checkpoint()
                                    }
                                    database.close()

                                    Log.i(TAG, "Testing new database for compatibility...")
                                    val destFile = requireContext().getDatabasePath(AppDatabase.TEST_DB_NAME)
                                    destFile.parentFile?.apply {
                                        if (!exists()) mkdirs()
                                    }
                                    FileOutputStream(destFile).use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }

                                    val status = try {
                                        val t = AppDatabase.newTestInstance(requireContext(), AppDatabase.TEST_DB_NAME)
                                        t.openHelper.writableDatabase.isDatabaseIntegrityOk
                                        t.close()
                                        true
                                    } catch (e: Exception) {
                                        Log.e(TAG, "DB validation failed", e)
                                        false
                                    }

                                    if (status) {
                                        Log.i(TAG, "Found valid database, proceeding with restore")
                                        destFile.inputStream().use { inputStream ->
                                            FileOutputStream(database.openHelper.writableDatabase.path).use { outputStream ->
                                                inputStream.copyTo(outputStream)
                                            }
                                        }
                                    } else {
                                        Log.e(TAG, "Incompatible database, aborting restore")
                                        e.printStackTrace()
                                        Toast.makeText(
                                            context,
                                            "R.string.err_restore_incompatible_database",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                            entry = inputStream.nextEntry
                        }
                    }
                }

//                val stopIntent = Intent(context, GramophonePlaybackService::class.java)
//                context.stopService(stopIntent)
//                val startIntent = Intent(context, MainActivity::class.java)
//                startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//                context.startActivity(startIntent)
                exitProcess(0)
            }.onFailure {
                it.printStackTrace()
                Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
            }
        }
    }


    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_experimental, rootKey)
        findPreference<Preference>("crash")!!.isVisible = BuildConfig.DEBUG
        if (!Flags.OFFLOAD)
            findPreference<Preference>("offload")!!.isVisible = false
        if (!Flags.MQ_PREVIEW)
            findPreference<Preference>("mq_preview")!!.isVisible = false
        if (BuildConfig.DEBUG)
            e = RuntimeException("skill issue")
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == "crash" && BuildConfig.DEBUG) {
            throw IllegalArgumentException("I crashed your app >:)", e)
        } else if (preference.key == "self_log") {
            Log.w("Gramophone", "Exporting logs...")
            CoroutineScope(Dispatchers.IO).launch {
                val p = ProcessBuilder()
                    .command("logcat", "-dball")
                    .start()
                val stdout = p.inputStream.readBytes().toString(Charset.defaultCharset())
                val stderr = p.errorStream.readBytes().toString(Charset.defaultCharset())
                runInterruptible {
                    p.waitFor()
                }
                val selfLogDir = File(requireContext().cacheDir, "SelfLog")
                val f = File(
                    selfLogDir.also { it.mkdirs() },
                    "GramophoneLog${System.currentTimeMillis()}.txt"
                )
                f.writeText(
                    "SDK: ${Build.VERSION.SDK_INT}\nDevice: ${Build.BRAND} ${Build.DEVICE} " +
                            "(${Build.MANUFACTURER} ${Build.PRODUCT} ${Build.MODEL})\nVersion: " +
                            "${BuildConfig.MY_VERSION_NAME} ${BuildConfig.RELEASE_TYPE} (${context?.packageName})" +
                            "\n$stdout\n$stderr"
                )
                withContext(Dispatchers.Main) {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TITLE, "Gramophone Logs")
                        putExtra(
                            Intent.EXTRA_STREAM,
                            FileProvider.getUriForFile(
                                requireContext(),
                                "${requireContext().packageName}.fileProvider",
                                f
                            )
                        )
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    startActivity(shareIntent)
                }
            }
        } else if (preference.key == "backup") {
            val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            val fileName = "${getString(R.string.app_name)}_${GramophoneDatabase.MUSIC_DATABASE_VERSION}_" +
                    "${LocalDateTime.now().format(formatter)}.backup"
            backupLauncher.launch(fileName)
        } else if (preference.key == "restore") {
            restoreLauncher.launch(arrayOf("application/octet-stream"))
        } else if (preference.key == "debug_pc") {
            startActivity(DebugSettingsActivity::class.java)
        }
        return super.onPreferenceTreeClick(preference)
    }
}
