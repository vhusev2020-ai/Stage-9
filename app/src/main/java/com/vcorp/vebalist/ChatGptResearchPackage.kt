package com.vcorp.vebalist

import android.content.Context
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ChatGptResearchPackage {
    fun create(context: Context, listings: List<Listing>, batchId: String): android.net.Uri {
        val shareDir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(shareDir, "VEbalist-Research-$batchId.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            val manifest = JSONObject()
                .put("package_type", "vebalist_research_request")
                .put("batch_version", BatchContract.CURRENT_VERSION)
                .put("workflow_version", WorkflowStore.CURRENT_WORKFLOW_VERSION)
                .put("batch_id", batchId)
                .put("marketplace", BatchContract.DEFAULT_MARKETPLACE)
                .put("listings", JSONArray().apply {
                    listings.forEachIndexed { index, _ ->
                        put(JSONObject().put("folder", "listing_${(index + 1).toString().padStart(3, '0')}"))
                    }
                })
            putText(zip, "batch.json", manifest.toString(2))
            putText(zip, "CHATGPT_INSTRUCTIONS.txt", instructions(batchId))
            listings.forEachIndexed { index, listing ->
                val folder = "listing_${(index + 1).toString().padStart(3, '0')}"
                putText(zip, "$folder/known-details.json", knownDetails(listing).toString(2))
                listing.photos.forEach { name ->
                    val source = File(listing.folder, name)
                    if (source.isFile) {
                        zip.putNextEntry(ZipEntry("$folder/photos/$name"))
                        source.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    private fun knownDetails(x: Listing) = JSONObject()
        .put("sku", x.sku)
        .put("title", x.title)
        .put("description", x.description)
        .put("category_id", x.categoryId)
        .put("condition", x.condition)
        .put("condition_description", x.conditionDescription)
        .put("price", x.price ?: JSONObject.NULL)
        .put("quantity", x.quantity)
        .put("item_specifics", JSONObject(x.itemSpecifics as Map<*, *>))
        .put("shipping", JSONObject()
            .put("weight_pounds", x.weightPounds ?: JSONObject.NULL)
            .put("weight_ounces", x.weightOunces ?: JSONObject.NULL)
            .put("package_length", x.packageLength ?: JSONObject.NULL)
            .put("package_width", x.packageWidth ?: JSONObject.NULL)
            .put("package_height", x.packageHeight ?: JSONObject.NULL)
            .put("package_type", x.packageType))

    private fun instructions(batchId: String) = """
        VEbalist fixed workflow ${WorkflowStore.CURRENT_WORKFLOW_VERSION}; batch $batchId.

        Create a completed VEbalist batch ZIP. For each product:
        1. Identify only facts supported by photos, labels, barcodes, authoritative sources, or credible comparable listings.
        1a. Decide the most accurate eBay condition from the full photo set. Write a specific condition description naming visible wear, packaging damage, stains, scratches, missing pieces, or other defects. Include condition confidence (high, medium, or low) and explain limitations. Never claim tested functionality, hidden condition, completeness, or authenticity unless evidence supports it. Low-confidence condition must produce a user-review warning rather than an invented fact.
        2. Research exact and close eBay comparables. Prefer sold evidence; separate sold from active asking prices.
        3. Produce an accurate title of at most 80 characters. Never use unrelated keywords or brands.
        4. Recommend the most accurate category and complete required/recommended item specifics.
        5. Recommend fast, balanced, and maximum-return prices; use balanced unless the supplied details request otherwise.
        6. Research product weight and dimensions. Estimate packed weight, packaging type, and box dimensions using manufacturer data, comparable listings, and reasonable packing allowance. Clearly mark estimates and confidence. Fragile, unusual, or low-confidence packages must require physical confirmation.
        7. Write a concise factual description including condition, defects, measurements, included items, and shipping notes.
        8. Check restricted-product and misleading-listing risks. Do not approve publication when identity, condition, authenticity, or a required safety fact is uncertain.
        9. Preserve original photos and their order. Do not generate substitute evidence photos.

        Return VEbalist-Ready-$batchId.zip using the existing batch.json + listing_NNN/listing.json contract.
        Include workflow_version, batch_id, market research sources, price range, condition confidence, shipping confidence, policy warnings, and publish_allowed.
        Do not include or request passwords, ChatGPT cookies, API keys, or eBay credentials.
    """.trimIndent()

    private fun putText(zip: ZipOutputStream, name: String, value: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(value.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
