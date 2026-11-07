# -*- coding: utf-8 -*-
"""Phase 11 路由修复验证：静态路由优先 + 导航单高亮。

场景：
1. /equipment/location、/equipment/lifecycle、/workorder/records（静态占位页）
   不再被 /equipment/:id、/workorder/:id 参数路由截胡 —— 不出现「页面加载失败」；
2. 真实设备详情 /equipment/{id} 仍正常渲染（参数路由兜底不被破坏）；
3. 导航高亮唯一性：占位页只点亮自己的菜单项，不再同时点亮「设备档案」。
"""
import json
import os
import urllib.request

from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:8080"
OUT = "docs/screenshots/phase11"
os.makedirs(OUT, exist_ok=True)

results = []


def check(name, ok, detail=""):
    results.append((name, ok, detail))
    print(("PASS " if ok else "FAIL ") + name + (" | " + str(detail) if detail else ""))


def http_json(method, path, body=None, token=None):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(body).encode() if body is not None else None
    with urllib.request.urlopen(req, data) as resp:
        return json.loads(resp.read().decode())


def active_titles(page):
    return page.eval_on_selector_all(
        ".nav-item.active .nav-label", "els => els.map(e => e.textContent)")


def visit(page, hash_path):
    page.goto(BASE + "/app.html#" + hash_path)
    page.wait_for_timeout(1500)
    failed = page.locator("text=页面加载失败").count()
    h1 = page.locator("h1").first.text_content() if page.locator("h1").count() else ""
    crumbs = page.locator("#crumbs").text_content()
    return failed, h1.strip(), crumbs


def main():
    token = http_json("POST", "/api/auth/login",
                      {"username": "e2e_admin", "password": os.environ.get("EQUIPCARE_ADMIN_PASSWORD", "CHANGE_ME")})["data"]["accessToken"]
    page_data = http_json(
        "GET", "/api/asset/equipment?pageNum=1&pageSize=1", token=token)
    eq_id = page_data["data"]["records"][0]["id"]

    with sync_playwright() as p:
        browser = p.chromium.launch(executable_path=os.environ.get("EQUIPCARE_CHROME"))
        page = browser.new_page(viewport={"width": 1600, "height": 900})
        console_errors = []
        page.on("console", lambda m: console_errors.append(m.text)
                if m.type == "error" and "401" not in m.text and "Failed to load resource" not in m.text else None)

        page.goto(BASE + "/index.html")
        page.fill("input[name=username]", "e2e_admin")
        page.fill("input[name=password]", os.environ.get("EQUIPCARE_ADMIN_PASSWORD", "CHANGE_ME"))
        page.click("button[type=submit]")
        page.wait_for_url("**/app.html*", timeout=20000)

        # 1. 三个曾被参数路由截胡的静态占位页
        for hp, title in [("/equipment/location", "设备位置"),
                          ("/equipment/lifecycle", "设备生命周期"),
                          ("/workorder/records", "维修记录")]:
            failed, h1, crumbs = visit(page, hp)
            check(f"{hp} 不再报「页面加载失败」", failed == 0, f"h1={h1}")
            check(f"{hp} 渲染占位页 {title}", title in (h1 or "") or title in (crumbs or ""),
                  f"h1={h1} crumbs={crumbs}")

        # 2. 导航高亮唯一性（当前 /workorder/records）
        actives = active_titles(page)
        check("导航高亮唯一", len(actives) == 1, f"actives={actives}")
        check("高亮项为「维修记录」", actives == ["维修记录"], f"actives={actives}")

        failed, h1, _ = visit(page, "/equipment/location")
        actives = active_titles(page)
        check("/equipment/location 只点亮「设备位置」", actives == ["设备位置"], f"actives={actives}")

        # 3. 参数路由兜底：真实设备详情仍正常
        failed, h1, _ = visit(page, f"/equipment/{eq_id}")
        check(f"/equipment/{eq_id} 正常渲染设备详情", failed == 0 and h1 != "",
              f"h1={h1}")

        # 4. 设备列表（/equipment 精确匹配）正常 + 只点亮「设备档案」
        failed, h1, _ = visit(page, "/equipment")
        actives = active_titles(page)
        check("/equipment 正常渲染", failed == 0, f"h1={h1}")
        check("/equipment 只点亮「设备档案」", actives == ["设备档案"], f"actives={actives}")

        page.screenshot(path=f"{OUT}/route-fix-equipment-list.png")
        browser.close()

    real_errors = [e for e in console_errors if "400" not in e]
    check("无 console 错误", len(real_errors) == 0, real_errors[:3])

    bad = [r for r in results if not r[1]]
    print(f"\n{len(results) - len(bad)}/{len(results)} 项通过")
    if bad:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
