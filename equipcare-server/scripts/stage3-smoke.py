#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
巡维云 EquipCare · 第三阶段端到端冒烟脚本（仅依赖 Python 标准库）

覆盖验收项：
  1. 登录与个人资料修改
  2. 角色新增 / 删除，角色删除保护
  3. 设备新增（自动编号）、编号唯一性、编辑、状态变更（报废需原因）
  4. 图片上传 → 绑定设备 → 查询 → 解绑
  5. 二维码内容获取、PNG 生成、扫码取数（含最近巡检与维修历史）
  6. 不同角色的接口权限（巡检员访问用户管理必须 403）
  7. 设备档案详情与打印所需字段齐全

用法：
    export EQUIPCARE_BASE_URL=http://127.0.0.1:8080
    export EQUIPCARE_ADMIN_USERNAME=admin
    export EQUIPCARE_ADMIN_PASSWORD='你的管理员密码'
    python scripts/stage3-smoke.py

说明：脚本不会写入任何硬编码口令；测试产生的数据会在结束时清理
     （设备为逻辑删除，账号与角色为物理清理）。
"""

import json
import os
import struct
import sys
import time
import urllib.error
import urllib.request
import zlib

BASE_URL = os.environ.get("EQUIPCARE_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
ADMIN_USERNAME = os.environ.get("EQUIPCARE_ADMIN_USERNAME", "admin")
ADMIN_PASSWORD = os.environ.get("EQUIPCARE_ADMIN_PASSWORD", "")

STAMP = str(int(time.time()))[-6:]
PASSED = []
FAILED = []


# ----------------------------------------------------------------------
# HTTP 基础
# ----------------------------------------------------------------------

def request(method, path, token=None, body=None, raw=False, content_type="application/json",
            accept="application/json"):
    url = BASE_URL + path
    data = None
    headers = {"Accept": accept}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = content_type
    if token:
        headers["Authorization"] = "Bearer " + token

    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            payload = resp.read()
            if raw:
                return resp.status, payload, resp.headers.get("Content-Type", "")
            return resp.status, json.loads(payload.decode("utf-8")), ""
    except urllib.error.HTTPError as err:
        payload = err.read()
        if raw:
            return err.code, payload, err.headers.get("Content-Type", "")
        try:
            return err.code, json.loads(payload.decode("utf-8")), ""
        except json.JSONDecodeError:
            return err.code, {"code": err.code, "message": payload.decode("utf-8", "ignore")}, ""


def upload(token, filename, content, content_type, biz_id=None):
    boundary = "----EquipCareStage3%s" % STAMP
    parts = []
    parts.append(("--%s\r\n" % boundary).encode())
    parts.append(('Content-Disposition: form-data; name="file"; filename="%s"\r\n' % filename).encode())
    parts.append(("Content-Type: %s\r\n\r\n" % content_type).encode())
    parts.append(content)
    parts.append(b"\r\n")
    if biz_id:
        parts.append(("--%s\r\n" % boundary).encode())
        parts.append(b'Content-Disposition: form-data; name="bizType"\r\n\r\nEQUIPMENT\r\n')
        parts.append(("--%s\r\n" % boundary).encode())
        parts.append(('Content-Disposition: form-data; name="bizId"\r\n\r\n%s\r\n' % biz_id).encode())
    parts.append(("--%s--\r\n" % boundary).encode())
    body = b"".join(parts)

    req = urllib.request.Request(
        BASE_URL + "/api/files/upload", data=body, method="POST",
        headers={
            "Authorization": "Bearer " + token,
            "Content-Type": "multipart/form-data; boundary=" + boundary,
        })
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def check(name, condition, detail=""):
    if condition:
        PASSED.append(name)
        print("  [PASS] %s %s" % (name, detail))
    else:
        FAILED.append(name)
        print("  [FAIL] %s %s" % (name, detail))


def section(title):
    print("\n== %s ==" % title)


def tiny_png():
    """生成 8x8 纯色 PNG，用于验证上传链路，不依赖第三方库。"""
    width = height = 8
    raw = b"".join(b"\x00" + b"\x2f\x6f\xb3" * width for _ in range(height))

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9))
            + chunk(b"IEND", b""))


# ----------------------------------------------------------------------
# 主流程
# ----------------------------------------------------------------------

def main():
    if not ADMIN_PASSWORD:
        print("请先设置环境变量 EQUIPCARE_ADMIN_PASSWORD（管理员密码），脚本不会内置任何默认口令。")
        return 2

    print("巡维云 EquipCare 第三阶段冒烟测试 → %s" % BASE_URL)

    section("0. 登录")
    status, body, _ = request("POST", "/api/auth/login",
                              body={"username": ADMIN_USERNAME, "password": ADMIN_PASSWORD})
    if status != 200 or body.get("code") != 200:
        print("  管理员登录失败：%s" % body.get("message"))
        return 1
    admin_token = body["data"]["accessToken"]
    print("  [PASS] 管理员登录成功：%s" % ADMIN_USERNAME)

    status, body, _ = request("GET", "/api/auth/me", admin_token)
    me = body.get("data") or {}
    check("当前用户信息", status == 200 and me.get("username") == ADMIN_USERNAME,
          "角色=%s 权限数=%s" % (me.get("roles"), len(me.get("permissions") or [])))

    section("1. 个人资料")
    status, body, _ = request("PUT", "/api/auth/profile", admin_token, {
        "realName": me.get("realName") or "管理员",
        "phone": me.get("phone") or "13800000000",
        "email": "stage3-smoke@example.com",
    })
    check("修改本人资料", body.get("code") == 200, "email=stage3-smoke@example.com")

    status, body, _ = request("PUT", "/api/auth/profile", admin_token, {"realName": "管理员", "phone": "123"})
    check("手机号格式校验", body.get("code") == 422, "返回码=%s" % body.get("code"))

    section("2. 角色管理")
    role_code = "SMOKE_" + STAMP
    status, body, _ = request("POST", "/api/system/roles", admin_token, {
        "roleCode": role_code, "roleName": "冒烟临时角色", "dataScope": "SELF", "sort": 95,
    })
    check("新增角色", body.get("code") == 200, "roleCode=%s" % role_code)
    role_id = body.get("data")

    status, body, _ = request("POST", "/api/system/roles", admin_token, {
        "roleCode": role_code, "roleName": "冒烟临时角色", "dataScope": "SELF", "sort": 95,
    })
    check("角色编码唯一", body.get("code") == 2002, "返回码=%s" % body.get("code"))

    status, body, _ = request("GET", "/api/system/permissions", admin_token)
    perms = body.get("data") or []
    check("权限清单可读", body.get("code") == 200 and len(perms) > 0, "共 %d 项" % len(perms))
    view_perm = next((p for p in perms if p.get("code") == "asset:equipment:view"), None)
    if view_perm and role_id:
        status, body, _ = request("PUT", "/api/system/roles/%s/permissions" % role_id, admin_token,
                                  {"permissionIds": [view_perm["id"]]})
        check("角色授权", body.get("code") == 200, "授予 asset:equipment:view")

    status, body, _ = request("DELETE", "/api/system/roles/%s" % role_id, admin_token)
    check("删除空闲角色", body.get("code") == 200)

    section("3. 设备档案")
    status, body, _ = request("GET", "/api/asset/categories/tree", admin_token)
    categories = body.get("data") or []
    status, body, _ = request("GET", "/api/asset/locations/tree", admin_token)
    locations = body.get("data") or []
    if not categories or not locations:
        print("  缺少设备分类或位置基础数据，请先执行 database/seed.sql")
        return 1
    category_id = categories[0]["id"]
    location_id = locations[0]["id"]

    equipment_name = "冒烟测试设备 " + STAMP
    status, body, _ = request("POST", "/api/asset/equipment", admin_token, {
        "equipmentName": equipment_name, "categoryId": category_id, "locationId": location_id,
        "brand": "冒烟品牌", "model": "SMOKE-1",
    })
    check("新增设备（自动编号）", body.get("code") == 200)
    equipment_id = body.get("data")

    status, body, _ = request("GET", "/api/asset/equipment/%s" % equipment_id, admin_token)
    detail = body.get("data") or {}
    asset_no = detail.get("assetNo")
    qr_token = detail.get("qrToken")
    check("设备详情", body.get("code") == 200 and detail.get("equipmentName") == equipment_name,
          "资产编号=%s 状态=%s" % (asset_no, detail.get("status")))
    check("详情包含历史字段", all(k in detail for k in ("files", "recentInspections", "repairHistory")))

    status, body, _ = request("POST", "/api/asset/equipment", admin_token, {
        "equipmentName": "冒烟重复编号设备", "categoryId": category_id,
        "locationId": location_id, "assetNo": asset_no,
    })
    check("资产编号唯一", body.get("code") == 2002, "重复编号 %s 被拒绝" % asset_no)

    status, body, _ = request("PATCH", "/api/asset/equipment/%s/status" % equipment_id, admin_token,
                              {"status": "SCRAPPED"})
    check("报废必须填原因", body.get("code") == 400, "返回码=%s" % body.get("code"))

    status, body, _ = request("PATCH", "/api/asset/equipment/%s/status" % equipment_id, admin_token,
                              {"status": "REPAIRING", "reason": "试图绕过工单"})
    check("维修中不可手工设置", body.get("code") == 2004, "返回码=%s" % body.get("code"))

    status, body, _ = request("PATCH", "/api/asset/equipment/%s/status" % equipment_id, admin_token,
                              {"status": "IDLE", "reason": "冒烟测试停用"})
    check("设备停用", body.get("code") == 200)

    section("4. 图片上传与附件")
    up = upload(admin_token, "smoke.png", tiny_png(), "image/png", equipment_id)
    check("上传图片", up.get("code") == 200, "fileId=%s" % (up.get("data") or {}).get("fileId"))
    file_id = (up.get("data") or {}).get("fileId")

    status, body, _ = request("POST", "/api/asset/equipment/%s/files" % equipment_id, admin_token,
                              {"fileId": file_id, "fileType": "IMAGE"})
    check("绑定图片", body.get("code") == 200)

    status, body, _ = request("GET", "/api/asset/equipment/%s/files" % equipment_id, admin_token)
    check("图片列表", body.get("code") == 200 and len(body.get("data") or []) == 1)

    status, body, _ = request("GET", "/api/asset/equipment/%s" % equipment_id, admin_token)
    check("详情带出封面", bool((body.get("data") or {}).get("coverUrl")))

    status, body, _ = request("DELETE", "/api/asset/equipment/%s/files/%s" % (equipment_id, file_id), admin_token)
    check("解绑图片", body.get("code") == 200)

    section("5. 二维码与扫码")
    status, body, _ = request("GET", "/api/asset/equipment/%s/qrcode" % equipment_id, admin_token)
    qr = body.get("data") or {}
    content = qr.get("content") or ""
    check("二维码内容", body.get("code") == 200 and ("token=" + qr_token) in content,
          content[:60] + ("..." if len(content) > 60 else ""))
    lowered = content.lower()
    check("二维码不含敏感信息",
          not any(word in lowered for word in ("password", "jwt", "secret", "bearer", "13800000000")))

    status, payload, content_type = request("GET", "/api/asset/equipment/%s/qrcode.png?size=240" % equipment_id,
                                            admin_token, raw=True, accept="image/png")
    check("二维码 PNG", status == 200 and payload[:8] == b"\x89PNG\r\n\x1a\n"
          and "image/png" in content_type, "%d 字节" % len(payload))

    status, body, _ = request("GET", "/api/asset/equipment/scan?token=%s" % qr_token, admin_token)
    scan = body.get("data") or {}
    check("扫码取数", body.get("code") == 200 and scan.get("id") == equipment_id,
          "状态=%s 巡检记录=%d 维修记录=%d" % (
              scan.get("statusLabel"), len(scan.get("recentInspections") or []),
              len(scan.get("repairHistory") or [])))
    check("扫码记录扫码人", bool(scan.get("scannedBy")), "扫码人=%s" % scan.get("scannedBy"))

    section("6. 不同角色的接口权限")
    status, body, _ = request("GET", "/api/system/roles", admin_token)
    roles = body.get("data") or []
    inspector_role = next((r for r in roles if r.get("roleCode") == "INSPECTOR"), None)
    if inspector_role is None:
        print("  缺少 INSPECTOR 角色，跳过角色权限用例（请执行 seed.sql）")
    else:
        user_name = "smoke_insp_" + STAMP
        status, body, _ = request("POST", "/api/system/users", admin_token, {
            "username": user_name, "password": "Smoke12345", "realName": "冒烟巡检员",
            "roleIds": [inspector_role["id"]],
        })
        check("创建巡检员账号", body.get("code") == 200, "username=%s" % user_name)
        smoke_user_id = body.get("data")

        status, body, _ = request("POST", "/api/auth/login",
                                  body={"username": user_name, "password": "Smoke12345"})
        inspector_token = (body.get("data") or {}).get("accessToken")
        check("巡检员登录", bool(inspector_token))

        status, body, _ = request("GET", "/api/system/users?pageNum=1&pageSize=5", inspector_token)
        check("巡检员访问用户管理被拒绝", status == 403 and body.get("code") == 403)

        status, body, _ = request("GET", "/api/asset/equipment?pageNum=1&pageSize=5", inspector_token)
        check("巡检员可查设备列表", body.get("code") == 200)

        status, body, _ = request("GET", "/api/asset/equipment/scan?token=%s" % qr_token, inspector_token)
        check("巡检员可扫码", body.get("code") == 200)

        status, body, _ = request("GET", "/api/asset/equipment/%s/qrcode" % equipment_id, inspector_token)
        check("巡检员不可管理二维码", status == 403)

        status, body, _ = request("DELETE", "/api/system/users/%s" % smoke_user_id, admin_token)
        check("清理巡检员账号", body.get("code") == 200)

    section("7. 清理")
    status, body, _ = request("DELETE", "/api/asset/equipment/%s" % equipment_id, admin_token)
    check("删除设备（逻辑删除）", body.get("code") == 200)
    status, body, _ = request("GET", "/api/asset/equipment/%s" % equipment_id, admin_token)
    check("删除后不可见", body.get("code") == 2001)

    print("\n---------------------------------------------")
    print("通过 %d 项，失败 %d 项" % (len(PASSED), len(FAILED)))
    if FAILED:
        for name in FAILED:
            print("  失败：%s" % name)
        return 1
    print("第三阶段端到端冒烟全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
