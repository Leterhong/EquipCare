"""
端到端冒烟脚本：巡检 → 异常 → 自动工单 → 派单 → 接单 → 维修 → 提交验收 → 验收通过。
仅用于本地交付前自测，不参与构建。
"""
import json
import os
import sys
import urllib.request
import urllib.error

BASE = os.environ.get("EQUIPCARE_BASE_URL", "http://127.0.0.1:8080")
ADMIN_USER = os.environ.get("EQUIPCARE_ADMIN_USER", "admin")
# 请通过环境变量传入引导时设置的管理员密码，脚本内不硬编码任何口令
ADMIN_PASSWORD = os.environ.get("EQUIPCARE_ADMIN_PASSWORD")
TOKEN = None


def call(method, path, body=None, token=None, expect=200):
    url = BASE + path
    data = None
    headers = {"Content-Type": "application/json"}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            status = resp.status
            payload = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        status = e.code
        payload = json.loads(e.read().decode("utf-8"))
    ok = (status == expect)
    mark = "OK " if ok else "FAIL"
    print(f"[{mark}] {method} {path} -> {status} code={payload.get('code')} msg={payload.get('message')}")
    if not ok:
        raise SystemExit(f"步骤失败: {method} {path} 期望 {expect} 实际 {status} {payload}")
    return payload.get("data")


def login(username, password):
    data = call("POST", "/api/auth/login", {"username": username, "password": password})
    return data["accessToken"]


if not ADMIN_PASSWORD:
    sys.exit("请先设置环境变量 EQUIPCARE_ADMIN_PASSWORD（引导创建管理员时使用的密码）")

print("=" * 70)
print("0. 管理员登录")
admin = login(ADMIN_USER, ADMIN_PASSWORD)

print("-" * 70)
print("1. 创建维修人员账号")
roles = call("GET", "/api/system/roles", token=admin)
repair_role = next(r["id"] for r in roles if r["roleCode"] == "REPAIRMAN")
try:
    call("POST", "/api/system/users", {
        "username": "repair01", "password": "Repair1234", "realName": "李维修",
        "roleIds": [repair_role], "skillTags": "电气,水泵"
    }, token=admin)
except SystemExit:
    print("     （账号已存在，继续）")

print("-" * 70)
print("2. 手工发起巡检任务（设备 11）")
task_id = call("POST", "/api/inspection/tasks", {"equipmentId": 11, "remark": "冒烟测试"}, token=admin)
detail = call("GET", f"/api/inspection/tasks/{task_id}", token=admin)
print(f"     任务编号={detail['taskCode']} 检查项={len(detail['items'])} 个")

print("-" * 70)
print("3. 开始巡检（带二维码核验）")
qr = call("GET", "/api/asset/equipment/11/qrcode", token=admin)["qrToken"]
call("POST", f"/api/inspection/tasks/{task_id}/start", {"qrToken": qr}, token=admin)

print("-" * 70)
print("4. 提交巡检：把一个数值项填成超限值，标记异常并填写原因，触发自动工单")
items = []
abnormal_done = False
for item in detail["items"]:
    value = "正常"
    result = "NORMAL"
    remark = None
    if item["itemType"] == "NUMBER":
        low = item["minValue"]
        high = item["maxValue"]
        if low is not None and high is not None and not abnormal_done:
            value = str(float(high) + 100)
            result = "ABNORMAL"
            remark = "冒烟测试：实测值超上限，需检修"
            abnormal_done = True
        elif low is not None and high is not None:
            value = str((float(low) + float(high)) / 2)
        else:
            value = "1"
    elif item["itemType"] == "ENUM" and item.get("enumOptions"):
        value = item["enumOptions"].split(",")[0]
    payload = {"templateItemId": item["templateItemId"], "itemValue": value, "result": result}
    if remark:
        payload["remark"] = remark
    items.append(payload)

abnormal_count = call("POST", f"/api/inspection/tasks/{task_id}/submit",
                      {"items": items, "remark": "自动化冒烟"}, token=admin)
print(f"     本次产生异常 {abnormal_count} 条")

print("-" * 70)
print("5. 查看异常单与自动生成的工单")
anomalies = call("GET", "/api/inspection/anomalies?pageSize=5", token=admin)
for a in anomalies["records"][:3]:
    print(f"     异常 {a['anomalyNo']} 状态={a['status']} 工单={a['workOrderNo']} 工单状态={a['workOrderStatus']}")

orders = call("GET", "/api/workorder?pageSize=5", token=admin)
if not orders["records"]:
    raise SystemExit("未找到自动生成的工单，闭环失败")
order = orders["records"][0]
order_id = order["id"]
print(f"     工单 {order['orderNo']} 状态={order['statusLabel']} 来源={order['source']} 允许动作={order['allowedActions']}")

print("-" * 70)
print("6. 派单给 repair01")
users = call("GET", "/api/system/users?keyword=repair01", token=admin)
repair_id = users["records"][0]["id"]
call("POST", f"/api/workorder/{order_id}/dispatch", {"assigneeId": repair_id, "reason": "就近安排"}, token=admin)

print("-" * 70)
print("7. 维修人登录并接单")
repair = login("repair01", "Repair1234")
call("POST", f"/api/workorder/{order_id}/accept", {}, token=repair)

print("-" * 70)
print("8. 写维修记录（诊断 + 完工带照片）")
call("POST", f"/api/workorder/{order_id}/records",
     {"recordType": "DIAGNOSIS", "content": "检查发现轴承磨损，需更换", "costMinutes": 20}, token=repair)
call("POST", f"/api/workorder/{order_id}/records",
     {"recordType": "FINISH", "content": "已更换轴承并试运行 30 分钟，无异响",
      "costMinutes": 60, "attachmentIds": "1,2"}, token=repair)

print("-" * 70)
print("9. 维修人提交验收")
call("POST", f"/api/workorder/{order_id}/submit-verify",
     {"repairSummary": "更换轴承，试运行正常", "laborHours": 1.5}, token=repair)

print("-" * 70)
print("10. 维修人尝试自验收（应当被 3003 拦截）")
call("POST", f"/api/workorder/{order_id}/verify-pass", {"qualityScore": 5, "remark": "自己验自己"},
     token=repair, expect=403)

print("-" * 70)
print("11. 管理员验收通过")
call("POST", f"/api/workorder/{order_id}/verify-pass",
     {"qualityScore": 5, "remark": "现场复查合格"}, token=admin)

print("-" * 70)
print("12. 已完成工单再次派单（应当被 3001 拦截）")
call("POST", f"/api/workorder/{order_id}/dispatch", {"assigneeId": repair_id, "reason": "重复派单"},
     token=admin, expect=409)

print("-" * 70)
print("13. 查询工单轨迹与设备健康分")
final = call("GET", f"/api/workorder/{order_id}", token=admin)
print(f"     最终状态={final['statusLabel']} 轨迹节点={len(final['timeline'])} 条")
for node in final["timeline"]:
    print(f"       {node['createTime']} {node['fromStatusLabel']} --{node['actionLabel']}--> {node['toStatusLabel']}")

health = call("POST", f"/api/analytics/health/11/recalculate", token=admin)
print(f"     设备健康分={health['healthScore']} 等级={health['levelLabel']} "
      f"故障扣分={health['faultScore']} 异常扣分={health['anomalyScore']}")

print("=" * 70)
print("端到端闭环冒烟测试全部通过")
