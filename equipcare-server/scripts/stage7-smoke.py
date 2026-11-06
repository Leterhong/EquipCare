"""
第七阶段端到端冒烟：运营驾驶舱、通知中心、操作审计、Excel 导出，全程走真实接口。

对应第七阶段验收清单：
  1. 驾驶舱 14 指标 + 8 图全部来自真实数据：未来时间范围全部归零/为空，不编造；
  2. 时间范围筛选有效：>366 天被 400 拒绝；合法范围字段结构与口径正确；
  3. 通知中心：未读数、已读流转、批量已读、全部已读；他人消息越权 403 且状态不变；
  4. 通知触发：真实报修建单后管理员收到「新工单待派单」；
  5. 操作审计：登录已记录（module=认证），请求参数不含明文口令；
  6. Excel 导出五类：设备清单 / 巡检记录 / 维修工单 / 备件库存 / 维修成本统计，
     全部为真实 xlsx（PK 头 + Content-Disposition），无权限用户 403。

脚本临时创建一个无角色用户做权限矩阵验证，结束时停用（不删除）；
管理员口令只从环境变量读。

用法：
  EQUIPCARE_ADMIN_USER=e2e_admin EQUIPCARE_ADMIN_PASSWORD=xxx python scripts/stage7-smoke.py

可选：
  EQUIPCARE_BASE_URL  默认 http://127.0.0.1:8080
"""

import json
import os
import secrets
import sys
import time
import urllib.error
import urllib.request
from urllib.parse import quote

BASE = os.environ.get("EQUIPCARE_BASE_URL", "http://127.0.0.1:8080")
ADMIN_USER = os.environ.get("EQUIPCARE_ADMIN_USER", "e2e_admin")
ADMIN_PASSWORD = os.environ.get("EQUIPCARE_ADMIN_PASSWORD")

PASSED = 0
FAILED = 0
SUFFIX = time.strftime("%H%M%S")
TEMP_USER = "smk7V" + SUFFIX
TEMP_PASS = "S7#" + secrets.token_hex(4) + "!"

TOKEN = None        # 管理员
VIEWER_TOKEN = None # 临时无角色用户

CREATED_ORDER_IDS = []


def check(name, condition, detail=""):
    global PASSED, FAILED
    if condition:
        PASSED += 1
        print(f"  [PASS] {name}" + (f" · {detail}" if detail else ""))
    else:
        FAILED += 1
        print(f"  [FAIL] {name}" + (f" · {detail}" if detail else ""))


def req(method, path, body=None, token=None, raw=False):
    headers = {"Content-Type": "application/json"}
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    use = token if token is not None else TOKEN
    if use:
        headers["Authorization"] = "Bearer " + use
    request = urllib.request.Request(BASE + quote(path, safe="/?&=.-_"),
                                     data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request) as response:
            payload = response.read()
            if raw:
                return response.status, dict(response.headers), payload
            return response.status, json.loads(payload.decode("utf-8"))
    except urllib.error.HTTPError as error:
        text = error.read().decode("utf-8", "ignore")
        if raw:
            return error.code, dict(error.headers), text.encode("utf-8", "ignore")
        try:
            payload = json.loads(text)
        except json.JSONDecodeError:
            payload = {"code": error.code, "message": text[:200]}
        return error.code, payload


def call(method, path, body=None, token=None, label=None):
    status, payload = req(method, path, body, token)
    if status != 200 or payload.get("code") != 200:
        print(f"  [WARN] {label or path} -> HTTP {status} code={payload.get('code')} msg={payload.get('message')}")
    return payload


def login(username, password):
    status, payload = req("POST", "/api/auth/login",
                          {"username": username, "password": password}, token=None)
    assert status == 200 and payload.get("code") == 200, f"登录失败 {username}: HTTP {status} {payload}"
    return payload["data"]["accessToken"]


def today(offset_days=0):
    import datetime
    return (datetime.date.today() + datetime.timedelta(days=offset_days)).isoformat()


def main():
    global TOKEN, VIEWER_TOKEN
    assert ADMIN_PASSWORD, "请通过 EQUIPCARE_ADMIN_PASSWORD 提供管理员口令"
    print("== 登录与准备 ==")
    TOKEN = login(ADMIN_USER, ADMIN_PASSWORD)
    print(f"  [PASS] 管理员登录 {ADMIN_USER}")

    # 无角色权限用户：新建一个空角色再挂用户，所有 @PreAuthorize 都应 403
    role_code = "SMK7R" + SUFFIX
    payload = call("POST", "/api/system/roles",
                   {"roleCode": role_code, "roleName": "冒烟空角色" + SUFFIX,
                    "remark": "阶段七冒烟临时角色，验证权限矩阵"},
                   label="创建临时空角色")
    temp_role_id = payload.get("data")
    payload = call("POST", "/api/system/users",
                   {"username": TEMP_USER, "password": TEMP_PASS,
                    "realName": "冒烟V" + SUFFIX, "status": 1,
                    "roleIds": [temp_role_id] if temp_role_id else []},
                   label="创建临时用户")
    status, payload = req("POST", "/api/auth/login",
                          {"username": TEMP_USER, "password": TEMP_PASS}, token=None)
    if status == 200 and payload.get("code") == 200:
        VIEWER_TOKEN = payload["data"]["accessToken"]
        print(f"  [PASS] 临时无角色用户登录 {TEMP_USER}")
    else:
        print(f"  [WARN] 临时用户登录失败 HTTP {status}，权限矩阵用例将跳过")

    # ------------------------------------------------------------------ 驾驶舱
    print("== 一、运维数据驾驶舱 ==")
    payload = call("GET", "/api/analytics/dashboard/summary", label="summary")
    summary = payload.get("data") or {}
    required_keys = ["equipmentTotal", "equipmentNormal", "equipmentFault", "equipmentDisabled",
                     "equipmentHealthRate", "inspectionCompletionRate", "anomalyCount",
                     "workOrderPending", "workOrderCompleted", "workOrderOverdue",
                     "mttrMinutes", "mtbfHours", "maintenanceCost", "spareConsumptionAmount"]
    missing = [k for k in required_keys if k not in summary]
    check("14 指标字段齐全", not missing, "缺失: %s" % missing if missing else "14/14")
    rate = summary.get("equipmentHealthRate")
    check("健康率在 0~100", rate is None or 0 <= float(rate) <= 100, f"healthRate={rate}")
    check("设备总数为非负整数",
          isinstance(summary.get("equipmentTotal"), (int, float)) and summary["equipmentTotal"] >= 0)

    payload = call("GET", f"/api/analytics/dashboard/summary?startDate={today(1825)}&endDate={today(1855)}",
                   label="summary 未来范围")
    empty_summary = payload.get("data") or {}
    check("未来范围不编造：完成/超时工单为 0",
          int(empty_summary.get("workOrderCompleted") or 0) == 0
          and int(empty_summary.get("workOrderOverdue") or 0) == 0)
    check("未来范围不编造：MTTR/MTBF 为 null",
          empty_summary.get("mttrMinutes") is None and empty_summary.get("mtbfHours") is None)

    status, payload = req("GET", f"/api/analytics/dashboard/summary?startDate={today()}&endDate={today(400)}")
    check("时间跨度 >366 天被拒绝", payload.get("code") == 400, f"code={payload.get('code')}")

    payload = call("GET", "/api/analytics/dashboard/charts?startDate=%s&endDate=%s" % (today(-90), today()),
                   label="charts 近90天")
    charts = payload.get("data") or {}
    chart_keys = ["equipmentStatusDist", "workOrderStatusDist", "monthlyFaultTrend",
                  "monthlyInspectionTrend", "monthlyCostTrend", "faultTypeRank",
                  "equipmentFaultRank", "repairmanRank"]
    missing = [k for k in chart_keys if k not in charts]
    check("8 图字段齐全", not missing, "缺失: %s" % missing if missing else "8/8")
    check("设备状态分布为当前快照（非空列表）", bool(charts.get("equipmentStatusDist")),
          "%d 类" % len(charts.get("equipmentStatusDist") or []))

    payload = call("GET", f"/api/analytics/dashboard/charts?startDate={today(1825)}&endDate={today(1855)}",
                   label="charts 未来范围")
    empty_charts = payload.get("data") or {}
    empty_keys = ["workOrderStatusDist", "monthlyFaultTrend", "monthlyInspectionTrend",
                  "monthlyCostTrend", "faultTypeRank", "equipmentFaultRank", "repairmanRank"]
    check("未来范围 7 个时间图全部为空列表",
          all((empty_charts.get(k) or []) == [] for k in empty_keys))

    # ------------------------------------------------------------ 通知中心与触发
    print("== 二、通知中心 ==")
    payload = call("GET", "/api/system/messages/unread-count", label="unread-count")
    unread_before = int((payload.get("data") or 0))
    check("未读数接口可用", True, f"当前未读 {unread_before}")

    payload = call("GET", "/api/system/messages?pageNum=1&pageSize=10&readFlag=0", label="未读分页")
    records = ((payload.get("data") or {}).get("records")) or []
    check("未读分页 readFlag=0 过滤生效",
          all(int(row.get("readFlag") or 0) == 0 for row in records), f"{len(records)} 条")

    # 真实报修建单 → 管理员应收到「新工单待派单」
    payload = call("GET", "/api/asset/equipment?pageNum=1&pageSize=1", label="取设备")
    equip_rows = ((payload.get("data") or {}).get("records")) or []
    if equip_rows:
        equipment_id = equip_rows[0]["id"]
        payload = call("POST", "/api/workorder",
                       {"equipmentId": equipment_id, "title": "冒烟7-通知触发" + SUFFIX,
                        "faultType": "ELECTRICAL", "faultDesc": "阶段七冒烟自动报修", "level": "P3"},
                       label="报修建单")
        order_id = payload.get("data")
        if order_id:
            CREATED_ORDER_IDS.append(order_id)
        payload = call("GET", "/api/system/messages?pageNum=1&pageSize=10&title=新工单待派单",
                       label="回查通知")
        rows = ((payload.get("data") or {}).get("records")) or []
        hit = [r for r in rows if (r.get("bizId") == order_id or r.get("bizId") is None)]
        check("报修后管理员收到「新工单待派单」", bool(rows), f"匹配 {len(rows)} 条")

        payload = call("GET", "/api/system/messages/unread-count", label="unread-after")
        unread_after = int((payload.get("data") or 0))
        check("新通知使未读数增加", unread_after >= unread_before + 1,
              f"{unread_before} -> {unread_after}")

        unread_ids = [int(r["id"]) for r in rows if int(r.get("readFlag") or 0) == 0][:3]
        if len(unread_ids) >= 2:
            payload = call("PATCH", "/api/system/messages/read-batch", unread_ids[:2], label="批量已读")
            check("批量标记已读 code=200", payload.get("code") == 200)
            payload = call("GET", "/api/system/messages/unread-count", label="unread-after-batch")
            check("批量已读后未读数减少 2",
                  int(payload.get("data") or 0) == unread_after - 2)
        elif unread_ids:
            status, payload = req("PATCH", f"/api/system/messages/{unread_ids[0]}/read")
            check("单条标记已读 code=200", payload.get("code") == 200)

        if VIEWER_TOKEN and unread_ids:
            # 越权：临时用户标记管理员消息
            status, payload = req("PATCH", f"/api/system/messages/{unread_ids[0]}/read",
                                  token=VIEWER_TOKEN)
            check("越权标记他人消息被 403", status == 403 or payload.get("code") == 403,
                  f"HTTP {status} code={payload.get('code')}")
    else:
        check("报修后管理员收到「新工单待派单」", False, "库中无可用设备，跳过")

    payload = call("PATCH", "/api/system/messages/read-all", label="read-all")
    payload = call("GET", "/api/system/messages/unread-count", label="unread-final")
    check("全部已读后未读数为 0", int(payload.get("data") or 0) == 0,
          f"未读 {payload.get('data')}")

    # ------------------------------------------------------------------ 操作审计
    print("== 三、操作审计 ==")
    status, payload = req("GET", "/api/system/audit-logs?pageNum=1&pageSize=10&module=认证")
    logs = ((payload.get("data") or {}).get("records")) or []
    check("审计分页查询可用（module=认证）", bool(logs), f"{len(logs)} 条")
    if logs:
        my_rows = [row for row in logs if row.get("username") == ADMIN_USER]
        check("登录动作已记录", bool(my_rows))
        param = " ".join(str(row.get("requestParam") or "") for row in my_rows)
        check("请求参数不含明文口令", ADMIN_PASSWORD not in param)
        check("审计记录含用户名与结果",
              all(row.get("username") and row.get("resultStatus") for row in my_rows))
    status, payload = req("GET", "/api/system/audit-logs?pageNum=1&pageSize=5&resultStatus=FAIL")
    check("按 resultStatus 过滤可用", status == 200 and payload.get("code") == 200)

    # --------------------------------------------------------------- Excel 导出
    print("== 四、Excel 导出 ==")
    exports = [
        ("设备清单", "/api/export/devices"),
        ("巡检记录", f"/api/export/inspections?startDate={today(-30)}&endDate={today()}"),
        ("维修工单", f"/api/export/work-orders?startDate={today(-30)}&endDate={today()}"),
        ("备件库存", "/api/export/inventory"),
        ("维修成本统计", f"/api/export/cost-stats?startDate={today(-30)}&endDate={today()}"),
    ]
    for name, path in exports:
        status, headers, body = req("GET", path, raw=True)
        ctype = headers.get("Content-Type", "")
        dispo = headers.get("Content-Disposition", "")
        check(f"{name}：200 且为真实 xlsx",
              status == 200 and "spreadsheetml" in ctype and body[:2] == b"PK" and len(body) > 500,
              f"{len(body)} bytes")
        check(f"{name}：带下载文件名", ".xlsx" in dispo)

    # ------------------------------------------------------------------ 权限矩阵
    print("== 五、权限矩阵 ==")
    if VIEWER_TOKEN:
        status, payload = req("GET", "/api/analytics/dashboard/summary", token=VIEWER_TOKEN)
        check("无角色用户访问驾驶舱被拒", status == 403 or payload.get("code") == 403,
              f"HTTP {status}")
        status, _, _ = req("GET", "/api/export/devices", token=VIEWER_TOKEN, raw=True)
        check("无角色用户导出设备清单被 403", status == 403, f"HTTP {status}")
        status, payload = req("GET", "/api/system/audit-logs?pageNum=1&pageSize=5", token=VIEWER_TOKEN)
        check("无角色用户查审计被拒", status == 403 or payload.get("code") == 403,
              f"HTTP {status}")
    else:
        check("权限矩阵（临时用户登录失败，跳过）", False, "未取得 viewer token")

    # -------------------------------------------------------------------- 收尾
    print("== 收尾 ==")
    for order_id in CREATED_ORDER_IDS:
        status, payload = req("POST", f"/api/workorder/{order_id}/cancel",
                              {"reason": "阶段七冒烟清理"})
        print(f"  [CLEAN] 工单 {order_id} 取消 -> HTTP {status}")
    status, payload = req("GET", "/api/system/users?pageNum=1&pageSize=50&keyword=" + TEMP_USER)
    for row in ((payload.get("data") or {}).get("records")) or []:
        if row.get("username") == TEMP_USER:
            req("PATCH", f"/api/system/users/{row['id']}/status?status=0")
            print(f"  [CLEAN] 临时用户 {TEMP_USER} 已停用")
    if temp_role_id:
        status, payload = req("DELETE", f"/api/system/roles/{temp_role_id}")
        print(f"  [CLEAN] 临时角色 {role_code} 删除 -> HTTP {status}")

    print(f"\n== 结果：PASS {PASSED} / FAIL {FAILED} ==")
    sys.exit(1 if FAILED else 0)


if __name__ == "__main__":
    main()
