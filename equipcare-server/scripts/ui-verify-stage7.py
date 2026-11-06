#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
巡维云 EquipCare · 第七阶段前端联调验证（Playwright）

把第七阶段新页面在真实浏览器里走一遍，逐页截图并收集控制台错误与非预期 HTTP 4xx/5xx：
  管理员：运维驾驶舱（14 KPI + 8 张 ECharts，切换时间范围 / 自定义范围校验）
        → 消息中心（未读 / 已读页签、全选 + 批量标记已读）
        → 用户操作记录（审计筛选）

驾驶舱图表为真实接口数据（本地 echarts.min.js 渲染），脚本校验 KPI 数量、
canvas 是否渲染、空范围提示与「暂无数据」占位；业务口径由 Stage7IT 与
stage7-smoke.py 覆盖，本脚本聚焦页面渲染与交互可用。

用法：
    export EQUIPCARE_BASE_URL=http://127.0.0.1:8080
    export EQUIPCARE_ADMIN_USERNAME=e2e_admin
    export EQUIPCARE_ADMIN_PASSWORD='管理员口令'
    python scripts/ui-verify-stage7.py
"""

import json
import os
import sys

from playwright.sync_api import sync_playwright

BASE_URL = os.environ.get("EQUIPCARE_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
ADMIN_USER = os.environ.get("EQUIPCARE_ADMIN_USERNAME", "e2e_admin")
ADMIN_PASSWORD = os.environ.get("EQUIPCARE_ADMIN_PASSWORD", "")
SHOT_DIR = os.environ.get("EQUIPCARE_SHOT_DIR", os.path.join("docs", "screenshots", "stage7"))

errors = []
steps = []
ADMIN_TOKEN = None


# --------------------------------------------------------------------- 工具


def shot(page, name):
    os.makedirs(SHOT_DIR, exist_ok=True)
    path = os.path.join(SHOT_DIR, name + ".png")
    page.screenshot(path=path, full_page=True)
    steps.append(name)
    print("  [SHOT] %s" % path)


def api(method, path, body=None):
    headers = {"Content-Type": "application/json"}
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    if ADMIN_TOKEN:
        headers["Authorization"] = "Bearer " + ADMIN_TOKEN
    import urllib.error
    import urllib.request
    request = urllib.request.Request(BASE_URL + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        text = error.read().decode("utf-8", "ignore")
        raise RuntimeError("%s %s 失败：HTTP %s %s" % (method, path, error.code, text[:200]))


def go(page, path):
    page.goto(BASE_URL + "/app.html#" + path, wait_until="domcontentloaded")
    page.wait_for_selector(".page-head, .card, .kpi-grid", timeout=20000)
    page.wait_for_timeout(800)


def login(browser):
    context = browser.new_context(viewport={"width": 1440, "height": 980}, device_scale_factor=1)
    page = context.new_page()
    page.set_default_timeout(20000)
    page.on("console", lambda msg: errors.append("console: %s" % msg.text)
            if msg.type == "error" else None)
    page.on("pageerror", lambda exc: errors.append("pageerror: %s" % exc))

    def watch(response):
        if response.status >= 400 and response.status != 401:
            errors.append("HTTP %s %s %s" % (response.status, response.request.method, response.url))

    page.on("response", watch)
    page.goto(BASE_URL + "/index.html", wait_until="networkidle")
    page.fill("#username", ADMIN_USER)
    page.fill("#password", ADMIN_PASSWORD)
    page.click("#login-submit")
    page.wait_for_url("**/app.html*", timeout=25000)
    page.wait_for_selector("table.data, .kpi-grid, .card", timeout=25000)
    print("   已登录：%s" % ADMIN_USER)
    return context, page


def kpi_count(page):
    return page.locator(".kpi-grid .stat").count()


def chart_canvas_count(page):
    return page.locator(".chart-dom canvas").count()


def empty_chart_count(page):
    return page.locator(".chart-empty").count()


# --------------------------------------------------------------------- 主流程


def main():
    global ADMIN_TOKEN
    if not ADMIN_PASSWORD:
        print("请设置 EQUIPCARE_ADMIN_PASSWORD")
        return 2

    session = api("POST", "/api/auth/login", {"username": ADMIN_USER, "password": ADMIN_PASSWORD})
    ADMIN_TOKEN = session["data"]["accessToken"]
    print("0. 接口登录成功")

    with sync_playwright() as playwright:
        chrome = os.environ.get("EQUIPCARE_CHROME")
        browser = playwright.chromium.launch(executable_path=chrome) if chrome \
            else playwright.chromium.launch()
        context, page = login(browser)

        print("1. 运维驾驶舱：14 KPI + 8 图表")
        go(page, "/dashboard")
        page.wait_for_selector(".kpi-grid", timeout=20000)
        page.wait_for_timeout(1500)  # 等 ECharts 渲染
        total = kpi_count(page)
        canvases = chart_canvas_count(page)
        empties = empty_chart_count(page)
        print("   KPI 卡 %d 张，canvas 图 %d 个，暂无数据占位 %d 个" % (total, canvases, empties))
        if total < 14:
            raise RuntimeError("驾驶舱 KPI 应有 14 张，实际 %d" % total)
        if canvases + empties < 8:
            raise RuntimeError("驾驶舱应有 8 个图表位（canvas 或暂无数据），实际 %d"
                               % (canvases + empties))
        shot(page, "01-dashboard-default")

        print("2. 驾驶舱：切换「近 90 天」范围")
        range_select = page.locator("select[aria-label='统计范围']")
        if range_select.count():
            range_select.select_option(label="近 90 天")
            page.wait_for_timeout(1800)
            shot(page, "02-dashboard-90d")
        else:
            print("   [WARN] 未找到统计范围下拉")

        print("3. 驾驶舱：自定义非法范围（起始晚于结束）应提示")
        start_input = page.locator("input[aria-label='起始日期']")
        end_input = page.locator("input[aria-label='结束日期']")
        if start_input.count() and end_input.count():
            range_select.select_option("custom")
            page.wait_for_timeout(400)
            start_input.fill("2026-12-31")
            end_input.fill("2026-01-01")
            page.locator("button", has_text="应用").first.click()
            page.wait_for_timeout(800)
            hint = page.locator(".page-actions .hint").inner_text()
            if "起始日期不能晚于结束日期" not in hint:
                print("   [WARN] 范围提示为：%s" % hint)
            shot(page, "03-dashboard-invalid-range")
            start_input.fill("2026-10-01")
            end_input.fill("2026-10-31")
            page.locator("button", has_text="应用").first.click()
            page.wait_for_timeout(1200)
        else:
            print("   [WARN] 未找到日期输入")

        print("4. 消息中心：页签与列表")
        go(page, "/messages")
        page.wait_for_selector(".msg-item, .table-empty, .card", timeout=20000)
        page.wait_for_timeout(800)
        shot(page, "04-messages-list")

        print("5. 消息中心：勾选消息 + 批量标记已读")
        checkboxes = page.locator(".msg-item input[type='checkbox']")
        if checkboxes.count() >= 1:
            for i in range(min(2, checkboxes.count())):
                checkboxes.nth(i).check()
            page.wait_for_timeout(400)
            shot(page, "05-messages-selected")
            if page.locator("button", has_text="批量标记已读").count():
                page.locator("button", has_text="批量标记已读").first.click()
                page.wait_for_timeout(1200)
                shot(page, "06-messages-read-done")
        else:
            print("   [WARN] 没有可勾选的消息")

        print("6. 用户操作记录（审计）：模块筛选查询")
        go(page, "/audit")
        page.wait_for_selector("table.data, .table-empty", timeout=20000)
        page.wait_for_timeout(800)
        shot(page, "07-audit-list")
        module_input = page.locator("input[aria-label='模块']")
        if module_input.count():
            module_input.fill("认证")
            page.locator("button", has_text="查询").first.click()
            page.wait_for_timeout(1200)
            shot(page, "08-audit-filtered")

        context.close()
        browser.close()

    print("\n截图 %d 张，控制台 / 网络错误 %d 条" % (len(steps), len(errors)))
    for item in errors:
        print("  [ERROR] " + item)
    return 0 if not errors else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except RuntimeError as error:
        print("\n[中断] %s" % error)
        sys.exit(1)
