# -*- coding: utf-8 -*-
import json, os, urllib.request
BASE = "http://127.0.0.1:8080"

def http(method, path, body=None, token=None):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token: req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(body).encode() if body is not None else None
    with urllib.request.urlopen(req, data) as resp:
        return json.loads(resp.read().decode())

login = http("POST", "/api/auth/login", {"username": "e2e_admin", "password": "S8e2e#2026!x"})
token = login["data"]["accessToken"]

# 新建一台正常设备拿 token
cat_ids = []
def collect(nodes, out):
    for n in nodes:
        if n.get("children"): collect(n["children"], out)
        else: out.append(n["id"])
    return out
cat_id = collect(http("GET", "/api/asset/categories/tree", token=token)["data"], [])[0]
loc_id = collect(http("GET", "/api/asset/locations/tree", token=token)["data"], [])[0]
eq = http("POST", "/api/asset/equipment", {"equipmentName": "探针设备ZZZ", "categoryId": cat_id, "locationId": loc_id}, token=token)["data"]
qr_req = urllib.request.Request(f"{BASE}/api/asset/equipment/{eq}/qrcode/regenerate", method="POST")
qr_req.add_header("Authorization", "Bearer " + token)
with urllib.request.urlopen(qr_req) as resp:
    qr = json.loads(resp.read().decode())["data"]["qrToken"]

from playwright.sync_api import sync_playwright
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=os.environ.get("EQUIPCARE_CHROME"))
    page = browser.new_page()
    msgs = []
    page.on("console", lambda m: msgs.append(f"{m.type}: {m.text}"))
    page.on("pageerror", lambda e: msgs.append(f"PAGEERROR: {e}"))
    page.goto(f"{BASE}/m/scan.html?token={qr}")
    page.wait_for_timeout(4000)
    print("HOST_HTML_HEAD:", page.evaluate("document.getElementById('scan-host').innerHTML.slice(0, 300)"))
    print("CONSOLE:", msgs[:8])
    browser.close()

# 清理
req = urllib.request.Request(f"{BASE}/api/asset/equipment/{eq}", method="DELETE")
req.add_header("Authorization", "Bearer " + token)
urllib.request.urlopen(req)
