package com.duncpro.festivalgarden.restapi

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class SerializationPlayground {
    @Serializable
    data class MyValue(val stringProp: String)
    @Serializable
    data class MapContainer(val s: Map<String,MyValue>)

    @Test
    fun canSerializeMap() {
        val v = MapContainer(mapOf(Pair("a", MyValue("b"))))
        val encoded = Json.encodeToString(v)
        val decoded = Json.decodeFromString<MapContainer>(encoded)
        assert(v == decoded)
        println(decoded)
    }
}