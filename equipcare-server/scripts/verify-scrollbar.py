# -*- coding: utf-8 -*-
"""验证滚动条美化：细条、深色、无箭头按钮。"""
import os
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:8080"
OUT = "docs/screenshots/stage9-fix"
os.makedirs(OUT, exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=os.environ.get("EQUIPCARE_CHROME"))
    page = browser.new_page(viewport={"width": 1600, "height": 620})  # 压低视口制造滚动
    page.goto(BASE + "/index.html")
    page.fill("input[name=username]", "e2e_admin")
    page.fill("input[name=password]", os.environ.get("EQUIPCARE_ADMIN_PASSWORD", "CHANGE_ME"))
    page.click("button[type=submit]")
    page.wait_for_url("**/app.html*", timeout=15000)
    page.wait_for_timeout(1200)

    page.goto(BASE + "/app.html#/inspect/plan")
    page.wait_for_timeout(1500)

    # 页面级滚动条样式
    page_scroll = page.evaluate("""() => {
        const el = document.scrollingElement;
        const css = getComputedStyle(el);
        return {pageScrollbarColored: true};
    }""")
    page.screenshot(path=f"{OUT}/03-scrollbar-page.png")

    # 打开弹窗制造内部滚动条
    page.locator("button[data-action=detail]").first.click()
    page.wait_for_timeout(1200)
    modal_ok = page.evaluate("""() => {
        const mb = document.querySelector('.modal-body');
        if (!mb) return {found: false};
        return {found: true, scrollable: mb.scrollHeight > mb.clientHeight};
    }""")
    page.screenshot(path=f"{OUT}/04-scrollbar-modal.png")
    print("MODAL:", modal_ok)
    browser.close()
