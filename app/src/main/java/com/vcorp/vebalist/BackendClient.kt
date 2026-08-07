package com.vcorp.vebalist

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Base64

object BackendClient {
    data class PublishResult(val sku: String, val ok: Boolean, val listingId: String?, val error: String?)

    private fun get(baseUrl: String, path: String): JSONObject {
        val c = URL(baseUrl + path).openConnection() as HttpURLConnection
        c.requestMethod = "GET"; c.connectTimeout = 15000; c.readTimeout = 30000
        return try {
            val body = (if (c.responseCode in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            if (c.responseCode !in 200..299) throw RuntimeException("Backend ${c.responseCode}: $body")
            JSONObject(body)
        } finally { c.disconnect() }
    }

    fun ping(baseUrl: String): Pair<Boolean,String> = try {
        Pair(true, get(baseUrl, "/api/status").toString())
    } catch (e: Exception) { Pair(false, e.message ?: "Unknown error") }

    fun loadAccountSetup(baseUrl: String): JSONObject = get(baseUrl, "/api/ebay/account-setup")

    fun loadAspects(baseUrl: String, categoryId: String): List<EbayAspect> {
        val j = get(baseUrl, "/api/ebay/aspects?category_id=" + URLEncoder.encode(categoryId, "UTF-8"))
        val arr = j.optJSONArray("aspects") ?: JSONArray()
        val out = mutableListOf<EbayAspect>()
        for (i in 0 until arr.length()) {
            val a = arr.getJSONObject(i)
            val vals = mutableListOf<String>()
            a.optJSONArray("values")?.let { v -> for (k in 0 until v.length()) vals += v.getString(k) }
            out += EbayAspect(
                name = a.getString("name"),
                required = a.optBoolean("required"),
                mode = a.optString("mode", "FREE_TEXT"),
                values = vals
            )
        }
        return out
    }


fun validateOnly(baseUrl: String, listing: Listing): JSONObject {
    val specifics = JSONObject()
    listing.itemSpecifics.forEach { (name, values) ->
        val a = JSONArray(); values.forEach { a.put(it) }; specifics.put(name, a)
    }
    val obj = JSONObject()
        .put("sku", listing.sku)
        .put("title", listing.title)
        .put("description", listing.description)
        .put("category_id", listing.categoryId)
        .put("condition", listing.condition)
        .put("price", listing.price ?: 0.0)
        .put("quantity", listing.quantity)
        .put("payment_policy_id", listing.paymentPolicyId)
        .put("return_policy_id", listing.returnPolicyId)
        .put("inventory_location_key", listing.inventoryLocationKey)
        .put("item_specifics", specifics)
        .put("shipping", JSONObject().put("fulfillment_policy_id", listing.fulfillmentPolicyId))

    val c = URL("$baseUrl/api/validate-listing").openConnection() as HttpURLConnection
    c.requestMethod = "POST"
    c.setRequestProperty("Content-Type", "application/json")
    c.doOutput = true
    c.connectTimeout = 30000
    c.readTimeout = 60000
    c.outputStream.use { it.write(obj.toString().toByteArray()) }
    return try {
        val body=(if(c.responseCode in 200..299)c.inputStream else c.errorStream)
            ?.bufferedReader()?.readText().orEmpty()
        if(c.responseCode !in 200..299) throw RuntimeException("Backend ${c.responseCode}: $body")
        JSONObject(body)
    } finally { c.disconnect() }
}

    fun publish(baseUrl: String, listings: List<Listing>): List<PublishResult> {
        val arr = JSONArray()
        listings.forEach { x ->
            val photos = JSONArray()
            x.photos.forEach { fileName ->
                val f = File(x.folder, fileName)
                photos.put(JSONObject()
                    .put("name", fileName)
                    .put("base64", Base64.getEncoder().encodeToString(f.readBytes())))
            }

            val specifics = JSONObject()
            x.itemSpecifics.forEach { (name, values) ->
                val a = JSONArray(); values.forEach { a.put(it) }; specifics.put(name, a)
            }

            val obj = JSONObject()
                .put("sku", x.sku).put("title", x.title).put("description", x.description)
                .put("category_id", x.categoryId).put("condition", x.condition)
                .put("condition_description", x.conditionDescription)
                .put("price", x.price ?: 0.0).put("quantity", x.quantity)
                .put("payment_policy_id", x.paymentPolicyId)
                .put("return_policy_id", x.returnPolicyId)
                .put("inventory_location_key", x.inventoryLocationKey)
                .put("item_specifics", specifics)
                .put("shipping", JSONObject().put("fulfillment_policy_id", x.fulfillmentPolicyId))
                .put("photos_data", photos)
            arr.put(obj)
        }

        val c = URL("$baseUrl/api/publish-batch").openConnection() as HttpURLConnection
        c.requestMethod="POST"; c.setRequestProperty("Content-Type","application/json")
        c.doOutput=true; c.connectTimeout=60000; c.readTimeout=180000
        c.outputStream.use { it.write(JSONObject().put("items",arr).toString().toByteArray()) }

        return try {
            val body=(if(c.responseCode in 200..299)c.inputStream else c.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            if(c.responseCode !in 200..299) throw RuntimeException("Backend ${c.responseCode}: $body")
            val results=JSONObject(body).optJSONArray("results")?:JSONArray()
            val out=mutableListOf<PublishResult>()
            for(i in 0 until results.length()){
                val r=results.getJSONObject(i)
                out+=PublishResult(
                    r.optString("sku"),r.optBoolean("ok"),
                    r.optString("listingId").takeIf{it.isNotBlank()},
                    r.optString("error").takeIf{it.isNotBlank()}
                )
            }
            out
        } finally { c.disconnect() }
    }
}
