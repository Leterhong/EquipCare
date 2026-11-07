# -*- coding: utf-8 -*-
"""阶段十第七步验证：工单看板双模式 + 详情双栏 + 备件浅橙提示"""
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
    page.wait_for_url("**/app.html*", timeout=20000)

    # ---- 1. 工单列表：双视图 Tab ----
    page.goto(BASE + "/app.html#/workorder")
    page.wait_for_timeout(2200)
    tabs = page.eval_on_selector_all(".section-tabs button", "els => els.map(e => e.textContent)")
    rows = page.locator("table.data tbody tr").count()
    page.screenshot(path=f"{OUT}/50-workorder-list.png")

    # 切到看板
    page.locator(".section-tabs button[data-view=board]").click()
    page.wait_for_timeout(2200)
    cols = page.locator(".board-col").count()
    counts = page.eval_on_selector_all(".board-count", "els => els.map(e => e.textContent)")
    cards = page.locator(".board-card").count()
    page.screenshot(path=f"{OUT}/51-workorder-board.png")

    # 点第一张看板卡进详情
    if cards:
        page.locator(".board-card").first.click()
        page.wait_for_timeout(1800)
        detail_h1 = page.locator("h1").first.text_content()
        back_btn = page.locator("button", has_text="返回列表").count()
        two_col = page.locator(".detail-two-col").count()
        page.screenshot(path=f"{OUT}/52-workorder-detail.png")

    # ---- 2. 备件库存：浅橙提示 + 表头 ----
    page.goto(BASE + "/app.html#/spare/stock")
    page.wait_for_timeout(2000)
    headers = page.eval_on_selector_all("table.data thead th", "els => els.map(e => e.textContent).slice(0, 4)")
    warn_rows = page.eval_on_selector_all("tr.row-below-safety", "els => els.length")
    below_tags = page.locator(".stock-below").count()
    page.screenshot(path=f"{OUT}/53-spare-stock-warn.png")

    print("WO_TABS:", tabs, "LIST_ROWS:", rows)
    print("BOARD: cols=", cols, "counts=", counts, "cards=", cards)
    if cards:
        print("DETAIL: h1=", detail_h1, "back=", back_btn, "two_col=", two_col)
    print("STOCK: headers=", headers, "warn_rows=", warn_rows, "below_spans=", below_tags)
    print("CONSOLE_ERRORS:", len(errors), errors[:3])
    browser.close()
