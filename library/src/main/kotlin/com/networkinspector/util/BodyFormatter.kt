package com.networkinspector.util

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Utility class for formatting request/response bodies into readable format.
 */
object BodyFormatter {
    
    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .disableHtmlEscaping()
        .create()
    
    /**
     * Format any body object into a readable string.
     * Handles JSON strings, objects, byte arrays, and other types.
     * 
     * @param body The body to format (can be String, Object, ByteArray, etc.)
     * @param maxSize Maximum size of the output (default 100KB)
     * @return Formatted string representation
     */
    @JvmStatic
    @JvmOverloads
    fun format(body: Any?, maxSize: Int = 100_000): String? {
        if (body == null) return null
        
        return try {
            when (body) {
                is String -> formatString(body, maxSize)
                is ByteArray -> formatByteArray(body, maxSize)
                is CharArray -> formatString(String(body), maxSize)
                is Map<*, *> -> formatMap(body, maxSize)
                is Collection<*> -> formatCollection(body, maxSize)
                else -> formatObject(body, maxSize)
            }
        } catch (e: Throwable) {
            // Catch everything including StackOverflowError
            try {
                body.toString().take(maxSize)
            } catch (e2: Throwable) {
                "[Object: ${body.javaClass.simpleName}]"
            }
        }
    }
    
    /**
     * Format a string body, attempting to pretty-print if it's JSON
     */
    private fun formatString(body: String, maxSize: Int): String {
        val trimmed = body.trim()
        
        // Check if it's JSON
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return tryFormatJson(trimmed, maxSize) ?: trimmed.take(maxSize)
        }
        
        // Check if it's URL-encoded form data
        if (trimmed.contains("=") && trimmed.contains("&")) {
            return formatFormData(trimmed, maxSize)
        }
        
        // Check if it's XML
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            return formatXml(trimmed, maxSize)
        }
        
        return trimmed.take(maxSize)
    }
    
    /**
     * Try to parse and pretty-print JSON
     */
    private fun tryFormatJson(json: String, maxSize: Int): String? {
        return try {
            // Try parsing as JSONObject
            if (json.startsWith("{")) {
                val jsonObject = JSONObject(json)
                jsonObject.toString(2).take(maxSize)
            } else {
                val jsonArray = JSONArray(json)
                jsonArray.toString(2).take(maxSize)
            }
        } catch (e: JSONException) {
            // Fallback to Gson
            try {
                val element = JsonParser.parseString(json)
                gson.toJson(element).take(maxSize)
            } catch (e2: JsonSyntaxException) {
                null
            }
        }
    }
    
    /**
     * Format URL-encoded form data into readable format
     */
    private fun formatFormData(data: String, maxSize: Int): String {
        val result = StringBuilder()
        result.appendLine("Form Data:")
        result.appendLine("─".repeat(40))
        
        data.split("&").forEach { pair ->
            val parts = pair.split("=", limit = 2)
            val key = try { 
                java.net.URLDecoder.decode(parts[0], "UTF-8") 
            } catch (e: Exception) { 
                parts[0] 
            }
            val value = if (parts.size > 1) {
                try { 
                    java.net.URLDecoder.decode(parts[1], "UTF-8") 
                } catch (e: Exception) { 
                    parts[1] 
                }
            } else ""
            
            result.appendLine("  $key: $value")
        }
        
        return result.toString().take(maxSize)
    }
    
    /**
     * Basic XML formatting (indentation)
     */
    private fun formatXml(xml: String, maxSize: Int): String {
        return try {
            val result = StringBuilder()
            var indent = 0
            val lines = xml.replace("><", ">\n<").split("\n")
            
            for (line in lines) {
                val trimmedLine = line.trim()
                if (trimmedLine.startsWith("</")) {
                    indent = maxOf(0, indent - 1)
                }
                result.appendLine("  ".repeat(indent) + trimmedLine)
                if (trimmedLine.startsWith("<") && 
                    !trimmedLine.startsWith("</") && 
                    !trimmedLine.startsWith("<?") &&
                    !trimmedLine.endsWith("/>") &&
                    !trimmedLine.contains("</")) {
                    indent++
                }
            }
            
            result.toString().take(maxSize)
        } catch (e: Exception) {
            xml.take(maxSize)
        }
    }
    
    /**
     * Format byte array - show size and attempt to decode as string
     */
    private fun formatByteArray(bytes: ByteArray, maxSize: Int): String {
        return try {
            val str = String(bytes, Charsets.UTF_8)
            "[${bytes.size} bytes]\n${formatString(str, maxSize - 20)}"
        } catch (e: Exception) {
            "[Binary data: ${bytes.size} bytes]"
        }
    }
    
    /**
     * Format Map to JSON
     */
    private fun formatMap(map: Map<*, *>, maxSize: Int): String {
        return try {
            gson.toJson(map).let { tryFormatJson(it, maxSize) ?: it.take(maxSize) }
        } catch (e: Throwable) {
            try {
                map.toString().take(maxSize)
            } catch (e2: Throwable) {
                "[Map: ${map.size} entries]"
            }
        }
    }
    
    /**
     * Format Collection to JSON array
     */
    private fun formatCollection(collection: Collection<*>, maxSize: Int): String {
        return try {
            gson.toJson(collection).let { tryFormatJson(it, maxSize) ?: it.take(maxSize) }
        } catch (e: Throwable) {
            try {
                collection.toString().take(maxSize)
            } catch (e2: Throwable) {
                "[Collection: ${collection.size} items]"
            }
        }
    }
    
    /**
     * Format any object using Gson
     */
    private fun formatObject(obj: Any, maxSize: Int): String {
        return try {
            // First try toString() as it's safer
            val simpleString = obj.toString()
            
            // If toString looks like a data class or has useful info, use it
            if (simpleString.length > 50 || !simpleString.contains("@")) {
                // Try to format as JSON for better readability
                try {
                    val json = gson.toJson(obj)
                    tryFormatJson(json, maxSize) ?: json.take(maxSize)
                } catch (e: Throwable) {
                    // Gson failed (circular ref, stack overflow, etc.)
                    simpleString.take(maxSize)
                }
            } else {
                simpleString.take(maxSize)
            }
        } catch (e: Throwable) {
            // Catch everything including StackOverflowError
            "[Object: ${obj.javaClass.simpleName}]"
        }
    }
    
    /**
     * Check if a string is likely binary/non-printable content
     */
    @JvmStatic
    fun isBinaryContent(content: String): Boolean {
        if (content.isEmpty()) return false
        
        val nonPrintableCount = content.count { char ->
            char.code < 32 && char != '\n' && char != '\r' && char != '\t'
        }
        
        return nonPrintableCount.toFloat() / content.length > 0.1
    }
    
    /**
     * Get content type summary for display
     */
    @JvmStatic
    fun getContentTypeSummary(contentType: String?): String {
        if (contentType == null) return "Unknown"
        
        return when {
            contentType.contains("json", ignoreCase = true) -> "JSON"
            contentType.contains("xml", ignoreCase = true) -> "XML"
            contentType.contains("html", ignoreCase = true) -> "HTML"
            contentType.contains("text", ignoreCase = true) -> "Text"
            contentType.contains("form-urlencoded", ignoreCase = true) -> "Form Data"
            contentType.contains("multipart", ignoreCase = true) -> "Multipart"
            contentType.contains("image", ignoreCase = true) -> "Image"
            contentType.contains("video", ignoreCase = true) -> "Video"
            contentType.contains("audio", ignoreCase = true) -> "Audio"
            contentType.contains("octet-stream", ignoreCase = true) -> "Binary"
            else -> contentType.substringBefore(";").substringAfterLast("/")
        }
    }
}




