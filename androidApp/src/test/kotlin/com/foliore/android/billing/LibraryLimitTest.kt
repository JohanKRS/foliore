package com.foliore.android.billing

import kotlin.test.*

class LibraryLimitTest {
    @Test fun theFiftiethBookIsAllowedButTheFiftyFirstIsNot() {
        assertTrue(LibraryLimit.canAdd(49, false))
        assertFalse(LibraryLimit.canAdd(50, false))
        assertEquals(0, LibraryLimit.remaining(51, false))
    }
    @Test fun deletionFreesCapacityAndReplacementWorksAboveTheLimit() {
        assertEquals(1, LibraryLimit.remaining(49, false))
        assertTrue(LibraryLimit.canAdd(50, false, replacingExistingBook = true))
        assertTrue(LibraryLimit.canAdd(200, false, replacingExistingBook = true))
        assertFalse(LibraryLimit.canAdd(200, false, replacingExistingBook = false))
    }
    @Test fun unlimitedLibrariesHaveNoNumericalImportCap() {
        assertTrue(LibraryLimit.canAdd(100_000, true))
        assertEquals(Int.MAX_VALUE, LibraryLimit.remaining(100_000, true))
    }
    @Test fun offlineFailuresAndPartialPurchaseUpdatesCannotRevokeVerifiedAccess() {
        assertFalse(shouldRevokePurchase(false, true, false, false))
        assertFalse(shouldRevokePurchase(true, false, false, false))
        assertFalse(shouldRevokePurchase(true, true, true, false))
        assertFalse(shouldRevokePurchase(true, true, false, true))
        assertTrue(shouldRevokePurchase(true, true, false, false))
    }
}
