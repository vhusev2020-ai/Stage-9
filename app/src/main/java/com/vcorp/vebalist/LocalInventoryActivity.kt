package com.vcorp.vebalist

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

data class LocalInventoryItem(
    val sku: String,
    val title: String,
    val condition: String,
    val cost: Double?,
    val location: String,
    val notes: String,
    val createdAt: Long
)

object LocalInventoryStore {
    private const val PREFS = "vebalist_local_inventory"
    private const val ITEMS = "items"

    fun all(context: android.content.Context): List<LocalInventoryItem> {
        val raw = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getString(ITEMS, "[]") ?: "[]"
        val array = JSONArray(raw)
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            LocalInventoryItem(
                o.optString("sku"), o.optString("title"), o.optString("condition"),
                if (o.isNull("cost")) null else o.optDouble("cost"),
                o.optString("location"), o.optString("notes"), o.optLong("createdAt")
            )
        }.sortedByDescending { it.createdAt }
    }

    fun add(context: android.content.Context, item: LocalInventoryItem) {
        val array = JSONArray()
        all(context).forEach { old ->
            array.put(JSONObject().put("sku", old.sku).put("title", old.title)
                .put("condition", old.condition).put("cost", old.cost ?: JSONObject.NULL)
                .put("location", old.location).put("notes", old.notes)
                .put("createdAt", old.createdAt))
        }
        array.put(JSONObject().put("sku", item.sku).put("title", item.title)
            .put("condition", item.condition).put("cost", item.cost ?: JSONObject.NULL)
            .put("location", item.location).put("notes", item.notes)
            .put("createdAt", item.createdAt))
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putString(ITEMS, array.toString()).apply()
    }
}

class LocalInventoryActivity : AppCompatActivity() {
    private lateinit var list: ListView
    private lateinit var count: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        root.addView(TextView(this).apply {
            text = "VEbalist • Local Inventory"
            textSize = 23f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        count = TextView(this).apply { textSize = 15f }
        root.addView(count)
        root.addView(Button(this).apply {
            text = "＋ Add item to inventory"
            setOnClickListener { showAddDialog() }
        })
        list = ListView(this)
        root.addView(list, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(Button(this).apply {
            text = "Back"
            setOnClickListener { finish() }
        })
        setContentView(root)
        refresh()
    }

    private fun showAddDialog() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 8, 28, 0)
        }
        fun field(hintText: String, type: Int = android.text.InputType.TYPE_CLASS_TEXT): EditText {
            val input = EditText(this).apply {
                hint = hintText
                inputType = type
                singleLine = hintText != "Notes / identifying details"
            }
            form.addView(input)
            return input
        }
        val title = field("Item name / title")
        val condition = field("Condition (e.g. new, good, fair)")
        val cost = field("Purchase cost ($)", android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val location = field("Storage location (e.g. shelf A)")
        val notes = field("Notes / identifying details")
        AlertDialog.Builder(this)
            .setTitle("Add inventory item")
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save item", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = title.text.toString().trim()
                        if (name.isBlank()) {
                            title.error = "Enter an item name"
                            return@setOnClickListener
                        }
                        val sku = "V-" + System.currentTimeMillis().toString().takeLast(9)
                        LocalInventoryStore.add(this, LocalInventoryItem(
                            sku, name, condition.text.toString().trim(),
                            cost.text.toString().toDoubleOrNull(), location.text.toString().trim(),
                            notes.text.toString().trim(), System.currentTimeMillis()
                        ))
                        dialog.dismiss()
                        refresh()
                    }
                }
                dialog.show()
            }
    }

    private fun refresh() {
        val items = LocalInventoryStore.all(this)
        count.text = "${items.size} items saved on this device"
        val rows = if (items.isEmpty()) listOf("No inventory yet. Add your first item above.")
        else items.map { item ->
            "${item.title}\nSKU ${item.sku} • ${item.condition.ifBlank { "Condition not set" }}\nCost: ${item.cost?.let { "$" + String.format("%.2f", it) } ?: "not entered"} • ${item.location.ifBlank { "Location not set" }}${if (item.notes.isBlank()) "" else "\n${item.notes}"}"
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
    }
}
