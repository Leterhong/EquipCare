/**
 * 工单状态展示映射测试（被测对象：static/js/ui.js 的状态字典）。
 * 对应用户要求：工单状态展示。
 * 断言与后端状态机（wo_work_order 7 状态）对齐。
 */
import { describe, it, expect } from 'vitest';
import {
    WORK_ORDER_STATUS, WORK_ORDER_PRIORITY, WORK_ORDER_LEVEL, WO_SOURCE,
    WO_ACTION, REPAIR_RECORD_TYPE, PART_ISSUE_STATUS, EQUIPMENT_STATUS,
} from '@static/ui.js';

describe('工单状态字典（与后端状态机对齐）', () => {
    it('覆盖后端全部 7 个在用状态', () => {
        for (const code of ['PENDING_ASSIGN', 'PENDING_ACCEPT', 'IN_PROGRESS', 'PENDING_REVIEW', 'REJECTED', 'COMPLETED', 'CANCELLED']) {
            expect(WORK_ORDER_STATUS[code], `缺少状态 ${code}`).toBeTruthy();
            expect(WORK_ORDER_STATUS[code].label).toBeTruthy();
            expect(WORK_ORDER_STATUS[code].tag).toMatch(/^tag-/);
        }
    });

    it('历史状态（PENDING_DISPATCH/PENDING_VERIFY/SUSPENDED）只做展示且明确标注「历史」', () => {
        expect(WORK_ORDER_STATUS.PENDING_DISPATCH.label).toContain('历史');
        expect(WORK_ORDER_STATUS.PENDING_VERIFY.label).toContain('历史');
        expect(WORK_ORDER_STATUS.SUSPENDED.label).toContain('历史');
    });

    it('正向与负向状态语义标签区分（完成绿色、驳回红色）', () => {
        expect(WORK_ORDER_STATUS.COMPLETED.tag).toBe('tag-green');
        expect(WORK_ORDER_STATUS.REJECTED.tag).toBe('tag-red');
        expect(WORK_ORDER_STATUS.CANCELLED.tag).toBe('tag-gray');
    });
});

describe('工单优先级 / 故障等级', () => {
    it('故障等级 P1–P4 齐备且 P1 最紧急（红色）', () => {
        expect(Object.keys(WORK_ORDER_LEVEL).sort()).toEqual(['P1', 'P2', 'P3', 'P4']);
        expect(WORK_ORDER_LEVEL.P1.tag).toBe('tag-red');
        expect(WORK_ORDER_LEVEL.P1.label).toContain('紧急');
    });

    it('维修优先级三档（高/中/低）', () => {
        expect(WORK_ORDER_PRIORITY.HIGH.label).toBe('高');
        expect(WORK_ORDER_PRIORITY.NORMAL.label).toBe('中');
        expect(WORK_ORDER_PRIORITY.LOW.label).toBe('低');
    });
});

describe('工单来源与动作映射', () => {
    it('四种来源（人工报修/巡检发现/异常转单/计划保养）', () => {
        expect(Object.keys(WO_SOURCE).sort())
            .toEqual(['ANOMALY', 'INSPECT', 'MANUAL', 'PLAN_MAINTAIN']);
    });

    it('动作码与后端 WorkOrderAction 一致（派单/接单/转派/验收等）', () => {
        expect(WO_ACTION.DISPATCH).toBe('派单');
        expect(WO_ACTION.ACCEPT).toBe('接单');
        expect(WO_ACTION.SUBMIT_VERIFY).toBe('提交验收');
        expect(WO_ACTION.VERIFY_PASS).toBe('验收通过');
        expect(WO_ACTION.VERIFY_REJECT).toBe('验收不通过');
        expect(WO_ACTION.FORCE_TRANSFER).toBe('强制转派');
    });
});

describe('维修记录类型与备件领用状态', () => {
    it('维修记录五类（开始/诊断/过程/完工/其他）', () => {
        expect(Object.keys(REPAIR_RECORD_TYPE).sort())
            .toEqual(['DIAGNOSIS', 'FINISH', 'OTHER', 'PROCESS', 'START']);
    });

    it('备件领用四态（待审批/已出库/已驳回/已退料）', () => {
        expect(Object.keys(PART_ISSUE_STATUS).sort())
            .toEqual(['APPLIED', 'ISSUED', 'REJECTED', 'RETURNED']);
    });

    it('设备状态字典覆盖运行全周期（在用/闲置/维修/报废）', () => {
        expect(EQUIPMENT_STATUS.REPAIRING.label).toBe('维修中');
        expect(EQUIPMENT_STATUS.SCRAPPED.tag).toBe('tag-red');
    });
});
