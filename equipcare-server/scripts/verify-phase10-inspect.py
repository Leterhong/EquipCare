# -*- coding: utf-8 -*-
"""阶段十第六步验证：巡检中心（任务列表/日历双视图 Tab + 状态标签 + 执行卡片增强）"""
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

    # ---- 1. 任务列表：双视图 Tab + 状态标签 ----
    page.goto(BASE + "/app.html#/inspect/task")
    page.wait_for_timeout(2200)
    tabs = page.eval_on_selector_all(".section-tabs button", "els => els.map(e => e.textContent)")
    chips = page.eval_on_selector_all(".status-chips button", "els => els.map(e => e.textContent)")
    rows = page.locator("table.data tbody tr").count()
    page.screenshot(path=f"{OUT}/40-inspect-task-tabs.png")

    # 点状态标签「待执行」
    page.locator(".status-chips button[data-status=PENDING]").click()
    page.wait_for_timeout(1200)
    chip_active = page.locator(".status-chips button.active").get_attribute("data-status")
    summary_text = page.locator(".card-head .hint").first.text_content()
    page.screenshot(path=f"{OUT}/41-inspect-task-status-chip.png")

    # ---- 2. 切到日历 Tab（懒加载） ----
    page.locator(".section-tabs button[data-view=calendar]").click()
    page.wait_for_timeout(1800)
    cal_cells = page.locator(".calendar-cell").count()
    cal_nav = page.locator(".calendar-nav button").count()
    legend = page.locator(".calendar-legend .legend-item").count()
    page.screenshot(path=f"{OUT}/42-inspect-task-calendar-tab.png")

    # 点一个有任务的日期
    has_task = page.locator(".calendar-cell.has-task")
    if has_task.count():
        has_task.first.click()
        page.wait_for_timeout(1200)
    day_rows = page.locator(".card table.data tbody tr").count()
    page.screenshot(path=f"{OUT}/43-inspect-task-calendar-day.png")

    # ---- 3. 独立日历页仍可用 ----
    page.goto(BASE + "/app.html#/inspect/calendar")
    page.wait_for_timeout(1800)
    cal_h1 = page.locator("h1").first.text_content()
    page.screenshot(path=f"{OUT}/44-inspect-calendar-page.png")

    # ---- 4. 执行页：卡片完成态 + 定位按钮 ----
    page.goto(BASE + "/app.html#/inspect/task")
    page.wait_for_timeout(1500)
    exec_btn = page.locator("table.data tbody tr button", has_text="去执行").first
    if exec_btn.count():
        exec_btn.click()
        page.wait_for_timeout(1500)
        # PENDING 任务需先点「开始巡检」
        start_btn = page.locator("button", has_text="开始巡检")
        if start_btn.count() and start_btn.first.is_visible():
            start_btn.first.click()
            page.wait_for_timeout(1200)
        locate_visible = page.locator("button", has_text="定位下一项未判定").count()
        items = page.locator(".exec-item").count()
        # 判定第一项为正常
        if items:
            page.locator(".exec-item").first.locator(".exec-btn").first.click()
            page.wait_for_timeout(300)
            first_done = "done" in (page.locator(".exec-item").first.get_attribute("class") or "")
            page.screenshot(path=f"{OUT}/45-inspect-execute-done-card.png")
        print("EXEC: locate_btn=", locate_visible, "items=", items, "first_done=", first_done)
    else:
        print("EXEC: no executable task found")

    print("TABS:", tabs)
    print("CHIPS:", chips, "ACTIVE_AFTER_CLICK:", chip_active, "SUMMARY:", summary_text)
    print("CAL: cells=", cal_cells, "nav=", cal_nav, "legend=", legend, "day_rows=", day_rows, "page_h1=", cal_h1)
    print("LIST_ROWS_FIRST_LOAD:", rows)
    print("CONSOLE_ERRORS:", len(errors), errors[:3])
    browser.close()
