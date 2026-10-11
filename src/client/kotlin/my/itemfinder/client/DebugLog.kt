package my.itemfinder.client

object DebugLog {
    private val dirs = listOf("/sdcard/mydebugformods", "mydebugformods")

    fun log(msg: String) {
        val line = java.time.LocalTime.now().toString() + " " + msg + "\n"
        for (d in dirs) {
            try {
                val dir = java.io.File(d)
                dir.mkdirs()
                java.io.File(dir, "log.txt").appendText("[" + d + "] " + line)
                return
            } catch (e: Throwable) {
            }
        }
    }

    fun error(where: String, t: Throwable) {
        log("ERROR " + where + ": " + t.toString() + "\n" + t.stackTraceToString())
    }
}
