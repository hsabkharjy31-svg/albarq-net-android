package net.albarq.hotspotsimulator

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap

class HotspotFiles(private val context: Context, var tree: Uri? = null) {
    private val cache = linkedMapOf<String, Uri>()

    fun index(): List<String> {
        cache.clear()
        val root = tree ?: return emptyList()
        walk(root, "", 0)
        return cache.keys.sorted()
    }

    private fun walk(parent: Uri, prefix: String, depth: Int) {
        if (depth > 12) return
        val id = DocumentsContract.getDocumentId(parent)
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, id)
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        context.contentResolver.query(children, cols, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0)
                val name = cursor.getString(1) ?: continue
                if (name == "." || name == ".." || name.contains('/')) continue
                val uri = DocumentsContract.buildDocumentUriUsingTree(parent, docId)
                val path = if (prefix.isEmpty()) name else prefix + "/" + name
                if (cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) walk(uri, path, depth + 1)
                else cache[path] = uri
            }
        }
    }

    fun read(path: String): ByteArray? {
        val parts = path.trimStart('/').split('/')
        if (parts.any { it == ".." }) return null
        val uri = cache[parts.filter { it.isNotBlank() && it != "." }.joinToString("/")] ?: return null
        return try { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (_: Exception) { null }
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
