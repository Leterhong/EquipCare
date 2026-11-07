# -*- coding: utf-8 -*-
import os
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:8080"
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=os.environ.get("EQUIPCARE_CHROME"))
    page = browser.new_page(viewport={"width": 1600, "height": 900})
    page.goto(BASE + "/index.html")
    page.fill("input[name=username]", "e2e_admin")
    page.fill("input[name=password]", os.environ.get("EQUIPCARE_ADMIN_PASSWORD", "CHANGE_ME"))
    page.click("button[type=submit]")
    page.wait_for_url("**/app.html*", timeout=20000)
    page.wait_for_timeout(1500)
    info = page.evaluate("""() => {
        const el = document.querySelector('#user-name');
        const cs = getComputedStyle(el);
        const r = el.getBoundingClientRect();
        const meta = getComputedStyle(el.parentElement);
        const menu = document.querySelector('#avatar-menu');
        const menuCS = getComputedStyle(menu);
        const menuHidden = menu.hidden;
        const menuRect = menu.getBoundingClientRect();
        return {
            nameRect: {x: r.x, y: r.y, w: r.width, h: r.height},
            nameStyle: {font: cs.fontSize, lh: cs.lineHeight, display: cs.display, color: cs.color,
                        whiteSpace: cs.whiteSpace, overflow: cs.overflow, textOverflow: cs.textOverflow},
            metaStyle: {display: meta.display, height: el.parentElement.offsetHeight, overflow: meta.overflow},
            chipH: document.querySelector('#user-chip').offsetHeight,
            menu: {hidden: menuHidden, display: menuCS.display, rect: {y: menuRect.y, h: menuRect.height}},
        };
    }""")
    for k, v in info.items():
        print(k, "=>", v)
    browser.close()
