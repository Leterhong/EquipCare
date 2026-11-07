# -*- coding: utf-8 -*-
"""Phase 11 批一 · 扫码页 XSS 安全测试。

覆盖场景：
1. 服务端返回的 equipmentName 含 HTML 标签/事件处理器 —— 必须按字面文本渲染，不得执行；
2. 含 <script> 的输入 —— 无脚本执行；
3. 恶意 URL token 参数 —— 显示"二维码无效"，且不发起任何后端请求；
4. 正常设备 —— 原有业务功能不受影响。
"""
import json
import os
import sys
import urllib.request

BASE = "http://127.0.0.1:8080"
OUT = "docs/screenshots/phase11"
os.makedirs(OUT, exist_ok=True)

MALICIOUS_NAME = '<img src=x onerror="window.__xss=1">设备<svg onload=window.__xss=2>'
SCRIPT_NAME = '<script>window.__xss=3</script>泵'


def http(method, path, body=None, token=None):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(body).encode() if body is not None else None
    with urllib.request.urlopen(req, data) as resp:
        return json.loads(resp.read().decode())


def collect_leaf_ids(nodes, out):
    for n in nodes:
        if n.get("children"):
            collect_leaf_ids(n["children"], out)
        else:
            out.append(n["id"])
    return out


def main():
    login = http("POST", "/api/auth/login",
                 {"username": "e2e_admin", "password": os.environ.get("EQUIPCARE_ADMIN_PASSWORD", "CHANGE_ME")})
    token = login["data"]["accessToken"]

    cat_ids = collect_leaf_ids(http("GET", "/api/asset/categories/tree", token=token)["data"], [])
    loc_ids = collect_leaf_ids(http("GET", "/api/asset/locations/tree", token=token)["data"], [])
    assert cat_ids and loc_ids, "缺少分类或位置测试数据"
    category_id, location_id = cat_ids[0], loc_ids[0]

    created = []
    for suffix, name in (("xss1", MALICIOUS_NAME), ("xss2", SCRIPT_NAME)):
        vo = http("POST", "/api/asset/equipment", {
            "equipmentName": name,
            "categoryId": category_id,
            "locationId": location_id,
        }, token=token)
        created.append((vo["data"], name))

    # 为恶意设备生成扫码 token
    scan_tokens = []
    for eq_id, _ in created:
        qr = urllib.request.Request(f"{BASE}/api/asset/equipment/{eq_id}/qrcode/regenerate", method="POST")
        qr.add_header("Authorization", "Bearer " + token)
        with urllib.request.urlopen(qr) as resp:
            scan_tokens.append(json.loads(resp.read().decode())["data"]["qrToken"])

    from playwright.sync_api import sync_playwright

    failures = []
    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=os.environ.get("EQUIPCARE_CHROME"))
        page = browser.new_page(viewport={"width": 480, "height": 900})

        # 预置登录态（扫码页从 localStorage 读取令牌）
        page.goto(f"{BASE}/index.html")
        page.evaluate(
            "([t, r]) => { localStorage.setItem('ec_access_token', t); localStorage.setItem('ec_refresh_token', r); }",
            [token, login["data"]["refreshToken"]])

        dialogs = []
        page.on("dialog", lambda d: (dialogs.append(d.message), d.dismiss()))

        api_requests = []
        page.on("request", lambda r: api_requests.append(r.url) if "/api/" in r.url else None)

        # ---- 场景 1/2：恶意设备名按字面渲染，不执行 ----
        for (eq_id, name), qr_token in zip(created, scan_tokens):
            page.goto(f"{BASE}/m/scan.html?token={qr_token}")
            page.wait_for_selector(".scan-card h2", timeout=15000)
            page.wait_for_timeout(800)
            h2_text = page.locator(".scan-card h2").text_content()
            xss_fired = page.evaluate("window.__xss || 0")
            if xss_fired:
                failures.append(f"XSS 脚本被执行 name={name!r} flag={xss_fired}")
            if h2_text != name:
                failures.append(f"设备名未按字面渲染: got {h2_text!r}")
            if dialogs:
                failures.append(f"出现意外弹窗: {dialogs}")
            page.screenshot(path=f"{OUT}/xss-literal-{eq_id}.png")

        # ---- 场景 3：恶意 token 参数不发起 API 请求 ----
        api_requests.clear()
        page.goto(f'{BASE}/m/scan.html?token="><script>alert(1)</script>')
        page.wait_for_timeout(1200)
        body_text = page.locator("#scan-host").text_content()
        if "二维码无效" not in body_text:
            failures.append(f"恶意 token 未显示无效提示: {body_text!r}")
        if any("/api/asset/equipment/scan" in u for u in api_requests):
            failures.append(f"恶意 token 仍发起了扫码请求: {api_requests}")
        if page.evaluate("window.__xss || 0"):
            failures.append("恶意 token 注入脚本被执行")

        # ---- 场景 4：正常扫码功能不回归 ----
        page.goto(f"{BASE}/m/scan.html?token={scan_tokens[0]}")
        page.wait_for_selector(".scan-actions a", timeout=15000)
        if page.locator(".scan-actions a", has_text="查看完整档案").count() != 1:
            failures.append("正常扫码缺少「查看完整档案」入口")
        page.screenshot(path=f"{OUT}/xss-normal-flow.png")

        browser.close()

    # 清理测试设备
    for eq_id, _ in created:
        try:
            req = urllib.request.Request(f"{BASE}/api/asset/equipment/{eq_id}", method="DELETE")
            req.add_header("Authorization", "Bearer " + token)
            urllib.request.urlopen(req)
        except Exception as ex:  # noqa: BLE001
            print(f"WARN: 清理设备 {eq_id} 失败: {ex}")

    if failures:
        for f in failures:
            print("FAIL:", f)
        sys.exit(1)
    print("PASS: XSS 安全测试 4 项全部通过（字面渲染 x2 / 恶意 token / 正常功能）")


if __name__ == "__main__":
    main()
