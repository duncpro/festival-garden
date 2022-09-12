package com.duncpro.festivalgarden.festivalwizardscraper

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import java.net.URL

private val logger = LoggerFactory.getLogger("FestivalWizard")

suspend fun scrapeFestivalDetailPage(httpClient: HttpClient, url: URL): FWFestivalDetails? {
    val response = httpClient.get(url) {
        accept(ContentType.Application.Json)
    }
    if (!response.status.isSuccess()) {
        logger.error("Unable to scrape $url because the web server responded with on-success error code:" +
                " ${response.status.value}: Body: ${response.bodyAsText()}")
        return null
    }

    val performingArtists = Jsoup.parse(response.bodyAsText()).select("div.hublineup ul")
        .asSequence()
        .flatMap { it.children() }
        .filter { element -> element.tagName().equals("li", ignoreCase = true) }
        .map { element -> element.text() }
        .toSet()

    return FWFestivalDetails(performingArtists)
}