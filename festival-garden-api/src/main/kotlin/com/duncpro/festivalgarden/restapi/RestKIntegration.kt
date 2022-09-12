package com.duncpro.festivalgarden.restapi

import com.duncpro.restk.ContentTypes
import com.duncpro.restk.RequestBodyReference
import com.duncpro.restk.ResponseBuilderContext
import com.duncpro.restk.asString
import com.duncpro.restk.body
import com.duncpro.restk.header
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URLEncoder

suspend inline fun <reified T> RequestBodyReference.deserializeJson(): T = Json.decodeFromString(this.asString())

inline fun <reified T> ResponseBuilderContext.jsonBody(dataClassInstance: T) {
    header("content-type", "application/json; charset=utf-8")
    body(Json.encodeToString(dataClassInstance), ContentTypes.Application.JSON)
}

fun compileQueryString(arguments: Map<String, String>): String = "?" + arguments
    .mapKeys { (param, _) -> URLEncoder.encode(param, Charsets.UTF_8) }
    .mapValues { (_, arg) -> URLEncoder.encode(arg, Charsets.UTF_8) }
    .map { (param, arg) -> "$param=$arg" }
    .joinToString("&")