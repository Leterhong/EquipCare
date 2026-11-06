#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
巡维云 EquipCare · 第六阶段前端联调验证（Playwright）

把第六阶段九个新页面在真实浏览器里走一遍，逐页截图并收集控制台错误与非预期 HTTP 4xx/5xx：
  管理员：备件管理（分类管理弹层）→ 库存管理（盘点调整弹层）→ 备件入库（真实提交 1 件）
        → 备件出库（真实提交 1 件）→ 库存流水 → 库存预警 → SLA 预警中心（立即扫描）
        → 设备健康列表 → 设备健康详情（风险明细 + 建议）

入库 / 出库提交后会通过接口回查库存，确保「界面操作真的改了数据」；
库存与 SLA 的业务闭环由 stage6-smoke.py 覆盖，本脚本聚焦页面渲染与交互可用。

用法：
    export EQUIPCARE_BASE_URL=http://127.0.0.1:8080
    export EQUIPCARE_ADMIN_USERNAME=admin
    export EQUIPCARE_ADMIN_PASSWORD='管理员口令'
    python scripts/ui-verify-stage6.py
"""

import json
import os
import sys
import time
import urllib.error
import urllib.request

from playwright.sync_api import sync_playwright

BASE_URL = os.environ.get("EQUIPCARE_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
ADMIN_USER = os.environ.get("EQUIPCARE_ADMIN_USERNAME", "admin")
ADMIN_PASSWORD = os.environ.get("EQUIPCARE_ADMIN_PASSWORD", "")
SHOT_DIR = os.environ.get("EQUIPCARE_SHOT_DIR", os.path.join("docs", "screenshots", "stage6"))

SUFFIX = time.strftime("%H%M%S")
PART_NO = "UI6-" + SUFFIX

errors = []
steps = []
ADMIN_TOKEN = None
PART_ID = None
CATEGORY_ID = None
EQUIPMENT_ID = None


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
    request = urllib.request.Request(BASE_URL + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        text = error.read().decode("utf-8", "ignore")
        raise RuntimeError("%s %s 失败：HTTP %s %s" % (method, path, error.code, text[:200]))


def api_ok(method, path, body=None):
    payload = api(method, path, body)
    if payload.get("code") != 200:
        raise RuntimeError("%s %s 业务失败：%s" % (method, path, payload.get("message")))
    return payload.get("data")


def go(page, path):
    page.goto(BASE_URL + "/app.html#" + path, wait_until="domcontentloaded")
    page.wait_for_selector(".page-head, .card", timeout=20000)
    page.wait_for_timeout(600)


def field(page, label):
    """按 label 精确定位一个表单项。"""
    return page.locator(".field").filter(has=page.locator("label:text-is('%s')" % label))


def select_part(page, label):
    """备件下拉异步填充：等出现非空 value 的选项再选，最后回读确认。"""
    box = field(page, label).locator("select")
    box.wait_for(state="visible", timeout=20000)
    page.wait_for_function(
        "(el) => !!el && [...el.options].some((o) => o.value === '%s')" % str(PART_ID),
        arg=box.element_handle(),
        timeout=20000,
    )
    box.select_option(str(PART_ID))
    if not box.input_value():
        raise RuntimeError("下拉「%s」选择后仍为空" % label)


def part_stock():
    page_data = api_ok("GET", "/api/inventory/parts?pageNum=1&pageSize=20&keyword=" + PART_NO)
    for row in page_data.get("records") or []:
        if row.get("id") == PART_ID:
            return int(row.get("currentStock") or 0)
    raise RuntimeError("备件 %s 查不到" % PART_NO)


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
    page.wait_for_selector("table.data, .table-empty", timeout=25000)
    print("   已登录：%s" % ADMIN_USER)
    return context, page


# --------------------------------------------------------------------- 准备


def prepare():
    """通过接口准备：分类、备件（入库 3 件，低于安全库存 5）、一台干净设备并重算健康分。"""
    global ADMIN_TOKEN, PART_ID, CATEGORY_ID, EQUIPMENT_ID
    session = api_ok("POST", "/api/auth/login", {"username": ADMIN_USER, "password": ADMIN_PASSWORD})
    ADMIN_TOKEN = session["accessToken"]

    CATEGORY_ID = api_ok("POST", "/api/inventory/categories", {
        "categoryName": "UI联调分类6_" + SUFFIX, "remark": "第六阶段 UI 联调", "sort": 98})
    PART_ID = api_ok("POST", "/api/inventory/parts", {
        "partNo": PART_NO, "partName": "UI联调轴承_" + SUFFIX, "categoryId": CATEGORY_ID,
        "spec": "6208-2Z", "unit": "套", "safetyStock": 5, "unitPrice": 30})
    api_ok("POST", "/api/inventory/stock/in", {
        "partId": PART_ID, "quantity": 3, "unitPrice": 30, "remark": "UI 联调初始库存"})
    if part_stock() != 3:
        raise RuntimeError("初始入库后库存应为 3")

    equipments = api_ok("GET", "/api/asset/equipment?pageNum=1&pageSize=1")["records"]
    if not equipments:
        raise RuntimeError("设备表为空，请先执行种子数据")
    base = equipments[0]
    EQUIPMENT_ID = base["id"]
    api_ok("POST", "/api/analytics/health/%d/recalculate" % EQUIPMENT_ID)
    print("   准备完成：备件 id=%s（库存 3），健康设备 id=%s" % (PART_ID, EQUIPMENT_ID))


# --------------------------------------------------------------------- 主流程


def main():
    if not ADMIN_PASSWORD:
        print("请设置 EQUIPCARE_ADMIN_PASSWORD")
        return 2

    print("0. 接口准备数据")
    prepare()

    with sync_playwright() as playwright:
        chrome = os.environ.get("EQUIPCARE_CHROME")
        browser = playwright.chromium.launch(executable_path=chrome) if chrome \
            else playwright.chromium.launch()
        context, page = login(browser)

        print("1. 备件管理：档案列表 + 分类管理弹层")
        go(page, "/spare/part")
        page.wait_for_selector("table.data, .table-empty", timeout=20000)
        page.wait_for_timeout(700)
        shot(page, "01-spare-part")
        if page.locator("button", has_text="分类管理").count():
            page.locator("button", has_text="分类管理").first.click()
            page.wait_for_selector(".modal", timeout=15000)
            page.wait_for_timeout(500)
            shot(page, "02-spare-part-categories")
            page.locator(".modal [aria-label='关闭']").first.click()
            page.wait_for_timeout(600)
            if page.locator(".modal").count():
                raise RuntimeError("分类管理弹层没有关闭")
        else:
            print("   [WARN] 没有分类管理按钮（权限未下发？）")

        print("2. 库存管理：总览 + 盘点调整弹层")
        go(page, "/spare/stock")
        page.wait_for_selector("table.data, .table-empty", timeout=20000)
        page.wait_for_timeout(700)
        shot(page, "03-spare-stock")
        if page.locator("button[data-action='adjust']").count():
            page.locator("button[data-action='adjust']").first.click()
            page.wait_for_selector(".modal", timeout=15000)
            page.wait_for_timeout(500)
            shot(page, "04-spare-stock-adjust-modal")
            page.locator(".modal-foot button:has-text('取消'), .modal button:has-text('取消')").first.click()
            page.wait_for_timeout(500)
        else:
            print("   [WARN] 没有可盘点的备件行")

        print("3. 备件入库：界面提交 1 件并回查库存")
        go(page, "/spare/stock/in")
        select_part(page, "备件 *")
        field(page, "入库数量 *").locator("input").fill("1")
        field(page, "备注").locator("input").fill("UI 联调入库一件")
        shot(page, "05-spare-stock-in-form")
        page.click("button:has-text('确认入库')")
        page.wait_for_timeout(2000)
        if part_stock() != 4:
            raise RuntimeError("入库提交后库存应为 4，实际 %s，提示：%s"
                               % (part_stock(), page.locator(".toast").last.inner_text()
                                  if page.locator(".toast").count() else "无"))
        shot(page, "06-spare-stock-in-done")

        print("4. 备件出库：界面提交 1 件并回查库存")
        go(page, "/spare/stock/out")
        select_part(page, "备件 *")
        field(page, "出库数量 *").locator("input").fill("1")
        field(page, "出库原因 / 备注 *").locator("input").fill("UI 联调出库一件")
        shot(page, "07-spare-stock-out-form")
        page.click("button:has-text('确认出库')")
        page.wait_for_timeout(2000)
        if part_stock() != 3:
            raise RuntimeError("出库提交后库存应为 3，实际 %s，提示：%s"
                               % (part_stock(), page.locator(".toast").last.inner_text()
                                  if page.locator(".toast").count() else "无"))
        shot(page, "08-spare-stock-out-done")

        print("5. 库存流水")
        go(page, "/spare/txn")
        page.wait_for_selector("table.data, .table-empty", timeout=20000)
        page.wait_for_timeout(700)
        shot(page, "09-spare-txn")

        print("6. 库存预警")
        go(page, "/spare/alert")
        page.wait_for_selector("table.data, .table-empty", timeout=20000)
        page.wait_for_timeout(700)
        shot(page, "10-spare-alert")

        print("7. SLA 预警中心：视图 + 立即扫描")
        go(page, "/sla/monitor")
        page.wait_for_selector("table.data, .card", timeout=20000)
        page.wait_for_timeout(900)
        shot(page, "11-sla-monitor")
        if page.locator("button", has_text="立即扫描").count():
            page.locator("button", has_text="立即扫描").first.click()
            page.wait_for_timeout(2000)
            shot(page, "12-sla-monitor-scanned")

        print("8. 设备健康列表")
        go(page, "/health")
        page.wait_for_selector("table.data, .table-empty", timeout=20000)
        page.wait_for_timeout(700)
        shot(page, "13-health-list")

        print("9. 设备健康详情：风险明细 + 建议")
        go(page, "/health/%d" % EQUIPMENT_ID)
        page.wait_for_selector(".card", timeout=20000)
        page.wait_for_timeout(900)
        shot(page, "14-health-detail")
        context.close()
        browser.close()

    print("\n截图 %d 张，控制台 / 网络错误 %d 条" % (len(steps), len(errors)))
    for item in errors:
        print("  [ERROR] " + item)
    print("本次备件 id=%s（编码 %s），健康设备 id=%s" % (PART_ID, PART_NO, EQUIPMENT_ID))
    return 0 if not errors else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except RuntimeError as error:
        print("\n[中断] %s" % error)
        sys.exit(1)
