#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
巡维云 EquipCare · 接口符合性测试（第九阶段交付）。

对核心接口逐项执行六类检查：
  1. 正常请求     —— 期望 HTTP 200 且业务 code=200
  2. 参数错误     —— 期望 4xx 且返回统一错误结构
  3. 未登录请求   —— 期望 HTTP 401
  4. 无权限请求   —— 期望 HTTP 403（用受限角色调用）
  5. 重复请求     —— 对有唯一性约束的接口验证重复提交被拒绝
  6. 异常请求     —— 不存在的资源 / 非法状态流转 / 库存不足等业务异常

输出逐项 PASS/FAIL 与汇总；全部通过时退出码 0。

用法：
    export EQUIPCARE_BASE_URL=http://127.0.0.1:8080
    export EQUIPCARE_ADMIN_USERNAME=e2e_admin
    export EQUIPCARE_ADMIN_PASSWORD='...'
    export EQUIPCARE_STAFF_PASSWORD='...'
    python scripts/api-conformance.py
"""

import json
import os
import sys
import urllib.error
import urllib.request

BASE = os.environ.get("EQUIPCARE_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
ADMIN_USER = os.environ.get("EQUIPCARE_ADMIN_USERNAME", "e2e_admin")
ADMIN_PWD = os.environ.get("EQUIPCARE_ADMIN_PASSWORD", "")
STAFF_PWD = os.environ.get("EQUIPCARE_STAFF_PASSWORD", ADMIN_PWD)

results = []          # (组名, 用例名, 期望, 实际, PASS)
stats = {"pass": 0, "fail": 0}


def call(method, path, body=None, token=None, raw_body=None, content_type="application/json"):
    headers = {"Content-Type": content_type}
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode()
    elif raw_body is not None:
        data = raw_body
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            text = resp.read().decode("utf-8", "ignore")
            try:
                return resp.status, json.loads(text)
            except Exception:
                return resp.status, text[:120]
    except urllib.error.HTTPError as err:
        text = err.read().decode("utf-8", "ignore")
        try:
            return err.code, json.loads(text)
        except Exception:
            return err.code, text[:120]
    except Exception as exc:  # noqa: BLE001
        return -1, str(exc)[:120]


def login(username, password):
    status, payload = call("POST", "/api/auth/login", {"username": username, "password": password})
    if status != 200 or payload.get("code") != 200:
        raise RuntimeError("登录失败 %s: HTTP %s %s" % (username, status, payload))
    return payload["data"]["accessToken"], payload["data"]


def check(group, name, expect_desc, actual_desc, passed):
    stats["pass" if passed else "fail"] += 1
    results.append((group, name, expect_desc, actual_desc, passed))
    print("  [%s] %s · %s 期望%s，实际%s" % ("PASS" if passed else "FAIL", group, name, expect_desc, actual_desc))


def http_ok(status, payload):
    return status == 200 and isinstance(payload, dict) and payload.get("code") == 200


def main():
    if not ADMIN_PWD:
        print("请设置 EQUIPCARE_ADMIN_PASSWORD / EQUIPCARE_STAFF_PASSWORD")
        return 2

    admin_token, admin_info = login(ADMIN_USER, ADMIN_PWD)
    inspect_token, _ = login("e2e_inspect", STAFF_PWD)
    repair_token, _ = login("e2e_repair", STAFF_PWD)
    print("登录完成：admin/inspect/repair 三角色就绪\n")

    # ---------- 1. 认证 ----------
    print("1. 用户认证 /api/auth/*")
    st, pl = call("POST", "/api/auth/login", {"username": ADMIN_USER, "password": ADMIN_PWD})
    check("认证", "正常登录", "200/200", "%s/%s" % (st, pl.get("code")), http_ok(st, pl))
    st, pl = call("POST", "/api/auth/login", {"username": ADMIN_USER, "password": "wrong-password"})
    check("认证", "错误口令", "401", str(st), st == 401)
    st, pl = call("POST", "/api/auth/login", {})
    check("认证", "空参数", "4xx", str(st), 400 <= st < 500)
    st, pl = call("POST", "/api/auth/refresh", {"refreshToken": "invalid-token"})
    check("认证", "非法刷新令牌", "40x/业务错误", "%s/%s" % (st, pl.get("code") if isinstance(pl, dict) else pl),
          st >= 400 or (isinstance(pl, dict) and pl.get("code") != 200))

    # ---------- 2. 设备资产 ----------
    print("2. 设备资产 /api/asset/*")
    st, pl = call("GET", "/api/asset/equipment?pageNum=1&pageSize=5", token=admin_token)
    check("设备", "设备列表", "200/200", "%s/%s" % (st, pl.get("code")), http_ok(st, pl))
    st, pl = call("GET", "/api/asset/equipment?pageNum=0&pageSize=5", token=admin_token)
    check("设备", "页码=0", "4xx", str(st), 400 <= st < 500)
    st, pl = call("GET", "/api/asset/equipment?pageNum=1&pageSize=1000", token=admin_token)
    check("设备", "页大小超上限(1000)", "4xx", str(st), 400 <= st < 500)
    st, pl = call("GET", "/api/asset/equipment", token=None)
    check("设备", "未登录访问", "401", str(st), st == 401)

    st, pl = call("POST", "/api/asset/equipment", {"assetNo": "", "name": ""}, token=admin_token)
    check("设备", "必填字段缺失", "4xx", str(st), 400 <= st < 500)
    st, pl = call("POST", "/api/asset/equipment", {"assetNo": 12345, "name": 1}, token=admin_token)
    check("设备", "字段类型错误", "4xx", str(st), 400 <= st < 500)

    # 重复资产编号：先取一个现有编号再重复创建
    st, pl = call("GET", "/api/asset/equipment?pageNum=1&pageSize=1", token=admin_token)
    existing = (pl.get("data") or {}).get("records") or (pl.get("data") or {}).get("list") or []
    if existing:
        eq = existing[0]
        dup = {"assetNo": eq.get("assetNo"), "name": "重复性验证设备", "categoryId": eq.get("categoryId"),
               "locationId": eq.get("locationId"), "status": "IN_USE"}
        st, pl = call("POST", "/api/asset/equipment", dup, token=admin_token)
        check("设备", "重复资产编号", "4xx/业务拒绝", "%s/%s" % (st, pl.get("code") if isinstance(pl, dict) else pl),
              400 <= st < 500 or (isinstance(pl, dict) and pl.get("code") != 200))
        st, pl = call("GET", "/api/asset/equipment/%s" % eq["id"], token=inspect_token)
        check("设备", "巡检员查看设备(有权限)", "200", str(st), st == 200)
        st, pl = call("DELETE", "/api/asset/equipment/%s" % eq["id"], token=inspect_token)
        check("设备", "巡检员删除设备(越权)", "403", str(st), st == 403)

    # ---------- 3. 二维码 ----------
    print("3. 设备二维码")
    if existing:
        eq = existing[0]
        st, pl = call("GET", "/api/asset/equipment/%s/qrcode.png" % eq["id"], token=admin_token)
        check("二维码", "正常生成 PNG", "200", str(st), st == 200)
        st, pl = call("GET", "/api/asset/equipment/999999/qrcode.png", token=admin_token)
        check("二维码", "设备不存在", "4xx", str(st), 400 <= st < 500)
        st, pl = call("GET", "/api/asset/equipment/%s/qrcode.png" % eq["id"], token=None)
        check("二维码", "未登录访问", "401", str(st), st == 401)

    # ---------- 4. 巡检 ----------
    print("4. 巡检中心 /api/inspection/*")
    st, pl = call("GET", "/api/inspection/tasks?pageNum=1&pageSize=5", token=inspect_token)
    check("巡检", "任务列表(巡检员)", "200", "%s/%s" % (st, pl.get("code")), http_ok(st, pl))
    st, pl = call("GET", "/api/inspection/tasks?pageNum=1&pageSize=5", token=admin_token)
    check("巡检", "任务列表(管理员)", "200", str(st), st == 200)
    st, pl = call("GET", "/api/inspection/tasks", token=None)
    check("巡检", "未登录访问", "401", str(st), st == 401)
    st, pl = call("GET", "/api/inspection/tasks/999999", token=inspect_token)
    check("巡检", "任务不存在", "4xx/业务错误", "%s/%s" % (st, pl.get("code") if isinstance(pl, dict) else pl),
          st >= 400 or (isinstance(pl, dict) and pl.get("code") != 200))

    # ---------- 5. 工单 ----------
    print("5. 维修工单 /api/workorder/*")
    st, pl = call("GET", "/api/asset/equipment?pageNum=1&pageSize=1", token=admin_token)
    rows = (pl.get("data") or {}).get("records") or []
    equip_id = rows[0]["id"] if rows else 0

    st, pl = call("POST", "/api/workorder",
                  {"equipmentId": equip_id, "title": "符合性验证工单", "faultType": "MECHANICAL",
                   "faultDesc": "接口符合性测试自动创建", "level": "P3"}, token=admin_token)
    ok_create = http_ok(st, pl)
    check("工单", "正常建单", "200", "%s/%s" % (st, pl.get("code")), ok_create)
    wo_id = pl.get("data") if ok_create else None

    st, pl = call("POST", "/api/workorder",
                  {"equipmentId": equip_id, "title": "缺等级", "faultType": "MECHANICAL",
                   "faultDesc": "缺少 level 字段"}, token=admin_token)
    check("工单", "缺 level 建单", "4xx", str(st), 400 <= st < 500)
    st, pl = call("POST", "/api/workorder", {"equipmentId": equip_id}, token=None)
    check("工单", "未登录建单", "401", str(st), st == 401)
    st, pl = call("POST", "/api/workorder",
                  {"equipmentId": equip_id, "title": "巡检员报修", "faultType": "OTHER",
                   "faultDesc": "INSPECTOR 角色种子含 wo:workorder:create，报修属设计内权限", "level": "P4"},
                  token=inspect_token)
    check("工单", "巡检员报修(设计内权限)", "200", "%s/%s" % (st, pl.get("code")), http_ok(st, pl))

    if wo_id:
        # 越权样本：巡检员没有 wo:workorder:verify，不能代替验收
        st, pl = call("POST", "/api/workorder/%s/verify-pass" % wo_id,
                      {"qualityScore": 5}, token=inspect_token)
        check("工单", "巡检员验收(越权)", "403", str(st), st == 403)
        # 异常状态流转：未派单直接接单
        st, pl = call("POST", "/api/workorder/%s/accept" % wo_id, token=repair_token)
        check("工单", "未派单直接接单(非法流转)", "4xx/业务拒绝",
              "%s/%s" % (st, pl.get("code") if isinstance(pl, dict) else pl),
              st >= 400 or (isinstance(pl, dict) and pl.get("code") != 200))
        # 派单给不存在的人
        st, pl = call("POST", "/api/workorder/%s/dispatch" % wo_id,
                      {"assigneeId": 999999, "priority": "NORMAL"}, token=admin_token)
        check("工单", "派单给不存在的人", "4xx/业务拒绝",
              "%s/%s" % (st, pl.get("code") if isinstance(pl, dict) else pl),
              st >= 400 or (isinstance(pl, dict) and pl.get("code") != 200))
        st, pl = call("GET", "/api/workorder/%s" % wo_id, token=admin_token)
        check("工单", "工单详情", "200", str(st), st == 200)
        st, pl = call("GET", "/api/workorder/999999", token=admin_token)
        check("工单", "工单不存在", "4xx/业务错误", "%s/%s" % (st, pl.get("code") if isinstance(pl, dict) else pl),
              st >= 400 or (isinstance(pl, dict) and pl.get("code") != 200))

    # ---------- 6. 备件库存 ----------
    print("6. 备品备件 /api/inventory/*")
    st, pl = call("GET", "/api/inventory/parts?pageNum=1&pageSize=5", token=admin_token)
    check("库存", "备件列表", "200", "%s/%s" % (st, pl.get("code")), http_ok(st, pl))
    st, pl = call("GET", "/api/inventory/parts?pageNum=1&pageSize=5", token=None)
    check("库存", "未登录访问", "401", str(st), st == 401)
    st, pl = call("POST", "/api/inventory/stock/out",
                  {"partId": 1, "quantity": 0, "reason": "测试"}, token=admin_token)
    check("库存", "出库数量=0", "4xx", str(st), 400 <= st < 500)
    st, pl = call("POST", "/api/inventory/stock/out",
                  {"partId": 1, "quantity": 99999999, "reason": "超量出库"}, token=admin_token)
    check("库存", "库存不足出库", "4xx/业务拒绝",
          "%s/%s" % (st, pl.get("code") if isinstance(pl, dict) else pl),
          st >= 400 or (isinstance(pl, dict) and pl.get("code") != 200))

    # ---------- 7. 统计驾驶舱 ----------
    print("7. 运营分析 /api/analytics/*")
    st, pl = call("GET", "/api/analytics/dashboard/summary", token=admin_token)
    check("统计", "驾驶舱汇总", "200", "%s/%s" % (st, pl.get("code")), http_ok(st, pl))
    st, pl = call("GET", "/api/analytics/dashboard/summary", token=inspect_token)
    # INSPECTOR 角色种子含 dashboard:view（巡检员可看驾驶舱，设计如此）
    check("统计", "巡检员访问驾驶舱(有权限)", "200", "%s/%s" % (st, pl.get("code")), http_ok(st, pl))
    st, pl = call("GET", "/api/analytics/dashboard/summary", token=None)
    check("统计", "未登录访问", "401", str(st), st == 401)
    st, pl = call("GET", "/api/analytics/dashboard/charts?start=2026-13-01&end=2026-01-31", token=admin_token)
    check("统计", "非法日期范围", "2xx空数据/4xx", "%s/%s" % (st, pl.get("code") if isinstance(pl, dict) else pl),
          200 <= st < 300)

    # ---------- 8. 消息 ----------
    print("8. 消息通知 /api/system/messages")
    st, pl = call("GET", "/api/system/messages?pageNum=1&pageSize=5", token=inspect_token)
    check("消息", "消息列表(巡检员)", "200", "%s/%s" % (st, pl.get("code")), http_ok(st, pl))
    st, pl = call("GET", "/api/system/messages", token=None)
    check("消息", "未登录访问", "401", str(st), st == 401)

    # ---------- 汇总 ----------
    print("\n========== 汇总 ==========")
    print("通过 %d / 失败 %d / 共 %d" % (stats["pass"], stats["fail"], stats["pass"] + stats["fail"]))
    if stats["fail"]:
        for group, name, exp, act, ok_flag in results:
            if not ok_flag:
                print("  FAIL: %s · %s (期望%s, 实际%s)" % (group, name, exp, act))
    return 0 if not stats["fail"] else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except RuntimeError as error:
        print("[中断] %s" % error)
        sys.exit(1)
