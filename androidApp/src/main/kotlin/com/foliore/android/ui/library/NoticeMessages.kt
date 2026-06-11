package com.foliore.android.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.foliore.android.R
import com.foliore.android.viewmodel.LibraryNoticeType

@Composable
fun noticeMessage(type: LibraryNoticeType): String {
    return when (type) {
        LibraryNoticeType.IMPORTED -> stringResource(R.string.import_notice_imported)
        LibraryNoticeType.DUPLICATE -> stringResource(R.string.import_notice_duplicates)
        LibraryNoticeType.FAILED -> stringResource(R.string.import_notice_failed)
        LibraryNoticeType.MIXED -> stringResource(R.string.import_notice_mixed)
        LibraryNoticeType.METADATA_SAVED -> stringResource(R.string.edit_notice_metadata_saved)
        LibraryNoticeType.COVER_UPDATED -> stringResource(R.string.edit_notice_cover_updated)
        LibraryNoticeType.UPDATE_FAILED -> stringResource(R.string.edit_notice_update_failed)
        LibraryNoticeType.INVALID_TITLE -> stringResource(R.string.edit_notice_invalid_title)
    }
}

