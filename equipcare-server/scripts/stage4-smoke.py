"""
第四阶段端到端冒烟：模板 → 计划 → 自动生成 → 三态提交 → 异常转工单 → 日历与统计。

只用标准库，跑在真实 MySQL 上，验证的是一条完整业务链路，不是接口连通性：
  1. 建模板（5 个检查项，含一个「异常必须留证」的数值项）并发布；
  2. 建每日计划，按设备作用域展开快照；
  3. 立即生成任务，再生成一次确认「同一计划同一设备同一天」不会重复；
  4. 开始巡检 → 逐项判定（正常 / 异常 / 跳过规则）→ 提交；
  5. 异常单转维修工单，回查工单详情里的巡检来源与现场照片；
  6. 日历与统计能看到刚才这批数据。

用法：
  EQUIPCARE_ADMIN_USER=xxx EQUIPCARE_ADMIN_PASSWORD=xxx python scripts/stage4-smoke.py
口令只从环境变量读取，脚本内不保存任何账号密码。
"""

import base64
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid

BASE = os.environ.get("EQUIPCARE_BASE_URL", "http://127.0.0.1:8080")
ADMIN_USER = os.environ.get("EQUIPCARE_ADMIN_USER", "e2e_admin")
ADMIN_PASSWORD = os.environ.get("EQUIPCARE_ADMIN_PASSWORD")

TOKEN = None
PASSED = 0
FAILED = 0
SUFFIX = time.strftime("%H%M%S")


def check(name, condition, detail=""):
    global PASSED, FAILED
    if condition:
        PASSED += 1
        print(f"  [PASS] {name}" + (f" · {detail}" if detail else ""))
    else:
        FAILED += 1
        print(f"  [FAIL] {name}" + (f" · {detail}" if detail else ""))


def call(method, path, body=None, expect=200):
    url = BASE + path
    data = None
    headers = {"Content-Type": "application/json"}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    if TOKEN:
        headers["Authorization"] = "Bearer " + TOKEN
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        text = error.read().decode("utf-8", "ignore")
        try:
            payload = json.loads(text)
        except json.JSONDecodeError:
            payload = {"code": error.code, "message": text}
        payload["_httpStatus"] = error.code
    if payload.get("code") != expect:
        raise RuntimeError(f"{method} {path} 期望 code={expect}，实际 {payload.get('code')}：{payload.get('message')}")
    return payload.get("data")


def upload_png():
    """上传一张 1x1 的 PNG，返回 fileId；用于验证异常留证链路。"""
    png = base64.b64decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8AAAwAB/wD/1gAAAABJRU5ErkJggg=="
    )
    boundary = "----equipcare" + uuid.uuid4().hex
    parts = [
        f"--{boundary}".encode(),
        b'Content-Disposition: form-data; name="file"; filename="site.png"',
        b"Content-Type: image/png",
        b"",
        png,
        f"--{boundary}".encode(),
        b'Content-Disposition: form-data; name="bizType"',
        b"",
        b"INSPECT",
        f"--{boundary}--".encode(),
        b"",
    ]
    body = b"\r\n".join(parts)
    headers = {
        "Content-Type": f"multipart/form-data; boundary={boundary}",
        "Authorization": "Bearer " + TOKEN,
    }
    request = urllib.request.Request(BASE + "/api/files/upload", data=body, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(request) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"文件上传失败：{error.read().decode('utf-8', 'ignore')}")
    if payload.get("code") != 200:
        raise RuntimeError(f"文件上传失败：{payload.get('message')}")
    return payload["data"]["fileId"]


def main():
    global TOKEN
    if not ADMIN_PASSWORD:
        print("请通过 EQUIPCARE_ADMIN_PASSWORD 传入管理员口令")
        return 2

    print("=" * 66)
    print("第四阶段冒烟：智能巡检中心")
    print("=" * 66)

    print("\n[1] 登录与账号权限")
    login = None
    url = BASE + "/api/auth/login"
    request = urllib.request.Request(
        url,
        data=json.dumps({"username": ADMIN_USER, "password": ADMIN_PASSWORD}).encode(),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request) as response:
        login = json.loads(response.read().decode("utf-8"))["data"]
    TOKEN = login["accessToken"]
    permissions = login.get("user", {}).get("permissions", [])
    check("登录成功", bool(TOKEN))
    check("巡检日历/统计权限已下发",
          "inspect:calendar:view" in permissions and "inspect:stats:view" in permissions,
          f"共 {len(permissions)} 个权限码")

    print("\n[2] 巡检模板：检查项 + 判定规则 + 留证要求")
    template_payload = {
        "templateCode": "SMK4TPL" + SUFFIX,
        "templateName": f"冒烟水泵模板 {SUFFIX}",
        "categoryId": None,
        "description": "冒烟脚本创建，用于验证第四阶段链路",
        "items": [
            {
                "itemName": "检查设备运行声音", "itemType": "ENUM",
                "enumOptions": '["正常","异响","噪声过大"]', "normalValue": None,
                "required": 1, "checkMethod": "听诊", "standardRef": "设备操作规程 5.1",
                "abnormalRule": "出现异响即判为异常", "autoWorkOrder": 1, "photoRequired": 0,
                "abnormalLevel": "HIGH", "seqNo": 1,
            },
            {
                "itemName": "检查设备外壳温度", "itemType": "NUMBER", "unit": "℃",
                "minValue": 0, "maxValue": 60, "required": 1,
                "checkMethod": "红外测温", "standardRef": "GB/T 29529-2013",
                "abnormalRule": "超出 0~60℃ 判为异常", "autoWorkOrder": 0, "photoRequired": 1,
                "abnormalLevel": "MEDIUM", "seqNo": 2,
            },
            {
                "itemName": "检查管道是否漏水", "itemType": "BOOLEAN", "normalValue": "0",
                "required": 1, "checkMethod": "目视", "standardRef": "给排水管道验收规范",
                "abnormalRule": "存在渗漏即异常", "autoWorkOrder": 1, "photoRequired": 0,
                "abnormalLevel": "HIGH", "seqNo": 3,
            },
            {
                "itemName": "检查电机振动情况", "itemType": "ENUM",
                "enumOptions": '["正常","轻微振动","明显振动"]',
                "required": 1, "checkMethod": "测振仪", "standardRef": "ISO 10816-3",
                "abnormalRule": "明显振动即异常", "autoWorkOrder": 1, "photoRequired": 0,
                "abnormalLevel": "HIGH", "seqNo": 4,
            },
            {
                "itemName": "检查电源连接情况", "itemType": "BOOLEAN", "normalValue": "1",
                "required": 0, "checkMethod": "目视", "standardRef": "GB 50169-2016",
                "abnormalRule": "接线松动或发热即异常", "autoWorkOrder": 1, "photoRequired": 0,
                "abnormalLevel": "CRITICAL", "seqNo": 5,
            },
        ],
    }
    template_id = call("POST", "/api/inspection/templates", template_payload)
    check("模板创建成功", isinstance(template_id, int) and template_id > 0, f"id={template_id}")

    detail = call("GET", f"/api/inspection/templates/{template_id}")
    check("模板详情含 5 个检查项", len(detail.get("items") or []) == 5)
    check("判定规则与留证要求已落库",
          bool(detail["items"][1].get("abnormalRule")) and detail["items"][1].get("photoRequired") == 1)

    call("POST", f"/api/inspection/templates/{template_id}/publish")
    detail = call("GET", f"/api/inspection/templates/{template_id}")
    check("模板发布成功", detail.get("status") == "PUBLISHED")

    print("\n[3] 巡检计划：作用域展开为设备快照")
    equipments = call("GET", "/api/asset/equipment?pageNum=1&pageSize=2")
    equipment_ids = [row["id"] for row in (equipments.get("records") or [])]
    if len(equipment_ids) < 2:
        print("  可用设备不足 2 台，脚本终止")
        return 1

    today = time.strftime("%Y-%m-%d")
    plan_payload = {
        "planCode": "SMK4PLAN" + SUFFIX,
        "planName": f"冒烟每日巡检 {SUFFIX}",
        "templateId": template_id,
        "scopeType": "EQUIPMENT",
        "scopeIds": equipment_ids,
        "executorStrategy": "FIXED",
        "executorIds": [login["user"]["userId"]],
        "cycleType": "DAILY",
        "executeTime": "00:01",
        "startDate": today,
        "endDate": None,
        "slaMinutes": 720,
        "generateAheadHours": 0,
    }
    plan_id = call("POST", "/api/inspection/plans", plan_payload)
    check("计划创建成功", isinstance(plan_id, int) and plan_id > 0, f"id={plan_id}")

    plan_detail = call("GET", f"/api/inspection/plans/{plan_id}")
    check("计划创建后默认暂停（确认无误再启用）", plan_detail.get("status") == "DISABLED",
          f"status={plan_detail.get('status')}")
    check("作用域展开为 2 台设备", (plan_detail.get("equipmentCount") or 0) == 2,
          f"equipmentCount={plan_detail.get('equipmentCount')}")
    check("周期描述可读", bool(plan_detail.get("cycleLabel")), plan_detail.get("cycleLabel"))

    call("PATCH", f"/api/inspection/plans/{plan_id}/status?status=ENABLED")
    enabled = call("GET", f"/api/inspection/plans/{plan_id}")
    check("计划启用成功", enabled.get("status") == "ENABLED" and bool(enabled.get("nextGenerateTime")))

    print("\n[4] 任务自动生成与重复防护")
    created = call("POST", f"/api/inspection/plans/{plan_id}/generate")
    check("首次生成任务", created >= 1, f"created={created}")
    again = call("POST", f"/api/inspection/plans/{plan_id}/generate")
    check("再次生成不重复", again == 0, f"created={again}")

    tasks = call("GET", f"/api/inspection/tasks?pageNum=1&pageSize=20&planId={plan_id}")
    rows = tasks.get("records") or []
    check("任务列表按计划过滤", len(rows) >= 1, f"{len(rows)} 条")
    task = rows[0]
    check("任务带计划、执行人与时限",
          task.get("planId") == plan_id and bool(task.get("assigneeName")) and bool(task.get("slaDueTime")))

    print("\n[5] 巡检执行：三态判定与现场证据")
    call("POST", f"/api/inspection/tasks/{task['id']}/start", {})
    task_detail = call("GET", f"/api/inspection/tasks/{task['id']}")
    items = task_detail.get("items") or []
    check("任务详情带检查标准与判定规则",
          len(items) == 5 and all(item.get("standardRef") for item in items))

    photo_id = upload_png()
    check("现场照片上传成功", isinstance(photo_id, int) and photo_id > 0, f"fileId={photo_id}")

    submit_items = []
    for index, item in enumerate(items):
        if index == 1:
            submit_items.append({
                "templateItemId": item["templateItemId"], "result": "ABNORMAL",
                "itemValue": "95", "remark": "外壳温度偏高，怀疑散热不良",
                "attachmentIds": str(photo_id),
            })
        elif index == 2:
            submit_items.append({"templateItemId": item["templateItemId"], "result": "NORMAL", "itemValue": "0"})
        elif index == 4:
            submit_items.append({"templateItemId": item["templateItemId"], "result": "SKIPPED",
                                 "skipReason": "该设备未接入市电，现场无法检查"})
        elif item["itemType"] == "ENUM":
            options = json.loads(item.get("enumOptions") or '["正常"]')
            submit_items.append({"templateItemId": item["templateItemId"], "result": "NORMAL", "itemValue": options[0]})
        else:
            submit_items.append({"templateItemId": item["templateItemId"], "result": "NORMAL", "itemValue": "0"})

    # 先验证约束：必检项跳过会被拒绝
    bad = [dict(row) for row in submit_items]
    bad[0] = {"templateItemId": items[0]["templateItemId"], "result": "SKIPPED", "skipReason": "试试"}
    try:
        call("POST", f"/api/inspection/tasks/{task['id']}/submit", {"items": bad}, expect=400)
        check("必检项跳过被拒绝", True)
    except RuntimeError as error:
        check("必检项跳过被拒绝", False, str(error))

    abnormal_count = call("POST", f"/api/inspection/tasks/{task['id']}/submit",
                          {"items": submit_items, "remark": "冒烟提交"})
    # 提交接口返回的是本次判为异常的项数（异常单已同步生成），不是填报项总数
    check("巡检结果提交成功", abnormal_count == 1, f"异常 {abnormal_count} 项")

    submitted = call("GET", f"/api/inspection/tasks/{task['id']}")
    check("任务状态变为已提交", submitted.get("status") == "SUBMITTED")
    check("异常与跳过数量正确",
          submitted.get("abnormalCount") == 1 and submitted.get("skippedCount") == 1,
          f"异常 {submitted.get('abnormalCount')} / 跳过 {submitted.get('skippedCount')}")

    print("\n[6] 异常转维修工单（只落关联，不复制数据）")
    anomalies = call("GET", f"/api/inspection/anomalies?pageNum=1&pageSize=10&taskId={task['id']}")
    anomaly_rows = anomalies.get("records") or []
    check("异常单自动生成", len(anomaly_rows) == 1)
    anomaly = anomaly_rows[0]
    check("异常单带实测值与现场照片",
          anomaly.get("itemValue") == "95" and len(anomaly.get("attachments") or []) == 1)

    work_order_id = call("POST", f"/api/inspection/anomalies/{anomaly['id']}/work-order",
                         {"level": "P2", "remark": "冒烟脚本转单"})
    check("转工单成功", isinstance(work_order_id, int) and work_order_id > 0, f"workOrderId={work_order_id}")

    order = call("GET", f"/api/workorder/{work_order_id}")
    source = order.get("sourceInspection") or {}
    merged = order.get("mergedInspections") or []
    # 同设备同故障类型已有在途工单时，新异常会合并到该工单：
    # 首张异常落在 sourceInspection，后续的落在 mergedInspections，两者都要能查到本次异常
    linked = [source] + merged
    hit = next((row for row in linked if row.get("anomalyId") == anomaly["id"]), None)
    check("工单能找到本次巡检异常",
          hit is not None,
          f"合并 {len(merged)} 条 · itemName={(hit or {}).get('itemName')}")
    check("工单侧可回看实测值", (hit or {}).get("itemValue") == "95")
    check("工单侧可回看现场照片", len((hit or {}).get("attachments") or []) == 1)
    check("合并进工单的异常也带任务号与上报人",
          all(row.get("taskCode") and row.get("reporterName") for row in merged) if merged else True)

    try:
        call("POST", f"/api/inspection/anomalies/{anomaly['id']}/work-order", {}, expect=2004)
        check("重复转单被拒绝", True)
    except RuntimeError as error:
        check("重复转单被拒绝", False, str(error))

    print("\n[7] 日历与统计")
    month = today[:7]
    calendar = call("GET", f"/api/inspection/calendar?month={month}")
    day = next((row for row in calendar if str(row.get("date")) == today), None)
    check("日历能看到当天任务", day is not None and (day.get("total") or 0) >= 1,
          f"{day.get('total') if day else 0} 条")

    stats = call("GET", "/api/inspection/stats?days=30")
    check("统计总量大于 0", (stats.get("totalTasks") or 0) > 0, f"totalTasks={stats.get('totalTasks')}")
    check("完成率在合理区间", 0 <= (stats.get("completionRate") or 0) <= 100,
          f"{stats.get('completionRate')}%")
    check("统计含每日趋势", len(stats.get("daily") or []) >= 1)
    check("统计含计划维度", any(row.get("planId") == plan_id for row in (stats.get("byPlan") or [])))

    print("\n[8] 清理（暂停并删除计划、删除模板）")
    call("PATCH", f"/api/inspection/plans/{plan_id}/status?status=DISABLED")
    call("DELETE", f"/api/inspection/plans/{plan_id}")
    call("DELETE", f"/api/inspection/templates/{template_id}")
    check("计划与模板已清理", call("GET", f"/api/inspection/plans?pageNum=1&pageSize=5&keyword=SMK4PLAN{SUFFIX}")
          .get("total") == 0)

    print("\n" + "=" * 66)
    print(f"通过 {PASSED} 项，失败 {FAILED} 项")
    print("=" * 66)
    return 0 if FAILED == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
