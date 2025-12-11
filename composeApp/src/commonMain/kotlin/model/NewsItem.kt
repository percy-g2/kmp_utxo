package model

import kotlinx.serialization.Serializable

@Serializable
data class NewsItem(
    val title: String,
    val description: String,
    val link: String,
    val pubDate: String,
    val source: String
)