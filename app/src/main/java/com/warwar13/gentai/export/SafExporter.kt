package com.warwar13.gentai.export
import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SafExporter {
 /** Writes lazily generated CSV lines to a user-selected Storage Access Framework URI. */
 suspend fun writeCsv(resolver: ContentResolver, destination: Uri, lines: Sequence<String>) = withContext(Dispatchers.IO) {
  resolver.openOutputStream(destination)?.bufferedWriter()?.use { writer -> lines.forEach { writer.appendLine(it) } } ?: error("Could not open selected export destination")
 }
}
