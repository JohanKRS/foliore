package com.foliore.android.licenses

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ThirdPartyCatalog(val groups: List<ThirdPartyGroup>) {
    companion object {
        fun parse(text: String): ThirdPartyCatalog = Json.decodeFromString(text)
    }
}

@Serializable
data class ThirdPartyGroup(
    val id: String,
    val name: String,
    val url: String,
    val license: String,
    val coordinates: List<String>,
    val notices: List<ThirdPartyNotice>,
)

@Serializable
data class ThirdPartyNotice(
    val title: String,
    val path: String,
    val sha256: String,
    val source: String,
)
