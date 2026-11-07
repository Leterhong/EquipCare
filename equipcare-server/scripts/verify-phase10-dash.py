# -*- coding: utf-8 -*-
"""阶段十第四步验证：五层驾驶舱"""
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
    page.fill("input[name=password]", os.environ.get("EQUIPCARE_ADMIN_PASSWORD", "CHANGE_ME"))
    page.click("button[type=submit]")
    page.wait_for_url("**/app.html*", timeout=15000)
    page.goto(BASE + "/app.html#/dashboard")
    page.wait_for_timeout(3000)
    page.screenshot(path=f"{OUT}/20-dashboard-top.png")

    greet = page.locator(".dash-greet").count()
    core = page.locator(".dash-core-card").count()
    chart_doms = page.locator(".chart-dom").count()
    feed_rows = page.locator(".feed-row").count()
    page.mouse.wheel(0, 1400)
    page.wait_for_timeout(800)
    page.screenshot(path=f"{OUT}/21-dashboard-middle.png")
    page.mouse.wheel(0, 1400)
    page.wait_for_timeout(800)
    page.screenshot(path=f"{OUT}/22-dashboard-bottom.png")

    print("GREET:", greet, "CORE_CARDS:", core, "CHARTS:", chart_doms, "FEED_ROWS:", feed_rows)
    print("CONSOLE_ERRORS:", len(errors), errors[:3])
    browser.close()
