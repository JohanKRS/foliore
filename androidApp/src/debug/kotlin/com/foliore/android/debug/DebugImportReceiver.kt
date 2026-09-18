package com.foliore.android.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.foliore.android.di.AppContainer
import com.foliore.android.storage.EpubImportResult
import java.io.File
import kotlinx.coroutines.runBlocking

class DebugImportReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pendingResult = goAsync()
        Thread {
            try {
                if (intent.getBooleanExtra("kindleBatchTest", false)) {
                    KindleBatchHarness.run(context)
                    return@Thread
                }
                if (intent.getBooleanExtra("corpusTest", false)) {
                    ReaderCorpusHarness.run(context)
                    return@Thread
                }
                if (intent.getBooleanExtra("conversionTest", false)) {
                    ConversionHarness.run(context)
                    return@Thread
                }
                val path = intent.getStringExtra(EXTRA_PATH)
                    ?: File(context.filesDir, DEFAULT_IMPORT_DIR).absolutePath
                val target = File(path)
                val files = target.importFiles()
                val importer = AppContainer(context).epubImporter
                var imported = 0
                var duplicates = 0
                var failed = 0

                runBlocking {
                    files.forEach { file ->
                        when (val result = importer.importFile(file)) {
                            is EpubImportResult.Imported -> imported += 1
                            is EpubImportResult.Duplicate -> duplicates += 1
                            is EpubImportResult.NeedsReview -> {
                                importer.discard(result.pendingImport)
                                duplicates += 1
                            }
                            EpubImportResult.LimitReached,
                            is EpubImportResult.Failed -> failed += 1
                        }
                    }
                }

                Log.d(
                    TAG,
                    "debug import path=${target.absolutePath} files=${files.size} " +
                        "imported=$imported duplicates=$duplicates failed=$failed",
                )
            } catch (error: Exception) {
                Log.e(TAG, "debug import failed", error)
            } finally {
                pendingResult.finish()
            }
        }.apply {
            name = "FolioreDebugImport"
            start()
        }
    }

    private fun File.importFiles(): List<File> {
        return if (isDirectory) {
            listFiles()
                .orEmpty()
                .filter { file -> file.isFile && com.foliore.android.storage.EbookFormat.from(file.name, null) != null }
                .sortedBy { file -> file.name }
        } else {
            listOf(this).filter { file -> file.isFile && com.foliore.android.storage.EbookFormat.from(file.name, null) != null }
        }
    }

    private companion object {
        const val TAG = "FolioreDebugImport"
        const val EXTRA_PATH = "path"
        const val DEFAULT_IMPORT_DIR = "debug-imports"
    }
}
