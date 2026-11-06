/**
 * 工单评价页。
 *
 * 三个维度分开打分（服务态度 / 维修质量 / 响应速度），而不是只给一个总分——
 * 「修得快但态度差」和「态度好但反复返修」在统计上必须能区分开，否则管理改进没有方向。
 *
 * 三条硬规则：
 * 1. 只有已完成的工单能评价（后端状态机之外单独校验，前端先把话说在前面）；
 * 2. 只有报修人本人或管理员能评价；
 * 3. 一单一评，唯一索引兜底，重复提交会被后端拒绝。
 */

import { api, auth } from '../api.js';
import { esc, formatDate, formatMoney, h, stars, toast, toastError } from '../ui.js';
import { attachmentThumbs, kv, woStatus } from './workorder-shared.js';

/** 分数 → 口头描述，让 4 分和 5 分的差别变得可读 */
const SCORE_WORDS = {
    1: '很不满意',
    2: '不满意',
    3: '一般',
    4: '满意',
    5: '非常满意',
};

const DIMENSIONS = [
    {
        key: 'attitudeScore', label: '服务态度',
        hint: '到场沟通、现场配合、是否交代清楚注意事项',
    },
    {
        key: 'qualityScore', label: '维修质量',
        hint: '故障是否根除、复测数据是否达标、外观是否复原',
    },
    {
        key: 'speedScore', label: '响应速度',
        hint: '接单及时性、到场耗时、是否在承诺时限内完成',
    },
];

/**
 * 星级打分控件。
 *
 * 用 radiogroup 语义而不是纯 div：键盘左右方向键能改分，读屏能播报当前分值，
 * 这是评分控件最容易被漏掉的可访问性细节。
 *
 * @returns {{node: HTMLElement, value: () => number, onChange: Function}}
 */
function ratingControl({ label, hint, value = 5, onChange }) {
    let score = value;
    const listeners = [];
    const text = h('span', { class: 'rating-text' });
    const wrap = h('div', { class: 'rating', role: 'radiogroup', 'aria-label': label });
    const buttons = [1, 2, 3, 4, 5].map((n) => {
        const btn = h('button', {
            class: 'rating-star', type: 'button', role: 'radio',
            title: `${n} 分`, 'aria-label': `${label} ${n} 分`,
            onclick: () => {
                apply(n);
                btn.focus();
            },
        }, '★');
        return btn;
    });

    function apply(n) {
        score = n;
        buttons.forEach((btn, index) => {
            const lit = index < n;
            btn.classList.toggle('on', lit);
            btn.setAttribute('aria-checked', String(index + 1 === n));
            // 漫游 tabindex：整组只占一个 Tab 位，进组后用方向键调整
            btn.tabIndex = index + 1 === n ? 0 : -1;
        });
        text.textContent = `${n} 分 · ${SCORE_WORDS[n]}`;
        listeners.forEach((fn) => fn(n));
    }

    wrap.addEventListener('keydown', (event) => {
        const step = event.key === 'ArrowRight' || event.key === 'ArrowUp' ? 1
            : event.key === 'ArrowLeft' || event.key === 'ArrowDown' ? -1 : 0;
        if (step === 0) {
            return;
        }
        event.preventDefault();
        const next = Math.min(5, Math.max(1, score + step));
        apply(next);
        buttons[next - 1].focus();
    });

    apply(value);
    // 分值文案挂在标题行（.rating-tail），不要塞进 wrap——节点只能有一个父元素，
    // 放进 wrap 会把它从标题行里搬走，标题行就空了。
    wrap.append(...buttons);

    const node = h('div', { class: 'rating-field' },
        h('div', { class: 'rating-head' },
            h('span', { class: 'rating-label', text: label }),
            h('span', { class: 'rating-tail' }, text)),
        wrap,
        hint ? h('div', { class: 'hint', text: hint }) : null);

    return {
        node,
        value: () => score,
        setScore: apply,
        onChange: (fn) => listeners.push(fn),
    };
}

export async function render(container, ctx) {
    const id = Number(ctx.params.id);
    let detail;
    try {
        detail = await api.get(`/api/workorder/${id}`);
    } catch (error) {
        container.append(failure(error.message, () => ctx.navigate('/workorder')));
        return;
    }

    ctx.setTitle(`评价 · ${detail.orderNo}`);

    const head = h('div', { class: 'page-head' },
        h('div', {},
            h('h1', { text: '工单评价' }),
            h('p', {}, h('span', { class: 'mono', text: detail.orderNo }), ' · ',
                h('span', { text: detail.title }), ' ',
                h('span', { html: woStatus(detail.status) }))),
        h('div', { class: 'page-actions' },
            h('button', {
                class: 'btn', type: 'button', text: '工单详情',
                onclick: () => ctx.navigate(`/workorder/${id}`),
            }),
            h('button', {
                class: 'btn', type: 'button', text: '操作时间线',
                onclick: () => ctx.navigate(`/workorder/${id}/timeline`),
            })));

    /* --------------------------- 前置条件守卫 --------------------------- */

    if (detail.status !== 'COMPLETED') {
        container.append(head, failure(
            `工单当前是「${detail.statusLabel || detail.status}」，只有已完成的工单可以评价。`,
            () => ctx.navigate(`/workorder/${id}`),
        ));
        return;
    }

    const me = auth.user || {};
    const myId = me.userId || me.id;
    const isReporter = !!detail.reporterId && detail.reporterId === myId;
    const isAdmin = auth.hasRole('ADMIN') || auth.hasRole('EQUIP_ADMIN');
    if (!isReporter && !isAdmin) {
        container.append(head, failure(
            `只有报修人「${detail.reporterName || '—'}」本人或管理员可以评价这张工单。`,
            () => ctx.navigate(`/workorder/${id}`),
        ));
        return;
    }

    /* ------------------------- 已评价：只读展示 ------------------------- */

    if (detail.evaluation) {
        container.append(head, readOnlyCard(detail), reviewContextCard(detail));
        return;
    }

    /* --------------------------- 可评价：表单 --------------------------- */

    const controls = DIMENSIONS.map((dim) => ratingControl({ label: dim.label, hint: dim.hint }));
    const averageValue = h('span', { class: 'metric-value', text: '5.0' });
    const averageHint = h('div', { class: 'metric-hint', text: '三项平均分' });

    function refreshAverage() {
        const sum = controls.reduce((total, control) => total + control.value(), 0);
        averageValue.textContent = (sum / controls.length).toFixed(1);
        averageValue.className = `metric-value ${sum / controls.length >= 4 ? 'ok' : sum / controls.length < 3 ? 'danger' : 'warn'}`;
    }

    controls.forEach((control) => control.onChange(refreshAverage));
    refreshAverage();

    const contentInput = h('textarea', {
        class: 'input', rows: '4', maxlength: '500',
        placeholder: '说说这次维修：故障是否彻底解决、有没有反复、师傅沟通如何（选填，最多 500 字）',
    });
    const counter = h('div', { class: 'hint', text: '0 / 500' });
    contentInput.addEventListener('input', () => {
        counter.textContent = `${contentInput.value.length} / 500`;
    });

    const submitButton = h('button', { class: 'btn btn-primary', type: 'button', text: '提交评价' });
    const resetButton = h('button', { class: 'btn', type: 'button', text: '重置为 5 分' });

    resetButton.addEventListener('click', () => {
        controls.forEach((control) => control.setScore?.(5));
    });

    submitButton.addEventListener('click', async () => {
        submitButton.disabled = true;
        submitButton.textContent = '提交中…';
        try {
            await api.post(`/api/workorder/${id}/evaluate`, {
                attitudeScore: controls[0].value(),
                qualityScore: controls[1].value(),
                speedScore: controls[2].value(),
                content: contentInput.value.trim() || null,
            });
            toast('评价已提交，感谢反馈', 'success');
            ctx.navigate(`/workorder/${id}`);
        } catch (error) {
            toastError(error);
            submitButton.disabled = false;
            submitButton.textContent = '提交评价';
        }
    });

    const formCard = h('div', { class: 'card' },
        h('div', { class: 'card-head' },
            h('h2', { text: '服务评价' }),
            h('span', { class: 'hint', text: '一单一评，提交后不可修改' })),
        h('div', { class: 'card-body' },
            h('div', { class: 'metric', style: 'margin-bottom:16px' },
                h('div', { class: 'metric-label', text: '综合得分' }),
                averageValue,
                averageHint),
            h('div', { class: 'rating-grid' }, ...controls.map((control) => control.node)),
            h('div', { class: 'field', style: 'margin-top:14px' },
                h('label', { text: '文字评价' }),
                contentInput,
                counter),
            h('div', { class: 'chip-row', style: 'margin-top:14px' }, submitButton, resetButton),
            h('p', { class: 'hint', text: '评价会写进工单时间线，维修人也会收到通知；提交后不能撤回或重评。' })));

    container.append(head, reviewContextCard(detail), formCard);
}

/* ---------------------------------------------------------------------- */

/** 评价前的参考信息：让评价人有据可依，而不是凭印象打分 */
function reviewContextCard(detail) {
    const records = detail.repairRecords || [];
    const finishNote = records.filter((record) => record.recordType === 'FINISH').pop();

    return h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '本次维修' })),
        h('div', { class: 'card-body' },
            h('div', { class: 'kv-grid' },
                kv('设备', `${esc(detail.equipmentName || '—')}　<span class="mono">${esc(detail.assetNo || '')}</span>`),
                kv('报修人', esc(detail.reporterName || '—')),
                kv('维修人', esc(detail.assigneeName || '—')),
                kv('故障等级', esc(detail.level || '—')),
                kv('报修时间', formatDate(detail.reportTime, true)),
                kv('完成时间', formatDate(detail.actualFinishTime || detail.closeTime, true)),
                kv('维修工时', detail.laborHours != null ? `${detail.laborHours} 小时` : '—'),
                kv('验收评分', detail.qualityScore != null ? `${detail.qualityScore} 分` : '—'),
                kv('备件成本', detail.spareCost != null ? formatMoney(detail.spareCost) : '—'),
                kv('驳回次数', String(detail.rejectCount || 0))),
            h('div', { class: 'hint', text: '故障描述' }),
            h('p', { class: 'fault-text', text: detail.faultDesc || '—' }),
            h('div', { class: 'hint', text: '维修总结' }),
            h('p', { class: 'fault-text', text: detail.repairSummary || (finishNote && finishNote.content) || '—' }),
            detail.verifyRemark
                ? h('div', {},
                    h('div', { class: 'hint', text: '验收意见' }),
                    h('p', { class: 'fault-text', text: detail.verifyRemark }))
                : null,
            (detail.faultImages || []).length
                ? h('div', {}, h('div', { class: 'hint', text: '报修现场照片' }), attachmentThumbs(detail.faultImages))
                : null));
}

/** 已评价：只读回显 */
function readOnlyCard(detail) {
    const evaluation = detail.evaluation;
    const dims = [
        ['服务态度', evaluation.attitudeScore],
        ['维修质量', evaluation.qualityScore],
        ['响应速度', evaluation.speedScore],
    ];

    return h('div', { class: 'card' },
        h('div', { class: 'card-head' },
            h('h2', { text: '服务评价' }),
            h('span', { class: 'hint', text: `综合 ${evaluation.averageScore} 分` })),
        h('div', { class: 'card-body' },
            h('div', { class: 'stack' }, ...dims.map(([label, score]) => h('div', { class: 'rating-readonly' },
                h('span', { class: 'rating-label', text: label }),
                h('span', { class: 'rating-stars', text: stars(score) }),
                h('span', { class: 'cell-sub', text: `${score} 分 · ${SCORE_WORDS[score] || ''}` })))),
            h('div', { class: 'kv-grid', style: 'margin-top:10px' },
                kv('评价人', esc(evaluation.evaluatorName || '—')),
                kv('评价时间', formatDate(evaluation.createTime, true)),
                kv('平均分', `${evaluation.averageScore} 分`)),
            h('p', { class: 'fault-text', text: evaluation.content || '（未填写文字评价）' }),
            h('p', { class: 'hint', text: '该工单已评价，一单只允许一条评价记录。' })));
}

function failure(message, onBack) {
    return h('div', { class: 'card' },
        h('div', { class: 'card-body' },
            h('p', { text: message }),
            h('button', { class: 'btn', type: 'button', text: '返回工单', onclick: onBack })));
}
