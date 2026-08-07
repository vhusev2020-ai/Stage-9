package com.vcorp.vebalist

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

object BatchImporter {
    fun importZip(context: Context, uri: Uri): List<Listing> {
        val work = File(context.cacheDir, "batch_" + System.currentTimeMillis())
        work.mkdirs()

        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected file." }
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val out = File(work, entry.name)
                    val root = work.canonicalPath + File.separator
                    if (!out.canonicalPath.startsWith(root)) {
                        throw SecurityException("Unsafe ZIP path: ${entry.name}")
                    }
                    if (entry.isDirectory) out.mkdirs()
                    else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        val batchFile = File(work, "batch.json")
        require(batchFile.exists()) { "batch.json not found in ZIP." }
        val batch = JSONObject(batchFile.readText())
        val items = batch.optJSONArray("listings")
            ?: throw IllegalArgumentException("No listings in batch.json")
        val result = mutableListOf<Listing>()

        for (i in 0 until items.length()) {
            val folderName = items.getJSONObject(i).optString("folder")
            val folder = File(work, folderName)
            val listingFile = File(folder, "listing.json")
            if (!listingFile.exists()) continue
            val j = JSONObject(listingFile.readText())
            val shipping = j.optJSONObject("shipping") ?: JSONObject()

            val photos = mutableListOf<String>()
            j.optJSONArray("photos")?.let { a ->
                for (p in 0 until a.length()) photos += a.getString(p)
            }

            val specifics = mutableMapOf<String, MutableList<String>>()
            j.optJSONObject("item_specifics")?.let { obj ->
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val v = obj.get(key)
                    val vals = mutableListOf<String>()
                    when (v) {
                        is org.json.JSONArray -> for (k in 0 until v.length()) vals += v.getString(k)
                        else -> vals += v.toString()
                    }
                    specifics[key] = vals
                }
            }

            val x = Listing(
                folder = folder.absolutePath,
                sku = j.optString("sku"),
                title = j.optString("title"),
                description = j.optString("description"),
                categoryId = j.optString("category_id"),
                condition = j.optString("condition"),
                conditionDescription = j.optString("condition_description"),
                price = if (j.has("price") && !j.isNull("price")) j.optDouble("price") else null,
                quantity = j.optInt("quantity", 1),
                paymentPolicyId = j.optString("payment_policy_id"),
                returnPolicyId = j.optString("return_policy_id"),
                fulfillmentPolicyId = shipping.optString("fulfillment_policy_id"),
                inventoryLocationKey = j.optString("inventory_location_key"),
                photos = photos,
                itemSpecifics = specifics
            )
            validateBase(x)
            result += x
        }
        return result
    }

    fun validateBase(x: Listing) {
        x.errors.clear()
        fun req(name: String, value: String) {
            if (value.isBlank()) x.errors += "Missing $name"
        }
        req("SKU", x.sku); req("title", x.title); req("description", x.description)
        req("category", x.categoryId); req("condition", x.condition)
        req("payment policy", x.paymentPolicyId); req("return policy", x.returnPolicyId)
        req("fulfillment policy", x.fulfillmentPolicyId); req("inventory location", x.inventoryLocationKey)
        if (x.title.length > 80) x.errors += "Title exceeds 80 characters"
        if (x.price == null || x.price!! <= 0.0) x.errors += "Invalid price"
        if (x.quantity <= 0) x.errors += "Invalid quantity"
        if (x.photos.isEmpty()) x.errors += "No photos"
        x.photos.forEach { if (!File(x.folder, it).exists()) x.errors += "Missing photo: $it" }
    }
}
