/**
 * 报修页面。
 *
 * 只收集「报修人当场就知道的信息」：哪台设备、什么问题、多严重、有没有现场照片。
 * 处理人、时限、优先级属于派单环节，这里不出现，避免报修人替派单人做决定。
 */

import { api } from '../api.js';
import { FAULT_TYPES, WORK_ORDER_LEVEL, h, toast, toastError } from '../ui.js';
import { loadEquipments, photoPicker } from './workorder-shared.js';

export async function render(container, ctx) {
    const equipmentSelect = h('select', { class: 'input' }, h('option', { value: '', text: '加载中…' }));
    const keywordInput = h('input', { class: 'input', type: 'search', placeholder: '按设备名 / 资产编号搜索' });
    const titleInput = h('input', { class: 'input', placeholder: '一句话说清故障，例如：空压机机头异响' });
    const faultSelect = h('select', { class: 'input' },
        ...FAULT_TYPES.map((t) => h('option', { value: t.code, text: t.label })));
    const levelSelect = h('select', { class: 'input' },
        ...Object.entries(WORK_ORDER_LEVEL).map(([code, item]) => h('option', { value: code, text: item.label })));
    levelSelect.value = 'P3';
    const descInput = h('textarea', {
        class: 'input', rows: '4',
        placeholder: '现象、发生时间、已做过的临时处理、是否影响生产……信息越具体，维修越快',
    });
    const photos = photoPicker({ label: '故障现场照片', max: 6 });
    const submitButton = h('button', { class: 'btn btn-primary', type: 'button', text: '提交报修' });

    async function fillEquipments(keyword = '') {
        try {
            const list = await loadEquipments(keyword);
            equipmentSelect.innerHTML = '';
            if (list.length === 0) {
                equipmentSelect.append(h('option', { value: '', text: '没有匹配的设备' }));
                return;
            }
            equipmentSelect.append(h('option', { value: '', text: '请选择设备' }));
            list.forEach((item) => {
                equipmentSelect.append(h('option', {
                    value: String(item.id),
                    text: `${item.equipmentName}（${item.assetNo}）`,
                }));
            });
        } catch (error) {
            toastError(error);
            equipmentSelect.innerHTML = '';
            equipmentSelect.append(h('option', { value: '', text: '设备加载失败' }));
        }
    }

    submitButton.addEventListener('click', async () => {
        if (!equipmentSelect.value) {
            toast('请选择设备', 'error');
            return;
        }
        if (!titleInput.value.trim()) {
            toast('请填写故障标题', 'error');
            return;
        }
        if (!descInput.value.trim()) {
            toast('请填写故障描述', 'error');
            return;
        }
        submitButton.disabled = true;
        try {
            const id = await api.post('/api/workorder', {
                equipmentId: Number(equipmentSelect.value),
                title: titleInput.value.trim(),
                faultType: faultSelect.value,
                faultDesc: descInput.value.trim(),
                level: levelSelect.value,
                faultImageIds: photos.ids() || null,
            });
            toast('报修已提交，等待派单', 'success');
            ctx.navigate(`/workorder/${id}`);
        } catch (error) {
            toastError(error);
            submitButton.disabled = false;
        }
    });

    const head = h('div', { class: 'page-head' },
        h('div', {},
            h('h1', { text: '报修' }),
            h('p', { text: '提交后进入「待派单」，派单人在派单工作台处理。' })),
        h('div', { class: 'page-actions' },
            h('button', {
                class: 'btn', type: 'button', text: '← 工单列表',
                onclick: () => ctx.navigate('/workorder'),
            })));

    const form = h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '故障信息' })),
        h('div', { class: 'card-body' },
            h('div', { class: 'form-grid' },
                h('div', { class: 'field grow' },
                    h('label', { text: '设备' }),
                    h('div', { class: 'toolbar', style: 'gap:8px' }, keywordInput, equipmentSelect)),
                h('div', { class: 'field grow' }, h('label', { text: '故障标题' }), titleInput),
                h('div', { class: 'field' }, h('label', { text: '故障类型' }), faultSelect),
                h('div', { class: 'field' }, h('label', { text: '故障等级（决定 SLA）' }), levelSelect),
                h('div', { class: 'field grow' }, h('label', { text: '故障描述' }), descInput),
                h('div', { class: 'field grow' }, photos.node)),
            h('div', { class: 'hint', text: '等级说明：P1 紧急（1 小时响应 / 4 小时修复）、P2 高（4 小时 / 24 小时）、'
                + 'P3 中（8 小时 / 3 天）、P4 低（1 天 / 7 天）。' }),
            h('div', { style: 'margin-top:16px' }, submitButton)));

    container.append(head, form);

    keywordInput.addEventListener('keydown', (event) => {
        if (event.key === 'Enter') {
            event.preventDefault();
            fillEquipments(keywordInput.value.trim());
        }
    });
    keywordInput.addEventListener('change', () => fillEquipments(keywordInput.value.trim()));

    await fillEquipments();
    if (!equipmentSelect.value) {
        equipmentSelect.prepend(h('option', { value: '', text: '请选择设备' }));
    }
    // 设备名过长时给出完整信息
    equipmentSelect.addEventListener('change', () => {
        const option = equipmentSelect.selectedOptions[0];
        if (option) {
            equipmentSelect.title = option.textContent;
        }
    });
}
