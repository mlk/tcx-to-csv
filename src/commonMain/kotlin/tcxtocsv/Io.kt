package tcxtocsv

/** Reads the whole file at [path] as UTF-8 text. Throws on any IO failure. */
internal expect fun readFileText(path: String): String

/** Reads all of standard input as UTF-8 text. */
internal expect fun readStdinText(): String

/** Writes [content] (UTF-8) to [path], replacing any existing file. */
internal expect fun writeFileText(path: String, content: String)
