package studio.nexora.app

import android.content.Context
import java.io.File

data class CacheReport(val bytesUsed: Long, val fileCount: Int)

/** Keeps generated previews and temporary render files bounded and disposable. */
object CacheManager {
    fun report(context: Context): CacheReport {
        val root = context.cacheDir
        var bytes = 0L
        var count = 0
        root.walkTopDown().filter { it.isFile }.forEach {
            bytes += it.length()
            count++
        }
        return CacheReport(bytes, count)
    }

    fun clearTemporary(context: Context): Boolean {
        val root = context.cacheDir
        val keep = setOf("nexora_keep")
        return root.listFiles().orEmpty().filterNot { it.name in keep }.all(::deleteRecursively)
    }

    private fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) file.listFiles().orEmpty().forEach(::deleteRecursively)
        return !file.exists() || file.delete()
    }
}
