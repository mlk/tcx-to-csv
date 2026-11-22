package tcxtocsv

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.refTo
import platform.posix.FILE
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fwrite
import platform.posix.stdin

@OptIn(ExperimentalForeignApi::class)
private fun readAll(fp: kotlinx.cinterop.CPointer<FILE>): ByteArray {
    val chunks = ArrayList<ByteArray>()
    var total = 0
    val buffer = ByteArray(8192)
    while (true) {
        val read = fread(buffer.refTo(0), 1u, buffer.size.toULong(), fp).toInt()
        if (read <= 0) break
        chunks += buffer.copyOf(read)
        total += read
    }
    val result = ByteArray(total)
    var offset = 0
    for (chunk in chunks) {
        chunk.copyInto(result, offset)
        offset += chunk.size
    }
    return result
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun readFileText(path: String): String {
    val fp = fopen(path, "rb") ?: throw IllegalArgumentException("cannot open file: $path")
    try {
        return readAll(fp).decodeToString()
    } finally {
        fclose(fp)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun readStdinText(): String = readAll(stdin!!).decodeToString()

@OptIn(ExperimentalForeignApi::class)
internal actual fun writeFileText(path: String, content: String) {
    val fp = fopen(path, "wb") ?: throw IllegalArgumentException("cannot open file for writing: $path")
    try {
        val bytes = content.encodeToByteArray()
        val written = fwrite(bytes.refTo(0), 1u, bytes.size.toULong(), fp).toInt()
        check(written == bytes.size) { "short write to $path" }
    } finally {
        fclose(fp)
    }
}
