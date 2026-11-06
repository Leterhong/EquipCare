# -*- coding: utf-8 -*-
"""阶段十第五步验证：设备列表统计卡 + 详情六 Tab"""
import os
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:8080"
OUT = "docs/screenshots/phase10"
errors = []

with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=os.environ.get("EQUIPCARE_CHROME"))
    page = browser.new_page(viewport={"width": 1600, "height": 900})
    page.on("console", lambda m: errors.append(m.text) if m.type == "error" and "401" not in m.text else None)

    page.goto(BASE + "/index.html")
    page.fill("input[name=username]", "e2e_admin")
    page.fill("input[name=password]", "S8e2e#2026!x")
    page.click("button[type=submit]")
    page.wait_for_url("**/app.html*", timeout=15000)
    page.goto(BASE + "/app.html#/equipment")
    page.wait_for_timeout(2200)

    stats = page.locator(".equip-stat").count()
    total_text = page.locator(".equip-stat .value").first.text_content() if stats else "?"
    page.screenshot(path=f"{OUT}/30-equipment-stats.png")

    # 点统计卡筛选
    page.locator(".equip-stat").nth(2).click()
    page.wait_for_timeout(1000)
    filtered = page.locator(".equip-stat .value").nth(2).text_content()
    rows = page.locator("table.data tbody tr").count()

    # 详情页 Tab
    page.goto(BASE + "/app.html#/equipment")
    page.wait_for_timeout(1200)
    page.locator("table.data tbody tr").first.locator("button[data-action=detail], a").first.click()
    page.wait_for_timeout(1500)
    tabs = page.eval_on_selector_all(".tab-btn", "els => els.map(e => e.textContent)")
    page.screenshot(path=f"{OUT}/31-equipment-detail.png")
    # 切到生命周期
    lc = page.get_by_role("button", name="生命周期", exact=True)
    if lc.count():
        lc.click()
        page.wait_for_timeout(700)
        page.screenshot(path=f"{OUT}/32-equipment-lifecycle.png")

    print("STATS:", stats, "TOTAL:", total_text, "FILTERED_ROWS:", rows)
    print("TABS:", tabs)
    print("CONSOLE_ERRORS:", len(errors), errors[:3])
    browser.close()
