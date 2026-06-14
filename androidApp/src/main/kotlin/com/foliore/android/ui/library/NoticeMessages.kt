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
        LibraryNoticeType.BATCH_UPDATED -> stringResource(R.string.batch_notice_done)
        LibraryNoticeType.BATCH_FAILED -> stringResource(R.string.batch_notice_failed)
        LibraryNoticeType.BATCH_NO_CHANGES -> stringResource(R.string.batch_notice_no_changes)
        LibraryNoticeType.BATCH_DELETED -> stringResource(R.string.batch_delete_notice_done)
        LibraryNoticeType.BATCH_DELETE_FAILED -> stringResource(R.string.batch_delete_notice_failed)
        LibraryNoticeType.PREPARE_FAILED -> stringResource(R.string.prepare_notice_failed)
        LibraryNoticeType.EXPORT_FAILED -> stringResource(R.string.export_notice_failed)
        LibraryNoticeType.SYNC_FOLDER_CONFIGURED -> stringResource(R.string.sync_notice_folder_configured)
        LibraryNoticeType.SYNC_COMPLETE -> stringResource(R.string.sync_notice_complete)
        LibraryNoticeType.SYNC_COMPLETE_WITH_CONFLICTS -> stringResource(R.string.sync_notice_complete_with_conflicts)
        LibraryNoticeType.SYNC_NOT_CONFIGURED -> stringResource(R.string.sync_notice_not_configured)
        LibraryNoticeType.SYNC_FOLDER_UNAVAILABLE -> stringResource(R.string.sync_notice_folder_unavailable)
        LibraryNoticeType.SYNC_FAILED -> stringResource(R.string.sync_notice_failed)
        LibraryNoticeType.SYNC_DISCONNECTED -> stringResource(R.string.sync_notice_disconnected)
        LibraryNoticeType.AI_SETTINGS_UPDATED -> stringResource(R.string.ai_notice_settings_updated)
        LibraryNoticeType.AI_API_KEY_SAVED -> stringResource(R.string.ai_notice_api_key_saved)
        LibraryNoticeType.AI_API_KEY_CLEARED -> stringResource(R.string.ai_notice_api_key_cleared)
        LibraryNoticeType.AI_BRIDGE_SETTINGS_SAVED -> stringResource(R.string.ai_notice_bridge_settings_saved)
        LibraryNoticeType.AI_BRIDGE_SETTINGS_CLEARED -> stringResource(R.string.ai_notice_bridge_settings_cleared)
        LibraryNoticeType.AI_BRIDGE_CONNECTED -> stringResource(R.string.ai_notice_bridge_connected)
        LibraryNoticeType.AI_BRIDGE_COVERS_UNAVAILABLE -> stringResource(R.string.ai_notice_bridge_covers_unavailable)
        LibraryNoticeType.AI_BRIDGE_UNAUTHORIZED -> stringResource(R.string.ai_notice_bridge_unauthorized)
        LibraryNoticeType.AI_BRIDGE_UNREACHABLE -> stringResource(R.string.ai_notice_bridge_unreachable)
        LibraryNoticeType.AI_BRIDGE_TIMEOUT -> stringResource(R.string.ai_notice_bridge_timeout)
        LibraryNoticeType.AI_BRIDGE_CODEX_FAILED -> stringResource(R.string.ai_notice_bridge_codex_failed)
        LibraryNoticeType.AI_PROVIDER_REQUIRED -> stringResource(R.string.ai_notice_provider_required)
        LibraryNoticeType.AI_PROVIDER_NOT_READY -> stringResource(R.string.ai_notice_provider_not_ready)
        LibraryNoticeType.AI_REQUEST_FAILED -> stringResource(R.string.ai_notice_request_failed)
        LibraryNoticeType.AI_METADATA_PREVIEW_READY -> stringResource(R.string.ai_notice_metadata_preview_ready)
        LibraryNoticeType.AI_COVER_PROVIDER_REQUIRED -> stringResource(R.string.ai_notice_cover_provider_required)
        LibraryNoticeType.AI_COVER_OPTIONS_READY -> stringResource(R.string.ai_notice_cover_options_ready)
        LibraryNoticeType.AI_COVER_PROMPT_SAVED -> stringResource(R.string.ai_notice_cover_prompt_saved)
    }
}
