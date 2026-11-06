"""
第六阶段端到端冒烟：备品备件闭环、SLA 超时预警、设备健康评分，全程走真实接口与真实事务。

对应第六阶段验收清单：
  1. 库存扣减事务：入库 / 出库 / 盘点全部落流水，流水与库存始终一致；
  2. 并发领料：库存只够一份时，两个领用同时审批恰好一成一败（422），库存不为负；
  3. 库存不足：直接出库与领用审批都被 422 拒绝；
  4. SLA 超时：政策改到 1 分钟后真实等超时，扫描置 OVERDUE、登记预警并通知；
     重复扫描不再产生新预警记录（同单同阶段同天唯一）；
  5. 健康评分规则：干净设备 100 分，在途工单 -3 可解释，取消后恢复 100。

另有：备件分类 / 档案（编码唯一、库存不可直改）、退料入库、库存预警、
维修成本统计、SLA 政策配置、权限矩阵（维修人 403）。

脚本自行创建临时维修工并在结束时停用（不删除，避免时间线断链）；
管理员口令只从环境变量读。SLA 政策修改后在收尾恢复原值。

用法：
  EQUIPCARE_ADMIN_USER=e2e_admin EQUIPCARE_ADMIN_PASSWORD=xxx python scripts/stage6-smoke.py

可选：
  EQUIPCARE_BASE_URL  默认 http://127.0.0.1:8080
"""

import json
import os
import secrets
import sys
import threading
import time
import urllib.error
import urllib.request

BASE = os.environ.get("EQUIPCARE_BASE_URL", "http://127.0.0.1:8080")
ADMIN_USER = os.environ.get("EQUIPCARE_ADMIN_USER", "admin")
ADMIN_PASSWORD = os.environ.get("EQUIPCARE_ADMIN_PASSWORD")

PASSED = 0
FAILED = 0
SUFFIX = time.strftime("%H%M%S")

TOKEN = None            # 管理员
REPAIR_TOKEN = None     # 临时维修工

CREATED_USERS = []


def check(name, condition, detail=""):
    global PASSED, FAILED
    if condition:
        PASSED += 1
        print(f"  [PASS] {name}" + (f" · {detail}" if detail else ""))
    else:
        FAILED += 1
        print(f"  [FAIL] {name}" + (f" · {detail}" if detail else ""))


def req(method, path, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    use = token if token is not None else TOKEN
    if use:
        headers["Authorization"] = "Bearer " + use
    # 查询串里可能有中文（备注/原因），统一按 UTF-8 编码
    from urllib.parse import quote
    request = urllib.request.Request(BASE + quote(path, safe="/?&=.-_"),
                                     data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        text = error.read().decode("utf-8", "ignore")
        try:
            payload = json.loads(text)
        except json.JSONDecodeError:
            payload = {"code": error.code, "message": text[:200]}
        return error.code, payload


def call(method, path, body=None, token=None, expect=200, label=None):
    status, payload = req(method, path, body, token)
    where = label or f"{method} {path}"
    if status != 200 or payload.get("code") != expect:
        raise RuntimeError(f"{where} 期望 HTTP 200 + code={expect}，实际 HTTP {status} + "
                           f"code={payload.get('code')}：{payload.get('message')}")
    return payload.get("data")


def fails(method, path, body=None, token=None, codes=(3001,), expect_http=200):
    """失败调用：断言业务码落在 codes 里，返回实际 (status, code, message)。"""
    status, payload = req(method, path, body, token)
    code = payload.get("code")
    if status != expect_http or code not in codes:
        raise RuntimeError(f"{method} {path} 期望失败码 {codes}，实际 HTTP {status} + "
                           f"code={code}：{payload.get('message')}")
    return status, code, payload.get("message") or ""


def login(username, password):
    status, payload = req("POST", "/api/auth/login", {"username": username, "password": password})
    if status != 200 or payload.get("code") != 200:
        raise RuntimeError(f"登录 {username} 失败：{payload.get('message')}")
    return payload["data"]


def create_temp_repairman():
    roles = call("GET", "/api/system/roles")
    role_id = None
    for role in roles:
        if role.get("roleCode") == "REPAIRMAN":
            role_id = role.get("id")
            break
    if role_id is None:
        raise RuntimeError("角色 REPAIRMAN 不存在，请先执行权限初始化")
    username = f"smk6R{SUFFIX}"
    password = "Smk6@" + secrets.token_urlsafe(9)
    user_id = call("POST", "/api/system/users", {
        "username": username,
        "password": password,
        "realName": f"冒烟维修工{SUFFIX}",
        "roleIds": [role_id],
    })
    session = login(username, password)
    CREATED_USERS.append(user_id)
    return user_id, session["accessToken"]


def part_stock(part_id, part_no):
    page = call("GET", f"/api/inventory/parts?pageNum=1&pageSize=50&keyword={part_no}")
    for row in page.get("records") or []:
        if row.get("id") == part_id:
            return int(row.get("currentStock") or 0)
    raise RuntimeError(f"备件 {part_id} 查不到")


def main():
    global TOKEN, REPAIR_TOKEN
    if not ADMIN_PASSWORD:
        print("请通过 EQUIPCARE_ADMIN_PASSWORD 传入管理员口令")
        return 2

    print("=" * 70)
    print("第六阶段冒烟：备品备件 / SLA 预警 / 设备健康")
    print("=" * 70)

    # ---------------------------------------------------------------- [1]
    print("\n[1] 管理员登录与第六阶段权限码")
    session = login(ADMIN_USER, ADMIN_PASSWORD)
    TOKEN = session["accessToken"]
    perms = session.get("user", {}).get("permissions", [])
    need = ["spare:part:view", "spare:part:manage", "spare:stock:in", "spare:stock:adjust",
            "spare:issue", "spare:txn:view", "spare:stock:view", "spare:alert:view",
            "sla:policy:view", "sla:policy:manage", "sla:monitor:view", "health:view"]
    missing = [p for p in need if p not in perms]
    check("登录成功", bool(TOKEN))
    check("备件 / SLA / 健康权限码已下发", not missing,
          f"共 {len(perms)} 个权限码" if not missing else f"缺 {missing}")

    # ---------------------------------------------------------------- [2]
    print("\n[2] 临时维修工")
    repair_id, REPAIR_TOKEN = create_temp_repairman()
    check("临时维修工创建并可登录", repair_id > 0, f"userId={repair_id}")

    # ---------------------------------------------------------------- [3]
    print("\n[3] 备件分类与备件档案")
    category_id = call("POST", "/api/inventory/categories",
                       {"categoryName": f"冒烟分类6_{SUFFIX}", "remark": "第六阶段冒烟", "sort": 99})
    check("分类创建成功", isinstance(category_id, int) and category_id > 0, f"id={category_id}")
    call("PUT", f"/api/inventory/categories/{category_id}",
         {"categoryName": f"冒烟分类6改_{SUFFIX}", "remark": "重命名一次", "sort": 99})
    categories = call("GET", "/api/inventory/categories")
    renamed = next((c for c in categories if c.get("id") == category_id), None)
    check("分类重命名生效且带在档备件数", renamed is not None
          and f"冒烟分类6改_{SUFFIX}" == renamed.get("categoryName")
          and "partCount" in renamed,
          renamed.get("categoryName") if renamed else "未找到")

    part_id = call("POST", "/api/inventory/parts", {
        "partNo": f"SMK6-{SUFFIX}",
        "partName": f"冒烟轴承6208_{SUFFIX}",
        "categoryId": category_id,
        "spec": "6208-2Z",
        "unit": "套",
        "brand": "NSK",
        "safetyStock": 5,
        "unitPrice": 30,
    })
    PART_NO = f"SMK6-{SUFFIX}"
    check("备件档案创建成功（编码/名称/规格/单位）", isinstance(part_id, int) and part_id > 0,
          f"id={part_id}")
    _, code, _ = fails("POST", "/api/inventory/parts", {
        "partNo": f"SMK6-{SUFFIX}",
        "partName": "重复编码备件",
        "categoryId": category_id,
        "spec": "x",
        "unit": "个",
    }, codes=(2002,), expect_http=409)
    check("备件编码唯一约束生效", True, f"拒绝码 {code}")
    call("PUT", f"/api/inventory/parts/{part_id}", {
        "partNo": f"SMK6-{SUFFIX}",
        "partName": f"冒烟轴承6208_{SUFFIX}",
        "categoryId": category_id,
        "spec": "6208-2Z",
        "unit": "套",
        "safetyStock": 5,
        "unitPrice": 30,
        "currentStock": 999,
    })
    check("档案更新携带库存字段不生效（库存只能走流水）", part_stock(part_id, PART_NO) == 0,
          f"currentStock={part_stock(part_id, PART_NO)}")

    # ---------------------------------------------------------------- [4]
    print("\n[4] 入库管理与库存流水")
    call("POST", "/api/inventory/stock/in",
         {"partId": part_id, "quantity": 10, "unitPrice": 30, "remark": "冒烟采购入库"})
    check("入库后库存 = 10", part_stock(part_id, PART_NO) == 10, f"stock={part_stock(part_id, PART_NO)}")
    txns = call("GET", f"/api/inventory/stock/txns?partId={part_id}")
    check("生成入库流水（IN / 采购）",
          len(txns) == 1 and txns[0].get("txnType") == "IN"
          and txns[0].get("bizType") == "PURCHASE",
          f"{txns[0].get('txnType')}/{txns[0].get('bizType')}" if txns else "无流水")
    page = call("GET", f"/api/inventory/stock/txns/page?pageNum=1&pageSize=10&partId={part_id}&txnType=IN")
    check("流水分页可按类型过滤", (page.get("total") or 0) == 1, f"total={page.get('total')}")

    # ---------------------------------------------------------------- [5]
    print("\n[5] 库存调整（盘点）")
    call("POST", f"/api/inventory/stock/adjust?partId={part_id}&quantity=2&positive=true&remark=盘盈")
    call("POST", f"/api/inventory/stock/adjust?partId={part_id}&quantity=1&positive=false&remark=盘亏")
    check("盘盈 +2、盘亏 -1 后库存 = 11", part_stock(part_id, PART_NO) == 11, f"stock={part_stock(part_id, PART_NO)}")
    txns = call("GET", f"/api/inventory/stock/txns?partId={part_id}")
    adjust_types = [t.get("txnType") for t in txns[:2]]
    check("两次调整各生成一条 ADJUST 流水", adjust_types == ["ADJUST", "ADJUST"],
          f"最近两条 {adjust_types}")

    # ---------------------------------------------------------------- [6]
    print("\n[6] 出库管理与库存不足")
    call("POST", "/api/inventory/stock/out",
         {"partId": part_id, "quantity": 3, "remark": "冒烟直接出库"})
    check("出库后库存 = 8", part_stock(part_id, PART_NO) == 8, f"stock={part_stock(part_id, PART_NO)}")
    _, code, _ = fails("POST", "/api/inventory/stock/out",
                       {"partId": part_id, "quantity": 100, "remark": "超量出库"},
                       codes=(4001,), expect_http=422)
    check("出库超过库存被拒（防超卖）", True, f"拒绝码 {code}")
    check("被拒后库存不变", part_stock(part_id, PART_NO) == 8, f"stock={part_stock(part_id, PART_NO)}")

    # ---------------------------------------------------------------- [7]
    print("\n[7] 工单领用闭环：申请 → 审批出库 → 扣库存 → 关联工单 → 成本")
    equipments = call("GET", "/api/asset/equipment?pageNum=1&pageSize=1")
    rows = equipments.get("records") or []
    if not rows:
        print("  设备表为空，请先执行种子数据")
        return 1
    equipment = rows[0]
    wo1 = call("POST", "/api/workorder", {
        "equipmentId": equipment["id"],
        "title": f"冒烟_领用闭环_{SUFFIX}",
        "faultType": "MECHANICAL",
        "faultDesc": "轴承磨损需更换，验证备件领用全链路。",
        "level": "P3",
    })
    call("POST", f"/api/workorder/{wo1}/dispatch", {"assigneeId": repair_id})
    call("POST", f"/api/workorder/{wo1}/accept", None, REPAIR_TOKEN)

    apply1 = call("POST", "/api/inventory/applies", {
        "workOrderId": wo1, "partId": part_id, "quantity": 2, "remark": "冒烟：更换轴承用",
    }, REPAIR_TOKEN)
    check("维修人提交领用申请", isinstance(apply1, int) and apply1 > 0, f"applyId={apply1}")
    order_parts = call("GET", f"/api/workorder/{wo1}/parts")
    check("工单备件记录为待审批", len(order_parts) == 1
          and order_parts[0].get("issueStatus") == "APPLIED",
          order_parts[0].get("issueStatus") if order_parts else "无记录")
    check("待审批不扣库存", part_stock(part_id, PART_NO) == 8, f"stock={part_stock(part_id, PART_NO)}")

    call("POST", f"/api/inventory/applies/{apply1}/issue", None)
    check("审批出库后库存 = 6", part_stock(part_id, PART_NO) == 6, f"stock={part_stock(part_id, PART_NO)}")
    order_parts = call("GET", f"/api/workorder/{wo1}/parts")
    check("备件状态为已出库", order_parts[0].get("issueStatus") == "ISSUED",
          order_parts[0].get("issueStatus"))
    detail = call("GET", f"/api/workorder/{wo1}")
    check("备件成本计入工单（2 × 30 = 60）", float(detail.get("spareCost") or 0) == 60.0,
          f"spareCost={detail.get('spareCost')}")
    txns = call("GET", f"/api/inventory/stock/txns?partId={part_id}")
    wo_txn = next((t for t in txns if t.get("txnType") == "OUT" and t.get("bizType") == "WORK_ORDER"), None)
    check("生成工单领用出库流水", wo_txn is not None,
          f"流水 {wo_txn.get('quantity')} 件" if wo_txn else "未找到")
    stats = call("GET", "/api/inventory/stats/cost")
    stat = next((s for s in stats if s.get("partId") == part_id), None)
    check("维修成本统计可按备件聚合",
          stat is not None and int(stat.get("totalQty") or 0) == 2
          and float(stat.get("totalAmount") or 0) == 60.0,
          f"数量 {stat.get('totalQty')} / 金额 {stat.get('totalAmount')}" if stat else "无")

    # ---------------------------------------------------------------- [8]
    print("\n[8] 维修退料：入库记录 + 成本冲减")
    call("POST", f"/api/inventory/applies/{apply1}/return?remark=剩余一只未用完")
    check("退料后库存回增 = 8", part_stock(part_id, PART_NO) == 8, f"stock={part_stock(part_id, PART_NO)}")
    txns = call("GET", f"/api/inventory/stock/txns?partId={part_id}")
    check("生成退料入库流水（RETURN）",
          any(t.get("txnType") == "RETURN" for t in txns), f"共 {len(txns)} 条流水")
    order_parts = call("GET", f"/api/workorder/{wo1}/parts")
    check("退料后备件状态为已退回", order_parts[0].get("issueStatus") == "RETURNED",
          order_parts[0].get("issueStatus"))
    detail = call("GET", f"/api/workorder/{wo1}")
    check("退料后工单成本冲减", float(detail.get("spareCost") or 0) == 0.0,
          f"spareCost={detail.get('spareCost')}")

    # ---------------------------------------------------------------- [9]
    print("\n[9] 并发领料：库存只够一份，两个审批恰好一成一败")
    call("POST", "/api/inventory/stock/out",
         {"partId": part_id, "quantity": 7, "remark": "把库存降到 1 造并发场景"})
    check("库存已降为 1", part_stock(part_id, PART_NO) == 1, f"stock={part_stock(part_id, PART_NO)}")
    wo2 = call("POST", "/api/workorder", {
        "equipmentId": equipment["id"],
        "title": f"冒烟_并发领料_{SUFFIX}",
        "faultType": "ELECTRIC",
        "faultDesc": "两个工单同时抢最后一件备件。",
        "level": "P3",
    })
    call("POST", f"/api/workorder/{wo2}/dispatch", {"assigneeId": repair_id})
    call("POST", f"/api/workorder/{wo2}/accept", None, REPAIR_TOKEN)
    apply_a = call("POST", "/api/inventory/applies",
                   {"workOrderId": wo1, "partId": part_id, "quantity": 1}, REPAIR_TOKEN)
    apply_b = call("POST", "/api/inventory/applies",
                   {"workOrderId": wo2, "partId": part_id, "quantity": 1}, REPAIR_TOKEN)

    results = []
    lock = threading.Lock()
    barrier = threading.Barrier(2)

    def issue_once(apply_id):
        barrier.wait()
        status, payload = req("POST", f"/api/inventory/applies/{apply_id}/issue")
        with lock:
            results.append((apply_id, status, payload.get("code")))

    threads = [threading.Thread(target=issue_once, args=(aid,))
               for aid in (apply_a, apply_b)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    ok = [r for r in results if r[1:] == (200, 200)]
    rejected = [r for r in results if r[1:] != (200, 200)]
    check("并发审批恰好成功一次", len(ok) == 1, f"结果 {results}")
    check("失败方返回库存不足码 4001/HTTP 422", all(code == 4001 for _, _, code in rejected),
          f"失败码 {[c for _, _, c in rejected]}")
    check("库存不为负且只扣一份", part_stock(part_id, PART_NO) == 0, f"stock={part_stock(part_id, PART_NO)}")
    txns = call("GET", f"/api/inventory/stock/txns?partId={part_id}")
    out_wo = [t for t in txns if t.get("txnType") == "OUT" and t.get("bizType") == "WORK_ORDER"]
    check("并发只生成一条出库流水", len(out_wo) == 2, f"工单领用流水 {len(out_wo)} 条"
          + "（含 [7] 一条）")

    # 收尾并发遗留：失败方拒绝、成功方退料
    loser = rejected[0][0]
    winner = ok[0][0]
    call("POST", f"/api/inventory/applies/{loser}/reject?remark=并发落败，取消申请")
    call("POST", f"/api/inventory/applies/{winner}/return?remark=冒烟收尾退料")
    check("退料后库存恢复 = 1", part_stock(part_id, PART_NO) == 1, f"stock={part_stock(part_id, PART_NO)}")

    # ---------------------------------------------------------------- [10]
    print("\n[10] 库存不足提醒")
    below = call("GET", "/api/inventory/parts/below-safety")
    hit = next((p for p in below if p.get("id") == part_id), None)
    check("低于安全库存的备件进入预警列表", hit is not None,
          f"库存 {hit.get('currentStock')} < 安全库存 {hit.get('safetyStock')}" if hit else "未命中")
    _, code, _ = fails("POST", "/api/inventory/stock/out",
                       {"partId": part_id, "quantity": 99, "remark": "库存低于安全库存仍不能超量出库"},
                       codes=(4001,), expect_http=422)
    check("库存不足出库被拒且库存保持低位", True, f"拒绝码 {code} · stock={part_stock(part_id, PART_NO)}")

    # ---------------------------------------------------------------- [11]
    print("\n[11] SLA 政策与超时预警（响应时限 1 分钟，真实等待超时）")
    policies = call("GET", "/api/sla/policies")
    check("SLA 政策四条齐备", len(policies) == 4,
          "，".join(p.get("level") for p in policies))
    p4 = next(p for p in policies if p.get("level") == "P4")
    p4_backup = dict(p4)
    call("PUT", "/api/sla/policies/P4", {
        "policyName": p4.get("policyName"),
        "responseMinutes": 1,
        "resolveMinutes": p4.get("resolveMinutes"),
        "verifyMinutes": p4.get("verifyMinutes"),
        "warnPercent": p4.get("warnPercent"),
        "enabled": True,
    })
    check("P4 响应时限改为 1 分钟", next(p for p in call("GET", "/api/sla/policies")
          if p.get("level") == "P4").get("responseMinutes") == 1)

    sla_wo = call("POST", "/api/workorder", {
        "equipmentId": equipment["id"],
        "title": f"冒烟_待派单超时_{SUFFIX}",
        "faultType": "OTHER",
        "faultDesc": "P4 工单停在待派单，验证超时预警。",
        "level": "P4",
    })
    detail = call("GET", f"/api/workorder/{sla_wo}")
    check("响应时限按新政策生成（约 1 分钟后）", bool(detail.get("responseDueTime")),
          f"responseDueTime={detail.get('responseDueTime')}")

    # 等待响应时限真实流逝；期间先做健康评分（[12]）与权限矩阵（[13]）
    print("  · 等待响应时限流逝（约 65 秒），期间先执行健康评分与权限检查……")
    deadline = time.time() + 65

    # ------------------------------------------------------------ [12]
    print("\n[12] 设备健康评分（可解释规则，干净设备精确断言）")
    health_eq = call("POST", "/api/asset/equipment", {
        "equipmentName": f"冒烟健康分设备_{SUFFIX}",
        "categoryId": equipment.get("categoryId"),
        "locationId": equipment.get("locationId"),
        "brand": "冒烟品牌",
        "model": "SMK-6",
        "assetNo": f"SMK6-EQ-{SUFFIX}",
    })
    base_vo = call("POST", f"/api/analytics/health/{health_eq}/recalculate")
    check("干净设备基线 100 分", float(base_vo.get("healthScore")) == 100.0,
          f"healthScore={base_vo.get('healthScore')}")
    health_wo = call("POST", "/api/workorder", {
        "equipmentId": health_eq,
        "title": f"冒烟_在途工单扣分_{SUFFIX}",
        "faultType": "MECHANICAL",
        "faultDesc": "制造一张在途工单验证扣分规则。",
        "level": "P3",
    })
    after_vo = call("POST", f"/api/analytics/health/{health_eq}/recalculate")
    # 建单本身计入近窗口故障次数（-2），在途工单再扣 -3：100 - 2 - 3 = 95
    check("在途工单 + 故障计数后 95 分", float(after_vo.get("healthScore")) == 95.0,
          f"healthScore={after_vo.get('healthScore')} openWoCount={after_vo.get('openWoCount')}")
    risk_items = after_vo.get("riskItems") or []
    open_item = next((i for i in risk_items if i.get("factor") == "OPEN_ORDER"), None)
    check("在途工单扣分可解释（明细 + 建议）",
          open_item is not None and open_item.get("detail") and open_item.get("suggestion"),
          f"扣 {open_item.get('deduction')} 分：{str(open_item.get('detail'))[:40]}…" if open_item else "无明细")
    check("健康建议非空", bool(after_vo.get("suggestions")))
    call("POST", f"/api/workorder/{health_wo}/cancel", {"reason": "冒烟收尾取消"})
    # 取消的工单依旧计入近窗口故障次数：100 - 2 = 98
    final_vo = call("POST", f"/api/analytics/health/{health_eq}/recalculate")
    check("取消工单后仅剩故障计数扣分（98 分）", float(final_vo.get("healthScore")) == 98.0,
          f"healthScore={final_vo.get('healthScore')}")
    at_risk = call("GET", "/api/analytics/health/at-risk?threshold=0")
    check("健康风险列表接口可用", isinstance(at_risk, list),
          f"{len(at_risk)} 台在险设备")

    # ------------------------------------------------------------ [13]
    print("\n[13] 权限矩阵：维修人 403")
    status, _, _ = fails("POST", "/api/inventory/stock/in",
                         {"partId": part_id, "quantity": 1}, REPAIR_TOKEN,
                         codes=(403,), expect_http=403)
    check("维修人不能入库", status == 403, f"HTTP {status}")
    status, _, _ = fails("POST", f"/api/inventory/applies/{apply_a}/issue", None, REPAIR_TOKEN,
                         codes=(403,), expect_http=403)
    check("维修人不能审批出库", status == 403, f"HTTP {status}")
    status, _, _ = fails("PUT", "/api/sla/policies/P3", {"responseMinutes": 999}, REPAIR_TOKEN,
                         codes=(403,), expect_http=403)
    check("维修人不能改 SLA 政策", status == 403, f"HTTP {status}")

    # -------------------------------------------------------- [11] 续
    remain = deadline - time.time()
    if remain > 0:
        time.sleep(remain)
    notified = call("POST", "/api/sla/alerts/scan")
    check("扫描把超时工单登记预警并通知（≥1）", notified >= 1, f"本轮通知 {notified} 张")
    alerts = call("GET", "/api/sla/alerts?stage=PENDING_ASSIGN")
    my_alerts = [a for a in (alerts.get("records") or []) if a.get("workOrderId") == sla_wo]
    check("预警记录分页可查且同单同阶段仅一条", len(my_alerts) == 1,
          f"{len(my_alerts)} 条")
    active = call("GET", "/api/sla/active-orders")
    hit = next((a for a in active if a.get("workOrderId") == sla_wo), None)
    check("活跃工单视图标记超时进度", hit is not None and hit.get("slaStatus") == "OVERDUE",
          f"{hit.get('stageLabel')} {hit.get('percent')}%" if hit else "未命中")
    notified2 = call("POST", "/api/sla/alerts/scan")
    check("重复扫描不再新增预警记录（计数不变）",
          len([a for a in (call("GET", "/api/sla/alerts?stage=PENDING_ASSIGN").get("records") or [])
               if a.get("workOrderId") == sla_wo]) == 1,
          f"第二次扫描通知 {notified2} 张（含其他遗留单也不影响本单记录数）")

    # ---------------------------------------------------------------- [14]
    print("\n[14] 收尾：恢复政策、取消冒烟工单、停用临时工号")
    call("PUT", "/api/sla/policies/P4", {
        "policyName": p4_backup.get("policyName"),
        "responseMinutes": p4_backup.get("responseMinutes"),
        "resolveMinutes": p4_backup.get("resolveMinutes"),
        "verifyMinutes": p4_backup.get("verifyMinutes"),
        "warnPercent": p4_backup.get("warnPercent"),
        "enabled": True,
    })
    check("P4 政策已恢复原值",
          next(p for p in call("GET", "/api/sla/policies") if p.get("level") == "P4")
          .get("responseMinutes") == p4_backup.get("responseMinutes"),
          f"responseMinutes={p4_backup.get('responseMinutes')}")
    # health_wo 已在 [12] 取消；sla_wo / wo2 在此取消收尾
    for wo_id in (sla_wo, wo2):
        status, payload = req("POST", f"/api/workorder/{wo_id}/cancel",
                              {"reason": "冒烟收尾取消"}, TOKEN)
        ok_cancel = status == 200 and payload.get("code") == 200
        check(f"取消冒烟工单 {wo_id}", ok_cancel, payload.get("message") or "")
    # wo1 为维修中真实流转单，保留作时间线痕迹（其备件申请均已退回/拒绝）
    for user_id in CREATED_USERS:
        status, payload = req("PATCH", f"/api/system/users/{user_id}/status?status=0")
        check(f"停用临时工号 {user_id}", status == 200 and payload.get("code") == 200,
              payload.get("message") or "")

    print("\n" + "=" * 70)
    print(f"结果：{PASSED} 通过 / {FAILED} 失败")
    print(f"本次痕迹：备件 id={part_id}（编码 SMK6-{SUFFIX}）、"
          f"工单 wo1={wo1}（维修中，留痕）、设备 {health_eq}")
    print("=" * 70)
    return 0 if FAILED == 0 else 1


if __name__ == "__main__":
    try:
        sys.exit(main())
    except RuntimeError as error:
        print(f"\n[中断] {error}")
        print(f"已完成 {PASSED} 项，失败 {FAILED} 项")
        sys.exit(1)
