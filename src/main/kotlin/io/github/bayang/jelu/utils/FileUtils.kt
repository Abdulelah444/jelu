package io.github.bayang.jelu.utils

fun imageName(
    title: String,
    bookId: String,
    extension: String,
): String = "$title-$bookId.$extension"

/**
 * The relative part of a path the downloader reported, with whichever ingest prefix it used
 * (/cwa-book-ingest/ on Shelfmark 1.3.x, /books/ from 1.6.x) removed.
 */
fun ebookRelativePath(reportedPath: String): String =
    listOf("/cwa-book-ingest/", "/books/")
        .fold(reportedPath) { path, prefix -> path.removePrefix(prefix) }
        .trimStart('/')

/**
 * The downloader reports paths inside its own container. Map one to the path this
 * container uses, where the same ebooks folder is mounted at [ebooksRoot].
 */
fun ebookPathInsideJelu(
    reportedPath: String,
    ebooksRoot: String = "/files/ebooks",
): String = "${ebooksRoot.trimEnd('/')}/${ebookRelativePath(reportedPath)}"
