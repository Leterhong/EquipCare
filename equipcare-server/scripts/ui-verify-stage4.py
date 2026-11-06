#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
巡维云 EquipCare · 第四阶段前端联调验证（Playwright）

真实浏览器里走完巡检中心八个页面：
  模板（新建 + 发布）→ 计划（新建 + 启用 + 立即生成）→ 任务列表 → 任务详情 →
  执行页（三态判定 + 上传现场照片 + 提交）→ 异常（转维修工单）→ 日历 → 统计
同时收集控制台报错与页面异常，逐页截图。

用法：
    export EQUIPCARE_BASE_URL=http://127.0.0.1:8080
    export EQUIPCARE_ADMIN_USERNAME=e2e_admin
    export EQUIPCARE_ADMIN_PASSWORD='你的密码'
    python scripts/ui-verify-stage4.py
"""

import os
import struct
import sys
import time
import zlib

from playwright.sync_api import sync_playwright

BASE_URL = os.environ.get("EQUIPCARE_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
USERNAME = os.environ.get("EQUIPCARE_ADMIN_USERNAME", "e2e_admin")
PASSWORD = os.environ.get("EQUIPCARE_ADMIN_PASSWORD", "")
SHOT_DIR = os.environ.get("EQUIPCARE_SHOT_DIR", os.path.join("docs", "screenshots", "stage4"))

errors = []
steps = []
SUFFIX = time.strftime("%H%M%S")


def shot(page, name):
    os.makedirs(SHOT_DIR, exist_ok=True)
    path = os.path.join(SHOT_DIR, name + ".png")
    page.screenshot(path=path, full_page=True)
    steps.append(name)
    print("  [SHOT] %s" % path)


def png_bytes():
    """生成一张纯色 PNG，用作巡检现场照片。"""
    width = height = 160
    rows = []
    for y in range(height):
        rows.append(b"\x00" + bytes([70, 110, 170]) * width)
    raw = b"".join(rows)

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def go(page, path):
    page.goto(BASE_URL + "/app.html#" + path, wait_until="domcontentloaded")
    page.wait_for_selector(".page-head", timeout=15000)
    page.wait_for_timeout(500)


def main():
    if not PASSWORD:
        print("请设置 EQUIPCARE_ADMIN_PASSWORD")
        return 2

    sample = os.path.join("logs", "inspect-photo.png")
    os.makedirs("logs", exist_ok=True)
    with open(sample, "wb") as handle:
        handle.write(png_bytes())

    with sync_playwright() as playwright:
        chrome = os.environ.get("EQUIPCARE_CHROME")
        browser = playwright.chromium.launch(executable_path=chrome) if chrome else playwright.chromium.launch()
        context = browser.new_context(viewport={"width": 1440, "height": 960}, device_scale_factor=1)
        page = context.new_page()
        page.on("console", lambda msg: errors.append("console: " + msg.text) if msg.type == "error" else None)
        page.on("pageerror", lambda exc: errors.append("pageerror: " + str(exc)))

        def watch(response):
            # 登录前的 401 属于正常探活，其余 4xx/5xx 都要暴露出来便于排查
            if response.status >= 400 and response.status != 401:
                errors.append(f"HTTP {response.status} {response.request.method} {response.url}")

        page.on("response", watch)

        print("1. 登录")
        page.goto(BASE_URL + "/index.html", wait_until="networkidle")
        page.fill("#username", USERNAME)
        page.fill("#password", PASSWORD)
        page.click("#login-submit")
        page.wait_for_url("**/app.html*", timeout=20000)
        page.wait_for_selector("table.data, .table-empty", timeout=20000)
        print("   登录成功")

        print("2. 巡检模板：新建 + 发布")
        go(page, "/inspect/template")
        shot(page, "01-template-list")
        page.click("text=＋ 新建模板")
        page.wait_for_selector(".modal")
        page.fill("input[name='templateName']", "UI 联调水泵模板 %s" % SUFFIX)
        page.fill("textarea[name='description']", "浏览器联调创建的模板")
        page.fill(".tpl-item:nth-child(1) input[name='itemName']", "检查设备运行声音")
        page.click("text=＋ 添加检查项")
        page.fill(".tpl-item:nth-child(2) input[name='itemName']", "检查设备外壳温度")
        page.select_option(".tpl-item:nth-child(2) select[data-field='itemType']", "NUMBER")
        page.wait_for_timeout(300)
        page.fill(".tpl-item:nth-child(2) input[data-field='minValue']", "0")
        page.fill(".tpl-item:nth-child(2) input[data-field='maxValue']", "60")
        page.fill(".tpl-item:nth-child(2) input[data-field='unit']", "℃")
        page.fill(".tpl-item:nth-child(2) input[data-field='standardRef']", "GB/T 29529-2013")
        page.fill(".tpl-item:nth-child(2) input[data-field='abnormalRule']", "超出 0~60℃ 判为异常")
        page.check(".tpl-item:nth-child(2) input[data-field='photoRequired']")
        shot(page, "02-template-editor")
        page.click(".modal-foot button:has-text('保存')")
        page.wait_for_selector(".modal", state="detached", timeout=15000)
        page.wait_for_timeout(800)
        page.click("button[data-action='publish']")
        page.wait_for_timeout(900)
        shot(page, "03-template-published")

        print("3. 巡检计划：新建 + 启用 + 立即生成")
        go(page, "/inspect/plan")
        page.click("text=＋ 新建计划")
        page.wait_for_selector(".modal")
        page.fill("input[name='planName']", "UI 联调每日巡检 %s" % SUFFIX)
        # 模板下拉只列已发布模板；选最新一个（列表按创建时间倒序时即为本次新建的）
        template_values = page.eval_on_selector_all(
            "select[name='templateId'] option", "els => els.map(e => e.value).filter(v => v)")
        if not template_values:
            print("   [FAIL] 模板下拉为空：确认上一步模板已发布")
            errors.append("模板下拉为空")
            return 1
        page.select_option("select[name='templateId']", template_values[-1])
        page.wait_for_timeout(300)
        # 作用域：按分类，勾第一个分类
        page.locator(".picker-list").nth(0).locator("input[type='checkbox']").first.check()
        # 执行人：勾第一个人员
        page.locator(".picker-list").nth(1).locator("input[type='checkbox']").first.check()
        page.fill("input[name='executeTime']", "07:30")
        page.fill("input[name='generateAheadHours']", "0")
        shot(page, "04-plan-editor")
        page.click(".modal-foot button:has-text('保存')")
        page.wait_for_selector(".modal", state="detached", timeout=15000)
        page.wait_for_timeout(900)
        shot(page, "05-plan-list")
        page.click("button[data-action='enable']")
        page.wait_for_timeout(900)
        page.click("button[data-action='generate']")
        page.wait_for_timeout(1200)
        shot(page, "06-plan-generated")

        print("4. 巡检任务列表与详情")
        go(page, "/inspect/task")
        page.wait_for_selector("table.data, .table-empty", timeout=15000)
        shot(page, "07-task-list")
        page.click("button[data-action='detail']")
        page.wait_for_selector(".detail-hero", timeout=15000)
        page.wait_for_timeout(800)
        shot(page, "08-task-detail")

        print("5. 执行页：三态判定 + 现场照片 + 提交")
        page.click("text=去执行 →")
        # 待执行任务先点「开始巡检」，填报区才会展开
        page.wait_for_selector("button:has-text('开始巡检')", timeout=15000)
        page.click("button:has-text('开始巡检')")
        page.wait_for_selector(".exec-list", state="visible", timeout=15000)
        page.wait_for_timeout(500)
        # 第 1 项判正常（是否项必须选值，否则"正常"没有依据）；第 2 项判异常，填原因并上传照片
        page.locator(".exec-item").nth(0).locator("button:has-text('正常')").click()
        page.locator(".exec-item").nth(0).locator("select").select_option("1")
        page.locator(".exec-item").nth(1).locator("button:has-text('异常')").click()
        page.wait_for_timeout(300)
        page.locator(".exec-item").nth(1).locator(".exec-extra textarea").fill("外壳温度偏高，怀疑散热不良")
        page.locator(".exec-item").nth(1).locator("input[type='file']").set_input_files(sample)
        page.wait_for_timeout(1500)
        shot(page, "09-inspect-execute")
        page.click("button:has-text('提交巡检结果')")
        page.wait_for_selector(".detail-hero", timeout=20000)
        page.wait_for_timeout(1000)
        shot(page, "10-task-submitted")

        print("6. 异常记录：转维修工单")
        go(page, "/inspect/anomaly")
        page.wait_for_timeout(900)
        shot(page, "11-anomaly-list")
        if page.locator("button:has-text('转维修工单')").count():
            page.click("button:has-text('转维修工单')")
            page.wait_for_selector(".modal")
            page.fill(".modal textarea", "浏览器联调转单")
            shot(page, "12-anomaly-convert")
            page.click(".modal-foot button:has-text('创建工单')")
            page.wait_for_timeout(1400)
            shot(page, "13-anomaly-converted")
        else:
            print("   [WARN] 当前没有可转单的异常（可能已被合并或在途）")

        print("7. 巡检日历")
        go(page, "/inspect/calendar")
        page.wait_for_selector(".calendar-grid", timeout=15000)
        page.wait_for_timeout(800)
        # 点一个有任务的日期，看当天任务
        cells = page.locator(".calendar-cell.has-task")
        if cells.count():
            cells.first.click()
            page.wait_for_timeout(1000)
        shot(page, "14-inspect-calendar")

        print("8. 巡检统计")
        go(page, "/inspect/stats")
        page.wait_for_selector(".metric-grid", timeout=15000)
        page.wait_for_timeout(1200)
        shot(page, "15-inspect-stats")

        print("9. 清理：暂停联调计划（模板保留，便于人工查看）")
        go(page, "/inspect/plan")
        page.wait_for_timeout(600)
        if page.locator("button[data-action='disable']").count():
            # 暂停/启用是可逆操作，设计上直接生效，不再弹二次确认
            page.locator("button[data-action='disable']").first.click()
            page.wait_for_timeout(900)
            print("   计划已暂停")

        browser.close()

    print("\n截图 %d 张，控制台错误 %d 条" % (len(steps), len(errors)))
    for item in errors:
        print("  [ERROR] " + item)
    return 0 if not errors else 1


if __name__ == "__main__":
    sys.exit(main())
