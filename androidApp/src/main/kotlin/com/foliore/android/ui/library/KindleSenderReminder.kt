package com.foliore.android.ui.library

import android.content.res.Resources
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.foliore.android.R

@Composable
internal fun KindleSenderReminder() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val error = stringResource(R.string.about_link_error)
    // The device region, rather than the app's chosen translation, selects the storefront.
    val country = Resources.getSystem().configuration.locales[0].country
    val domain = when (country) {
        "BR" -> "amazon.com.br"
        "GB" -> "amazon.co.uk"
        "CA" -> "amazon.ca"
        "AU" -> "amazon.com.au"
        "IN" -> "amazon.in"
        "MX" -> "amazon.com.mx"
        "JP" -> "amazon.co.jp"
        "DE", "AT" -> "amazon.de"
        "FR" -> "amazon.fr"
        "IT" -> "amazon.it"
        "ES" -> "amazon.es"
        else -> "amazon.com"
    }
    val url = "https://www.$domain/mycd"
    val linkStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
    )
    Text(
        text = buildAnnotatedString {
            append(stringResource(R.string.kindle_sender_approval_hint))
            append(" ")
            withLink(LinkAnnotation.Url(url, TextLinkStyles(style = linkStyle)) {
                runCatching { uriHandler.openUri(url) }.onFailure {
                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                }
            }) {
                append(stringResource(R.string.kindle_sender_approval_link))
            }
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
    )
}
