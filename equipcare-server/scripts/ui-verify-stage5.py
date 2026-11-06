#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
巡维云 EquipCare · 第五阶段前端联调验证（Playwright）

在真实浏览器里把工单中心九个页面走一遍，三种角色各用一个浏览器上下文：
  管理员：工单列表 → 报修（提交）→ 工单详情 → 派单工作台（派单）→ 操作时间线
  维修工：维修工作台 → 维修过程记录（开始维修 → 过程记录 → 完工说明 → 提交验收）
  验收员：维修验收（通过）
  管理员：工单评价（三维度打分 + 文字评价）

脚本会先通过接口准备数据（临时维修工 / 临时验收员 / 一张待派单工单），
然后用界面完成真实操作；逐页截图，并收集控制台错误与非预期 HTTP 4xx/5xx。

用法：
    export EQUIPCARE_BASE_URL=http://127.0.0.1:8080
    export EQUIPCARE_ADMIN_USERNAME=admin
    export EQUIPCARE_ADMIN_PASSWORD='管理员口令'
    python scripts/ui-verify-stage5.py
"""

import json
import os
import re
import struct
import sys
import time
import urllib.error
import urllib.request
import zlib

from playwright.sync_api import sync_playwright

BASE_URL = os.environ.get("EQUIPCARE_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
ADMIN_USER = os.environ.get("EQUIPCARE_ADMIN_USERNAME", "admin")
ADMIN_PASSWORD = os.environ.get("EQUIPCARE_ADMIN_PASSWORD", "")
SHOT_DIR = os.environ.get("EQUIPCARE_SHOT_DIR", os.path.join("docs", "screenshots", "stage5"))

SUFFIX = time.strftime("%H%M%S")
REPAIR_PASSWORD = "Ui5rep!" + SUFFIX
VERIFIER_PASSWORD = "Ui5ver!" + SUFFIX
REPAIR_USER = "ui5rep" + SUFFIX
VERIFIER_USER = "ui5ver" + SUFFIX

errors = []
steps = []
ADMIN_TOKEN = None
TEMP_USER_IDS = []


# --------------------------------------------------------------------- 工具


def shot(page, name):
    os.makedirs(SHOT_DIR, exist_ok=True)
    path = os.path.join(SHOT_DIR, name + ".png")
    page.screenshot(path=path, full_page=True)
    steps.append(name)
    print("  [SHOT] %s" % path)


def png_bytes():
    """生成一张纯色 PNG，用作故障现场 / 维修前后照片。"""
    width = height = 160
    rows = [b"\x00" + bytes([92, 116, 158]) * width for _ in range(height)]
    raw = b"".join(rows)

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def api(method, path, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    use = token or ADMIN_TOKEN
    if use:
        headers["Authorization"] = "Bearer " + use
    request = urllib.request.Request(BASE_URL + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        text = error.read().decode("utf-8", "ignore")
        raise RuntimeError("%s %s 失败：HTTP %s %s" % (method, path, error.code, text[:200]))


def api_ok(method, path, body=None, token=None):
    payload = api(method, path, body, token)
    if payload.get("code") != 200:
        raise RuntimeError("%s %s 业务失败：%s" % (method, path, payload.get("message")))
    return payload.get("data")


def go(page, path):
    page.goto(BASE_URL + "/app.html#" + path, wait_until="domcontentloaded")
    # 有的页面在状态不满足时只渲染一张提示卡（没有 .page-head），所以两者都算渲染完成
    page.wait_for_selector(".page-head, .card", timeout=20000)
    page.wait_for_timeout(500)


def field(page, label):
    """按 label 精确定位一个表单项，避免「设备」误命中「故障设备」之类。"""
    return page.locator(".field").filter(has=page.locator("label:text-is('%s')" % label))


def toast_text(page):
    """取当前提示条文案，失败时用它说明「为什么没成功」。"""
    nodes = page.locator(".toast")
    if nodes.count() == 0:
        return "（无提示条）"
    return " / ".join(nodes.nth(i).inner_text() for i in range(nodes.count()))


def select_first(page, label):
    """
    选中下拉里第一个「真实选项」。

    设备下拉是异步填充的，页面刚渲染时只有「加载中…」；按 index 选会撞上空档，
    所以这里先等出现非空 value 的选项，再按 value 选，最后回读确认——
    回读这一步能立刻暴露「看着选了、其实还是空」的情况。
    """
    box = field(page, label).locator("select")
    box.wait_for(state="visible", timeout=20000)
    page.wait_for_function(
        "(el) => !!el && [...el.options].some((o) => o.value)",
        arg=box.element_handle(),
        timeout=20000,
    )
    value = box.evaluate("(el) => ([...el.options].find((o) => o.value) || {}).value || ''")
    if not value:
        raise RuntimeError("下拉「%s」没有可选项" % label)
    box.select_option(value)
    if not box.input_value():
        raise RuntimeError("下拉「%s」选择后仍为空" % label)
    return value


ORDER_URL = re.compile(r".*#/workorder/\d+$")


def wait_detail(page, order_id=None):
    """等路由落到某张工单详情。用正则而不是 glob：'#/workorder/*' 会把 /new 也算命中。"""
    pattern = re.compile(r".*#/workorder/%s$" % (order_id if order_id else r"\d+"))
    page.wait_for_url(pattern, timeout=25000)
    page.wait_for_timeout(1200)


def login(browser, username, password, tag):
    context = browser.new_context(viewport={"width": 1440, "height": 980}, device_scale_factor=1)
    page = context.new_page()
    page.set_default_timeout(20000)
    page.on("console", lambda msg: errors.append("%s console: %s" % (tag, msg.text))
            if msg.type == "error" else None)
    page.on("pageerror", lambda exc: errors.append("%s pageerror: %s" % (tag, exc)))

    def watch(response):
        if response.status >= 400 and response.status != 401:
            errors.append("%s HTTP %s %s %s" % (tag, response.status,
                                                response.request.method, response.url))

    page.on("response", watch)
    page.goto(BASE_URL + "/index.html", wait_until="networkidle")
    page.fill("#username", username)
    page.fill("#password", password)
    page.click("#login-submit")
    page.wait_for_url("**/app.html*", timeout=25000)
    page.wait_for_selector("table.data, .table-empty", timeout=25000)
    print("   已登录：%s" % username)
    return context, page


# --------------------------------------------------------------------- 准备


def prepare():
    """通过接口准备：临时维修工、临时验收员、一张待派单工单。"""
    global ADMIN_TOKEN
    session = api_ok("POST", "/api/auth/login", {"username": ADMIN_USER, "password": ADMIN_PASSWORD})
    ADMIN_TOKEN = session["accessToken"]

    roles = api_ok("GET", "/api/system/roles")
    role_ids = {role["roleCode"]: role["id"] for role in roles}
    for code in ("REPAIRMAN", "VERIFIER"):
        if code not in role_ids:
            raise RuntimeError("角色 %s 不存在，请先初始化 05 阶段权限" % code)

    repair_id = api_ok("POST", "/api/system/users", {
        "username": REPAIR_USER, "password": REPAIR_PASSWORD,
        "realName": "联调维修工" + SUFFIX, "roleIds": [role_ids["REPAIRMAN"]],
    })
    verifier_id = api_ok("POST", "/api/system/users", {
        "username": VERIFIER_USER, "password": VERIFIER_PASSWORD,
        "realName": "联调验收员" + SUFFIX, "roleIds": [role_ids["VERIFIER"]],
    })
    TEMP_USER_IDS.extend([repair_id, verifier_id])

    equipments = api_ok("GET", "/api/asset/equipment?pageNum=1&pageSize=1")["records"]
    if not equipments:
        raise RuntimeError("设备表为空，请先执行种子数据")
    equipment_id = equipments[0]["id"]

    # 待派单工单：给派单工作台用
    pending_id = api_ok("POST", "/api/workorder", {
        "equipmentId": equipment_id,
        "title": "联调_待派单_冷却塔风机抖动" + SUFFIX,
        "faultType": "MECHANICAL",
        "faultDesc": "冷却塔风机运行抖动明显，底座螺栓有松动痕迹。",
        "level": "P2",
    })
    # 已派单工单：给维修 / 验收 / 评价链路用
    order_id = api_ok("POST", "/api/workorder", {
        "equipmentId": equipment_id,
        "title": "联调_水泵轴承异响" + SUFFIX,
        "faultType": "MECHANICAL",
        "faultDesc": "水泵运行中轴承位持续异响，温度偏高，怀疑润滑失效。",
        "level": "P2",
    })
    api_ok("POST", "/api/workorder/%d/dispatch" % order_id, {
        "assigneeId": repair_id, "priority": "HIGH", "resolveHours": 8,
        "reason": "联调派单：影响生产，8 小时内完成",
    })
    print("   准备完成：待派单 id=%s，已派单 id=%s" % (pending_id, order_id))
    return order_id, pending_id


# --------------------------------------------------------------------- 主流程


def main():
    if not ADMIN_PASSWORD:
        print("请设置 EQUIPCARE_ADMIN_PASSWORD")
        return 2

    sample = os.path.join("logs", "workorder-photo.png")
    os.makedirs("logs", exist_ok=True)
    with open(sample, "wb") as handle:
        handle.write(png_bytes())

    print("0. 接口准备数据")
    order_id, pending_id = prepare()

    with sync_playwright() as playwright:
        chrome = os.environ.get("EQUIPCARE_CHROME")
        browser = playwright.chromium.launch(executable_path=chrome) if chrome \
            else playwright.chromium.launch()

        # ------------------------------------------------ 管理员：报修与派单
        context_a, page = login(browser, ADMIN_USER, ADMIN_PASSWORD, "admin")

        print("1. 工单列表")
        go(page, "/workorder")
        page.wait_for_selector("table.data, .table-empty", timeout=20000)
        page.wait_for_timeout(600)
        shot(page, "01-workorder-list")

        print("2. 报修页面：填表并提交")
        go(page, "/workorder/new")
        select_first(page, "设备")
        field(page, "故障标题").locator("input").fill("联调_空压机机头异响 " + SUFFIX)
        field(page, "故障描述").locator("textarea").fill(
            "开机 10 分钟后机头异响加剧，排气温度升至 95℃，已临时降载运行。")
        page.locator("input[type='file']").first.set_input_files(sample)
        page.wait_for_timeout(1800)
        shot(page, "02-workorder-form")
        page.click("button:has-text('提交报修')")
        try:
            wait_detail(page)
        except Exception:
            raise RuntimeError("提交报修没有跳转，页面提示：%s" % toast_text(page))
        shot(page, "03-workorder-detail")

        print("3. 派单工作台：展开派单弹层并派单")
        go(page, "/workorder/dispatch")
        page.wait_for_timeout(900)
        shot(page, "04-dispatch-board")
        if page.locator("button[data-dispatch]").count():
            page.locator("button[data-dispatch]").first.click()
            page.wait_for_selector(".modal", timeout=15000)
            assignee = page.locator(".modal select").first
            value = assignee.evaluate(
                "(el) => ([...el.options].find((o) => o.value) || {}).value || ''")
            if not value:
                raise RuntimeError("派单弹层里没有可选维修人")
            assignee.select_option(value)
            page.locator(".modal select").nth(1).select_option("HIGH")
            page.locator(".modal input[type='number']").fill("8")
            page.locator(".modal textarea").fill("联调：优先处理，8 小时内闭环")
            shot(page, "05-dispatch-modal")
            page.click(".modal-foot button:has-text('确认派单')")
            page.wait_for_timeout(1800)
            if page.locator(".modal").count():
                raise RuntimeError("派单没有关闭弹层，提示：%s" % toast_text(page))
            shot(page, "06-dispatch-done")
        else:
            print("   [WARN] 没有待派单工单，跳过派单交互")

        print("4. 工单操作时间线")
        go(page, "/workorder/%d/timeline" % order_id)
        page.wait_for_selector(".timeline", timeout=15000)
        page.wait_for_timeout(700)
        shot(page, "07-workorder-timeline")
        context_a.close()

        # --------------------------------------------------- 维修工：过程记录
        context_b, page_b = login(browser, REPAIR_USER, REPAIR_PASSWORD, "repair")

        print("5. 维修人员工作台：界面接单")
        go(page_b, "/workorder/workbench")
        page_b.wait_for_timeout(1000)
        shot(page_b, "08-repair-workbench")
        if page_b.locator("button", has_text="接单").count():
            page_b.locator("button", has_text="接单").first.click()
            page_b.wait_for_timeout(2000)
            shot(page_b, "09-workbench-accepted")
        else:
            print("   [WARN] 工作台没有待接单任务，跳过接单交互")

        print("6. 维修过程记录：开始维修")
        go(page_b, "/workorder/%d/repair" % order_id)
        field(page_b, "维修原因").locator("textarea").fill("轴承润滑失效导致滚道点蚀，需更换轴承")
        field(page_b, "故障诊断").locator("textarea").fill(
            "听针定位轴承位，振动值 8.6mm/s，拆检见润滑脂干涸、滚道点蚀。")
        page_b.locator("input[type='file']").first.set_input_files(sample)
        page_b.wait_for_timeout(1600)
        shot(page_b, "10-repair-start")
        page_b.click("button:has-text('开始维修')")
        page_b.wait_for_timeout(1800)
        shot(page_b, "11-repair-in-progress")

        print("7. 维修过程记录：过程记录 + 完工说明")
        field(page_b, "记录类型").locator("select").select_option("PROCESS")
        field(page_b, "内容").locator("textarea").fill(
            "更换轴承 6208 两只，加注锂基润滑脂，联轴器重新对中至 0.05mm。")
        field(page_b, "耗时（分钟）").locator("input").fill("95")
        page_b.click("button:has-text('保存记录')")
        page_b.wait_for_timeout(1800)

        # 保存后页面会整块重绘，照片选择器随之清空，所以完工照片必须重新上传
        field(page_b, "记录类型").locator("select").select_option("FINISH")
        field(page_b, "内容").locator("textarea").fill(
            "复测振动 2.1mm/s、轴承温度 48℃，异响消除，试运行 30 分钟正常。")
        field(page_b, "本次维修后照片（完工说明必传）").locator("input[type='file']").set_input_files(sample)
        page_b.wait_for_timeout(1500)
        page_b.click("button:has-text('保存记录')")
        page_b.wait_for_timeout(1800)
        shot(page_b, "12-repair-records")

        print("8. 提交验收")
        field(page_b, "维修总结").locator("textarea").fill(
            "更换轴承并重新对中，振动与温度恢复正常，试运行 30 分钟无异常。")
        field(page_b, "工时（小时）").locator("input").fill("2.5")
        page_b.wait_for_timeout(400)
        shot(page_b, "13-ready-to-submit")
        page_b.click("button:has-text('提交验收')")
        page_b.wait_for_timeout(2600)
        if "/workorder/%d/repair" % order_id in page_b.url:
            raise RuntimeError("提交验收没有生效，页面提示：%s" % toast_text(page_b))
        page_b.wait_for_timeout(600)
        shot(page_b, "14-submitted")
        context_b.close()

        # --------------------------------------------------- 验收员：验收通过
        context_c, page_c = login(browser, VERIFIER_USER, VERIFIER_PASSWORD, "verifier")

        print("9. 维修验收：验收通过")
        go(page_c, "/workorder/%d/verify" % order_id)
        page_c.wait_for_timeout(1200)
        shot(page_c, "15-verify-page")
        page_c.locator(".field", has=page_c.locator("label:text-is('质量评分')")).locator("select").select_option("5")
        page_c.locator(".field", has=page_c.locator("label:text-is('验收意见')")).locator("textarea").fill(
            "现场复测振动 1.6mm/s、温度 45℃，整改到位，验收通过。")
        page_c.click("button:has-text('验收通过')")
        try:
            wait_detail(page_c, order_id)
        except Exception:
            raise RuntimeError("验收通过没有生效，页面提示：%s" % toast_text(page_c))
        shot(page_c, "16-verified")
        context_c.close()

        # --------------------------------------------------- 管理员：服务评价
        context_d, page_d = login(browser, ADMIN_USER, ADMIN_PASSWORD, "evaluate")

        print("10. 工单评价：三维度打分 + 文字")
        go(page_d, "/workorder/%d/evaluate" % order_id)
        page_d.wait_for_selector(".rating-star", timeout=15000)
        page_d.wait_for_timeout(600)
        shot(page_d, "17-evaluate-page")
        # 三组星级依次点 5 / 4 / 3 分
        page_d.locator(".rating").nth(0).locator(".rating-star").nth(4).click()
        page_d.locator(".rating").nth(1).locator(".rating-star").nth(3).click()
        page_d.locator(".rating").nth(2).locator(".rating-star").nth(2).click()
        page_d.locator("textarea").last.fill("师傅到场及时、沟通到位；质量问题整改一轮后达标，整体可接受。")
        page_d.wait_for_timeout(500)
        shot(page_d, "18-evaluate-filled")
        page_d.click("button:has-text('提交评价')")
        try:
            wait_detail(page_d, order_id)
        except Exception:
            raise RuntimeError("提交评价没有生效，页面提示：%s" % toast_text(page_d))
        shot(page_d, "19-evaluated-detail")
        context_d.close()

        browser.close()

    print("\n11. 收尾：停用临时工号")
    for user_id in TEMP_USER_IDS:
        try:
            api_ok("PATCH", "/api/system/users/%d/status?status=0" % user_id)
        except RuntimeError as error:
            print("   [WARN] %s" % error)

    print("\n截图 %d 张，控制台 / 网络错误 %d 条" % (len(steps), len(errors)))
    for item in errors:
        print("  [ERROR] " + item)
    print("本次工单 id=%s（已完成并评价），待派单样本 id=%s" % (order_id, pending_id))
    return 0 if not errors else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except RuntimeError as error:
        print("\n[中断] %s" % error)
        sys.exit(1)
