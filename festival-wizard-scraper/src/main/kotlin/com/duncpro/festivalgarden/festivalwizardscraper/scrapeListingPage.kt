package com.duncpro.festivalgarden.festivalwizardscraper

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant.now
import java.util.Date

private val logger = LoggerFactory.getLogger("FestivalWizard")

suspend fun scrapeListingPage(httpClient: HttpClient, pageNumber: Int): Set<FWFestivalIdentity> {
    // https://www.musicfestivalwizard.com/all-festivals/?edate=January+1%2C+2023&ranked=yes&lineup=onlylineups
    val response = httpClient.get("https://www.musicfestivalwizard.com/all-festivals/page/${pageNumber}") {
        parameter("edate", fwQueryDateStringFormat.format(Date.from(now().plus(Duration.ofDays(365)))))
        parameter("ranked", "yes")
        parameter("lineup", "onlylineups")
        accept(ContentType.Text.Html)
    }
    if (!response.status.isSuccess()) {
        logger.error("Unable to scrape https://www.musicalfestivalwizard.com/all-festivals because the web server" +
                " responded with non-success error code: ${response.status.value}. Body: ${response.bodyAsText()}")
        return emptySet()
    }

    return Jsoup.parse(response.bodyAsText(), response.request.url.toString())
        .getElementsByTag("script")
        .asSequence()
        .filter { scriptElement -> scriptElement.attr("type").equals("application/ld+json", ignoreCase = true) }
        .mapNotNull { scriptElement ->
            try {
                logger.debug("JSON from festival wizard: ${scriptElement.html()}")
                Json.parseToJsonElement(scriptElement.html())
            } catch (e: Exception) {
                // Kotlinx does not expose JsonDecodingException publicly (not sure why?)
                if (e::class.qualifiedName?.equals("kotlinx.serialization.json.internal.JsonDecodingException") == true) {
                    logger.warn("An unexpected error occurred while trying to parse the JSON returned by " +
                            "https://www.musicalfestivalwizard.com/all-festivals. Expected valid json but encountered" +
                            ": \"${scriptElement.html()}\".")
                    return@mapNotNull null
                }
                throw e
            }
        }
        .filterIsInstance<JsonObject>()
        .filter { json ->
            val context = json["@context"] ?: return@filter false
            if (context !is JsonPrimitive) return@filter false
            return@filter setOf("https://schema.org", "http://schema.org").contains(context.content)
        }
        .filter { json ->
            val type = json["@type"] ?: return@filter false
            if (type !is JsonPrimitive) return@filter false
            return@filter type.content == "Festival"
        }
        .map { json -> FESTIVAL_WIZARD_JSON_FORMAT.decodeFromJsonElement<FWFestivalIdentity>(json) }
        .toSet()
}