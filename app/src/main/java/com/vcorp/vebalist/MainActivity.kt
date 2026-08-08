package com.vcorp.vebalist

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.vcorp.vebalist.databinding.ActivityMainBinding
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private var listings = mutableListOf<Listing>()
    private var payments = listOf<EbayPolicy>()
    private var returns = listOf<EbayPolicy>()
    private var fulfillment = listOf<EbayPolicy>()
    private var locations = listOf<EbayLocation>()
    private val operationalErrors = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater); setContentView(b.root)
        b.backendUrl.setText(AppPrefs.getBackendUrl(this))
        b.backendApiKey.setText(AppPrefs.getApiKey(this))

        b.saveBackendButton.setOnClickListener { testBackend() }
        b.loadEbaySetupButton.setOnClickListener { loadEbaySetup() }
        b.applyDefaultsButton.setOnClickListener { applyDefaults() }
        b.validateAspectsButton.setOnClickListener { validateAllAspects() }
        b.testListingButton.setOnClickListener { testFirstReadyListing() }
        b.fillMissingButton.setOnClickListener { fillMissingAspects() }
        b.historyButton.setOnClickListener { showHistory() }
        b.newListingButton.setOnClickListener { createListing() }
        b.addPicturesButton.setOnClickListener { choosePictures() }
        b.exportBatchButton.setOnClickListener { saveBatch() }
        b.exportErrorLogButton.setOnClickListener { saveErrorLog() }

        b.importButton.setOnClickListener {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type="application/zip"
            },1001)
        }

        b.publishReadyButton.setOnClickListener {
            publishItems(listings.filter { it.selected && it.ready && it.publishState!=PublishState.PUBLISHED })
        }
        b.retryFailedButton.setOnClickListener {
            publishItems(listings.filter { it.publishState==PublishState.FAILED && it.ready })
        }
        b.listView.setOnItemClickListener { _,_,pos,_ -> listings[pos].selected=!listings[pos].selected; show() }
        b.listView.setOnItemLongClickListener { _,_,pos,_ -> editListing(pos); true }
    }

    private fun baseUrl(): String = b.backendUrl.text.toString().trim().trimEnd('/')
    private fun apiKey(): String = b.backendApiKey.text.toString().trim()

    private fun testBackend() {
        val url=baseUrl()
        if(!url.startsWith("https://")) { toast("Backend URL must start with https://"); return }
        val key=apiKey()
        if(key.isBlank()) { toast("Backend API key is required."); return }
        AppPrefs.setBackendUrl(this,url); AppPrefs.setApiKey(this,key); b.statusText.text="Testing backend…"
        thread {
            val r=BackendClient.ping(url,key)
            runOnUiThread {
                if (r.first) {
                    b.statusText.text="✓ Connected securely — now load your eBay setup"
                    b.stepProgress.progress=1
                } else {
                    recordError("Connection", r.second)
                    b.statusText.text="Could not connect. Export the error log if you need help."
                }
            }
        }
    }

    private fun loadEbaySetup() {
        val url=baseUrl()
        if(!url.startsWith("https://")) { toast("Configure backend first."); return }
        val key=apiKey()
        if(key.isBlank()) { toast("Backend API key is required."); return }
        b.statusText.text="Loading eBay policies and locations…"
        thread {
            try {
                val j=BackendClient.loadAccountSetup(url,key)
                payments=parsePolicies(j,"paymentPolicies","paymentPolicyId")
                returns=parsePolicies(j,"returnPolicies","returnPolicyId")
                fulfillment=parsePolicies(j,"fulfillmentPolicies","fulfillmentPolicyId")
                locations=parseLocations(j)
                runOnUiThread {
                    setSpinner(b.paymentSpinner,payments.map{"${it.name} — ${it.id}"})
                    setSpinner(b.returnSpinner,returns.map{"${it.name} — ${it.id}"})
                    setSpinner(b.fulfillmentSpinner,fulfillment.map{"${it.name} — ${it.id}"})
                    setSpinner(b.locationSpinner,locations.map{"${it.name} — ${it.key}"})
                    b.statusText.text="✓ eBay setup loaded"
                }
            } catch(e:Exception){ runOnUiThread{
                recordError("Load eBay setup", e.message)
                b.statusText.text="Could not load eBay setup. Export the error log if you need help."
            } }
        }
    }

    private fun parsePolicies(j:JSONObject,key:String,idKey:String):List<EbayPolicy>{
        val a=j.optJSONArray(key)?:return emptyList(); val out=mutableListOf<EbayPolicy>()
        for(i in 0 until a.length()){val x=a.getJSONObject(i); out+=EbayPolicy(x.optString(idKey),x.optString("name",x.optString(idKey)))}
        return out
    }
    private fun parseLocations(j:JSONObject):List<EbayLocation>{
        val a=j.optJSONArray("locations")?:return emptyList(); val out=mutableListOf<EbayLocation>()
        for(i in 0 until a.length()){val x=a.getJSONObject(i); out+=EbayLocation(x.optString("merchantLocationKey"),x.optString("name",x.optString("merchantLocationKey")))}
        return out
    }
    private fun setSpinner(s:Spinner,items:List<String>){s.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,items)}

    private fun applyDefaults() {
        if(listings.isEmpty()){toast("Import a batch first.");return}
        val p=payments.getOrNull(b.paymentSpinner.selectedItemPosition)
        val r=returns.getOrNull(b.returnSpinner.selectedItemPosition)
        val f=fulfillment.getOrNull(b.fulfillmentSpinner.selectedItemPosition)
        val l=locations.getOrNull(b.locationSpinner.selectedItemPosition)
        listings.forEach{
            if(it.paymentPolicyId.isBlank())it.paymentPolicyId=p?.id.orEmpty()
            if(it.returnPolicyId.isBlank())it.returnPolicyId=r?.id.orEmpty()
            if(it.fulfillmentPolicyId.isBlank())it.fulfillmentPolicyId=f?.id.orEmpty()
            if(it.inventoryLocationKey.isBlank())it.inventoryLocationKey=l?.key.orEmpty()
            BatchImporter.validateBase(it)
        }
        show(); toast("Defaults applied.")
    }

    private fun validateAllAspects() {
        val url=baseUrl()
        val key=apiKey()
        if(listings.isEmpty()){toast("Import a batch first.");return}
        if(!url.startsWith("https://")){toast("Configure backend first.");return}
        if(key.isBlank()){toast("Backend API key is required.");return}
        b.statusText.text="Checking category-specific eBay fields…"
        thread {
            listings.forEach { x ->
                try {
                    val aspects=BackendClient.loadAspects(url,key,x.categoryId)
                    val missing=aspects.filter{it.required && x.itemSpecifics[it.name].isNullOrEmpty()}
                    x.errors.removeAll{it.startsWith("Missing eBay aspect:")}
                    missing.forEach{x.errors+="Missing eBay aspect: ${it.name}"}
                } catch(e:Exception){ x.errors+="Aspect check failed: ${e.message}" }
            }
            runOnUiThread { show(); b.statusText.text="Category field validation complete." }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==1002 && resultCode==Activity.RESULT_OK){
            addSelectedPictures(data)
            return
        }
        if(requestCode==1003 && resultCode==Activity.RESULT_OK){
            try {
                BatchExporter.write(this,data?.data?:return,listings)
                toast("Batch ZIP saved. No API call was used.")
            } catch(e:Exception){ recordError("Save batch", e.message); toast("Unable to save batch: ${e.message}") }
            return
        }
        if(requestCode==1004 && resultCode==Activity.RESULT_OK){
            try {
                contentResolver.openOutputStream(data?.data?:return,"w")?.bufferedWriter()?.use {
                    it.write(buildErrorLog())
                }
                toast("Error log saved.")
            } catch(e:Exception){ toast("Unable to save error log: ${e.message}") }
            return
        }
        if(requestCode!=1001||resultCode!=Activity.RESULT_OK)return
        try{listings=BatchImporter.importZip(this,data?.data?:return).toMutableList();show()}
        catch(e:Exception){recordError("Import batch", e.message);b.statusText.text="Import failed. Export the error log if you need help."}
    }

    private fun saveBatch() {
        if(listings.isEmpty()){toast("Create or import a listing first.");return}
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type="application/zip"
            putExtra(Intent.EXTRA_TITLE,"VEbalist-batch-${System.currentTimeMillis()}.zip")
        },1003)
    }

    private fun saveErrorLog() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type="text/plain"
            putExtra(Intent.EXTRA_TITLE,"VEbalist-error-log-${System.currentTimeMillis()}.txt")
        },1004)
    }

    private fun buildErrorLog(): String = buildString {
        appendLine("VEbalist error report")
        appendLine("Created: ${java.util.Date()}")
        appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        appendLine("Service: ${baseUrl().ifBlank { "Not configured" }}")
        appendLine("Listings: ${listings.size}")
        appendLine()
        appendLine("App events:")
        if (operationalErrors.isEmpty()) appendLine("No app-level errors recorded in this session.")
        operationalErrors.forEach { appendLine("- $it") }
        appendLine()
        if(listings.isEmpty()) appendLine("No listings have been imported.")
        listings.forEachIndexed { index,x ->
            appendLine("${index + 1}. ${x.title.ifBlank { "Untitled listing" }}")
            appendLine("   SKU: ${x.sku.ifBlank { "Not provided" }}")
            appendLine("   State: ${x.publishState}")
            if(x.errors.isEmpty() && x.publishError.isNullOrBlank()) appendLine("   No errors")
            x.errors.forEach { appendLine("   Validation: $it") }
            x.publishError?.takeIf { it.isNotBlank() }?.let { appendLine("   Publishing: $it") }
            appendLine()
        }
        appendLine("Security note: service keys and eBay credentials are intentionally excluded.")
    }

    private fun createListing() {
        val folder = File(cacheDir, "listing_" + System.currentTimeMillis()).apply { mkdirs() }
        val x = Listing(
            folder=folder.absolutePath, sku="", title="", description="", categoryId="",
            condition="", conditionDescription="", price=null, quantity=1,
            paymentPolicyId=payments.getOrNull(b.paymentSpinner.selectedItemPosition)?.id.orEmpty(),
            returnPolicyId=returns.getOrNull(b.returnSpinner.selectedItemPosition)?.id.orEmpty(),
            fulfillmentPolicyId=fulfillment.getOrNull(b.fulfillmentSpinner.selectedItemPosition)?.id.orEmpty(),
            inventoryLocationKey=locations.getOrNull(b.locationSpinner.selectedItemPosition)?.key.orEmpty(),
            weightPounds=null, weightOunces=null, packageLength=null, packageWidth=null,
            packageHeight=null, packageType="", photos=mutableListOf()
        )
        BatchImporter.validateBase(x)
        listings.add(0,x)
        show()
        editListing(0)
    }

    private fun choosePictures() {
        if(listings.none { it.selected }) { toast("Select a listing first."); return }
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type="image/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true)
        },1002)
    }

    private fun addSelectedPictures(data: Intent?) {
        val target=listings.firstOrNull { it.selected } ?: return
        val uris=mutableListOf<android.net.Uri>()
        data?.data?.let { uris += it }
        data?.clipData?.let { clip -> for(i in 0 until clip.itemCount) uris += clip.getItemAt(i).uri }
        val folder=File(target.folder).apply { mkdirs() }
        uris.forEachIndexed { index, uri ->
            val mime=contentResolver.getType(uri).orEmpty()
            val ext=when { mime.contains("png")->"png"; mime.contains("webp")->"webp"; else->"jpg" }
            val name="photo_${System.currentTimeMillis()}_${index + 1}.$ext"
            contentResolver.openInputStream(uri)?.use { input -> File(folder,name).outputStream().use { input.copyTo(it) } }
            target.photos += name
        }
        BatchImporter.validateBase(target)
        show()
        toast("${uris.size} picture(s) added to ${target.title.ifBlank { "listing" }}.")
    }

    private fun editListing(pos:Int){
        val x=listings[pos]
        val v=LayoutInflater.from(this).inflate(R.layout.dialog_listing_editor,null)
        fun e(id:Int)=v.findViewById<EditText>(id)
        e(R.id.editTitle).setText(x.title);e(R.id.editPrice).setText(x.price?.toString().orEmpty())
        e(R.id.editCategory).setText(x.categoryId);e(R.id.editCondition).setText(x.condition)
        e(R.id.editDescription).setText(x.description);e(R.id.editPayment).setText(x.paymentPolicyId)
        e(R.id.editQuantity).setText(x.quantity.toString());e(R.id.editConditionDescription).setText(x.conditionDescription)
        e(R.id.editWeightPounds).setText(x.weightPounds?.toString().orEmpty());e(R.id.editWeightOunces).setText(x.weightOunces?.toString().orEmpty())
        e(R.id.editPackageLength).setText(x.packageLength?.toString().orEmpty());e(R.id.editPackageWidth).setText(x.packageWidth?.toString().orEmpty())
        e(R.id.editPackageHeight).setText(x.packageHeight?.toString().orEmpty());e(R.id.editPackageType).setText(x.packageType)
        e(R.id.editReturn).setText(x.returnPolicyId);e(R.id.editFulfillment).setText(x.fulfillmentPolicyId)
        e(R.id.editLocation).setText(x.inventoryLocationKey)
        AlertDialog.Builder(this).setTitle("Edit Listing").setView(v)
            .setPositiveButton("Save"){_,_->
                x.title=e(R.id.editTitle).text.toString();x.price=e(R.id.editPrice).text.toString().toDoubleOrNull()
                x.categoryId=e(R.id.editCategory).text.toString();x.condition=e(R.id.editCondition).text.toString()
                x.quantity=e(R.id.editQuantity).text.toString().toIntOrNull() ?: 0;x.conditionDescription=e(R.id.editConditionDescription).text.toString()
                x.weightPounds=e(R.id.editWeightPounds).text.toString().toDoubleOrNull();x.weightOunces=e(R.id.editWeightOunces).text.toString().toDoubleOrNull()
                x.packageLength=e(R.id.editPackageLength).text.toString().toDoubleOrNull();x.packageWidth=e(R.id.editPackageWidth).text.toString().toDoubleOrNull()
                x.packageHeight=e(R.id.editPackageHeight).text.toString().toDoubleOrNull();x.packageType=e(R.id.editPackageType).text.toString().trim()
                x.description=e(R.id.editDescription).text.toString();x.paymentPolicyId=e(R.id.editPayment).text.toString()
                x.returnPolicyId=e(R.id.editReturn).text.toString();x.fulfillmentPolicyId=e(R.id.editFulfillment).text.toString()
                x.inventoryLocationKey=e(R.id.editLocation).text.toString();BatchImporter.validateBase(x);show()
            }.setNegativeButton("Cancel",null).show()
    }



    private fun testFirstReadyListing() {
        val x = listings.firstOrNull { it.ready && it.publishState != PublishState.PUBLISHED }
            ?: run { toast("No ready listing available for test."); return }
        val url = baseUrl()
        if (!url.startsWith("https://")) { toast("Configure backend first."); return }
        val key = apiKey()
        if (key.isBlank()) { toast("Backend API key is required."); return }

        b.statusText.text = "Testing first ready listing against eBay requirements…"
        thread {
            try {
                val result = BackendClient.validateOnly(url, key, x)
                runOnUiThread {
                    if (result.optBoolean("ok")) {
                        b.statusText.text = "✓ Test passed for ${x.title}"
                    } else {
                        b.statusText.text = "Test failed: ${result.optString("error")}"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { b.statusText.text = "Test error: ${e.message}" }
            }
        }
    }

    private fun fillMissingAspects() {
        val target = listings.firstOrNull { x ->
            x.errors.any { it.startsWith("Missing eBay aspect:") }
        } ?: run {
            toast("No missing required eBay fields.")
            return
        }

        val url = baseUrl()
        val key = apiKey()
        if (key.isBlank()) { runOnUiThread { toast("Backend API key is required.") }; return }
        thread {
            try {
                val aspects = BackendClient.loadAspects(url, key, target.categoryId)
                val missing = aspects.filter { it.required && target.itemSpecifics[it.name].isNullOrEmpty() }
                runOnUiThread {
                    if (missing.isEmpty()) {
                        toast("No missing required fields for ${target.title}")
                        return@runOnUiThread
                    }

                    val v = LayoutInflater.from(this).inflate(R.layout.dialog_aspect_editor, null)
                    val container = v.findViewById<LinearLayout>(R.id.aspectEditorContainer)
                    val inputs = mutableMapOf<String, EditText>()

                    missing.forEach { aspect ->
                        val label = TextView(this).apply {
                            text = aspect.name + if (aspect.required) " *" else ""
                            textSize = 15f
                        }
                        val edit = EditText(this).apply {
                            hint = if (aspect.values.isNotEmpty())
                                "Examples: " + aspect.values.take(5).joinToString(", ")
                            else "Enter ${aspect.name}"
                        }
                        container.addView(label)
                        container.addView(edit)
                        inputs[aspect.name] = edit
                    }

                    AlertDialog.Builder(this)
                        .setTitle("Required eBay Fields")
                        .setView(v)
                        .setPositiveButton("Save") { _, _ ->
                            inputs.forEach { (name, edit) ->
                                val value = edit.text.toString().trim()
                                if (value.isNotBlank()) {
                                    target.itemSpecifics[name] = mutableListOf(value)
                                }
                            }
                            BatchImporter.validateBase(target)
                            target.errors.removeAll { it.startsWith("Missing eBay aspect:") }
                            missing.filter { target.itemSpecifics[it.name].isNullOrEmpty() }
                                .forEach { target.errors += "Missing eBay aspect: ${it.name}" }
                            show()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            } catch (e: Exception) {
                runOnUiThread { toast("Unable to load eBay fields: ${e.message}") }
            }
        }
    }

    private fun showHistory() {
        val history = HistoryStore.list(this)
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_history, null)
        val list = v.findViewById<ListView>(R.id.historyList)
        val rows = if (history.isEmpty()) listOf("No publishing history yet.") else history.map { h ->
            val state = if (h.success) "PUBLISHED #${h.listingId ?: ""}" else "FAILED"
            "${h.title}\n$state • SKU ${h.sku}" + (h.error?.let { "\n$it" } ?: "")
        }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, rows)
        AlertDialog.Builder(this)
            .setTitle("Published History")
            .setView(v)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun publishItems(items:List<Listing>){
        if(items.isEmpty()){toast("No ready listings selected.");return}
        val url=baseUrl(); if(!url.startsWith("https://")){toast("Configure backend first.");return}
        val key=apiKey(); if(key.isBlank()){toast("Backend API key is required.");return}
        items.forEach{it.publishState=PublishState.PUBLISHING};show()
        thread{
            try{
                BackendClient.publish(url,key,items).forEach{r->
                    listings.firstOrNull{it.sku==r.sku}?.let{x->
                        if(r.ok){
                            x.publishState=PublishState.PUBLISHED;x.listingId=r.listingId;x.publishError=null
                            HistoryStore.add(this, PublishHistoryItem(x.sku,x.title,r.listingId,System.currentTimeMillis(),true,null))
                        } else {
                            x.publishState=PublishState.FAILED;x.publishError=r.error
                            HistoryStore.add(this, PublishHistoryItem(x.sku,x.title,null,System.currentTimeMillis(),false,r.error))
                        }
                    }
                }
            }catch(e:Exception){
                items.forEach{it.publishState=PublishState.FAILED;it.publishError=e.message}
                runOnUiThread { recordError("Publish listings", e.message) }
            }
            runOnUiThread{show()}
        }
    }

    private fun show(){
        val published=listings.count{it.publishState==PublishState.PUBLISHED}
        val failed=listings.count{it.publishState==PublishState.FAILED}
        val ready=listings.count{it.ready}
        val step=when {
            listings.isEmpty()->2
            listings.any{!it.ready}->3
            published < listings.size->4
            else->4
        }
        b.statusText.text="Step $step of 4 — ${listings.size} listings • $ready ready • $published published • $failed failed"
        b.stepProgress.progress=step
        b.publishReadyButton.isEnabled=listings.any{it.selected&&it.ready&&it.publishState!=PublishState.PUBLISHED}
        b.retryFailedButton.isEnabled=failed>0
        b.errorSummary.text=when {
            failed>0 -> "$failed listing(s) failed. Export the error log for a safe support report, then retry."
            operationalErrors.isNotEmpty() -> "A problem was recorded. Export the error log for a safe support report."
            else -> "Need help? Export a safe diagnostic report. Passwords and keys are never included."
        }
        val rows=listings.mapIndexed{i,x->
            val mark=if(x.selected)"☑" else "☐"
            val state=when(x.publishState){
                PublishState.NOT_PUBLISHED->if(x.ready)"READY" else "REVIEW"
                PublishState.PUBLISHING->"PUBLISHING"
                PublishState.PUBLISHED->"PUBLISHED #${x.listingId?:""}"
                PublishState.FAILED->"FAILED: ${x.publishError?:""}"
            }
            val info=if(x.ready)"$${x.price?:0.0} • SKU ${x.sku}" else x.errors.joinToString("; ")
            "$mark ${i+1}. ${x.title.ifBlank{"Untitled"}}\n$state • $info"
        }
        b.listView.adapter=ArrayAdapter(this,android.R.layout.simple_list_item_1,rows)
    }

    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_LONG).show()

    private fun recordError(action:String, message:String?) {
        val safeMessage=message?.take(1000)?.ifBlank { "Unknown error" } ?: "Unknown error"
        operationalErrors += "${java.util.Date()}: $action — $safeMessage"
        if (::b.isInitialized) {
            b.errorSummary.text="A problem was recorded. Tap Export error log for a safe support report."
        }
    }
}
