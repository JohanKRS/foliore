package com.foliore.android.ui.library

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.foliore.android.R

/** Ordinary external links only. Selection and linking permissions: docs/PUBLIC_DOMAIN_SOURCES.md. */
@Composable
internal fun PublicDomainBookLinks(modifier: Modifier = Modifier, isEmptyLibrary: Boolean = false) {
    // Resolving through resources also follows the app's fallback language in system mode.
    val language = stringResource(R.string.public_domain_language)
        .takeIf { it in setOf("pt", "en", "es", "fr", "de", "it", "ja", "zh") } ?: "pt"
    val gutenbergUrl = if (language == "en") "https://www.gutenberg.org/" else "https://www.gutenberg.org/browse/languages/$language"
    Column(modifier = modifier) {
        Text(
            text = stringResource(if (isEmptyLibrary) R.string.public_domain_start_title else R.string.public_domain_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(if (isEmptyLibrary) R.string.public_domain_start_hint else R.string.public_domain_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            PublicDomainLink(stringResource(R.string.public_domain_gutenberg), gutenbergUrl)
            when (language) {
                "en" -> PublicDomainLink(stringResource(R.string.public_domain_wikisource), "https://en.wikisource.org/wiki/Category:Ready_for_export")
                "fr" -> {
                    PublicDomainLink(stringResource(R.string.public_domain_ebooks_gratuits), "https://www.ebooksgratuits.com/")
                    PublicDomainLink(stringResource(R.string.public_domain_wikisource), "https://fr.wikisource.org/wiki/Cat%C3%A9gorie:Bon_pour_export")
                }
                "es" -> PublicDomainLink(stringResource(R.string.public_domain_elejandria), "https://www.elejandria.com/")
                "it" -> PublicDomainLink(stringResource(R.string.public_domain_liber_liber), "https://liberliber.it/")
            }
        }
    }
}

@Composable
private fun PublicDomainLink(label: String, url: String) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val error = stringResource(R.string.about_link_error)
    TextButton(
        onClick = {
            runCatching { uriHandler.openUri(url) }.onFailure {
                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
            }
        },
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        Text(label)
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
    }
}
