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

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Latest Videos",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data.removeSuffix("/")}/page/$page/"
        val document = app.get(url).document
        val items = document.select("a[href*='watch.php?id=']").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, items, hasNext = true)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val url = if (page <= 1) "$mainUrl/?s=$query" else "$mainUrl/?s=$query&page=$page"
        val document = app.get(url).document
        
        val resultsList = document.select("a[href*='watch.php?id=']").mapNotNull { it.toSearchResult() }
        
        // FIXED: Swapped parameters to match expected signature types
        return newSearchResponseList(resultsList, hasNext = true)
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

        val viewsLikes = document.selectFirst("strong")?.text()
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")

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

        // Direct Download Link
        val downloadLink = document.selectFirst("a[href*='vidaratem.com']")?.attr("href")
        if (downloadLink != null) {
            callback(
                ExtractorLink(
                    source = "Direct",
                    name = name,
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
                        source = "Video Source",
                        name = name,
                        url = fixUrl(src),
                        referer = mainUrl,
                        quality = Qualities.Unknown.value,
                        type = ExtractorLinkType.VIDEO
                    )
                )
            }
        }

        return true
    }
}
