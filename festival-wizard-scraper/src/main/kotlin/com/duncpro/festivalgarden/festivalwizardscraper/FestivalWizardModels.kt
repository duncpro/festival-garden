package com.duncpro.festivalgarden.festivalwizardscraper

import io.ktor.client.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

val FESTIVAL_WIZARD_JSON_FORMAT = Json { ignoreUnknownKeys = true }

val fwJsonDateStringFormat = SimpleDateFormat("MMM dd yyyy")
val fwQueryDateStringFormat = SimpleDateFormat("MMMMM dd yyyy")

object FWDateDeserializer: KSerializer<Date> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor(URLSerializer::class.qualifiedName!!, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Date {
        val dateString = decoder.decodeString()
        return fwJsonDateStringFormat.parse(dateString)
    }

    override fun serialize(encoder: Encoder, value: Date) {
        throw IllegalStateException("${FWDateDeserializer::class.qualifiedName} should not be used for" +
                " serialization for dates, since the format does not conform to any known standard.")
    }
}

suspend fun scrapeFestivalWizardIndex(httpClient: HttpClient): Flow<FWFestivalIdentity> =
    flow {
        var page = 1
        loop {
            val listings = scrapeListingPage(httpClient, page++)
            listings.forEach { emit(it) }
            return@loop if (listings.isEmpty()) LoopResult.BREAK else LoopResult.CONTINUE
        }
    }

@Serializable
data class FWFestivalDetails(
    val performingArtists: Set<String>
)

@Serializable
data class FWFestival(
    val identity: FWFestivalIdentity,
    val details: FWFestivalDetails?
)

@Serializable
data class FWFestivalIdentity(
    val name: String,
    @Serializable(URLSerializer::class) val url: URL,
    val location: FWPlace,
    @Serializable(FWDateDeserializer::class) val startDate: Date,
    @Serializable(FWDateDeserializer::class) val endDate: Date,
    val description: String
)

@Serializable
data class FWAddress(
    val addressLocality: String?,
    val addressRegion: String?
)

@Serializable
data class FWPlace(
    val name: String,
    val geo: FWGeoCoordinates,
    val address: FWAddress?
)

@Serializable
data class FWGeoCoordinates(
    val latitude: Double,
    val longitude: Double
)