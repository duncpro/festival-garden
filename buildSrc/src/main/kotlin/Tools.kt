//fun readJsonAsProperties(file: File): Properties {
//
//}

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.*
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.util.Properties

fun File.writeProperties(map: Map<String, String>) {
    val properties = Properties()
    for ((key, value) in map) {
        properties.setProperty(key, value)
    }
    if (this.exists()) this.delete()
    this.createNewFile()
    this.outputStream().use { properties.store(it, "") }
}

fun File.readSimpleJsonFileToFlatMap(): Map<String, String> {
    val jsonElement = Json.parseToJsonElement(this.readText())
    if (jsonElement !is JsonObject) throw UnsupportedOperationException()
    return flattenSimpleJsonObject(jsonElement)
}

fun File.readProperties(): Map<String, String> {
    val properties = Properties()
    this.inputStream().use { properties.load(it) }
    val map = mutableMapOf<String, String>()
    for (key in properties.stringPropertyNames()) {
        map[key] = properties.getProperty(key)
    }
    return map
}

fun File.writeFlatJsFile(properties: Map<String, String>) {
    if (!this.exists()) this.createNewFile()
    val jsonObject = JsonObject(properties.mapValues { (_, value) -> JsonPrimitive(value) })
    this.outputStream().use { Json.encodeToStream(jsonObject, it) }
}

private fun flattenSimpleJsonObject(prefix: List<String>, flattened: MutableMap<String, String>, jsonObject: JsonObject) {
    for ((key, value) in jsonObject) {
        if (value is JsonObject) {
            flattenSimpleJsonObject(prefix + key, flattened, value)
            continue
        }
        if (value is JsonArray) throw UnsupportedOperationException()
        if (value is JsonPrimitive) {
            val flatKey = (prefix + key)
                .joinToString(".")
            flattened[flatKey] = value.content
        }
    }
}

private fun flattenSimpleJsonObject(jsonObject: JsonObject): Map<String, String> {
    val flattened = mutableMapOf<String, String>()
    flattenSimpleJsonObject(emptyList<String>(), flattened, jsonObject)
    return flattened
}