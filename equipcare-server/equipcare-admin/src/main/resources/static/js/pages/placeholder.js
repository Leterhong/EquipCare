/**
 * 通用「建设中」占位页（阶段十）。
 *
 * 导航树按产品规范列出完整站点地图；部分页面属后续迭代范围，
 * 以统一占位页保持导航完整性与信息架构，不做任何假数据。
 */
import { h } from '../ui.js';

const LABELS = {
    '/equipment/location': ['设备位置', '按区域 / 楼栋 / 楼层 / 房间维度查看设备分布。'],
    '/equipment/lifecycle': ['设备生命周期', '从采购入库、安装调试、运行维保到报废退役的全生命周期视图。'],
    '/workorder/records': ['维修记录', '全部已完成工单的维修过程档案检索。'],
    '/analytics/fault': ['故障分析', '故障类型分布、频发设备与根因分析。'],
    '/analytics/cost': ['维修成本', '备件消耗、人工成本与单台维修成本趋势。'],
    '/analytics/report': ['运维报表', '周期性运维报表生成与 Excel 导出。'],
    '/system/config': ['系统配置', '系统参数、上传目录与业务规则的集中配置。'],
};

export function render(container, ctx) {
    const [title, desc] = LABELS[ctx.path] || ['建设中', '该功能正在规划中。'];
    const card = h('div', { class: 'card placeholder-card' },
        h('div', { class: 'placeholder-icon', 'aria-hidden': 'true' }, '🚧'),
        h('h2', { text: title }),
        h('p', { text: desc }),
        h('p', { class: 'placeholder-note', text: '该模块将在后续版本中上线，当前可使用相近功能完成同类工作。' }));
    const head = h('div', { class: 'page-head' },
        h('div', {},
            h('h1', { class: 'page-title', text: title }),
            h('p', { class: 'page-desc', text: '巡维云 EquipCare · 功能规划中' })));
    container.append(head, card);
}
