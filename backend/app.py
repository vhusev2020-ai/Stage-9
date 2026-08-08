import os, time, base64, tempfile, requests, hmac
from urllib.parse import quote
from flask import Flask, request, jsonify

app=Flask(__name__)
API="https://api.ebay.com"
MEDIA_API="https://apim.ebay.com"
TOKEN_URL="https://api.ebay.com/identity/v1/oauth2/token"
MARKETPLACE=os.getenv("EBAY_MARKETPLACE_ID","EBAY_US")
LOCATION_KEY=os.getenv("EBAY_LOCATION_KEY","vebalist-40517")
LOCATION_POSTAL_CODE=os.getenv("EBAY_LOCATION_POSTAL_CODE","40517")
LOCATION_COUNTRY=os.getenv("EBAY_LOCATION_COUNTRY","US")
_cache={"token":None,"expires":0}

@app.get("/healthz")
def healthz():
    return jsonify(ok=True,service="vebalist-backend")

@app.before_request
def require_app_key():
    if request.path == "/healthz":
        return None
    expected=os.getenv("VEBALIST_API_KEY","")
    supplied=request.headers.get("X-VEbalist-Key","")
    if not expected:
        return jsonify(ok=False,error="Server API key is not configured"),503
    if not hmac.compare_digest(supplied,expected):
        return jsonify(ok=False,error="Unauthorized"),401
    return None

def access_token():
    now=time.time()
    if _cache["token"] and now < _cache["expires"]-120:
        return _cache["token"]

    client_id=os.getenv("EBAY_CLIENT_ID")
    client_secret=os.getenv("EBAY_CLIENT_SECRET")
    refresh=os.getenv("EBAY_REFRESH_TOKEN")
    scopes=os.getenv("EBAY_REFRESH_SCOPES","").strip()
    if not all([client_id,client_secret,refresh]):
        fallback=os.getenv("EBAY_USER_ACCESS_TOKEN")
        if fallback: return fallback
        raise RuntimeError("Configure EBAY_CLIENT_ID, EBAY_CLIENT_SECRET, EBAY_REFRESH_TOKEN and EBAY_REFRESH_SCOPES")

    basic=base64.b64encode(f"{client_id}:{client_secret}".encode()).decode()
    r=requests.post(TOKEN_URL,
        headers={"Authorization":f"Basic {basic}","Content-Type":"application/x-www-form-urlencoded"},
        data={"grant_type":"refresh_token","refresh_token":refresh},
        timeout=30)
    if r.status_code!=200:
        raise RuntimeError(f"OAuth refresh {r.status_code}: {r.text[:800]}")
    j=r.json()
    _cache["token"]=j["access_token"]
    _cache["expires"]=now+int(j.get("expires_in",7200))
    return _cache["token"]

def h(content_type="application/json"):
    return {"Authorization":f"Bearer {access_token()}","Content-Type":content_type,"Accept-Language":"en-US","Content-Language":"en-US"}

def ebay_get(path,params=None):
    r=requests.get(API+path,headers=h(),params=params,timeout=40)
    if r.status_code!=200: raise RuntimeError(f"eBay GET {r.status_code}: {r.text[:800]}")
    return r.json()

def ensure_inventory_location(locations):
    if locations:
        return locations
    payload={
        "location":{"address":{
            "postalCode":LOCATION_POSTAL_CODE,
            "country":LOCATION_COUNTRY}},
        "name":"VEbalist Ship From",
        "merchantLocationStatus":"ENABLED",
        "locationTypes":["WAREHOUSE"]}
    key=quote(LOCATION_KEY,safe="")
    r=requests.post(API+f"/sell/inventory/v1/location/{key}",headers=h(),json=payload,timeout=40)
    if r.status_code not in (200,201,204,409):
        raise RuntimeError(f"Create inventory location {r.status_code}: {r.text[:800]}")
    refreshed=ebay_get("/sell/inventory/v1/location",{"limit":100})
    locations=refreshed.get("locations",[])
    if not locations:
        raise RuntimeError("eBay inventory location was not created")
    return locations

@app.get("/api/status")
def status():
    try:
        access_token()
        return jsonify(ok=True,marketplace=MARKETPLACE,oauth="ready")
    except Exception as e:
        return jsonify(ok=False,error=str(e)),500

@app.get("/api/ebay/account-setup")
def account_setup():
    try:
        p=ebay_get("/sell/account/v1/payment_policy",{"marketplace_id":MARKETPLACE})
        r=ebay_get("/sell/account/v1/return_policy",{"marketplace_id":MARKETPLACE})
        f=ebay_get("/sell/account/v1/fulfillment_policy",{"marketplace_id":MARKETPLACE})
        l=ebay_get("/sell/inventory/v1/location",{"limit":100})
        locations=ensure_inventory_location(l.get("locations",[]))
        return jsonify(ok=True,
            paymentPolicies=p.get("paymentPolicies",[]),
            returnPolicies=r.get("returnPolicies",[]),
            fulfillmentPolicies=f.get("fulfillmentPolicies",[]),
            locations=locations)
    except Exception as e:
        return jsonify(ok=False,error=str(e)),400

@app.get("/api/ebay/aspects")
def aspects():
    category_id=request.args.get("category_id","")
    if not category_id: return jsonify(error="category_id required"),400
    try:
        tree=ebay_get("/commerce/taxonomy/v1/get_default_category_tree_id",{"marketplace_id":MARKETPLACE})
        tree_id=tree["categoryTreeId"]
        meta=ebay_get(
            f"/commerce/taxonomy/v1/category_tree/{tree_id}/get_item_aspects_for_category",
            {"category_id":category_id})
        out=[]
        for a in meta.get("aspects",[]):
            c=a.get("aspectConstraint",{})
            vals=[x.get("localizedValue") for x in a.get("aspectValues",[]) if x.get("localizedValue")]
            out.append({
                "name":a.get("localizedAspectName"),
                "required":bool(c.get("aspectRequired",False)),
                "mode":"SELECTION_ONLY" if c.get("aspectMode")=="SELECTION_ONLY" else "FREE_TEXT",
                "cardinality":c.get("itemToAspectCardinality","SINGLE"),
                "values":vals
            })
        return jsonify(categoryTreeId=tree_id,aspects=out)
    except Exception as e:
        return jsonify(error=str(e)),400

def upload_image(photo):
    raw=base64.b64decode(photo["base64"])
    with tempfile.NamedTemporaryFile(delete=False,suffix=".jpg") as f:
        f.write(raw); path=f.name
    try:
        with open(path,"rb") as img:
            r=requests.post(MEDIA_API+"/commerce/media/v1_beta/image/create_image_from_file",
                headers={"Authorization":f"Bearer {access_token()}"},
                files={"image":(photo["name"],img,"image/jpeg")},timeout=90)
        if r.status_code not in (200,201):
            raise RuntimeError(f"Image upload {r.status_code}: {r.text[:600]}")
        j=r.json() if r.text else {}
        if not j.get("imageUrl"): raise RuntimeError("Image upload returned no imageUrl")
        return j["imageUrl"]
    finally:
        try: os.remove(path)
        except OSError: pass

def create_inventory(item,image_urls):
    shipping=item.get("shipping",{})
    pounds=float(shipping.get("weight_pounds") or 0)
    ounces=float(shipping.get("weight_ounces") or 0)
    condition=item["condition"]
    # Men's Athletic Shoes uses eBay's apparel condition mapping, where
    # USED_EXCELLENT (ID 3000) is displayed as "Pre-owned - Good". Older
    # VEbalist batches used USED_GOOD (ID 5000), which eBay rejects here.
    if str(item.get("category_id")) == "15709" and condition == "USED_GOOD":
        condition="USED_EXCELLENT"
    payload={
        "availability":{"shipToLocationAvailability":{"quantity":int(item.get("quantity",1))}},
        "condition":condition,
        "product":{
            "title":item["title"],
            "description":item["description"],
            "imageUrls":image_urls,
            "aspects":item.get("item_specifics",{})
        }
    }
    if shipping.get("package_length") and shipping.get("package_width") and shipping.get("package_height") and (pounds or ounces):
        package={
            "dimensions":{"height":float(shipping["package_height"]),"length":float(shipping["package_length"]),"width":float(shipping["package_width"]),"unit":"INCH"},
            "weight":{"value":pounds*16+ounces,"unit":"OUNCE"}}
        # Although MAILING_BOX exists in the Inventory API enum, eBay US can
        # reject it during publish as Trading value "MailingBoxes" (25101).
        # Package type is optional; dimensions and weight are sufficient.
        package_type=shipping.get("package_type")
        if package_type and package_type != "MAILING_BOX":
            package["packageType"]=package_type
        payload["packageWeightAndSize"]=package
    if item.get("condition_description"):payload["conditionDescription"]=item["condition_description"]
    r=requests.put(API+f"/sell/inventory/v1/inventory_item/{item['sku']}",headers=h(),json=payload,timeout=60)
    if r.status_code not in (200,201,204): raise RuntimeError(f"Inventory {r.status_code}: {r.text[:800]}")

def offer_payload(item):
    return {
        "sku":item["sku"],"marketplaceId":MARKETPLACE,"format":"FIXED_PRICE",
        "availableQuantity":int(item.get("quantity",1)),
        "categoryId":str(item["category_id"]),
        "merchantLocationKey":item["inventory_location_key"],
        "listingDescription":item["description"],"listingDuration":"GTC",
        "listingPolicies":{
            "fulfillmentPolicyId":item["shipping"]["fulfillment_policy_id"],
            "paymentPolicyId":item["payment_policy_id"],
            "returnPolicyId":item["return_policy_id"]},
        "pricingSummary":{"price":{"currency":"USD","value":str(item["price"])}}
    }

def find_offer(sku):
    r=requests.get(API+"/sell/inventory/v1/offer",headers=h(),params={"sku":sku,"limit":100},timeout=40)
    if r.status_code==404:
        return None
    if r.status_code!=200: raise RuntimeError(f"Offer lookup {r.status_code}: {r.text[:800]}")
    offers=r.json().get("offers",[])
    return offers[0] if offers else None

def create_offer(item):
    payload=offer_payload(item)
    r=requests.post(API+"/sell/inventory/v1/offer",headers=h(),json=payload,timeout=60)
    if r.status_code not in (200,201): raise RuntimeError(f"Offer {r.status_code}: {r.text[:800]}")
    return r.json()["offerId"]

def update_offer(offer_id,item):
    r=requests.put(API+f"/sell/inventory/v1/offer/{offer_id}",headers=h(),json=offer_payload(item),timeout=60)
    if r.status_code not in (200,204): raise RuntimeError(f"Update offer {r.status_code}: {r.text[:800]}")

def publish_offer(offer_id):
    r=requests.post(API+f"/sell/inventory/v1/offer/{offer_id}/publish",headers=h(),json={},timeout=60)
    if r.status_code not in (200,201): raise RuntimeError(f"Publish {r.status_code}: {r.text[:800]}")
    return r.json()


def validate_listing_payload(item):
    required = [
        "sku","title","description","category_id","condition","price","quantity",
        "payment_policy_id","return_policy_id","inventory_location_key"
    ]
    missing=[x for x in required if item.get(x) in (None,"",[],{})]
    if not item.get("shipping",{}).get("fulfillment_policy_id"):
        missing.append("shipping.fulfillment_policy_id")
    if missing:
        return False, "Missing fields: " + ", ".join(missing)

    if len(str(item.get("title",""))) > 80:
        return False, "Title exceeds 80 characters"
    try:
        if float(item.get("price",0)) <= 0:
            return False, "Price must be > 0"
    except Exception:
        return False, "Invalid price"
    if int(item.get("quantity",0)) <= 0:
        return False, "Quantity must be > 0"
    shipping=item.get("shipping",{})
    shipping_missing=[]
    if not (shipping.get("weight_pounds") or shipping.get("weight_ounces")): shipping_missing.append("packed weight")
    for key,label in (("package_length","package length"),("package_width","package width"),("package_height","package height")):
        if not shipping.get(key): shipping_missing.append(label)
    if shipping_missing:
        return False, "Shipping information incomplete: " + ", ".join(shipping_missing)

    # Check category-required aspects against eBay taxonomy
    category_id=str(item["category_id"])
    tree=ebay_get("/commerce/taxonomy/v1/get_default_category_tree_id",{"marketplace_id":MARKETPLACE})
    tree_id=tree["categoryTreeId"]
    meta=ebay_get(
        f"/commerce/taxonomy/v1/category_tree/{tree_id}/get_item_aspects_for_category",
        {"category_id":category_id})
    specifics=item.get("item_specifics",{})
    missing_aspects=[]
    for a in meta.get("aspects",[]):
        c=a.get("aspectConstraint",{})
        name=a.get("localizedAspectName")
        if c.get("aspectRequired",False) and not specifics.get(name):
            missing_aspects.append(name)
    if missing_aspects:
        return False, "Missing required eBay aspects: " + ", ".join(missing_aspects)
    return True, None

@app.post("/api/validate-listing")
def validate_listing():
    try:
        item=request.get_json(force=True)
        ok,error=validate_listing_payload(item)
        if not ok:
            return jsonify(ok=False,error=error),400
        # Make a lightweight live API call to confirm auth/account access
        ebay_get("/sell/account/v1/payment_policy",{"marketplace_id":MARKETPLACE})
        return jsonify(ok=True)
    except Exception as e:
        return jsonify(ok=False,error=str(e)),400

@app.post("/api/publish-batch")
def publish_batch():
    results=[]
    for item in request.get_json(force=True).get("items",[]):
        x={"sku":item.get("sku"),"ok":False}
        try:
            valid,error=validate_listing_payload(item)
            if not valid: raise RuntimeError(error)
            existing=find_offer(item.get("sku"))
            listing_id=existing.get("listing",{}).get("listingId") if existing else None
            if listing_id and not item.get("update_existing"):
                raise RuntimeError("SKU already published" + (f" as listing {listing_id}" if listing_id else ""))
            urls=[upload_image(p) for p in item.get("photos_data",[])]
            create_inventory(item,urls)
            if existing:
                offer=existing["offerId"]
                update_offer(offer,item)
                if listing_id:
                    x.update(ok=True,offerId=offer,listingId=listing_id,updated=True)
                else:
                    pub=publish_offer(offer)
                    x.update(ok=True,offerId=offer,listingId=pub.get("listingId"),resumed=True)
            else:
                offer=create_offer(item)
                pub=publish_offer(offer)
                x.update(ok=True,offerId=offer,listingId=pub.get("listingId"))
        except Exception as e: x["error"]=str(e)
        results.append(x)
    return jsonify(results=results)

if __name__=="__main__":
    app.run(host="0.0.0.0",port=int(os.getenv("PORT","5000")))
