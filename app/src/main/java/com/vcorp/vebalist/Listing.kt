package com.vcorp.vebalist

data class Listing(
    val folder: String,
    var sku: String,
    var title: String,
    var description: String,
    var categoryId: String,
    var condition: String,
    var conditionDescription: String,
    var price: Double?,
    var quantity: Int,
    var paymentPolicyId: String,
    var returnPolicyId: String,
    var fulfillmentPolicyId: String,
    var inventoryLocationKey: String,
    val photos: List<String>,
    val itemSpecifics: MutableMap<String, MutableList<String>> = mutableMapOf(),
    var selected: Boolean = true,
    val errors: MutableList<String> = mutableListOf(),
    var publishState: PublishState = PublishState.NOT_PUBLISHED,
    var listingId: String? = null,
    var publishError: String? = null
) {
    val ready: Boolean get() = errors.isEmpty()
}

enum class PublishState {
    NOT_PUBLISHED, PUBLISHING, PUBLISHED, FAILED
}

data class EbayPolicy(val id: String, val name: String)
data class EbayLocation(val key: String, val name: String)
data class EbayAspect(
    val name: String,
    val required: Boolean,
    val mode: String,
    val values: List<String>
)
