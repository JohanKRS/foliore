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
        LibraryNoticeType.IMPORT_DRM_PROTECTED -> stringResource(R.string.import_notice_drm_protected)
        LibraryNoticeType.IMPORT_CONVERSION_FAILED -> stringResource(R.string.import_notice_conversion_failed)
        LibraryNoticeType.IMPORT_TOO_LARGE -> stringResource(R.string.import_notice_too_large)
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
        LibraryNoticeType.SYNC_EXISTING_LIBRARY_CONFIGURED -> {
            stringResource(R.string.sync_notice_existing_library_configured)
        }
        LibraryNoticeType.SYNC_COMPLETE -> stringResource(R.string.sync_notice_complete)
        LibraryNoticeType.SYNC_COMPLETE_WITH_CONFLICTS -> stringResource(R.string.sync_notice_complete_with_conflicts)
        LibraryNoticeType.SYNC_NOT_CONFIGURED -> stringResource(R.string.sync_notice_not_configured)
        LibraryNoticeType.SYNC_FOLDER_UNAVAILABLE -> stringResource(R.string.sync_notice_folder_unavailable)
        LibraryNoticeType.SYNC_FOLDER_LOOKS_LIKE_SUBFOLDER -> {
            stringResource(R.string.sync_notice_folder_looks_like_subfolder)
        }
        LibraryNoticeType.SYNC_GOOGLE_DRIVE_CONNECTED -> stringResource(R.string.sync_notice_google_drive_connected)
        LibraryNoticeType.SYNC_GOOGLE_DRIVE_AUTH_REQUIRED -> {
            stringResource(R.string.sync_notice_google_drive_auth_required)
        }
        LibraryNoticeType.SYNC_GOOGLE_DRIVE_CONFIGURATION_REQUIRED -> {
            stringResource(R.string.sync_notice_google_drive_configuration_required)
        }
        LibraryNoticeType.SYNC_GOOGLE_DRIVE_QUOTA_EXCEEDED -> {
            stringResource(R.string.sync_notice_google_drive_quota_exceeded)
        }
        LibraryNoticeType.SYNC_GOOGLE_DRIVE_UNAVAILABLE -> {
            stringResource(R.string.sync_notice_google_drive_unavailable)
        }
        LibraryNoticeType.SYNC_GOOGLE_DRIVE_BACKUP_DELETED -> {
            stringResource(R.string.sync_notice_google_drive_backup_deleted)
        }
        LibraryNoticeType.SYNC_AUTO_SETTINGS_UPDATED -> stringResource(R.string.sync_notice_auto_settings_updated)
        LibraryNoticeType.SYNC_FAILED -> stringResource(R.string.sync_notice_failed)
        LibraryNoticeType.SYNC_DISCONNECTED -> stringResource(R.string.sync_notice_disconnected)
        LibraryNoticeType.AI_SETTINGS_UPDATED -> stringResource(R.string.ai_notice_settings_updated)
        LibraryNoticeType.AI_API_KEY_SAVED -> stringResource(R.string.ai_notice_api_key_saved)
        LibraryNoticeType.AI_API_KEY_CLEARED -> stringResource(R.string.ai_notice_api_key_cleared)
        LibraryNoticeType.AI_PROVIDER_REQUIRED -> stringResource(R.string.ai_notice_provider_required)
        LibraryNoticeType.AI_PROVIDER_NOT_READY -> stringResource(R.string.ai_notice_provider_not_ready)
        LibraryNoticeType.AI_REQUEST_FAILED -> stringResource(R.string.ai_notice_request_failed)
        LibraryNoticeType.AI_METADATA_PREVIEW_READY -> stringResource(R.string.ai_notice_metadata_preview_ready)
        LibraryNoticeType.METADATA_LOOKUP_READY -> stringResource(R.string.metadata_lookup_notice_ready)
        LibraryNoticeType.METADATA_LOOKUP_EMPTY -> stringResource(R.string.metadata_lookup_notice_empty)
        LibraryNoticeType.METADATA_LOOKUP_FAILED -> stringResource(R.string.metadata_lookup_notice_failed)
        LibraryNoticeType.AI_COVER_PROVIDER_REQUIRED -> stringResource(R.string.ai_notice_cover_provider_required)
        LibraryNoticeType.AI_COVER_OPTIONS_READY -> stringResource(R.string.ai_notice_cover_options_ready)
        LibraryNoticeType.AI_COVER_PROMPT_SAVED -> stringResource(R.string.ai_notice_cover_prompt_saved)
        LibraryNoticeType.KINDLE_EMAIL_SAVED -> stringResource(R.string.kindle_notice_email_saved)
        LibraryNoticeType.KINDLE_EMAIL_CLEARED -> stringResource(R.string.kindle_notice_email_cleared)
        LibraryNoticeType.KINDLE_EMAIL_INVALID -> stringResource(R.string.kindle_notice_email_invalid)
        LibraryNoticeType.KINDLE_EMAIL_MISSING -> stringResource(R.string.kindle_notice_email_missing)
        LibraryNoticeType.KINDLE_SMTP_SAVED -> stringResource(R.string.kindle_notice_smtp_saved)
        LibraryNoticeType.KINDLE_SMTP_CLEARED -> stringResource(R.string.kindle_notice_smtp_cleared)
        LibraryNoticeType.KINDLE_SMTP_INVALID -> stringResource(R.string.kindle_notice_smtp_invalid)
        LibraryNoticeType.GMAIL_CONNECTED -> stringResource(R.string.gmail_notice_connected)
        LibraryNoticeType.GMAIL_DISCONNECTED -> stringResource(R.string.gmail_notice_disconnected)
        LibraryNoticeType.GMAIL_AUTH_REQUIRED -> stringResource(R.string.gmail_notice_auth_required)
        LibraryNoticeType.GMAIL_CONFIGURATION_REQUIRED -> stringResource(R.string.gmail_notice_configuration_required)
        LibraryNoticeType.GMAIL_UNAVAILABLE -> stringResource(R.string.gmail_notice_unavailable)
        LibraryNoticeType.GMAIL_TOO_LARGE -> stringResource(R.string.gmail_notice_too_large)
        LibraryNoticeType.GMAIL_RATE_LIMITED -> stringResource(R.string.gmail_notice_rate_limited)
        LibraryNoticeType.GMAIL_SEND_UNKNOWN -> stringResource(R.string.gmail_notice_send_unknown)
        LibraryNoticeType.KINDLE_EMAIL_SENT -> stringResource(R.string.kindle_notice_sent)
        LibraryNoticeType.KINDLE_EMAIL_FAILED -> stringResource(R.string.kindle_notice_send_failed)
    }
}
