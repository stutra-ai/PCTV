package com.pinaycum

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class PinayCum : MainAPI() {
    override var mainUrl = "https://pinaycum.tv"
    override var name = "PinayCum.tv"
    override val supportedTypes = setOf(TvType.NSFW)
    override var lang = "tl"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val hasSearch = true

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Latest Videos",
        // You can add more if categories exist
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data.removeSuffix("/")}/page/$page/" // adjust if pagination differs
        val document = app.get(url).document
        val items = document.select("a[href*='watch.php?id=']").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items, hasNext = true)
    }

    override suspend fun search(query: String, page: Int): List<SearchResponse> {
        // Site search is weak; using main page with query if possible, or fallback
        val url = if (page <= 1) "$mainUrl/?s=$query" else "$mainUrl/?s=$query&page=$page"
        val document = app.get(url).document
        return document.select("a[href*='watch.php?id=']").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h2, strong, .title")?.text()?.trim() 
            ?: this.ownText().trim().takeIf { it.isNotEmpty() } 
            ?: return null
        
        val href = fixUrlNull(attr("href")) ?: return null
        val poster = fixUrlNull(selectFirst("img")?.attr("src") ?: selectFirst("img")?.attr("data-src"))

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = poster
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1, h2, title")?.text()?.trim() ?: "Pinay Video"
        
        val poster = fixUrlNull(
            document.selectFirst("meta[property=og:image]")?.attr("content")
                ?: document.selectFirst("img")?.attr("src")
        )

        val viewsLikes = document.selectFirst("strong")?.text() // e.g., "122866 Views | 13764 Likes"
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")

        // Related videos
        val recommendations = document.select("a[href*='watch.php?id=']").mapNotNull { it.toSearchResult() }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot = description
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // Direct Download Link (main source on this site)
        val downloadLink = document.selectFirst("a[href*='vidaratem.com']")?.attr("href")
        if (downloadLink != null) {
            callback(
                ExtractorLink(
                    name = name,
                    source = "Direct",
                    url = fixUrl(downloadLink),
                    referer = mainUrl,
                    quality = Qualities.Unknown.value,
                    type = ExtractorLinkType.VIDEO
                )
            )
        }

        // Fallback: Look for any <video> or <source> tags
        document.select("video source, video").forEach { el ->
            val src = el.attr("src").takeIf { it.isNotEmpty() }
            if (src != null) {
                callback(
                    ExtractorLink(
                        name = name,
                        source = "Video Source",
                        url = fixUrl(src),
                        referer = mainUrl,
                        quality = Qualities.Unknown.value
                    )
                )
            }
        }

        // If needed, add more extractors or JS evaluation later
        return true
    }
}