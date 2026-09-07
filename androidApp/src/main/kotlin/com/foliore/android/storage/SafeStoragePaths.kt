package com.foliore.android.storage

import java.io.File

internal fun requireSafeBookId(id: String): String {
    require(Regex("[A-Za-z0-9_-]{1,128}").matches(id)) { "Invalid book identifier" }
    return id
}

internal fun safeChildDirectory(root: File, name: String): File {
    requireSafeBookId(name)
    val child = File(root, name).canonicalFile
    require(child.parentFile == root.canonicalFile) { "Book directory escapes storage root" }
    return child
}
