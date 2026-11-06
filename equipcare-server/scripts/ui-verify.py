#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
巡维云 EquipCare · 第三阶段前端联调验证（Playwright）

真实浏览器里完成：登录 → 设备列表 → 新增设备 → 详情 → 上传图片 → 二维码 →
打印页 → 用户 / 角色 / 权限 / 分类 / 操作记录，并收集控制台报错与截图。

用法（需安装 playwright 与 chromium）：
    export EQUIPCARE_BASE_URL=http://127.0.0.1:8080
    export EQUIPCARE_ADMIN_USERNAME=e2e_admin
    export EQUIPCARE_ADMIN_PASSWORD='你的密码'
    python scripts/ui-verify.py
"""

import os
import struct
import sys
import time
import zlib

from playwright.sync_api import sync_playwright, expect

BASE_URL = os.environ.get("EQUIPCARE_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
USERNAME = os.environ.get("EQUIPCARE_ADMIN_USERNAME", "admin")
PASSWORD = os.environ.get("EQUIPCARE_ADMIN_PASSWORD", "")
SHOT_DIR = os.environ.get("EQUIPCARE_SHOT_DIR", os.path.join("docs", "screenshots", "stage3"))

errors = []
steps = []


def shot(page, name):
    os.makedirs(SHOT_DIR, exist_ok=True)
    path = os.path.join(SHOT_DIR, name + ".png")
    page.screenshot(path=path, full_page=True)
    steps.append(name)
    print("  [SHOT] %s" % path)


def png_bytes():
    width = height = 160
    rows = []
    for y in range(height):
        rows.append(b"\x00" + b"".join(
            b"\x2f\x6f\xb3" if ((x // 16 + y // 16) % 2 == 0) else b"\xe8\xef\xf7"
            for x in range(width)))
    raw = b"".join(rows)

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 6))
            + chunk(b"IEND", b""))


def main():
    if not PASSWORD:
        print("请设置 EQUIPCARE_ADMIN_PASSWORD")
        return 2

    sample = os.path.join("logs", "ui-sample.png")
    os.makedirs("logs", exist_ok=True)
    with open(sample, "wb") as handle:
        handle.write(png_bytes())

    with sync_playwright() as playwright:
        # 环境已装好的 Chromium 可直接指定，避免重复下载
        chrome = os.environ.get("EQUIPCARE_CHROME")
        browser = playwright.chromium.launch(executable_path=chrome) if chrome else playwright.chromium.launch()
        context = browser.new_context(viewport={"width": 1440, "height": 900}, device_scale_factor=1)
        page = context.new_page()
        page.on("console", lambda msg: errors.append("console: " + msg.text) if msg.type == "error" else None)
        page.on("pageerror", lambda exc: errors.append("pageerror: " + str(exc)))

        print("1. 登录页")
        page.goto(BASE_URL + "/index.html", wait_until="networkidle")
        shot(page, "01-login")
        page.fill("#username", USERNAME)
        page.fill("#password", PASSWORD)
        page.click("#login-submit")
        page.wait_for_url("**/app.html*", timeout=15000)
        page.wait_for_selector("table.data, .table-empty", timeout=15000)
        print("  登录成功 →", page.url)
        shot(page, "02-equipment-list")

        print("2. 新增设备")
        page.click("text=＋ 新增设备")
        page.wait_for_selector("form")
        page.fill("input[name='equipmentName']", "UI 联调测试设备 %s" % time.strftime("%H%M%S"))
        page.fill("input[name='brand']", "联调品牌")
        page.fill("input[name='model']", "UI-2026")
        page.select_option("select[name='categoryId']", index=1)
        page.select_option("select[name='locationId']", index=1)
        shot(page, "03-equipment-new")
        page.click("button[type='submit']:has-text('创建设备')")
        page.wait_for_url("**/app.html#/equipment/*", timeout=15000)
        page.wait_for_selector(".detail-hero", timeout=15000)
        detail_url = page.url
        equipment_id = detail_url.split("/equipment/")[1]
        print("  设备已创建 id=%s" % equipment_id)
        shot(page, "04-equipment-detail")

        print("3. 上传图片")
        page.click("button:has-text('图片与附件')")
        page.wait_for_selector(".file-grid, .empty", timeout=10000)
        page.set_input_files("input[type='file'][accept='image/*']", sample)
        page.wait_for_selector(".file-card", timeout=20000)
        page.wait_for_timeout(600)
        shot(page, "05-equipment-files")

        print("4. 切换标签页查看巡检与维修记录")
        page.click("button:has-text('巡检记录')")
        page.wait_for_timeout(300)
        page.click("button:has-text('维修历史')")
        page.wait_for_timeout(300)
        page.click("button:has-text('档案信息')")
        page.wait_for_timeout(300)

        print("5. 二维码页")
        page.goto(BASE_URL + "/app.html#/equipment/%s/qrcode" % equipment_id, wait_until="networkidle")
        page.wait_for_selector(".qr-preview img[src^='blob:']", timeout=15000)
        shot(page, "06-equipment-qrcode")

        print("6. 打印页")
        page.goto(BASE_URL + "/app.html#/equipment/%s/print" % equipment_id, wait_until="networkidle")
        page.wait_for_selector(".print-sheet", timeout=15000)
        page.wait_for_selector(".print-qr img[src^='blob:']", timeout=15000)
        shot(page, "07-equipment-print")

        print("7. 用户管理")
        page.goto(BASE_URL + "/app.html#/users", wait_until="networkidle")
        page.wait_for_selector("table.data", timeout=15000)
        shot(page, "08-users")
        page.click("text=＋ 新增用户")
        page.wait_for_selector(".modal")
        page.wait_for_timeout(400)
        shot(page, "09-user-create-modal")
        page.keyboard.press("Escape")

        print("8. 角色管理")
        page.goto(BASE_URL + "/app.html#/roles", wait_until="networkidle")
        page.wait_for_selector(".role-row", timeout=15000)
        shot(page, "10-roles")
        page.click(".role-row:has-text('巡检人员') button:has-text('配置权限')")
        page.wait_for_selector(".perm-group", timeout=15000)
        page.wait_for_timeout(400)
        shot(page, "11-role-permissions")
        page.keyboard.press("Escape")

        print("9. 权限管理")
        page.goto(BASE_URL + "/app.html#/permissions", wait_until="networkidle")
        page.wait_for_selector("table.data", timeout=15000)
        shot(page, "12-permissions")

        print("10. 设备分类")
        page.goto(BASE_URL + "/app.html#/categories", wait_until="networkidle")
        page.wait_for_selector("table.data", timeout=15000)
        shot(page, "13-categories")

        print("11. 用户操作记录")
        page.goto(BASE_URL + "/app.html#/audit", wait_until="networkidle")
        page.wait_for_selector("table.data, .table-empty", timeout=15000)
        shot(page, "14-audit")

        print("12. 扫码落地页")
        qr_content = page.evaluate("() => null")
        page.goto(BASE_URL + "/app.html#/equipment/%s/qrcode" % equipment_id, wait_until="networkidle")
        scan_url = page.locator(".qr-content").inner_text().strip()
        page.goto(BASE_URL + scan_url, wait_until="networkidle")
        page.wait_for_selector(".scan-card .scan-top", timeout=15000)
        shot(page, "15-mobile-scan")

        print("13. 个人资料")
        page.goto(BASE_URL + "/app.html#/profile", wait_until="networkidle")
        page.wait_for_selector("form", timeout=15000)
        shot(page, "16-profile")

        browser.close()

    print("\n截图 %d 张 → %s" % (len(steps), SHOT_DIR))
    if errors:
        print("控制台错误 %d 条：" % len(errors))
        for item in errors[:10]:
            print("  -", item)
        return 1
    print("前端联调通过，无控制台错误")
    return 0


if __name__ == "__main__":
    sys.exit(main())
