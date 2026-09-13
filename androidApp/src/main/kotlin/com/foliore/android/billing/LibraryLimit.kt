package com.foliore.android.billing

/** Only explicit additions use this policy. Sync/restore/edit/export never do. */
object LibraryLimit {
    const val FREE_BOOKS = 50
    const val PRODUCT_ID = "foliore_unlimited"

    fun remaining(bookCount: Int, unlimited: Boolean): Int =
        if (unlimited) Int.MAX_VALUE else (FREE_BOOKS - bookCount.coerceAtLeast(0)).coerceAtLeast(0)

    fun canAdd(bookCount: Int, unlimited: Boolean, replacingExistingBook: Boolean = false): Boolean =
        replacingExistingBook || remaining(bookCount, unlimited) > 0
}
