# -*- coding: utf-8 -*-
"""验证顶栏用户名颜色 + 折叠侧栏首字徽标"""
import os
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:8080"
OUT = "docs/screenshots/phase11"
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=os.environ.get("EQUIPCARE_CHROME"))
    page = browser.new_page(viewport={"width": 1600, "height": 900})
    page.goto(BASE + "/index.html")
    page.fill("input[name=username]", "e2e_admin")
    page.fill("input[name=password]", "S8e2e#2026!x")
    page.click("button[type=submit]")
    page.wait_for_url("**/app.html*", timeout=20000)
    page.wait_for_timeout(1800)

    name_color = page.evaluate("getComputedStyle(document.querySelector('#user-name')).color")
    page.locator(".avatar-wrap").screenshot(path=f"{OUT}/fix-user-chip.png")

    page.locator(".sidebar-toggle").first.click()
    page.wait_for_timeout(600)
    abbrs = page.evaluate("""() => {
        const items = [...document.querySelectorAll('.sidebar.collapsed .nav-item')].slice(0, 5);
        return items.map(it => ({
            abbr: it.querySelector('.nav-abbr') ? it.querySelector('.nav-abbr').textContent : null,
            abbrVisible: it.querySelector('.nav-abbr') ? getComputedStyle(it.querySelector('.nav-abbr')).display !== 'none' : false,
            w: it.offsetWidth, h: it.offsetHeight,
        }));
    }""")
    page.locator(".sidebar").screenshot(path=f"{OUT}/fix-collapsed.png")
    page.screenshot(path=f"{OUT}/fix-collapsed-full.png")
    print("NAME_COLOR:", name_color, "(expect rgb(38, 52, 69))")
    print("COLLAPSED_ABBRS:", abbrs)
    browser.close()
