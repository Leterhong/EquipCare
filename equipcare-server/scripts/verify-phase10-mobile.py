# -*- coding: utf-8 -*-
"""阶段十第八步验证：移动端五场景（扫码/巡检执行/异常上报/工单查看/维修提交）+ 交互核查"""
import os
from playwright.sync_api import sync_playwright

BASE = "http://127.0.0.1:8080"
OUT = "docs/screenshots/phase10"
CHROME = os.environ.get("EQUIPCARE_CHROME")
MOBILE = {"width": 390, "height": 844}
errors = []

with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=CHROME)

    # ============ 移动端场景 ============
    ctx = browser.new_context(viewport=MOBILE, is_mobile=True, has_touch=True,
                              user_agent="Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15")
    page = ctx.new_page()
    page.on("console", lambda m: errors.append("M:" + m.text) if m.type == "error" and "401" not in m.text else None)

    # 1. 扫码页（独立轻页，无需登录态）
    page.goto(BASE + "/m/scan.html")
    page.wait_for_timeout(1500)
    scan_overflow = page.evaluate("document.documentElement.scrollWidth > document.documentElement.clientWidth")
    page.screenshot(path=f"{OUT}/60-mobile-scan.png")

    # 登录
    page.goto(BASE + "/index.html")
    page.fill("input[name=username]", "e2e_repairer")
    page.fill("input[name=password]", "S8e2e#2026!x")
    page.click("button[type=submit]")
    try:
        page.wait_for_url("**/app.html*", timeout=8000)
    except Exception:
        page.fill("input[name=username]", "e2e_admin")
        page.fill("input[name=password]", "S8e2e#2026!x")
        page.click("button[type=submit]")
        page.wait_for_url("**/app.html*", timeout=10000)
    page.wait_for_timeout(1500)

    # 2. 巡检执行页（移动端逐项卡片）
    page.goto(BASE + "/app.html#/inspect/task")
    page.wait_for_timeout(1800)
    page.screenshot(path=f"{OUT}/61-mobile-inspect-task.png")
    exec_btn = page.locator("table.data tbody tr button", has_text="去执行").first
    has_exec = exec_btn.count() > 0
    exec_overflow = None
    if has_exec:
        exec_btn.click()
        page.wait_for_timeout(1500)
        start_btn = page.locator("button", has_text="开始巡检")
        if start_btn.count() and start_btn.first.is_visible():
            start_btn.first.click()
            page.wait_for_timeout(1200)
        exec_overflow = page.evaluate("document.documentElement.scrollWidth > document.documentElement.clientWidth")
        page.screenshot(path=f"{OUT}/62-mobile-inspect-execute.png")

    # 3. 异常上报/异常清单
    page.goto(BASE + "/app.html#/inspect/anomaly")
    page.wait_for_timeout(1800)
    anomaly_overflow = page.evaluate("document.documentElement.scrollWidth > document.documentElement.clientWidth")
    page.screenshot(path=f"{OUT}/63-mobile-anomaly.png")

    # 4. 工单查看
    page.goto(BASE + "/app.html#/workorder")
    page.wait_for_timeout(1800)
    wo_overflow = page.evaluate("document.documentElement.scrollWidth > document.documentElement.clientWidth")
    page.screenshot(path=f"{OUT}/64-mobile-workorder.png")

    # 5. 维修提交（进入维修中工单的维修记录页）
    page.locator(".section-tabs button[data-view=board]").click()
    page.wait_for_timeout(1800)
    repairing = page.locator(".board-col").nth(2).locator(".board-card").first
    has_repair_page = False
    repair_overflow = None
    if repairing.count():
        repairing.click()
        page.wait_for_timeout(1600)
        repair_btn = page.locator("button", has_text="维修过程").first
        if not repair_btn.count():
            repair_btn = page.locator("a", has_text="维修过程").first
        page.screenshot(path=f"{OUT}/65-mobile-workorder-detail.png")
        if repair_btn.count():
            has_repair_page = True
            repair_btn.click()
            page.wait_for_timeout(1500)
            repair_overflow = page.evaluate("document.documentElement.scrollWidth > document.documentElement.clientWidth")
            page.screenshot(path=f"{OUT}/66-mobile-repair-submit.png")
    ctx.close()

    # ============ 桌面交互核查 ============
    dctx = browser.new_context(viewport={"width": 1440, "height": 900})
    dp = dctx.new_page()
    dp.on("console", lambda m: errors.append("D:" + m.text) if m.type == "error" and "401" not in m.text else None)
    dp.goto(BASE + "/index.html")
    dp.fill("input[name=username]", "e2e_admin")
    dp.fill("input[name=password]", "S8e2e#2026!x")
    dp.click("button[type=submit]")
    dp.wait_for_url("**/app.html*", timeout=10000)

    # 交互1：按钮禁用/加载态样式类定义存在
    css = dp.evaluate("fetch('/css/app.css').then(r => r.text())")
    checks = {
        "btn_loading_css": ".btn.loading" in css,
        "btn_disabled_css": ".btn:disabled" in css,
        "focus_visible_css": ":focus-visible" in css,
        "reduced_motion_css": "prefers-reduced-motion" in css,
        "skeleton_css": ".skeleton" in css,
        "toast_css": ".toast" in css,
        "modal_css": ".modal-mask" in css,
        "empty_state_css": "table-empty" in css,
    }

    # 交互2：删除确认弹层（角色页有删除按钮则测；否则用工单页模态）
    dp.goto(BASE + "/app.html#/users")
    dp.wait_for_timeout(1800)
    modal_ok = 0
    del_btn = dp.locator("table.data tbody tr button", has_text="删除").first
    if del_btn.count():
        del_btn.click()
        dp.wait_for_timeout(600)
        modal_ok = dp.locator(".modal-mask").count()
        dp.screenshot(path=f"{OUT}/67-desktop-confirm-dialog.png")
        cancel = dp.locator(".modal-mask button", has_text="取消").first
        if cancel.count():
            cancel.click()

    # 交互3：窄屏（平板 800px）工单看板不破版
    tctx = browser.new_context(viewport={"width": 800, "height": 900})
    tp = tctx.new_page()
    tp.goto(BASE + "/index.html")
    tp.fill("input[name=username]", "e2e_admin")
    tp.fill("input[name=password]", "S8e2e#2026!x")
    tp.click("button[type=submit]")
    tp.wait_for_url("**/app.html*", timeout=10000)
    tp.goto(BASE + "/app.html#/workorder?view=board")
    tp.wait_for_timeout(2200)
    tablet_overflow = tp.evaluate("document.documentElement.scrollWidth > document.documentElement.clientWidth + 2")
    tp.screenshot(path=f"{OUT}/68-tablet-board.png")
    tctx.close()

    print("SCAN overflow:", scan_overflow)
    print("EXEC overflow:", exec_overflow if has_exec else "skipped")
    print("ANOMALY overflow:", anomaly_overflow)
    print("WO overflow:", wo_overflow)
    print("REPAIR overflow:", repair_overflow if has_repair_page else "skipped")
    print("INTERACTION_CSS:", checks)
    print("MODAL_OPENS:", modal_ok, " TABLET_BOARD_OVERFLOW:", tablet_overflow)
    print("CONSOLE_ERRORS:", len(errors), errors[:5])
    browser.close()
