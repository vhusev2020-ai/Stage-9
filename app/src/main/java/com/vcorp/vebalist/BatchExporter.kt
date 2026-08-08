package com.vcorp.vebalist

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object BatchExporter {
    fun write(context: Context, uri: Uri, listings: List<Listing>) {
        val folders = listings.mapIndexed { index, _ -> "listing_${(index + 1).toString().padStart(3,'0')}" }
        val batch = JSONObject().put("listings", JSONArray().apply {
            folders.forEach { put(JSONObject().put("folder",it)) }
        })
        context.contentResolver.openOutputStream(uri,"w").use { output ->
            requireNotNull(output) { "Unable to create destination file." }
            ZipOutputStream(output).use { zip ->
                putText(zip,"batch.json",batch.toString(2))
                listings.forEachIndexed { index, listing ->
                    val folder=folders[index]
                    putText(zip,"$folder/listing.json",listingJson(listing).toString(2))
                    listing.photos.forEach { name ->
                        val source=File(listing.folder,name)
                        if(source.isFile){
                            zip.putNextEntry(ZipEntry("$folder/$name"))
                            source.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }
        }
    }

    private fun putText(zip: ZipOutputStream, name: String, value: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(value.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun listingJson(x: Listing): JSONObject {
        val specifics=JSONObject()
        x.itemSpecifics.forEach { (name,values) -> specifics.put(name,JSONArray(values)) }
        val shipping=JSONObject()
            .put("weight_pounds",x.weightPounds ?: JSONObject.NULL)
            .put("weight_ounces",x.weightOunces ?: JSONObject.NULL)
            .put("package_length",x.packageLength ?: JSONObject.NULL)
            .put("package_width",x.packageWidth ?: JSONObject.NULL)
            .put("package_height",x.packageHeight ?: JSONObject.NULL)
            .put("package_type",x.packageType)
            .put("fulfillment_policy_id",x.fulfillmentPolicyId)
        return JSONObject()
            .put("sku",x.sku).put("title",x.title).put("description",x.description)
            .put("category_id",x.categoryId).put("condition",x.condition)
            .put("condition_description",x.conditionDescription).put("price",x.price ?: JSONObject.NULL)
            .put("quantity",x.quantity).put("item_specifics",specifics).put("shipping",shipping)
            .put("payment_policy_id",x.paymentPolicyId).put("return_policy_id",x.returnPolicyId)
            .put("inventory_location_key",x.inventoryLocationKey).put("photos",JSONArray(x.photos))
    }
}
