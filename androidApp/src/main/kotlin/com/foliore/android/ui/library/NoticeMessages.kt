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
        LibraryNoticeType.BOOK_DELETED -> stringResource(R.string.delete_notice_done)
        LibraryNoticeType.DELETE_FAILED -> stringResource(R.string.delete_notice_failed)
        LibraryNoticeType.EPUB_PREPARED -> stringResource(R.string.prepare_notice_done)
        LibraryNoticeType.PREPARE_FAILED -> stringResource(R.string.prepare_notice_failed)
        LibraryNoticeType.EXPORT_FAILED -> stringResource(R.string.export_notice_failed)
        LibraryNoticeType.SYNC_FOLDER_CONFIGURED -> stringResource(R.string.sync_notice_folder_configured)
        LibraryNoticeType.SYNC_COMPLETE -> stringResource(R.string.sync_notice_complete)
        LibraryNoticeType.SYNC_COMPLETE_WITH_CONFLICTS -> stringResource(R.string.sync_notice_complete_with_conflicts)
        LibraryNoticeType.SYNC_NOT_CONFIGURED -> stringResource(R.string.sync_notice_not_configured)
        LibraryNoticeType.SYNC_FOLDER_UNAVAILABLE -> stringResource(R.string.sync_notice_folder_unavailable)
        LibraryNoticeType.SYNC_FAILED -> stringResource(R.string.sync_notice_failed)
        LibraryNoticeType.SYNC_DISCONNECTED -> stringResource(R.string.sync_notice_disconnected)
    }
}
