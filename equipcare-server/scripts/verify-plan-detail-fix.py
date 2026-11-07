# -*- coding: utf-8 -*-
"""验证修复：巡检计划详情弹窗两块表格不再显示转义 HTML 文本。"""
import os, sys
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:8080"
OUT = "docs/screenshots/stage9-fix"
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
    page.wait_for_timeout(1200)

    page.goto(BASE + "/app.html#/inspect/plan")
    page.wait_for_timeout(1500)
    page.screenshot(path=f"{OUT}/01-plan-list.png")

    # 打开第一条计划的详情
    page.locator("button[data-action=detail]").first.click()
    page.wait_for_timeout(1800)
    page.screenshot(path=f"{OUT}/02-plan-detail-fixed.png")

    # 断言弹窗内不再出现转义的 HTML 原文
    modal_text = page.locator(".modal, [class*=modal]").first.inner_text()
    leaked = "<div" in modal_text or "<table" in modal_text or "table-wrap" in modal_text
    # 断言设备表格真实渲染（有数据行或空态文案）
    has_table = page.locator(".modal .data, .modal .table-empty").count() > 0
    print("LEAK_ESCAPED_HTML:", leaked)
    print("TABLE_RENDERED:", has_table)
    print("CONSOLE_ERRORS:", len(errors))
    browser.close()
    sys.exit(1 if (leaked or not has_table or errors) else 0)
