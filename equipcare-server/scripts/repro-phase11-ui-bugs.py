# -*- coding: utf-8 -*-
"""复现顶栏用户区文字裁切 + 侧栏折叠显示问题"""
import os
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:8080"
OUT = "docs/screenshots/phase11"
os.makedirs(OUT, exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=os.environ.get("EQUIPCARE_CHROME"))
    page = browser.new_page(viewport={"width": 1600, "height": 900})
    page.goto(BASE + "/index.html")
    page.fill("input[name=username]", "e2e_admin")
    page.fill("input[name=password]", "S8e2e#2026!x")
    page.click("button[type=submit]")
    page.wait_for_url("**/app.html*", timeout=20000)
    page.wait_for_timeout(2000)

    # 1. 顶栏用户区现状
    chip = page.locator("#user-chip")
    print("USER_NAME:", repr(page.locator("#user-name").text_content()))
    print("USER_ROLE:", repr(page.locator("#user-role").text_content()))
    box = chip.bounding_box()
    print("CHIP_BOX:", box)
    meta_box = page.locator(".user-meta").bounding_box()
    print("META_BOX:", meta_box)
    page.locator(".topbar").screenshot(path=f"{OUT}/repro-topbar.png")
    page.locator(".avatar-wrap").screenshot(path=f"{OUT}/repro-user-chip.png")

    # 2. 折叠侧栏现状
    page.locator("#sidebar-toggle, .sidebar-toggle").first.click()
    page.wait_for_timeout(600)
    page.screenshot(path=f"{OUT}/repro-collapsed.png")
    # nav-item 折叠态可见内容
    vis = page.evaluate("""() => {
        const items = [...document.querySelectorAll('.sidebar.collapsed .nav-item')].slice(0, 4);
        return items.map(it => ({
            text: it.textContent.trim().slice(0, 10),
            w: it.offsetWidth, h: it.offsetHeight,
        }));
    }""")
    print("COLLAPSED_ITEMS:", vis)
    browser.close()
