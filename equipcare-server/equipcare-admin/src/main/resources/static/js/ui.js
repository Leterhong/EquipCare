/**
 * 轻量 UI 工具：DOM、提示、弹层、表格、分页、格式化。
 * 不依赖任何第三方库，全部使用原生 DOM API。
 */

export function esc(value) {
    if (value === null || value === undefined) {
        return '';
    }
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

export function h(tag, attrs = {}, ...children) {
    const node = document.createElement(tag);
    Object.entries(attrs || {}).forEach(([key, value]) => {
        if (value === undefined || value === null || value === false) {
            return;
        }
        if (key === 'class') {
            node.className = value;
        } else if (key === 'text') {
            node.textContent = value;
        } else if (key === 'html') {
            node.innerHTML = value;
        } else if (key.startsWith('on') && typeof value === 'function') {
            node.addEventListener(key.slice(2).toLowerCase(), value);
        } else if (key === 'dataset') {
            Object.assign(node.dataset, value);
        } else {
            node.setAttribute(key, value === true ? '' : value);
        }
    });
    children.flat().forEach((child) => {
        if (child === null || child === undefined || child === false) {
            return;
        }
        node.append(child instanceof Node ? child : document.createTextNode(String(child)));
    });
    return node;
}

export function qs(selector, root = document) {
    return root.querySelector(selector);
}

export function qsa(selector, root = document) {
    return Array.from(root.querySelectorAll(selector));
}

/* ------------------------------------------------------------------ */
/* 提示                                                                */
/* ------------------------------------------------------------------ */

let toastStack = null;

export function toast(message, type = 'info', duration = 2600) {
    if (!toastStack) {
        toastStack = h('div', { class: 'toast-stack', role: 'status', 'aria-live': 'polite' });
        document.body.append(toastStack);
    }
    const node = h('div', { class: `toast ${type}`, text: message });
    toastStack.append(node);
    setTimeout(() => {
        node.style.opacity = '0';
        setTimeout(() => node.remove(), 200);
    }, duration);
}

export function toastError(error) {
    const message = error && error.message ? error.message : '操作失败';
    toast(message, 'error', 3600);
}

/* ------------------------------------------------------------------ */
/* 弹层                                                                */
/* ------------------------------------------------------------------ */

/**
 * 打开弹层。
 * @param {object} options { title, body, width, footer:[{label,type,onClick,close}], onClose }
 * @returns {{close:Function, root:HTMLElement, body:HTMLElement}}
 */
export function modal({ title, body, width, footer = [], onClose } = {}) {
    const bodyNode = h('div', { class: 'modal-body' });
    if (typeof body === 'string') {
        bodyNode.innerHTML = body;
    } else if (body) {
        bodyNode.append(body);
    }

    const footNode = h('div', { class: 'modal-foot' });
    const dialog = h('div', { class: `modal${width === 'wide' ? ' wide' : ''}`, role: 'dialog', 'aria-modal': 'true' },
        h('div', { class: 'modal-head' },
            h('h3', { text: title || '提示' }),
            h('button', { class: 'icon-btn', type: 'button', 'aria-label': '关闭', onclick: () => close() }, '×')),
        bodyNode,
        footer.length ? footNode : null,
    );

    const mask = h('div', { class: 'modal-mask' }, dialog);

    function close() {
        document.removeEventListener('keydown', onKey);
        mask.remove();
        if (onClose) {
            onClose();
        }
    }

    function onKey(event) {
        if (event.key === 'Escape') {
            close();
        }
    }

    footer.forEach((item) => {
        // 两种用法都支持：老页面传 {label, type, onClick} 描述对象；
        // 也有页面直接传现成的 h('button') 节点（自行绑定事件）
        if (item instanceof Node) {
            footNode.append(item);
            return;
        }
        const btn = h('button', {
            class: `btn ${item.type === 'primary' ? 'btn-primary' : item.type === 'danger' ? 'btn-danger' : ''}`,
            type: 'button',
            text: item.label,
            onclick: async (event) => {
                const target = event.currentTarget;
                if (!item.onClick) {
                    close();
                    return;
                }
                const result = await item.onClick({ close, button: target });
                if (result !== false && item.close !== false) {
                    close();
                }
            },
        });
        if (item.id) {
            btn.id = item.id;
        }
        footNode.append(btn);
    });

    document.body.append(mask);
    document.addEventListener('keydown', onKey);
    mask.addEventListener('mousedown', (event) => {
        if (event.target === mask) {
            close();
        }
    });
    const focusable = dialog.querySelector('input, select, textarea, button');
    if (focusable) {
        focusable.focus();
    }
    return { close, root: dialog, body: bodyNode, footer: footNode };
}

export function confirmDialog({ title = '确认操作', message = '', confirmText = '确定', danger = false } = {}) {
    return new Promise((resolve) => {
        let settled = false;
        const done = (value) => {
            if (!settled) {
                settled = true;
                resolve(value);
            }
        };
        modal({
            title,
            body: h('div', { style: 'font-size:13.5px;line-height:1.8', html: message }),
            footer: [
                { label: '取消', onClick: () => done(false) },
                {
                    label: confirmText,
                    type: danger ? 'danger' : 'primary',
                    onClick: () => done(true),
                },
            ],
            onClose: () => done(false),
        });
    });
}

/* ------------------------------------------------------------------ */
/* 表格 / 分页                                                         */
/* ------------------------------------------------------------------ */

/**
 * 渲染表格。
 * @param {object} options { columns:[{key,title,width,align,render(row)}], rows, empty, rowKey }
 */
export function renderTable({ columns, rows, empty = '暂无数据', rowAttrs }) {
    if (!rows || rows.length === 0) {
        return `<div class="table-empty">${esc(empty)}</div>`;
    }
    const head = columns.map((col) => {
        const style = col.width ? ` style="width:${col.width}"` : '';
        const align = col.align ? ` style="text-align:${col.align}"` : '';
        return `<th${style || align}>${esc(col.title)}</th>`;
    }).join('');

    const body = rows.map((row, index) => {
        const extra = rowAttrs ? rowAttrs(row) : '';
        const cells = columns.map((col) => {
            const value = col.render ? col.render(row, index) : esc(row[col.key]);
            const align = col.align ? ` style="text-align:${col.align}"` : '';
            return `<td${align}>${value === undefined || value === null ? '' : value}</td>`;
        }).join('');
        return `<tr ${extra}>${cells}</tr>`;
    }).join('');

    return `<div class="table-wrap"><table class="data"><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table></div>`;
}

export function renderPagination(meta, { onChange }) {
    const { total = 0, pageNum = 1, pageSize = 10 } = meta || {};
    const pages = Math.max(1, Math.ceil(total / pageSize));
    const wrap = h('div', { class: 'pagination' });
    wrap.append(h('span', { text: `共 ${total} 条 · 第 ${pageNum}/${pages} 页` }));

    const pager = h('div', { class: 'pager' });
    const addBtn = (label, page, opts = {}) => {
        const btn = h('button', {
            type: 'button',
            text: label,
            disabled: !!opts.disabled,
            class: opts.active ? 'active' : '',
            onclick: () => onChange && onChange(page),
        });
        pager.append(btn);
    };

    addBtn('上一页', pageNum - 1, { disabled: pageNum <= 1 });
    const windowStart = Math.max(1, Math.min(pageNum - 2, pages - 4));
    const windowEnd = Math.min(pages, windowStart + 4);
    for (let page = windowStart; page <= windowEnd; page += 1) {
        addBtn(String(page), page, { active: page === pageNum });
    }
    addBtn('下一页', pageNum + 1, { disabled: pageNum >= pages });
    wrap.append(pager);
    return wrap;
}

/* ------------------------------------------------------------------ */
/* 格式化                                                              */
/* ------------------------------------------------------------------ */

export function formatDate(value, withTime = false) {
    if (!value) {
        return '—';
    }
    const text = String(value).replace('T', ' ');
    return withTime ? text.slice(0, 16) : text.slice(0, 10);
}

export function formatMoney(value) {
    if (value === null || value === undefined || value === '') {
        return '—';
    }
    return `¥ ${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export function formatSize(bytes) {
    if (!bytes && bytes !== 0) {
        return '—';
    }
    if (bytes < 1024) {
        return `${bytes} B`;
    }
    if (bytes < 1024 * 1024) {
        return `${(bytes / 1024).toFixed(1)} KB`;
    }
    return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
}

export const EQUIPMENT_STATUS = {
    IN_USE: { label: '在用', tag: 'tag-green' },
    IDLE: { label: '停用/闲置', tag: 'tag-gray' },
    REPAIRING: { label: '维修中', tag: 'tag-amber' },
    SCRAPPED: { label: '已报废', tag: 'tag-red' },
};

export const WORK_ORDER_STATUS = {
    PENDING_ASSIGN: { label: '待派单', tag: 'tag-amber' },
    PENDING_ACCEPT: { label: '待接单', tag: 'tag-amber' },
    IN_PROGRESS: { label: '维修中', tag: 'tag-blue' },
    PENDING_REVIEW: { label: '待验收', tag: 'tag-purple' },
    REJECTED: { label: '已驳回', tag: 'tag-red' },
    COMPLETED: { label: '已完成', tag: 'tag-green' },
    CANCELLED: { label: '已取消', tag: 'tag-gray' },
    // 第五阶段之前产生的历史状态：触发器保护旧轨迹不被改写，这里只做展示映射
    PENDING_DISPATCH: { label: '待派单（历史）', tag: 'tag-gray' },
    PENDING_VERIFY: { label: '待验收（历史）', tag: 'tag-gray' },
    SUSPENDED: { label: '已挂起（历史）', tag: 'tag-gray' },
};

/** 维修优先级：派单时设定的处理次序，与故障等级（SLA）区分 */
export const WORK_ORDER_PRIORITY = {
    HIGH: { label: '高', tag: 'tag-red' },
    NORMAL: { label: '中', tag: 'tag-blue' },
    LOW: { label: '低', tag: 'tag-gray' },
};

/** 故障等级：决定响应/修复时限 */
export const WORK_ORDER_LEVEL = {
    P1: { label: 'P1 紧急', tag: 'tag-red' },
    P2: { label: 'P2 高', tag: 'tag-amber' },
    P3: { label: 'P3 中', tag: 'tag-blue' },
    P4: { label: 'P4 低', tag: 'tag-gray' },
};

export const WO_SOURCE = {
    MANUAL: { label: '人工报修', tag: 'tag-blue' },
    INSPECT: { label: '巡检发现', tag: 'tag-purple' },
    ANOMALY: { label: '异常转单', tag: 'tag-red' },
    PLAN_MAINTAIN: { label: '计划保养', tag: 'tag-gray' },
};

/** 允许的故障类型（与 sys_dict_type = fault_type 的常用项对应） */
export const FAULT_TYPES = [
    { code: 'MECHANICAL', label: '机械故障' },
    { code: 'ELECTRIC', label: '电气故障' },
    { code: 'HYDRAULIC', label: '液压故障' },
    { code: 'PNEUMATIC', label: '气动故障' },
    { code: 'INSTRUMENT', label: '仪表/控制' },
    { code: 'LEAKAGE', label: '跑冒滴漏' },
    { code: 'OTHER', label: '其他' },
];

export const REPAIR_RECORD_TYPE = {
    START: { label: '开始维修', tag: 'tag-blue' },
    DIAGNOSIS: { label: '故障诊断', tag: 'tag-purple' },
    PROCESS: { label: '维修过程', tag: 'tag-gray' },
    FINISH: { label: '完工说明', tag: 'tag-green' },
    OTHER: { label: '其他记录', tag: 'tag-gray' },
};

export const PART_ISSUE_STATUS = {
    APPLIED: { label: '待审批', tag: 'tag-amber' },
    ISSUED: { label: '已出库', tag: 'tag-green' },
    REJECTED: { label: '已驳回', tag: 'tag-red' },
    RETURNED: { label: '已退料', tag: 'tag-gray' },
};

/** 工单动作码 → 中文名，与后端 WorkOrderAction 一一对应 */
export const WO_ACTION = {
    DISPATCH: '派单',
    ACCEPT: '接单',
    REJECT_DISPATCH: '拒接',
    TRANSFER: '转派',
    REVOKE: '撤回派单',
    SUBMIT_VERIFY: '提交验收',
    VERIFY_PASS: '验收通过',
    VERIFY_REJECT: '验收不通过',
    CANCEL: '取消工单',
    FORCE_TRANSFER: '强制转派',
};

/** 五星评分展示 */
export function stars(score) {
    const n = Number(score) || 0;
    return '★'.repeat(n) + '☆'.repeat(Math.max(0, 5 - n));
}

/** 时限提示：返回「剩余 x 小时」/「已超期 x 小时」，空值返回 — */
export function dueText(value) {
    if (!value) {
        return '—';
    }
    const target = new Date(value).getTime();
    if (Number.isNaN(target)) {
        return '—';
    }
    const diffHours = (target - Date.now()) / 3600000;
    const abs = Math.abs(diffHours);
    const text = abs >= 24 ? `${(abs / 24).toFixed(1)} 天` : `${abs.toFixed(1)} 小时`;
    return diffHours >= 0 ? `剩余 ${text}` : `已超期 ${text}`;
}

export const INSPECT_STATUS = {
    PENDING: { label: '待执行', tag: 'tag-gray' },
    IN_PROGRESS: { label: '执行中', tag: 'tag-blue' },
    SUBMITTED: { label: '已提交', tag: 'tag-purple' },
    EXPIRED: { label: '已过期', tag: 'tag-red' },
    COMPLETED: { label: '已完成', tag: 'tag-green' },
    OVERDUE: { label: '已逾期', tag: 'tag-red' },
    CANCELLED: { label: '已取消', tag: 'tag-gray' },
};

/** 巡检检查项的三态判定结果 */
export const ITEM_RESULT = {
    NORMAL: { label: '正常', tag: 'tag-green' },
    ABNORMAL: { label: '异常', tag: 'tag-red' },
    SKIPPED: { label: '跳过', tag: 'tag-amber' },
};

export const ITEM_TYPE = {
    NUMBER: { label: '数值', hint: '填写实测值，超出区间判为异常' },
    ENUM: { label: '枚举', hint: '从预设选项中选择一项' },
    BOOLEAN: { label: '是否', hint: '是/否二选一' },
    TEXT: { label: '文本', hint: '自由填写，由巡检人判定' },
};

export const TEMPLATE_STATUS = {
    DRAFT: { label: '草稿', tag: 'tag-gray' },
    PUBLISHED: { label: '已发布', tag: 'tag-green' },
    DISABLED: { label: '已停用', tag: 'tag-gray' },
};

export const PLAN_STATUS = {
    ENABLED: { label: '启用中', tag: 'tag-green' },
    DISABLED: { label: '已暂停', tag: 'tag-gray' },
};

export const CYCLE_TYPE = {
    DAILY: { label: '每日' },
    WEEKLY: { label: '每周' },
    MONTHLY: { label: '每月' },
};

export const SCOPE_TYPE = {
    EQUIPMENT: { label: '按设备' },
    CATEGORY: { label: '按设备分类' },
    LOCATION: { label: '按所在位置' },
};

export const EXECUTOR_STRATEGY = {
    FIXED: { label: '指定人员' },
    ROUND_ROBIN: { label: '轮流执行' },
    DEPT_OWNER: { label: '设备负责人' },
};

export const ANOMALY_STATUS = {
    OPEN: { label: '待处理', tag: 'tag-amber' },
    WORK_ORDER: { label: '已转工单', tag: 'tag-blue' },
    CLOSED: { label: '已关闭', tag: 'tag-green' },
};

export const ANOMALY_LEVEL = {
    CRITICAL: { label: '紧急', tag: 'tag-red' },
    HIGH: { label: '严重', tag: 'tag-amber' },
    MEDIUM: { label: '一般', tag: 'tag-blue' },
    LOW: { label: '轻微', tag: 'tag-gray' },
};

/** 巡检周期里的星期序号：1=周一 … 7=周日（与后端 DayOfWeek 对齐） */
export const WEEKDAYS = [
    { value: 1, label: '周一' },
    { value: 2, label: '周二' },
    { value: 3, label: '周三' },
    { value: 4, label: '周四' },
    { value: 5, label: '周五' },
    { value: 6, label: '周六' },
    { value: 7, label: '周日' },
];

/** 任务是否还能继续执行 */
export function canExecute(status) {
    return status === 'PENDING' || status === 'IN_PROGRESS';
}

export function statusTag(dict, code, fallback = '—') {
    const item = dict[code];
    if (!item) {
        return `<span class="tag tag-gray plain">${esc(code || fallback)}</span>`;
    }
    return `<span class="tag ${item.tag}">${esc(item.label)}</span>`;
}

/* ------------------------------------------------------------------ */
/* 表单工具                                                            */
/* ------------------------------------------------------------------ */

export function formValues(form) {
    const data = {};
    Array.from(form.elements).forEach((field) => {
        if (!field.name || field.disabled) {
            return;
        }
        if (field.type === 'checkbox') {
            if (!Array.isArray(data[field.name])) {
                data[field.name] = [];
            }
            if (field.checked) {
                data[field.name].push(field.value);
            }
            return;
        }
        if (field.type === 'radio') {
            if (field.checked) {
                data[field.name] = field.value;
            }
            return;
        }
        data[field.name] = field.value;
    });
    return data;
}

export function setLoading(button, loading) {
    if (!button) {
        return;
    }
    button.disabled = loading;
    button.classList.toggle('loading', loading);
}

export function debounce(fn, delay = 320) {
    let timer = null;
    return (...args) => {
        clearTimeout(timer);
        timer = setTimeout(() => fn(...args), delay);
    };
}

/** 统一处理异步按钮点击：自动 loading + 错误提示 */
export function guard(button, action) {
    // 兼容两种用法：
    //   guard(buttonEl, async () => {...})  —— 立即执行并控制按钮 loading（返回 Promise，可 await）；
    //   guard(async (event) => {...})       —— 同步返回可绑定的 onclick 处理器
    // 注意 guard 本身必须是同步函数：addEventListener 只接受函数监听器，Promise 会被静默忽略
    if (typeof button === 'function') {
        const only = button;
        return function (event) {
            const target = event && event.currentTarget;
            setLoading(target, true);
            return Promise.resolve().then(() => only(event))
                .then((result) => {
                    setLoading(target, false);
                    return result;
                })
                .catch((error) => {
                    setLoading(target, false);
                    toastError(error);
                    return undefined;
                });
        };
    }
    setLoading(button, true);
    return Promise.resolve().then(action)
        .then((result) => {
            setLoading(button, false);
            return result;
        })
        .catch((error) => {
            setLoading(button, false);
            toastError(error);
            return undefined;
        });
}

export function emptyState(text) {
    return h('div', { class: 'empty', text });
}

/** 扁平数组转树（parentId=0 为根） */
export function toTree(list, { idKey = 'id', parentKey = 'parentId' } = {}) {
    const map = new Map();
    list.forEach((item) => map.set(item[idKey], { ...item, children: [] }));
    const roots = [];
    map.forEach((item) => {
        const parent = map.get(item[parentKey]);
        if (parent && parent[idKey] !== item[idKey]) {
            parent.children.push(item);
        } else {
            roots.push(item);
        }
    });
    return roots;
}

/** 展平树，用于下拉选项 */
export function flattenTree(nodes, level = 0, result = []) {
    nodes.forEach((node) => {
        result.push({ ...node, level });
        if (node.children && node.children.length) {
            flattenTree(node.children, level + 1, result);
        }
    });
    return result;
}

// ---------------- 第六阶段：备件 / SLA / 健康分字典 ----------------

/** 库存流水类型 */
export const STOCK_TXN_TYPE = {
    IN: { label: '入库', cls: 'tag-success' },
    OUT: { label: '出库', cls: 'tag-danger' },
    RETURN: { label: '退料', cls: 'tag-info' },
    ADJUST: { label: '调整', cls: 'tag-warning' },
};

/** 库存流水业务类型 */
export const STOCK_BIZ_TYPE = {
    WORK_ORDER: { label: '工单领用' },
    PURCHASE: { label: '采购入库' },
    RETURN: { label: '退料' },
    CHECK: { label: '盘点调整' },
    OTHER: { label: '其他出库' },
};

/** SLA 状态 */
export const SLA_STATUS = {
    NORMAL: { label: '正常', cls: 'tag-success' },
    WARN: { label: '临近超时', cls: 'tag-warning' },
    OVERDUE: { label: '已超时', cls: 'tag-danger' },
    PAUSED: { label: '已暂停', cls: 'tag-muted' },
};

/** SLA 扫描阶段 */
export const SLA_STAGE = {
    PENDING_ASSIGN: { label: '待派单' },
    PENDING_ACCEPT: { label: '待接单' },
    IN_PROGRESS: { label: '维修中' },
    PENDING_REVIEW: { label: '待验收' },
};

/** 设备健康等级 */
export const HEALTH_LEVEL = {
    HEALTHY: { label: '健康', cls: 'tag-success' },
    SUB_HEALTHY: { label: '亚健康', cls: 'tag-info' },
    WARNING: { label: '预警', cls: 'tag-warning' },
    DANGER: { label: '危险', cls: 'tag-danger' },
};

/** 分钟数 → 「x 天 x 小时」可读文本 */
export function minutesText(minutes) {
    const value = Number(minutes) || 0;
    if (value < 60) return `${value} 分钟`;
    if (value < 1440) return `${Math.floor(value / 60)} 小时 ${value % 60 ? (value % 60) + ' 分' : ''}`.trim();
    return `${Math.floor(value / 1440)} 天 ${Math.floor((value % 1440) / 60)} 小时`;
}
