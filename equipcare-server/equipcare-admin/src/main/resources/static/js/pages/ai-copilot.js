/**
 * AI 智能副驾 · 前端集成模块。
 *
 * 职责：
 *  1. 探测 AI 能力开关（/api/ai/status，一次会话缓存）；未启用时所有入口自动隐藏；
 *  2. 故障诊断：票据 → EventSource 流式订阅 /api/ai/diagnosis/{id}/stream，
 *     事件名 delta（增量文本）/ ai.error（受控降级错误）/ done（完成）；
 *  3. 维修报告：一次性拉取 Markdown，弹窗渲染 + 复制/下载；
 *  4. AI 派单推荐：POST /api/ai/dispatch-suggest/{id}，点击候选即回填派单表单。
 *
 * 设计约束：零依赖、复用 ui.js 的 modal/h/esc 与 api.js 的票据化 EventSource 通道，
 * 与现有 28 个页面同构（不引入框架、不引入 marked 等外部库）。
 */

import { api } from '../api.js';
import { esc, h, modal, toast, toastError } from '../ui.js';

let aiEnabledCache = null;

/** AI 是否启用（会话级缓存；404/异常一律视为未启用） */
export async function aiEnabled() {
    if (aiEnabledCache !== null) {
        return aiEnabledCache;
    }
    try {
        const status = await api.get('/api/ai/status');
        aiEnabledCache = !!status.enabled;
    } catch (e) {
        aiEnabledCache = false;
    }
    return aiEnabledCache;
}

/* ------------------------------------------------------------------ */
/* 轻量 Markdown 渲染（诊断/报告输出用；先转义再标记，杜绝注入）        */
/* ------------------------------------------------------------------ */

export function renderMarkdownLite(md) {
    if (!md) {
        return '';
    }
    const lines = String(md).split(/\r?\n/);
    const out = [];
    let inCode = false;
    let codeBuf = [];
    let inList = false;
    const closeList = () => {
        if (inList) {
            out.push('</ul>');
            inList = false;
        }
    };
    for (const line of lines) {
        if (line.trim().startsWith('```')) {
            if (inCode) {
                out.push(`<pre class="ai-code">${esc(codeBuf.join('\n'))}</pre>`);
                codeBuf = [];
                inCode = false;
            } else {
                closeList();
                inCode = true;
            }
            continue;
        }
        if (inCode) {
            codeBuf.push(line);
            continue;
        }
        const heading = /^(#{1,4})\s+(.*)$/.exec(line);
        if (heading) {
            closeList();
            const level = Math.min(heading[1].length + 1, 5);
            out.push(`<h${level} class="ai-h">${inline(heading[2])}</h${level}>`);
            continue;
        }
        const li = /^\s*[-*]\s+(.*)$/.exec(line);
        if (li) {
            if (!inList) {
                out.push('<ul class="ai-ul">');
                inList = true;
            }
            out.push(`<li>${inline(li[1])}</li>`);
            continue;
        }
        const ol = /^\s*\d+[.)]\s+(.*)$/.exec(line);
        if (ol) {
            if (!inList) {
                out.push('<ul class="ai-ul">');
                inList = true;
            }
            out.push(`<li>${inline(ol[1])}</li>`);
            continue;
        }
        closeList();
        if (line.trim() === '') {
            out.push('<div class="ai-gap"></div>');
        } else {
            out.push(`<p class="ai-p">${inline(line)}</p>`);
        }
    }
    closeList();
    if (inCode) {
        out.push(`<pre class="ai-code">${esc(codeBuf.join('\n'))}</pre>`);
    }
    return out.join('');

    function inline(s) {
        return esc(s)
            .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
            .replace(/`([^`]+)`/g, '<code class="ai-inline">$1</code>');
    }
}

/* ------------------------------------------------------------------ */
/* 票据化 EventSource（复用 SSE 通道鉴权方案）                          */
/* ------------------------------------------------------------------ */

/**
 * 建立一次性流式订阅。
 * @param {string} path 事件端点（如 /api/ai/diagnosis/1/stream）
 * @param {{onDelta:Function, onDone:Function, onError:Function, onFail:Function}} handlers
 * @returns {Function} close()
 */
export async function streamViaSse(path, { onDelta, onDone, onError, onFail }) {
    let ticket;
    try {
        const r = await api.post('/api/sse/ticket');
        ticket = r.ticket;
    } catch (e) {
        onFail(new Error('无法建立 AI 连接（' + (e.message || '票据获取失败') + '）'));
        return () => {};
    }
    const source = new EventSource(`/api/sse/stream?ticket=${encodeURIComponent(ticket)}`);
    const deliver = (fn, arg) => {
        try {
            fn(arg);
        } catch (ignored) {
            // 渲染回调异常不中断流
        }
    };
    source.addEventListener('delta', (e) => deliver(onDelta, e.data));
    source.addEventListener('done', () => {
        source.close();
        deliver(onDone);
    });
    source.addEventListener('ai.error', (e) => {
        let message = e.data;
        try {
            message = JSON.parse(e.data).message || message;
        } catch (ignored) {
            // 纯文本错误
        }
        source.close();
        deliver(onError, message);
    });
    source.onerror = () => {
        source.close();
        deliver(onError, '连接中断，请重试');
    };
    source.onopen = () => deliver(onDelta, '');
    return () => source.close();
}

/* ------------------------------------------------------------------ */
/* 故障诊断弹窗（流式）                                                 */
/* ------------------------------------------------------------------ */

export function openDiagnosisModal(order) {
    let acc = '';
    const output = h('div', { class: 'ai-md' },
        h('p', { class: 'hint ai-cursor', text: '正在汇总工单、设备档案、健康分与历史维修数据，AI 推理中…' }));
    let closeStream = () => {};
    const dlg = modal({
        title: `AI 故障诊断 · ${order.orderNo}`,
        width: 720,
        body: output,
        footer: [{ label: '关闭' }],
        onClose: () => closeStream(),
    });
    void dlg;
    streamViaSse(`/api/ai/diagnosis/${order.id}/stream`, {
        onDelta: (delta) => {
            const cursor = output.querySelector('.ai-cursor');
            if (cursor) {
                cursor.remove();
            }
            acc += delta || '';
            output.innerHTML = renderMarkdownLite(acc) + '<span class="ai-caret">▍</span>';
        },
        onError: (message) => {
            output.innerHTML = `<p class="hint">⚠️ ${esc(message)}</p>`;
        },
        onFail: (e) => {
            output.innerHTML = `<p class="hint">⚠️ ${esc(e.message)}</p>`;
            toastError(e);
        },
        onDone: () => {
            const caret = output.querySelector('.ai-caret');
            if (caret) {
                caret.remove();
            }
        },
    }).then((close) => {
        closeStream = close;
    });
}

/* ------------------------------------------------------------------ */
/* 维修报告弹窗（一次性生成）                                           */
/* ------------------------------------------------------------------ */

export async function openReportModal(order) {
    const body = h('div', { class: 'ai-md' }, h('p', { class: 'hint', text: 'AI 正在撰写维修报告，约需 10~30 秒…' }));
    const dlg = modal({
        title: `维修报告 · ${order.orderNo}`,
        width: 720,
        body,
        footer: [
            {
                label: '复制 Markdown',
                onClick: () => {
                    const text = body.dataset.raw || '';
                    navigator.clipboard.writeText(text)
                        .then(() => toast('已复制到剪贴板', 'success'))
                        .catch(() => toast('复制失败，请手动选择文本', 'warn'));
                    return false;
                },
            },
            {
                label: '下载 .md',
                onClick: () => {
                    const blob = new Blob([body.dataset.raw || ''], { type: 'text/markdown;charset=utf-8' });
                    const a = document.createElement('a');
                    a.href = URL.createObjectURL(blob);
                    a.download = `维修报告-${order.orderNo}.md`;
                    a.click();
                    URL.revokeObjectURL(a.href);
                    return false;
                },
            },
            { label: '关闭' },
        ],
    });
    try {
        const { markdown } = await api.post(`/api/ai/report/${order.id}`);
        body.dataset.raw = markdown;
        body.innerHTML = renderMarkdownLite(markdown);
    } catch (e) {
        body.innerHTML = `<p class="hint">⚠️ ${esc(e.message || '报告生成失败')}</p>`;
        toastError(e);
    }
    void dlg;
}

/* ------------------------------------------------------------------ */
/* 工单详情页 AI 卡片                                                   */
/* ------------------------------------------------------------------ */

/**
 * 工单详情页的 AI 副驾卡片。AI 未启用时返回 null（页面照常渲染）。
 * @param {object} detail 工单详情 VO
 */
export function aiCard(detail) {
    const enabled = aiEnabledCache;
    if (enabled === false) {
        return null;
    }
    const body = h('div', { class: 'card-body' });
    if (enabled === null) {
        // 状态查询进行中：先渲染占位，查询完成后再填充
        body.append(h('p', { class: 'hint', text: 'AI 能力探测中…' }));
        aiEnabled().then((on) => {
            const fresh = aiCard(detail);
            body.replaceWith(fresh ? fresh.querySelector('.card-body') : h('p', { class: 'hint', text: 'AI 功能未启用（equipcare.ai.enabled=false），系统功能不受影响' }));
        });
        return h('div', { class: 'card' },
            h('div', { class: 'card-head' }, h('h2', { text: 'AI 智能副驾' }),
                h('span', { class: 'tag tag-blue', text: 'Beta' })), body);
    }
    const actions = [
        h('button', {
            class: 'btn btn-primary', type: 'button', text: '⚡ AI 故障诊断',
            onclick: () => openDiagnosisModal(detail),
        })];
    if ((detail.faultImages || []).length > 0) {
        actions.push(h('button', {
            class: 'btn', type: 'button', text: `📷 AI 看图识别 (${Math.min(detail.faultImages.length, 4)})`,
            onclick: () => openDefectModal(detail.faultImages, detail.faultDesc),
        }));
    }
    actions.push(h('button', {
        class: 'btn', type: 'button', text: '📄 生成维修报告',
        onclick: () => openReportModal(detail),
    }));
    body.append(h('div', { class: 'ai-actions' }, ...actions),
        h('p', { class: 'hint', text: '基于工单轨迹、设备档案、健康分与同设备历史工单推断，仅供维修参考，不替代现场判断。' }));
    return h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: 'AI 智能副驾' }),
            h('span', { class: 'tag tag-blue', text: 'Beta' })), body);
}
