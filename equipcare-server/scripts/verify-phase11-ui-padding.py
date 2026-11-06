# -*- coding: utf-8 -*-
"""验证：出入库表单卡片内边距（card-body）+ XSS 脏数据已清理。"""
import os
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:8080"
OUT = "docs/screenshots/phase11"
os.makedirs(OUT, exist_ok=True)

ok = True


def check(name, cond, detail=""):
    global ok
    print(("PASS " if cond else "FAIL ") + name + (" | " + str(detail) if detail else ""))
    ok = ok and cond


with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=os.environ.get("EQUIPCARE_CHROME"))
    page = browser.new_page(viewport={"width": 1600, "height": 900})
    page.goto(BASE + "/index.html")
    page.fill("input[name=username]", "e2e_admin")
    page.fill("input[name=password]", "S8e2e#2026!x")
    page.click("button[type=submit]")
    page.wait_for_url("**/app.html*", timeout=20000)

    # 1. 出库页：表单容器有 18px 内边距，不再贴边
    page.goto(BASE + "/app.html#/spare/stock/out")
    page.wait_for_timeout(1500)
    padding = page.eval_on_selector(
        ".grid-2 .card.no-stretch .card-body", "el => getComputedStyle(el).padding")
    check("出库表单有内边距", padding == "18px", padding)
    # 卡片不再被拉伸：表单卡高度 < 记录卡高度
    hs = page.eval_on_selector_all(
        ".grid-2 > .card", "els => els.map(e => e.getBoundingClientRect().height)")
    check("出库表单卡高度小于记录卡", len(hs) == 2 and hs[0] < hs[1], hs)
    page.screenshot(path=f"{OUT}/fix-stock-out-padding.png")

    # 2. 入库页同样验证
    page.goto(BASE + "/app.html#/spare/stock/in")
    page.wait_for_timeout(1500)
    padding = page.eval_on_selector(
        ".grid-2 .card.no-stretch .card-body", "el => getComputedStyle(el).padding")
    check("入库表单有内边距", padding == "18px", padding)
    hs = page.eval_on_selector_all(
        ".grid-2 > .card", "els => els.map(e => e.getBoundingClientRect().height)")
    check("入库表单卡高度小于记录卡", len(hs) == 2 and hs[0] < hs[1], hs)
    page.screenshot(path=f"{OUT}/fix-stock-in-padding.png")

    # 3. 设备列表：XSS 脏数据已清理
    page.goto(BASE + "/app.html#/equipment")
    page.wait_for_timeout(1800)
    body = page.locator("table.data").text_content() or ""
    check("设备列表无 <script> 残留", "<script>" not in body)
    check("设备列表无 onerror 残留", "onerror" not in body)
    page.screenshot(path=f"{OUT}/fix-equipment-list-clean.png")

    browser.close()

print("\n" + ("ALL PASS" if ok else "HAS FAILURES"))
raise SystemExit(0 if ok else 1)
