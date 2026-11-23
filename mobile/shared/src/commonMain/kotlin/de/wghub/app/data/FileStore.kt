package de.wghub.app.data

/** Small text files in the app's private folder: settings, cached server data, unsent changes. */
expect object FileStore {
    fun read(name: String): String?
    fun write(name: String, text: String)
    fun delete(name: String)
    fun clear()
}
