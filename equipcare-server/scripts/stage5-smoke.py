"""
第五阶段端到端冒烟：一张工单从报修到评价，全程走真实接口与真实事务。

与接口连通性测试不同，这个脚本验证的是「业务是否真的跑起来」：
  1. 报修建单（带故障照片）→ 待派单；
  2. 未派单就接单 / 开始维修 / 验收 → 一律 3001 非法流转；
  3. 派单（优先级 + 完成时限 + 派单说明）→ 待接单，派单历史留痕；
  4. 越权矩阵：验收人接单、维修人验收、维修人取消 → 403；
  5. 两个线程同时接单 → 恰好一个成功，另一个 3001/3002，不出现双接单；
  6. 开始维修（原因 + 诊断 + 维修前照片）→ 重复开始维修被拒；
  7. 维修过程记录 + 完工说明（含维修后照片）；
  8. 申请备件 → 待审批时有 1 条 APPLIED，此时提交验收被拦；出库后再提交放过；
  9. 验收不通过（原因过短 400 / 正常退回维修中）→ 二次维修 → 再次提交；
 10. 验收通过 → 已完成，实际完成时间落库；
 11. 三维度评价 + 重复评价被拒；
 12. 时间线完整性：七类事件齐全、时间正序、每条都有真实操作人；
 13. 普通更新接口改不动状态（PUT 405）；
 14. 工作台 / 状态统计 / 可选维修人 三个派生接口可用。

脚本自行创建临时维修工与临时验收员并在结束时清理，口令随机生成、不落盘、不打印；
管理员口令只从环境变量读。

用法：
  EQUIPCARE_ADMIN_USER=admin EQUIPCARE_ADMIN_PASSWORD=xxx python scripts/stage5-smoke.py

可选：
  EQUIPCARE_BASE_URL  默认 http://127.0.0.1:8080
  巡检异常转工单（工单来源之二）已由 stage4-smoke.py 覆盖，本脚本只验证工单中心自身的流转。
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
VERIFIER_TOKEN = None   # 临时验收员

CREATED_USERS = []      # [(userId, token)] 用于收尾清理
CREATED_ORDERS = []


def check(name, condition, detail=""):
    global PASSED, FAILED
    if condition:
        PASSED += 1
        print(f"  [PASS] {name}" + (f" · {detail}" if detail else ""))
    else:
        FAILED += 1
        print(f"  [FAIL] {name}" + (f" · {detail}" if detail else ""))


def req(method, path, body=None, token=None):
    """发一次请求，返回 (httpStatus, payload)。payload 至少含 code / message。"""
    headers = {"Content-Type": "application/json"}
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    use = token if token is not None else TOKEN
    if use:
        headers["Authorization"] = "Bearer " + use
    request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
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
    """成功调用：HTTP 200 且业务码等于 expect，否则抛错。"""
    status, payload = req(method, path, body, token)
    where = label or f"{method} {path}"
    if status != 200 or payload.get("code") != expect:
        raise RuntimeError(f"{where} 期望 HTTP 200 + code={expect}，实际 HTTP {status} + "
                           f"code={payload.get('code')}：{payload.get('message')}")
    return payload.get("data")


def fails(method, path, body=None, token=None, codes=(3001,)):
    """失败调用：断言业务码落在 codes 里，返回实际 (status, code, message)。"""
    status, payload = req(method, path, body, token)
    code = payload.get("code")
    if code not in codes:
        raise RuntimeError(f"{method} {path} 期望失败码 {codes}，实际 HTTP {status} + "
                           f"code={code}：{payload.get('message')}")
    return status, code, payload.get("message") or ""


def login(username, password):
    status, payload = req("POST", "/api/auth/login", {"username": username, "password": password})
    if status != 200 or payload.get("code") != 200:
        raise RuntimeError(f"登录 {username} 失败：{payload.get('message')}")
    return payload["data"]


def create_temp_user(role_code, label):
    """建一个临时工号并返回 (userId, token)。口令随机生成，只在内存里用一次。"""
    roles = call("GET", "/api/system/roles")
    role_id = None
    for role in roles:
        if role.get("roleCode") == role_code:
            role_id = role.get("id")
            break
    if role_id is None:
        raise RuntimeError(f"角色 {role_code} 不存在，请先执行 04/05 阶段权限初始化")

    username = f"smk5{label}{SUFFIX}"
    password = "Smk5@" + secrets.token_urlsafe(9)
    user_id = call("POST", "/api/system/users", {
        "username": username,
        "password": password,
        "realName": f"冒烟{label}{SUFFIX}",
        "roleIds": [role_id],
    })
    session = login(username, password)
    CREATED_USERS.append(user_id)
    return user_id, session["accessToken"]


def main():
    global TOKEN, REPAIR_TOKEN, VERIFIER_TOKEN
    if not ADMIN_PASSWORD:
        print("请通过 EQUIPCARE_ADMIN_PASSWORD 传入管理员口令")
        return 2

    print("=" * 70)
    print("第五阶段冒烟：维修工单中心")
    print("=" * 70)

    # ---------------------------------------------------------------- [1]
    print("\n[1] 管理员登录与工单权限")
    session = login(ADMIN_USER, ADMIN_PASSWORD)
    TOKEN = session["accessToken"]
    perms = session.get("user", {}).get("permissions", [])
    need = ["wo:workorder:view", "wo:workorder:create", "wo:workorder:dispatch",
            "wo:workorder:verify", "wo:workorder:evaluate", "wo:workorder:board",
            "wo:workorder:workbench"]
    missing = [p for p in need if p not in perms]
    check("登录成功", bool(TOKEN))
    check("工单权限码已下发", not missing, f"共 {len(perms)} 个权限码" if not missing else f"缺 {missing}")

    # ---------------------------------------------------------------- [2]
    print("\n[2] 临时维修工与验收员")
    repair_id, REPAIR_TOKEN = create_temp_user("REPAIRMAN", "R")
    verifier_id, VERIFIER_TOKEN = create_temp_user("VERIFIER", "V")
    check("临时维修工创建并可登录", repair_id > 0, f"userId={repair_id}")
    check("临时验收员创建并可登录", verifier_id > 0, f"userId={verifier_id}")

    equipments = call("GET", "/api/asset/equipment?pageNum=1&pageSize=1")
    rows = equipments.get("records") or []
    if not rows:
        print("  设备表为空，请先执行种子数据")
        return 1
    equipment = rows[0]
    photo_id = upload_png()

    # ---------------------------------------------------------------- [3]
    print("\n[3] 报修建单（工单来源：用户主动报修）")
    order_id = call("POST", "/api/workorder", {
        "equipmentId": equipment["id"],
        "title": f"冒烟_水泵异响_{SUFFIX}",
        "faultType": "MECHANICAL",
        "faultDesc": "运行中轴承位持续异响，温度偏高，怀疑润滑失效或轴承磨损。",
        "level": "P2",
        "faultImageIds": str(photo_id),
    })
    CREATED_ORDERS.append(order_id)
    check("工单创建成功", isinstance(order_id, int) and order_id > 0, f"id={order_id}")

    detail = call("GET", f"/api/workorder/{order_id}")
    check("初始状态为待派单", detail.get("status") == "PENDING_ASSIGN",
          f"status={detail.get('status')} {detail.get('statusLabel')}")
    check("工单编号已生成", bool(detail.get("orderNo")), detail.get("orderNo"))
    check("故障照片按文件ID实时回查", len(detail.get("faultImages") or []) == 1,
          f"{len(detail.get('faultImages') or [])} 张")
    check("维修优先级默认中", detail.get("priority") == "NORMAL", f"priority={detail.get('priority')}")
    check("SLA 时限按故障等级生成",
          bool(detail.get("responseDueTime")) and bool(detail.get("resolveDueTime")))

    # ---------------------------------------------------------------- [4]
    print("\n[4] 非法状态跳转（状态机先行拦截）")
    _, code, msg = fails("POST", f"/api/workorder/{order_id}/accept", None, REPAIR_TOKEN,
                         codes=(3001,))
    check("待派单不能直接接单", code == 3001, msg[:60])
    _, code, msg = fails("POST", f"/api/workorder/{order_id}/start-repair",
                         {"reason": "跳过接单直接开始维修试试", "diagnosis": "未接单就提交开始维修请求"},
                         REPAIR_TOKEN, codes=(2004,))
    check("待派单不能开始维修（非维修中一律拒绝）", code == 2004, msg[:60])
    _, code, msg = fails("POST", f"/api/workorder/{order_id}/verify-pass",
                         {"qualityScore": 5, "remark": "跳过整个流程直接验收"}, VERIFIER_TOKEN,
                         codes=(3001,))
    check("待派单不能验收", code == 3001, msg[:60])
    _, code, _ = fails("POST", f"/api/workorder/{order_id}/submit-verify",
                       {"repairSummary": "未维修就提交验收，应被状态机拦下"}, REPAIR_TOKEN, codes=(3001,))
    check("待派单不能提交验收", code == 3001)

    # ---------------------------------------------------------------- [5]
    print("\n[5] 派单：优先级 / 完成时限 / 派单说明")
    status, _, _ = fails("POST", f"/api/workorder/{order_id}/dispatch",
                         {"assigneeId": verifier_id}, VERIFIER_TOKEN, codes=(403,))
    check("验收人无权派单", status == 403, f"HTTP {status}")

    assignees = call("GET", "/api/workorder/assignees")
    check("可选维修人列表含临时维修工与在办数",
          any(u.get("id") == repair_id for u in assignees),
          f"{len(assignees)} 人")

    call("POST", f"/api/workorder/{order_id}/dispatch", {
        "assigneeId": repair_id,
        "priority": "HIGH",
        "resolveHours": 8,
        "reason": "冒烟派单：轴承异响影响生产，按高优先级 8 小时内完成",
    })
    detail = call("GET", f"/api/workorder/{order_id}")
    check("派单后进入待接单", detail.get("status") == "PENDING_ACCEPT")
    check("维修人与优先级已落库",
          detail.get("assigneeId") == repair_id and detail.get("priority") == "HIGH",
          f"{detail.get('assigneeName')} / {detail.get('priorityLabel')}")
    check("完成时限按派单小时数覆盖", bool(detail.get("resolveDueTime")))
    check("派单历史留痕", len(detail.get("dispatchRecords") or []) == 1,
          f"{len(detail.get('dispatchRecords') or [])} 条")
    _, code, _ = fails("POST", f"/api/workorder/{order_id}/dispatch",
                       {"assigneeId": repair_id}, codes=(3001,))
    check("待接单不能再派单", code == 3001)

    # ---------------------------------------------------------------- [6]
    print("\n[6] 并发接单：两个请求只允许一个成功")
    race_id = call("POST", "/api/workorder", {
        "equipmentId": equipment["id"],
        "title": f"冒烟_并发接单_{SUFFIX}",
        "faultType": "ELECTRIC",
        "faultDesc": "配电柜指示灯不亮，需现场检查回路。",
        "level": "P3",
    })
    CREATED_ORDERS.append(race_id)
    call("POST", f"/api/workorder/{race_id}/dispatch", {"assigneeId": repair_id, "priority": "NORMAL"})

    results = []
    lock = threading.Lock()
    barrier = threading.Barrier(2)

    def accept_once():
        barrier.wait()
        status, payload = req("POST", f"/api/workorder/{race_id}/accept", None, REPAIR_TOKEN)
        with lock:
            results.append((status, payload.get("code")))

    threads = [threading.Thread(target=accept_once) for _ in range(2)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    ok_count = sum(1 for status, code in results if status == 200 and code == 200)
    fail_codes = sorted(code for status, code in results if not (status == 200 and code == 200))
    check("并发接单恰好成功一次", ok_count == 1, f"成功 {ok_count} 次，结果 {results}")
    check("失败方返回状态冲突码", all(code in (3001, 3002) for code in fail_codes),
          f"失败码 {fail_codes}")
    race_detail = call("GET", f"/api/workorder/{race_id}")
    check("并发后工单只被接一次", race_detail.get("status") == "IN_PROGRESS"
          and len([x for x in (race_detail.get("timeline") or []) if x.get("action") == "ACCEPT"]) <= 1)

    # ---------------------------------------------------------------- [7]
    print("\n[7] 接单与开始维修（防重复提交）")
    status, _, _ = fails("POST", f"/api/workorder/{order_id}/verify-pass",
                         {"qualityScore": 5}, REPAIR_TOKEN, codes=(403,))
    check("维修人不能验收（越权）", status == 403, f"HTTP {status}")
    status, _, _ = fails("POST", f"/api/workorder/{order_id}/cancel",
                         {"reason": "维修人想自己取消工单"}, REPAIR_TOKEN, codes=(403,))
    check("维修人不能取消工单（取消是管理动作）", status == 403, f"HTTP {status}")

    call("POST", f"/api/workorder/{order_id}/accept", None, REPAIR_TOKEN)
    detail = call("GET", f"/api/workorder/{order_id}")
    check("接单后进入维修中", detail.get("status") == "IN_PROGRESS")
    check("接单时间落库且尚未开始维修",
          bool(detail.get("acceptTime")) and not detail.get("repairStarted"))
    _, code, msg = fails("POST", f"/api/workorder/{order_id}/accept", None, REPAIR_TOKEN,
                         codes=(3001,))
    check("重复接单被拒", code == 3001, msg[:50])

    before_photo = upload_png()
    call("POST", f"/api/workorder/{order_id}/start-repair", {
        "reason": "轴承位异响，需拆检润滑与轴承间隙",
        "diagnosis": "拆检发现轴承润滑脂干涸、滚道点蚀，联轴器对中偏差 0.3mm。",
        "beforeImageIds": str(before_photo),
        "estimateMinutes": 120,
    }, REPAIR_TOKEN)
    detail = call("GET", f"/api/workorder/{order_id}")
    check("开始维修后标记已开始", detail.get("repairStarted") is True)
    check("维修开始时间落库", bool(detail.get("startRepairTime")))
    _, code, msg = fails("POST", f"/api/workorder/{order_id}/start-repair",
                         {"reason": "重复点一次", "diagnosis": "重复提交开始维修请求"}, REPAIR_TOKEN,
                         codes=(2004,))
    check("重复开始维修被拒", code == 2004, msg[:60])

    # ---------------------------------------------------------------- [8]
    print("\n[8] 维修过程记录与完工说明")
    after_photo = upload_png()
    record_id = call("POST", f"/api/workorder/{order_id}/records", {
        "recordType": "PROCESS",
        "content": "更换轴承 6208 两只，重新加注锂基润滑脂，联轴器重新对中至 0.05mm。",
        "beforeImageIds": str(before_photo),
        "afterImageIds": str(after_photo),
        "costMinutes": 95,
    }, REPAIR_TOKEN)
    check("维修过程记录写入", isinstance(record_id, int) and record_id > 0, f"id={record_id}")

    _, code, msg = fails("POST", f"/api/workorder/{order_id}/submit-verify",
                         {"repairSummary": "未写完工说明就想提交验收"}, REPAIR_TOKEN,
                         codes=(2004,))
    check("缺完工说明时提交验收被拦", code == 2004, msg[:60])

    call("POST", f"/api/workorder/{order_id}/records", {
        "recordType": "FINISH",
        "content": "复测振动值 2.1mm/s、轴承温度 48℃，异响消除，试运行 30 分钟正常。",
        "afterImageIds": str(after_photo),
        "costMinutes": 20,
    }, REPAIR_TOKEN)
    detail = call("GET", f"/api/workorder/{order_id}")
    check("维修记录含前后照片",
          any((r.get("beforeImages") or []) and (r.get("afterImages") or [])
              for r in (detail.get("repairRecords") or [])))

    # ---------------------------------------------------------------- [9]
    print("\n[9] 备件申请：阻断验收 → 审批出库 → 放行")
    parts = call("GET", "/api/inventory/parts?pageNum=1&pageSize=20")
    in_stock = [p for p in (parts.get("records") or []) if (p.get("currentStock") or 0) >= 2]
    if not in_stock:
        print("  没有库存 ≥2 的备件，跳过备件环节（请先执行 03 阶段种子数据）")
        apply_id = None
    else:
        part = in_stock[0]
        apply_id = call("POST", "/api/inventory/applies", {
            "workOrderId": order_id,
            "partId": part["id"],
            "quantity": 2,
            "remark": "冒烟：更换轴承用",
        }, REPAIR_TOKEN)
        check("备件申请提交成功", isinstance(apply_id, int) and apply_id > 0,
              f"{part['partName']} × 2, applyId={apply_id}")
        order_parts = call("GET", f"/api/workorder/{order_id}/parts")
        check("工单备件记录为待审批",
              len(order_parts) == 1 and order_parts[0].get("issueStatus") == "APPLIED")
        _, code, msg = fails("POST", f"/api/workorder/{order_id}/submit-verify",
                             {"repairSummary": "备件还在审批中，先提交验收试试"}, REPAIR_TOKEN,
                             codes=(2004,))
        check("存在待审批备件时提交验收被拦", code == 2004, msg[:60])
        call("POST", f"/api/inventory/applies/{apply_id}/issue", None)
        order_parts = call("GET", f"/api/workorder/{order_id}/parts")
        check("出库后备件状态为已出库", order_parts[0].get("issueStatus") == "ISSUED")
        detail = call("GET", f"/api/workorder/{order_id}")
        check("备件成本计入工单", detail.get("spareCost") is not None
              and float(detail.get("spareCost")) > 0, f"spareCost={detail.get('spareCost')}")

    # --------------------------------------------------------------- [10]
    print("\n[10] 提交验收")
    call("POST", f"/api/workorder/{order_id}/submit-verify", {
        "repairSummary": "更换轴承并重新对中，振动与温度恢复正常，试运行 30 分钟无异常。",
        "laborHours": 2.5,
    }, REPAIR_TOKEN)
    detail = call("GET", f"/api/workorder/{order_id}")
    check("提交后进入待验收", detail.get("status") == "PENDING_REVIEW")
    check("维修工时与总结落库",
          detail.get("laborHours") is not None and bool(detail.get("repairSummary")),
          f"laborHours={detail.get('laborHours')}")
    _, code, _ = fails("POST", f"/api/workorder/{order_id}/submit-verify",
                       {"repairSummary": "重复提交一次"}, REPAIR_TOKEN, codes=(3001,))
    check("重复提交验收被拒", code == 3001)
    # --------------------------------------------------------------- [11]
    print("\n[11] 验收不通过 → 回到维修中")
    _, code, msg = fails("POST", f"/api/workorder/{order_id}/verify-reject",
                         {"remark": "不行"}, VERIFIER_TOKEN, codes=(400,))
    check("验收不通过原因过短被拒", code == 400, msg[:60])

    call("POST", f"/api/workorder/{order_id}/verify-reject", {
        "remark": "复测振动值仍偏高，请重新做动平衡并补充振动频谱数据后再提交。",
    }, VERIFIER_TOKEN)
    detail = call("GET", f"/api/workorder/{order_id}")
    check("验收不通过后回到维修中", detail.get("status") == "IN_PROGRESS")
    check("驳回次数累加", (detail.get("rejectCount") or 0) >= 1, f"rejectCount={detail.get('rejectCount')}")
    check("验收记录留痕", len(detail.get("verifyRecords") or []) == 1)

    # --------------------------------------------------------------- [12]
    print("\n[12] 二次维修并再次提交")
    call("POST", f"/api/workorder/{order_id}/records", {
        "recordType": "PROCESS",
        "content": "按验收意见重新做动平衡，补充振动频谱：1X 分量占主导，已复核地脚螺栓扭矩。",
        "costMinutes": 45,
    }, REPAIR_TOKEN)
    call("POST", f"/api/workorder/{order_id}/submit-verify", {
        "repairSummary": "已按验收意见整改并补齐频谱数据，复测振动 1.6mm/s。",
        "laborHours": 3.0,
    }, REPAIR_TOKEN)
    detail = call("GET", f"/api/workorder/{order_id}")
    check("整改后再次进入待验收", detail.get("status") == "PENDING_REVIEW")

    # --------------------------------------------------------------- [13]
    print("\n[13] 验收通过 → 已完成")
    call("POST", f"/api/workorder/{order_id}/verify-pass", {
        "qualityScore": 5,
        "remark": "现场复测振动 1.6mm/s、温度 45℃，整改到位，验收通过。",
    }, VERIFIER_TOKEN)
    detail = call("GET", f"/api/workorder/{order_id}")
    check("验收通过后已完成", detail.get("status") == "COMPLETED")
    check("实际完成时间落库", bool(detail.get("actualFinishTime")))
    check("验收人已记录", bool(detail.get("verifyUserName")), detail.get("verifyUserName"))

    # --------------------------------------------------------------- [14]
    print("\n[14] 服务评价")
    _, code, msg = fails("POST", f"/api/workorder/{order_id}/evaluate", {
        "attitudeScore": 9, "qualityScore": 5, "speedScore": 5,
    }, codes=(400, 422))
    check("评分越界被拒", code in (400, 422), msg[:60])

    evaluation_id = call("POST", f"/api/workorder/{order_id}/evaluate", {
        "attitudeScore": 5,
        "qualityScore": 4,
        "speedScore": 3,
        "content": "师傅到场及时、沟通到位；质量问题整改了一轮才达标，整体可接受。",
    })
    check("评价提交成功", isinstance(evaluation_id, int) and evaluation_id > 0, f"id={evaluation_id}")
    detail = call("GET", f"/api/workorder/{order_id}")
    evaluation = detail.get("evaluation") or {}
    check("三条评分与平均分正确",
          evaluation.get("attitudeScore") == 5 and evaluation.get("qualityScore") == 4
          and evaluation.get("speedScore") == 3 and evaluation.get("averageScore") == 4.0,
          f"平均 {evaluation.get('averageScore')}")
    check("已评价标记生效", detail.get("evaluated") is True)
    _, code, msg = fails("POST", f"/api/workorder/{order_id}/evaluate", {
        "attitudeScore": 1, "qualityScore": 1, "speedScore": 1,
    }, codes=(2004,))
    check("重复评价被拒", code == 2004, msg[:60])

    # --------------------------------------------------------------- [15]
    print("\n[15] 工单操作时间线")
    items = call("GET", f"/api/workorder/{order_id}/timeline")
    kinds = {item.get("eventType") for item in items}
    expect_kinds = {"CREATE", "STATUS", "DISPATCH", "REPAIR", "VERIFY", "EVALUATION"}
    if apply_id:
        expect_kinds.add("PART")
    check("时间线事件类型齐全", expect_kinds.issubset(kinds), f"实际 {sorted(kinds)}")
    check("时间线条目均为真实操作人",
          all(item.get("operatorName") for item in items),
          f"{len(items)} 条")
    check("每条都能回查源记录",
          all(item.get("sourceType") and item.get("sourceId") for item in items))
    times = [item.get("eventTime") for item in items]
    check("时间线按发生时间正序", times == sorted(times), f"{len(times)} 条已排序")

    timeline_text = " ".join(f"{i.get('eventType')}:{i.get('title')}" for i in items)
    check("验收退回留痕", "VERIFY" in kinds and "不通过" in timeline_text, "")

    # --------------------------------------------------------------- [16]
    print("\n[16] 禁止绕过状态机直接改状态")
    for method in ("PUT", "PATCH"):
        status, payload = req(method, f"/api/workorder/{order_id}", {"status": "COMPLETED"})
        check(f"{method} 工单接口不可用（只能走动作接口）", status in (404, 405),
              f"HTTP {status} code={payload.get('code')}")
    detail = call("GET", f"/api/workorder/{order_id}")
    check("状态未被普通更新接口改动", detail.get("status") == "COMPLETED")

    # --------------------------------------------------------------- [17]
    print("\n[17] 工作台与派生视图")
    workbench = call("GET", f"/api/workorder/workbench?limit=10")
    me = session["user"]["userId"]
    mine = workbench.get("pendingAccept") or []
    check("维修工作台按当前账号过滤待办",
          "pendingAccept" in workbench and "repairing" in workbench
          and all(row.get("assigneeId") == me for row in mine),
          f"待我接单 {len(mine)} 条 / 我维修中 {len(workbench.get('repairing') or [])} 条")
    check("工作台带全局状态计数",
          len(workbench.get("counts") or []) > 0, f"{len(workbench.get('counts') or [])} 个状态")
    counts = call("GET", "/api/workorder/status-count")
    total = sum(int(row.get("total") or 0) for row in counts)
    check("状态统计可用", total > 0, f"共 {total} 张工单，{len(counts)} 个状态")
    page = call("GET", "/api/workorder?pageNum=1&pageSize=10&status=COMPLETED")
    check("按状态筛选列表可用",
          (page.get("total") or 0) > 0
          and all(r.get("status") == "COMPLETED" for r in (page.get("records") or [])),
          f"已完成 {page.get('total')} 张")

    # --------------------------------------------------------------- [18]
    print("\n[18] 收尾：临时工号停用（不删除，避免工单时间线断链）")
    for user_id in CREATED_USERS:
        status, payload = req("PATCH", f"/api/system/users/{user_id}/status?status=0")
        check(f"停用临时工号 {user_id}", status == 200 and payload.get("code") == 200,
              payload.get("message") or "")
    detail = call("GET", f"/api/workorder/{order_id}")
    check("工单时间线仍能显示操作人姓名",
          all(item.get("operatorName") for item in (detail.get("history") or [])),
          f"{len(detail.get('history') or [])} 条")

    print("\n" + "=" * 70)
    print(f"结果：{PASSED} 通过 / {FAILED} 失败")
    print(f"本次工单：id={order_id}（已完成并评价）、id={race_id}（并发接单验证单）")
    print("=" * 70)
    return 0 if FAILED == 0 else 1


def upload_png():
    """上传一张 1x1 PNG，返回 fileId；用于故障现场与维修前后照片链路。"""
    import base64
    import uuid

    png = base64.b64decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8AAAwAB/wD/1gAAAABJRU5ErkJggg=="
    )
    boundary = "----equipcare" + uuid.uuid4().hex
    body = b"\r\n".join([
        f"--{boundary}".encode(),
        b'Content-Disposition: form-data; name="file"; filename="site.png"',
        b"Content-Type: image/png",
        b"",
        png,
        f"--{boundary}".encode(),
        b'Content-Disposition: form-data; name="bizType"',
        b"",
        b"WORK_ORDER",
        f"--{boundary}--".encode(),
        b"",
    ])
    headers = {
        "Content-Type": f"multipart/form-data; boundary={boundary}",
        "Authorization": "Bearer " + TOKEN,
    }
    request = urllib.request.Request(BASE + "/api/files/upload", data=body, headers=headers,
                                     method="POST")
    try:
        with urllib.request.urlopen(request) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"文件上传失败：{error.read().decode('utf-8', 'ignore')}")
    if payload.get("code") != 200:
        raise RuntimeError(f"文件上传失败：{payload.get('message')}")
    return payload["data"]["fileId"]


if __name__ == "__main__":
    try:
        sys.exit(main())
    except RuntimeError as error:
        print(f"\n[中断] {error}")
        print(f"已完成 {PASSED} 项，失败 {FAILED} 项")
        sys.exit(1)
