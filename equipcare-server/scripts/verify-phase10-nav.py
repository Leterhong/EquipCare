# -*- coding: utf-8 -*-
"""阶段十第三步验证：八组导航树 / 侧栏折叠 / 占位页 / 验收入口带参过滤"""
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
    page.fill("input[name=username]", "e2e_admin")
    page.fill("input[name=password]", os.environ.get("EQUIPCARE_ADMIN_PASSWORD", "CHANGE_ME"))
    page.click("button[type=submit]")
    page.wait_for_url("**/app.html*", timeout=15000)
    page.wait_for_timeout(1500)
    page.screenshot(path=f"{OUT}/10-nav-tree.png")

    groups = page.eval_on_selector_all(".nav-group-title", "els => els.map(e => e.textContent)")
    labels = page.eval_on_selector_all(".nav-label", "els => els.map(e => e.textContent)")
    print("GROUPS:", groups)
    print("NAV_ITEMS:", len(labels))

    # 折叠
    page.click("#sidebar-toggle")
    page.wait_for_timeout(400)
    collapsed = page.evaluate("document.getElementById('sidebar').classList.contains('collapsed')")
    page.screenshot(path=f"{OUT}/11-sidebar-collapsed.png")
    page.click("#sidebar-toggle")
    page.wait_for_timeout(300)
    print("COLLAPSE_WORKS:", collapsed)

    # 占位页
    page.goto(BASE + "/app.html#/analytics/cost")
    page.wait_for_timeout(900)
    ph = page.locator(".placeholder-card").count()
    page.screenshot(path=f"{OUT}/12-placeholder.png")

    # 维修验收带参入口
    page.goto(BASE + "/app.html#/workorder?status=PENDING_REVIEW")
    page.wait_for_timeout(1300)
    status_val = page.eval_on_selector("select.input", "el => el.value") if page.locator("select.input").count() else "?"
    page.screenshot(path=f"{OUT}/13-verify-queue.png")

    print("PLACEHOLDER_OK:", ph > 0)
    print("VERIFY_QUEUE_STATUS:", status_val)
    print("CONSOLE_ERRORS:", len(errors), errors[:3])
    browser.close()
