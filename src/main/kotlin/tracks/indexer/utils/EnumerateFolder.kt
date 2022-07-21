package tracks.indexer.utils

import java.io.File
import java.util.*

object EnumerateFolder {
    fun at(path: String, extensions: List<String>): List<String> {
        val folders = mutableListOf<String>()
        val dirFiles = mutableListOf<String>()
        File(path).list()?.forEach {
            val asFile = File("$path/$it")
            if (asFile.isDirectory) {
                folders.add(asFile.absolutePath)
            } else if (asFile.isFile && extensions.contains(asFile.extension.uppercase(Locale.getDefault()))) {
                dirFiles.add(asFile.absolutePath)
            }
        }

        folders.sortDescending()
        folders.forEach {
            dirFiles.addAll(at(it, extensions))
        }

        return dirFiles.sortedDescending()
    }
}
