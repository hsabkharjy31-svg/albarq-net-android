package net.albarq.hotspotsimulator

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import java.io.ByteArrayOutputStream

class HotspotFiles(private val context: Context, @Volatile var tree: Uri? = null) {
    @Volatile private var cache: Map<String, Uri> = emptyMap()
    @Volatile var scanWarnings: List<String> = emptyList()
        private set

    companion object {
        private const val MAX_DEPTH = 16
        private const val MAX_FILES = 12000
        private const val MAX_FILE_BYTES = 24 * 1024 * 1024
    }

    /**
     * Indexes the selected SAF tree. Providers are inconsistent, so a broken or
     * inaccessible child directory is recorded and skipped rather than crashing the Activity.
     * Call from a worker thread; the resulting immutable cache is safe for WebView reads.
     */
    fun index(): List<String> {
        val root = tree ?: run {
            cache = emptyMap()
            scanWarnings = emptyList()
            return emptyList()
        }

        val found = linkedMapOf<String, Uri>()
        val warnings = mutableListOf<String>()
        val visitedDirectories = mutableSetOf<String>()

        try {
            val rootDocumentId = DocumentsContract.getTreeDocumentId(root)
            walk(root, rootDocumentId, "", 0, found, visitedDirectories, warnings)
        } catch (e: Exception) {
            warnings.add("تعذر قراءة المجلد المحدد: ${e.message ?: e.javaClass.simpleName}")
        }

        cache = found.toMap()
        scanWarnings = warnings.toList()
        return found.keys.sorted()
    }

    private fun walk(
        treeUri: Uri,
        documentId: String,
        prefix: String,
        depth: Int,
        found: MutableMap<String, Uri>,
        visitedDirectories: MutableSet<String>,
        warnings: MutableList<String>
    ) {
        if (depth > MAX_DEPTH || found.size >= MAX_FILES) return
        if (!visitedDirectories.add(documentId)) return

        try {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            )

            context.contentResolver.query(children, projection, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                if (idColumn < 0 || nameColumn < 0 || mimeColumn < 0) {
                    warnings.add("مزود الملفات أعاد بيانات مجلد غير مكتملة: $prefix")
                    return@use
                }

                while (cursor.moveToNext() && found.size < MAX_FILES) {
                    try {
                        val childId = cursor.getString(idColumn) ?: continue
                        val name = cursor.getString(nameColumn)?.trim() ?: continue
                        if (name.isBlank() || name == "." || name == ".." ||
                            name.contains('/') || name.contains('\\')) continue

                        val childMime = cursor.getString(mimeColumn)
                        val relativePath = if (prefix.isEmpty()) name else "$prefix/$name"
                        if (childMime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            walk(treeUri, childId, relativePath, depth + 1, found, visitedDirectories, warnings)
                        } else {
                            val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            found[relativePath] = documentUri
                        }
                    } catch (e: Exception) {
                        warnings.add("تجاوز عنصرًا تعذر قراءته داخل $prefix: ${e.message ?: e.javaClass.simpleName}")
                    }
                }
            } ?: warnings.add("تعذر فتح قائمة الملفات داخل: ${prefix.ifEmpty { "المجلد الرئيسي" }}")
        } catch (e: Exception) {
            warnings.add("تعذر فهرسة ${prefix.ifEmpty { "المجلد الرئيسي" }}: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun read(path: String): ByteArray? {
        val normalized = path.trimStart('/')
        val parts = normalized.split('/')
        if (parts.any { it == ".." || it.isBlank() }) return null
        val uri = cache[parts.joinToString("/")] ?: return null

        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_FILE_BYTES) {
                        scanWarnings = scanWarnings + "تم تجاوز ملف أكبر من 24 MB: $normalized"
                        return null
                    }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } catch (e: Exception) {
            scanWarnings = scanWarnings + "تعذر فتح الملف $normalized: ${e.message ?: e.javaClass.simpleName}"
            null
        }
    }

    fun mime(path: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "js" -> "application/javascript"
            "woff2" -> "font/woff2"
            "woff" -> "font/woff"
            "ttf" -> "font/ttf"
            "svg" -> "image/svg+xml"
            else -> "application/octet-stream"
        }
    }
}
