#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
巡维云 EquipCare · 第八阶段前端统一优化 + 前后端全面联调验证（Playwright）

三个层面：
  A. 多角色完整业务闭环（API 走真实角色权限）：管理员报修建单 → 派单给维修工 →
     维修工接单/开始维修/记录/提交验收 → 验收员验收通过。每一步都以对应角色
     的 token 调用，后端 @PreAuthorize 独立校验，任何一个角色越权都会失败。
  B. UI 交互验证（真实浏览器）：新主题顶栏（全局搜索 / 消息铃铛 / 头像菜单）、
     驾驶舱深色渲染、设备详情与二维码、工单详情时间线、无权限拦截页、
     登录过期跳转、错误口令提示、移动端视口。
  C. 全程收集控制台错误与非预期 HTTP 4xx/5xx（401 登录探测除外）。

用法：
    export EQUIPCARE_BASE_URL=http://127.0.0.1:8080
    export EQUIPCARE_ADMIN_USERNAME=e2e_admin
    export EQUIPCARE_ADMIN_PASSWORD='管理员口令'
    python scripts/ui-verify-stage8.py
"""

import json
import os
import sys
import urllib.error
import urllib.request

from playwright.sync_api import sync_playwright

BASE_URL = os.environ.get("EQUIPCARE_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
ADMIN_USER = os.environ.get("EQUIPCARE_ADMIN_USERNAME", "e2e_admin")
ADMIN_PASSWORD = os.environ.get("EQUIPCARE_ADMIN_PASSWORD", "")
STAFF_PASSWORD = os.environ.get("EQUIPCARE_STAFF_PASSWORD", ADMIN_PASSWORD)
SHOT_DIR = os.environ.get("EQUIPCARE_SHOT_DIR", os.path.join("docs", "screenshots", "stage8"))

errors = []
shots = []
closed_wo_id = None
# 登录过期测试期间浏览器会打印预期的 401 console 错误，此时不计数
token_expired_test = [False]


# --------------------------------------------------------------------- 工具

def shot(page, name):
    os.makedirs(SHOT_DIR, exist_ok=True)
    path = os.path.join(SHOT_DIR, name + ".png")
    page.screenshot(path=path, full_page=True)
    shots.append(name)
    print("  [SHOT] %s" % path)


def api(method, path, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(BASE_URL + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        text = error.read().decode("utf-8", "ignore")
        raise RuntimeError("%s %s 失败：HTTP %s %s" % (method, path, error.code, text[:200]))


def api_login(username, password):
    payload = api("POST", "/api/auth/login", {"username": username, "password": password})
    if payload.get("code") != 200:
        raise RuntimeError("登录失败 %s: %s" % (username, payload.get("message")))
    print("  [登录] %s ok" % username)
    return payload["data"]["accessToken"]


MIN_PNG = bytes.fromhex(
    "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c489"
    "0000000d4944415478da63fccf00f6030003030100f34a1e0d0000000049454e44ae426082")


def upload_image(token, filename="after-repair.png"):
    """上传一张最小 PNG，返回文件 ID 字符串（维修后照片凭证）"""
    boundary = "----stage8boundary7d1a2c"
    body = (("--%s\r\nContent-Disposition: form-data; name=\"file\"; filename=\"%s\"\r\n"
             "Content-Type: image/png\r\n\r\n" % (boundary, filename)).encode()
            + MIN_PNG
            + ("\r\n--%s--\r\n" % boundary).encode())
    request = urllib.request.Request(
        BASE_URL + "/api/files/upload?bizType=WORK_ORDER",
        data=body, method="POST",
        headers={"Content-Type": "multipart/form-data; boundary=" + boundary,
                 "Authorization": "Bearer " + token})
    with urllib.request.urlopen(request) as response:
        payload = json.loads(response.read().decode("utf-8"))
    if payload.get("code") != 200:
        raise RuntimeError("图片上传失败：%s" % payload.get("message"))
    file_id = payload["data"]["fileId"]
    print("  [上传] 维修后照片 fileId=%s" % file_id)
    return str(file_id)


def go(page, path, wait=".card, .kpi-grid, .page-head"):
    page.goto(BASE_URL + "/app.html#" + path, wait_until="domcontentloaded")
    page.wait_for_selector(wait, timeout=20000)
    page.wait_for_timeout(900)


def watch_responses(page):
    def watch(response):
        if response.status >= 400 and response.status != 401:
            errors.append("HTTP %s %s %s" % (response.status, response.request.method, response.url))
    page.on("response", watch)


def new_page(browser, label):
    context = browser.new_context(viewport={"width": 1440, "height": 980}, device_scale_factor=1)
    page = context.new_page()
    page.set_default_timeout(20000)
    page.on("console", lambda msg: errors.append("console: %s" % msg.text)
            if msg.type == "error"
            and not (token_expired_test[0] and "401" in msg.text) else None)
    page.on("pageerror", lambda exc: errors.append("pageerror: %s" % exc))
    watch_responses(page)
    print("  [CTX] %s" % label)
    return context, page


def ui_login(page, username, password):
    page.goto(BASE_URL + "/index.html", wait_until="networkidle")
    page.fill("#username", username)
    page.fill("#password", password)
    page.click("#login-submit")
    page.wait_for_url("**/app.html*", timeout=25000)
    page.wait_for_selector("table.data, .kpi-grid, .card", timeout=25000)


# --------------------------------------------------------------------- A. 业务闭环

def business_loop():
    """多角色完整工单闭环：报修 → 派单 → 接单 → 维修 → 提交验收 → 验收通过"""
    global closed_wo_id

    admin = api_login(ADMIN_USER, ADMIN_PASSWORD)
    repair = api_login("e2e_repair", STAFF_PASSWORD)
    verifier = api_login("e2e_verify", STAFF_PASSWORD)

    # 1. 管理员找一台在用设备报修（Result 解包：列表在 data.records）
    equip_page = api("GET", "/api/asset/equipment?pageNum=1&pageSize=5&status=IN_USE", token=admin)
    equip_data = equip_page.get("data") or {}
    rows = equip_data.get("list") or equip_data.get("records") or []
    if not rows:
        raise RuntimeError("没有在用设备可用于建单")
    equipment = rows[0]
    created = api("POST", "/api/workorder", {
        "equipmentId": equipment["id"],
        "title": "阶段八联调 · 主轴异响",
        "faultType": "MECHANICAL",
        "faultDesc": "联调验收用：设备运行时主轴出现周期性异响，需停机检查。",
        "level": "P3",
    }, token=admin)
    wo_id = created["data"]
    print("  [闭环1] 管理员报修建单 id=%s（设备 %s）" % (wo_id, equipment.get("assetNo")))

    # 2. 派单给 e2e_repair
    assignees = api("GET", "/api/workorder/assignees", token=admin)["data"]
    target = next((a for a in assignees if a.get("username") == "e2e_repair"), None)
    if target is None:
        raise RuntimeError("派单人列表中找不到 e2e_repair: %s" % assignees)
    api("POST", "/api/workorder/%s/dispatch" % wo_id, {
        "assigneeId": target["id"], "priority": "NORMAL",
    }, token=admin)
    print("  [闭环2] 已派单给 e2e_repair（assigneeId=%s）" % target["id"])

    # 3. 维修工接单 → 开始维修 → 记录 → 提交验收
    api("POST", "/api/workorder/%s/accept" % wo_id, token=repair)
    api("POST", "/api/workorder/%s/start-repair" % wo_id, {
        "reason": "接到派单，设备异响需要检修",
        "diagnosis": "初步判断为主轴轴承磨损导致周期性异响",
        "estimateMinutes": 90,
    }, token=repair)
    api("POST", "/api/workorder/%s/records" % wo_id, {
        "recordType": "PROCESS", "content": "更换主轴轴承并加注润滑脂，空载试机异响消失。",
        "costMinutes": 80,
    }, token=repair)
    api("POST", "/api/workorder/%s/records" % wo_id, {
        "recordType": "FINISH", "content": "维修完成：主轴轴承更换完毕，连续试机一小时无异常，设备恢复可用。",
        "costMinutes": 90, "afterImageIds": upload_image(repair),
    }, token=repair)
    api("POST", "/api/workorder/%s/submit-verify" % wo_id, {
        "repairSummary": "更换主轴轴承一套，试机一小时无异常，可恢复生产。",
        "laborHours": 1.5,
    }, token=repair)
    print("  [闭环3] 维修工已接单/维修/提交验收")

    # 4. 验收员验收通过
    api("POST", "/api/workorder/%s/verify-pass" % wo_id, {
        "qualityScore": 5, "remark": "阶段八联调验收：维修记录完整，试机正常。",
    }, token=verifier)
    detail = api("GET", "/api/workorder/%s" % wo_id, token=admin)["data"]
    status = detail.get("status")
    if status != "COMPLETED":
        raise RuntimeError("闭环结束工单状态应为 COMPLETED，实际 %s" % status)
    closed_wo_id = wo_id
    print("  [闭环4] 验收员验收通过，工单状态 = COMPLETED ✔")
    return wo_id


# --------------------------------------------------------------------- B. UI 流程

def ui_flows(browser):
    # ---- 管理员：新主题 + 顶栏三件套 + 全局搜索
    ctx, page = new_page(browser, "管理员")
    ui_login(page, ADMIN_USER, ADMIN_PASSWORD)
    print("1. 新主题与顶栏：搜索框 / 铃铛 / 头像")
    for selector, name in [(".topbar-search #global-search", "全局搜索"),
                           ("#bell-btn", "消息铃铛"), ("#user-chip", "用户头像")]:
        if not page.locator(selector).count():
            raise RuntimeError("顶栏缺少「%s」" % name)
    bg = page.evaluate("getComputedStyle(document.body).backgroundColor")
    print("   body 背景色 = %s" % bg)
    if bg not in ("rgb(11, 14, 20)", "rgb(11,13,19)"):
        # 允许主题微调，但必须是深色（亮度 < 60）
        rgb = [int(x) for x in bg.strip("rgb()").split(",")]
        if sum(rgb[:3]) / 3 > 60:
            raise RuntimeError("body 背景不是深色主题：%s" % bg)
    shot(page, "01-admin-home-dark")

    print("2. 全局搜索：输入关键词出结果面板")
    page.fill("#global-search", "冒烟")
    page.wait_for_timeout(1500)
    hits = page.locator("#search-panel .search-item").count()
    print("   命中 %d 条" % hits)
    if hits == 0 and page.locator("#search-panel .search-empty").count() == 0:
        raise RuntimeError("全局搜索面板没有渲染结果")
    shot(page, "02-global-search")
    page.keyboard.press("Escape")

    print("3. 铃铛 → 消息页；头像菜单 → 个人中心")
    page.click("#bell-btn")
    page.wait_for_timeout(1200)
    if "#/messages" not in page.url:
        raise RuntimeError("点击铃铛未跳转消息页，当前 %s" % page.url)
    shot(page, "03-messages-via-bell")
    page.click("#user-chip")
    page.wait_for_timeout(400)
    if page.locator("#avatar-menu button", has_text="个人中心").count() == 0:
        raise RuntimeError("头像菜单未展开")
    page.locator("#avatar-menu button", has_text="个人中心").click()
    page.wait_for_timeout(1000)
    if "#/profile" not in page.url:
        raise RuntimeError("头像菜单「个人中心」未跳转")
    shot(page, "04-profile-via-avatar")

    print("4. 驾驶舱：KPI + 图表（深色）")
    go(page, "/dashboard")
    page.wait_for_selector(".kpi-grid", timeout=20000)
    page.wait_for_timeout(1600)
    kpis = page.locator(".kpi-grid .stat").count()
    canvases = page.locator(".chart-dom canvas").count()
    empties = page.locator(".chart-empty").count()
    print("   KPI %d 张，canvas %d 个，暂无数据 %d 个" % (kpis, canvases, empties))
    if kpis < 14 or canvases + empties < 8:
        raise RuntimeError("驾驶舱渲染不完整（KPI=%d, charts=%d）" % (kpis, canvases + empties))
    shot(page, "05-dashboard-dark")

    print("5. 设备列表 → 详情 → 二维码（分页/搜索/二维码联调）")
    go(page, "/equipment")
    page.wait_for_selector("table.data, .table-empty", timeout=20000)
    page.wait_for_timeout(600)
    shot(page, "06-equipment-list")
    first_link = page.locator("table.data tbody a").first
    if first_link.count():
        first_link.click()
        page.wait_for_timeout(1400)
        if "#/equipment/" not in page.url:
            raise RuntimeError("点击设备未进入详情页")
        shot(page, "07-equipment-detail")
        qrcode = page.locator("a[href*='/qrcode'], button", has_text="二维码").first
        if qrcode.count():
            qrcode.click()
            page.wait_for_timeout(1600)
            if page.locator(".qr-preview img, .qr-box img").count() == 0:
                raise RuntimeError("二维码页没有渲染二维码图片")
            shot(page, "08-equipment-qrcode")

    print("6. 工单详情：闭环工单时间线（状态更新联调）")
    go(page, "/workorder/%s/timeline" % closed_wo_id)
    page.wait_for_selector(".timeline, .card", timeout=20000)
    page.wait_for_timeout(800)
    items = page.locator(".timeline-item").count()
    print("   时间线节点 %d 个" % items)
    if items < 5:
        raise RuntimeError("闭环工单时间线应至少 5 个节点（建单/派单/接单/维修/验收），实际 %d" % items)
    shot(page, "09-workorder-timeline")

    print("7. 登录过期：清空令牌后点击导航 → 跳回登录页")
    token_expired_test[0] = True
    page.evaluate("localStorage.removeItem('ec_access_token')")
    page.goto(BASE_URL + "/app.html#/equipment", wait_until="domcontentloaded")
    page.wait_for_timeout(1500)
    if "/index.html" not in page.url and "/app.html" in page.url:
        raise RuntimeError("令牌失效后未跳回登录页，当前 %s" % page.url)
    shot(page, "10-token-expired-redirect")
    ctx.close()
    token_expired_test[0] = False

    # ---- 巡检员：受限菜单 + 无权限拦截
    ctx, page = new_page(browser, "巡检员")
    ui_login(page, "e2e_inspect", STAFF_PASSWORD)
    print("8. 巡检员：巡检任务页 + 越权访问系统管理被拦截")
    go(page, "/inspect/task")
    page.wait_for_selector("table.data, .table-empty, .card", timeout=20000)
    page.wait_for_timeout(600)
    shot(page, "11-inspector-task")
    go(page, "/users")
    page.wait_for_timeout(800)
    if page.locator("text=没有访问权限").count() == 0:
        raise RuntimeError("巡检员访问 /users 应显示无权限拦截页")
    shot(page, "12-inspector-forbidden")
    ctx.close()

    # ---- 维修工：工作台
    ctx, page = new_page(browser, "维修工")
    ui_login(page, "e2e_repair", STAFF_PASSWORD)
    print("9. 维修工：工作台渲染")
    go(page, "/workorder/workbench")
    page.wait_for_selector(".card, .metric, .table-empty", timeout=20000)
    page.wait_for_timeout(800)
    shot(page, "13-repairman-workbench")
    ctx.close()

    # ---- 错误口令提示（后端拒绝登录返回 401 属预期行为，console 噪音不计数）
    ctx, page = new_page(browser, "登录失败提示")
    print("10. 错误口令登录：明确错误提示")
    token_expired_test[0] = True
    page.goto(BASE_URL + "/index.html", wait_until="networkidle")
    page.fill("#username", ADMIN_USER)
    page.fill("#password", "wrong-password-123")
    page.click("#login-submit")
    page.wait_for_timeout(1500)
    alert = page.locator("#login-alert")
    if not alert.is_visible():
        raise RuntimeError("错误口令未显示错误提示")
    print("   提示内容：%s" % alert.inner_text())
    shot(page, "15-login-error-tip")
    ctx.close()
    token_expired_test[0] = False

    # ---- 移动端视口
    ctx, page = new_page(browser, "移动端")
    ui_login(page, ADMIN_USER, ADMIN_PASSWORD)
    print("12. 移动端视口 375×812：驾驶舱自适应")
    page.set_viewport_size({"width": 375, "height": 812})
    go(page, "/dashboard")
    page.wait_for_timeout(1600)
    shot(page, "16-mobile-dashboard")
    ctx.close()


# --------------------------------------------------------------------- 主流程

def main():
    if not ADMIN_PASSWORD:
        print("请设置 EQUIPCARE_ADMIN_PASSWORD")
        return 2

    print("A. 多角色完整业务闭环（API · 真实角色）")
    business_loop()

    print("\nB. UI 交互与异常状态（Playwright · 真实浏览器）")
    with sync_playwright() as playwright:
        chrome = os.environ.get("EQUIPCARE_CHROME")
        browser = playwright.chromium.launch(executable_path=chrome) if chrome \
            else playwright.chromium.launch()
        ui_flows(browser)
        browser.close()

    print("\n截图 %d 张，控制台 / 网络错误 %d 条" % (len(shots), len(errors)))
    for item in errors:
        print("  [ERROR] " + item)
    return 0 if not errors else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except RuntimeError as error:
        print("\n[中断] %s" % error)
        sys.exit(1)
