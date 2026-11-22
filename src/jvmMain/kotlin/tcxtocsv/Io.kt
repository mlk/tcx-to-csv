package tcxtocsv

import java.io.File

internal actual fun readFileText(path: String): String =
    File(path).readText(Charsets.UTF_8)

internal actual fun readStdinText(): String =
    System.`in`.readBytes().decodeToString()

internal actual fun writeFileText(path: String, content: String) {
    File(path).writeText(content, Charsets.UTF_8)
}
