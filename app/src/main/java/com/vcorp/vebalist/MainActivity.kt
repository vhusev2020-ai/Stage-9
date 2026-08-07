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
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private var listings = mutableListOf<Listing>()
    private var payments = listOf<EbayPolicy>()
    private var returns = listOf<EbayPolicy>()
    private var fulfillment = listOf<EbayPolicy>()
    private var locations = listOf<EbayLocation>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater); setContentView(b.root)
        b.backendUrl.setText(AppPrefs.getBackendUrl(this))

        b.saveBackendButton.setOnClickListener { testBackend() }
        b.loadEbaySetupButton.setOnClickListener { loadEbaySetup() }
        b.applyDefaultsButton.setOnClickListener { applyDefaults() }
        b.validateAspectsButton.setOnClickListener { validateAllAspects() }
        b.testListingButton.setOnClickListener { testFirstReadyListing() }
        b.fillMissingButton.setOnClickListener { fillMissingAspects() }
        b.historyButton.setOnClickListener { showHistory() }

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

    private fun testBackend() {
        val url=baseUrl()
        if(!url.startsWith("https://")) { toast("Backend URL must start with https://"); return }
        AppPrefs.setBackendUrl(this,url); b.statusText.text="Testing backend…"
        thread {
            val r=BackendClient.ping(url)
            runOnUiThread { b.statusText.text=if(r.first)"✓ Backend connected" else "Backend error: ${r.second}" }
        }
    }

    private fun loadEbaySetup() {
        val url=baseUrl()
        if(!url.startsWith("https://")) { toast("Configure backend first."); return }
        b.statusText.text="Loading eBay policies and locations…"
        thread {
            try {
                val j=BackendClient.loadAccountSetup(url)
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
            } catch(e:Exception){ runOnUiThread{b.statusText.text="eBay setup error: ${e.message}"} }
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
        if(listings.isEmpty()){toast("Import a batch first.");return}
        b.statusText.text="Checking category-specific eBay fields…"
        thread {
            listings.forEach { x ->
                try {
                    val aspects=BackendClient.loadAspects(url,x.categoryId)
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
        if(requestCode!=1001||resultCode!=Activity.RESULT_OK)return
        try{listings=BatchImporter.importZip(this,data?.data?:return).toMutableList();show()}
        catch(e:Exception){b.statusText.text="Import failed: ${e.message}"}
    }

    private fun editListing(pos:Int){
        val x=listings[pos]
        val v=LayoutInflater.from(this).inflate(R.layout.dialog_listing_editor,null)
        fun e(id:Int)=v.findViewById<EditText>(id)
        e(R.id.editTitle).setText(x.title);e(R.id.editPrice).setText(x.price?.toString().orEmpty())
        e(R.id.editCategory).setText(x.categoryId);e(R.id.editCondition).setText(x.condition)
        e(R.id.editDescription).setText(x.description);e(R.id.editPayment).setText(x.paymentPolicyId)
        e(R.id.editReturn).setText(x.returnPolicyId);e(R.id.editFulfillment).setText(x.fulfillmentPolicyId)
        e(R.id.editLocation).setText(x.inventoryLocationKey)
        AlertDialog.Builder(this).setTitle("Edit Listing").setView(v)
            .setPositiveButton("Save"){_,_->
                x.title=e(R.id.editTitle).text.toString();x.price=e(R.id.editPrice).text.toString().toDoubleOrNull()
                x.categoryId=e(R.id.editCategory).text.toString();x.condition=e(R.id.editCondition).text.toString()
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

        b.statusText.text = "Testing first ready listing against eBay requirements…"
        thread {
            try {
                val result = BackendClient.validateOnly(url, x)
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
        thread {
            try {
                val aspects = BackendClient.loadAspects(url, target.categoryId)
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
        items.forEach{it.publishState=PublishState.PUBLISHING};show()
        thread{
            try{
                BackendClient.publish(url,items).forEach{r->
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
            }catch(e:Exception){items.forEach{it.publishState=PublishState.FAILED;it.publishError=e.message}}
            runOnUiThread{show()}
        }
    }

    private fun show(){
        val published=listings.count{it.publishState==PublishState.PUBLISHED}
        val failed=listings.count{it.publishState==PublishState.FAILED}
        val ready=listings.count{it.ready}
        b.statusText.text="${listings.size} imported • $ready ready • $published published • $failed failed"
        b.publishReadyButton.isEnabled=listings.any{it.selected&&it.ready&&it.publishState!=PublishState.PUBLISHED}
        b.retryFailedButton.isEnabled=failed>0
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
}
