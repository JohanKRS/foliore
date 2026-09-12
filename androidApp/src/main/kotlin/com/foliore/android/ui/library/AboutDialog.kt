package com.foliore.android.ui.library

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.foliore.android.R
import com.foliore.android.licenses.ThirdPartyCatalog
import com.foliore.android.licenses.ThirdPartyNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val packageInfo = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0) }
    val catalog by produceState<Result<ThirdPartyCatalog>?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("licenses/catalog.json").bufferedReader().use { ThirdPartyCatalog.parse(it.readText()) }
            }
        }
    }
    var section by rememberSaveable { mutableStateOf(AboutSection.HOME) }
    var groupId by rememberSaveable { mutableStateOf<String?>(null) }
    var noticePath by rememberSaveable { mutableStateOf<String?>(null) }
    var noticeTitle by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = catalog?.getOrNull()?.groups.orEmpty()
    val group = groups.firstOrNull { it.id == groupId }
    val notice = group?.notices?.firstOrNull { it.path == noticePath && it.title == noticeTitle }
    val goBack: () -> Unit = {
        when {
            noticePath != null -> {
                noticePath = null
                noticeTitle = null
            }
            groupId != null -> groupId = null
            section != AboutSection.HOME -> section = AboutSection.HOME
            else -> onDismiss()
        }
    }
    if (notice != null) {
        LicenseTextDialog(notice, goBack)
        return
    }
    FolioreOptionsDialog(
        title = group?.name ?: stringResource(section.title),
        onDismiss = goBack,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (group != null) {
                Text(group.license, style = MaterialTheme.typography.bodyMedium)
                ExternalCreditLink(stringResource(R.string.about_project_site), group.url)
                if (group.id == "google-play") {
                    ExternalCreditLink(stringResource(R.string.about_terms), "https://developer.android.com/studio/terms")
                }
                if (group.coordinates.isNotEmpty()) {
                    SelectionContainer {
                        Text(group.coordinates.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
                    }
                }
                group.notices.forEach { entry ->
                    val subtitle = if (group.notices.count { it.title == entry.title } > 1) {
                        entry.source.substringBefore('!')
                    } else stringResource(R.string.about_read_notice)
                    AboutRow(entry.title, subtitle) {
                        noticePath = entry.path
                        noticeTitle = entry.title
                    }
                }
            } else when (section) {
                AboutSection.HOME -> {
                    Text("Foliore", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.about_version, packageInfo.versionName.orEmpty(), PackageInfoCompat.getLongVersionCode(packageInfo)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(stringResource(R.string.about_thanks), style = MaterialTheme.typography.bodyMedium)
                    AboutSection.entries.filter { it != AboutSection.HOME }.forEach { entry ->
                        AboutRow(stringResource(entry.title), stringResource(entry.description)) { section = entry }
                    }
                }
                AboutSection.METADATA -> {
                    CreditService("Open Library", R.string.about_open_library, "https://openlibrary.org", "https://openlibrary.org/developers/licensing")
                    CreditService("Internet Archive", R.string.about_internet_archive, "https://archive.org", "https://archive.org/about/terms")
                    CreditService("Bibliothèque nationale de France (BnF)", R.string.about_bnf, "https://catalogue.bnf.fr", "https://www.bnf.fr/fr/conditions-de-reutilisations-des-donnees-de-la-bnf")
                    CreditService("Google Books", R.string.about_google_books, "https://books.google.com", "https://developers.google.com/books/terms")
                    Text(stringResource(R.string.about_content_rights), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AboutSection.LIBRARIES -> {
                    Text(stringResource(R.string.about_licenses_offline), style = MaterialTheme.typography.bodyMedium)
                    when {
                        catalog == null -> CircularProgressIndicator()
                        catalog?.isFailure == true -> Text(stringResource(R.string.about_load_error))
                        else -> groups.filter { it.id != "cormorant" }.forEach { entry ->
                            AboutRow(entry.name, entry.license) { groupId = entry.id }
                        }
                    }
                }
                AboutSection.DESIGN -> {
                    Text(stringResource(R.string.about_font_credit), style = MaterialTheme.typography.bodyMedium)
                    when {
                        catalog == null -> CircularProgressIndicator()
                        catalog?.isFailure == true -> Text(stringResource(R.string.about_load_error))
                        else -> groups.firstOrNull { it.id == "cormorant" }?.let { entry ->
                            AboutRow(entry.name, entry.license) { groupId = entry.id }
                        }
                    }
                    Text(stringResource(R.string.about_icons_credit), style = MaterialTheme.typography.bodyMedium)
                    groups.firstOrNull { it.id == "androidx" }?.let { entry ->
                        AboutRow("Material Icons", "Apache License 2.0") { groupId = entry.id }
                    }
                }
                AboutSection.INTEGRATIONS -> {
                    CreditService("Google Drive / Google Play services", R.string.about_drive, "https://drive.google.com", "https://developers.google.com/terms")
                    CreditService("OpenAI", R.string.about_openai, "https://openai.com", "https://openai.com/policies/services-agreement/")
                    CreditService("Kindle", R.string.about_kindle, "https://www.amazon.com/sendtokindle", "https://www.amazon.com/sendtokindleterms")
                    Text(stringResource(R.string.about_independent), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (section != AboutSection.HOME || group != null) {
                TextButton(onClick = goBack) { Text(stringResource(R.string.action_back)) }
            }
        }
    }
}

@Composable
private fun AboutRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null)
    }
}

@Composable
private fun CreditService(name: String, @StringRes description: Int, url: String, termsUrl: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(name, style = MaterialTheme.typography.titleSmall)
        Text(stringResource(description), style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ExternalCreditLink(stringResource(R.string.about_project_site), url)
            ExternalCreditLink(stringResource(R.string.about_terms), termsUrl)
        }
    }
}

@Composable
private fun ExternalCreditLink(label: String, url: String) {
    val handler = LocalUriHandler.current
    val context = LocalContext.current
    val errorMessage = stringResource(R.string.about_link_error)
    TextButton(onClick = {
        runCatching { handler.openUri(url) }.onFailure {
            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }) {
        Text(label)
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun LicenseTextDialog(notice: ThirdPartyNotice, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val paragraphs by produceState<Result<List<String>>?>(null, notice.path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(notice.path).bufferedReader().use {
                    it.readText().lineSequence().chunked(40).map { lines -> lines.joinToString("\n") }.toList()
                }
            }
        }
    }
    FolioreOptionsDialog(title = notice.title, onDismiss = onDismiss, scrollContent = false) {
        when {
            paragraphs == null -> CircularProgressIndicator()
            paragraphs?.isFailure == true -> Text(stringResource(R.string.about_load_error))
            else -> SelectionContainer {
                LazyColumn {
                    items(paragraphs?.getOrNull().orEmpty()) { paragraph ->
                        Text(paragraph, style = MaterialTheme.typography.bodySmall)
                    }
                    item { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_back)) } }
                }
            }
        }
    }
}

private enum class AboutSection(@get:StringRes val title: Int, @get:StringRes val description: Int) {
    HOME(R.string.about_title, R.string.about_thanks),
    METADATA(R.string.about_metadata, R.string.about_metadata_summary),
    LIBRARIES(R.string.about_libraries, R.string.about_libraries_summary),
    DESIGN(R.string.about_design, R.string.about_design_summary),
    INTEGRATIONS(R.string.about_integrations, R.string.about_integrations_summary),
}
