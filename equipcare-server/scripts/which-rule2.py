# -*- coding: utf-8 -*-
import os
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:8080"
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=os.environ.get("EQUIPCARE_CHROME"))
    page = browser.new_page(viewport={"width": 1600, "height": 900})
    page.goto(BASE + "/index.html")
    page.fill("input[name=username]", "e2e_admin")
    page.fill("input[name=password]", "S8e2e#2026!x")
    page.click("button[type=submit]")
    page.wait_for_url("**/app.html*", timeout=20000)
    page.wait_for_timeout(1200)
    rules = page.evaluate("""() => {
        const el = document.querySelector('#user-chip');
        const out = [];
        for (const sheet of document.styleSheets) {
            let list;
            try { list = sheet.cssRules; } catch (e) { continue; }
            const walk = (rules) => {
                for (const rule of rules) {
                    if (rule.cssRules) { walk(rule.cssRules); continue; }
                    if (!rule.selectorText || !rule.style) continue;
                    let matched = false;
                    try { matched = el.matches(rule.selectorText); } catch (e) {}
                    if (matched) {
                        const css = rule.style.cssText;
                        if (css.includes('color')) {
                            out.push({ sel: rule.selectorText, css: css.slice(0, 200) });
                        }
                    }
                }
            };
            walk(list);
        }
        return out;
    }""")
    for r in rules:
        print("SEL:", r["sel"])
        print("   ", r["css"][:180])
    browser.close()
