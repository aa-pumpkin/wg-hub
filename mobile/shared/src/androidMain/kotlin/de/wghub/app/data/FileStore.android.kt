package de.wghub.app.data

import java.io.File

actual object FileStore {
    private lateinit var dir: File

    /** Called once from MainActivity before anything else. */
    fun init(filesDir: File) {
        dir = File(filesDir, "store").apply { mkdirs() }
    }

    private fun file(name: String) = File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))

    actual fun read(name: String): String? = file(name).takeIf { it.exists() }?.readText()

    actual fun write(name: String, text: String) {
        // Write then rename, so a crash never leaves half a file.
        val tmp = File(dir, "$name.tmp".replace(Regex("[^A-Za-z0-9._-]"), "_"))
        tmp.writeText(text)
        tmp.renameTo(file(name))
    }

    actual fun delete(name: String) {
        file(name).delete()
    }

    actual fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
