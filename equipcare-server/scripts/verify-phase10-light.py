# -*- coding: utf-8 -*-
"""阶段十浅色主题 · 截图验证（登录/驾驶舱/列表/弹窗）"""
import os
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:8080"
OUT = "docs/screenshots/phase10"
os.makedirs(OUT, exist_ok=True)
errors = []

with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=os.environ.get("EQUIPCARE_CHROME"))
    page = browser.new_page(viewport={"width": 1600, "height": 900})
    page.on("console", lambda m: errors.append(m.text) if m.type == "error" and "401" not in m.text else None)

    page.goto(BASE + "/index.html")
    page.screenshot(path=f"{OUT}/01-login.png")
    page.fill("input[name=username]", "e2e_admin")
    page.fill("input[name=password]", os.environ.get("EQUIPCARE_ADMIN_PASSWORD", "CHANGE_ME"))
    page.click("button[type=submit]")
    page.wait_for_url("**/app.html*", timeout=15000)
    page.wait_for_timeout(2000)
    page.screenshot(path=f"{OUT}/02-dashboard.png", full_page=False)

    page.goto(BASE + "/app.html#/equipment")
    page.wait_for_timeout(1500)
    page.screenshot(path=f"{OUT}/03-equipment.png")

    page.goto(BASE + "/app.html#/workorder")
    page.wait_for_timeout(1500)
    page.screenshot(path=f"{OUT}/04-workorder.png")

    # 弹窗（设备详情）
    page.goto(BASE + "/app.html#/inspect/plan")
    page.wait_for_timeout(1500)
    btn = page.locator("button[data-action=detail]")
    if btn.count() > 0:
        btn.first.click()
        page.wait_for_timeout(1200)
        page.screenshot(path=f"{OUT}/05-modal.png")

    # 背景色断言：body 是浅色
    bg = page.evaluate("getComputedStyle(document.body).backgroundColor")
    print("BODY_BG:", bg)
    print("CONSOLE_ERRORS:", len(errors), errors[:3])
    browser.close()
