/**
 * 设备新增 / 编辑。
 *
 * 编辑复用同一页面：状态不在本页修改（由详情页的独立状态接口控制），
 * 图片与附件在保存后进入详情页上传。
 */

import { api, auth } from '../api.js';
import { esc, flattenTree, guard, h, toast, toastError } from '../ui.js';

export async function render(container, ctx) {
    const id = ctx.params.id;
    const editing = Boolean(id);
    ctx.setTitle(editing ? '编辑设备' : '新增设备');

    let detail = {};
    const [categories, locations, depts, users] = await Promise.all([
        api.get('/api/asset/categories/tree'),
        api.get('/api/asset/locations/tree'),
        api.get('/api/system/depts/tree').catch(() => []),
        api.get('/api/system/users/options').catch(() => []),
    ]);
    if (editing) {
        detail = await api.get(`/api/asset/equipment/${id}`);
    }

    const head = h('div', { class: 'page-head' },
        h('div', {},
            h('h1', { text: editing ? '编辑设备' : '新增设备' }),
            h('p', {
                text: editing
                    ? `资产编号 ${detail.assetNo} · 修改基础信息不会影响设备状态与历史记录`
                    : '资产编号留空时，系统按「类别编码-年月-流水」自动生成且不可复用',
            })),
        h('div', { class: 'page-actions' },
            h('button', {
                class: 'btn', type: 'button', text: '返回列表',
                onclick: () => ctx.navigate('/equipment'),
            })),
    );

    const form = h('form', { novalidate: true });

    /* ---------------- 字段 ---------------- */
    const assetNo = input({ name: 'assetNo', value: detail.assetNo, placeholder: '留空自动生成，如 EQ-KT-202610-0001', maxlength: 40 });
    const name = input({ name: 'equipmentName', value: detail.equipmentName, required: true, maxlength: 100, placeholder: '如 1 号中央空调机组' });
    const category = select('categoryId', flattenTree(categories || []).map((node) => ({
        value: node.id,
        label: `${'　'.repeat(node.level)}${node.categoryName}`,
    })), detail.categoryId, true);
    const location = select('locationId', flattenTree(locations || []).map((node) => ({
        value: node.id,
        label: `${'　'.repeat(node.level)}${node.locationName}`,
    })), detail.locationId, true);
    const dept = select('deptId', flattenTree(depts || []).map((node) => ({
        value: node.id,
        label: `${'　'.repeat(node.level)}${node.deptName}`,
    })), detail.deptId);
    const responsible = select('responsibleUserId', (users || []).map((user) => ({
        value: user.id,
        label: user.deptName ? `${user.realName}（${user.deptName}）` : user.realName,
    })), detail.responsibleUserId);

    const brand = input({ name: 'brand', value: detail.brand, maxlength: 60 });
    const model = input({ name: 'model', value: detail.model, maxlength: 80 });
    const spec = input({ name: 'spec', value: detail.spec, maxlength: 120 });
    const serialNo = input({ name: 'serialNo', value: detail.serialNo, maxlength: 80 });
    const manufacturer = input({ name: 'manufacturer', value: detail.manufacturer, maxlength: 100 });
    const supplier = input({ name: 'supplier', value: detail.supplier, maxlength: 100 });
    const purchaseDate = input({ name: 'purchaseDate', type: 'date', value: detail.purchaseDate });
    const installDate = input({ name: 'installDate', type: 'date', value: detail.installDate });
    const warrantyEndDate = input({ name: 'warrantyEndDate', type: 'date', value: detail.warrantyEndDate });
    const originalValue = input({ name: 'originalValue', type: 'number', step: '0.01', min: '0', value: detail.originalValue });
    const remark = h('textarea', { class: 'textarea', name: 'remark', maxlength: 500, placeholder: '安装位置说明、特殊注意事项等' });
    remark.value = detail.remark || '';

    form.append(h('div', { class: 'form-grid' },
        h('div', { class: 'form-section-title', text: '基础信息' }),
        field('资产编号', assetNo, { help: '4-40 位字母/数字/下划线/中划线；保存后仍可修改，但必须全局唯一' }),
        field('设备名称', name, { required: true }),
        field('设备分类', category, { required: true }),
        field('所在位置', location, { required: true }),
        field('归属部门', dept),
        field('设备责任人', responsible, { help: '责任人可在设备详情页随时调整' }),

        h('div', { class: 'form-section-title', text: '规格与来源' }),
        field('品牌', brand),
        field('型号', model),
        field('规格参数', spec),
        field('出厂序列号', serialNo),
        field('制造商', manufacturer),
        field('供应商', supplier),

        h('div', { class: 'form-section-title', text: '资产与保修' }),
        field('采购日期', purchaseDate),
        field('安装日期', installDate),
        field('保修截止', warrantyEndDate),
        field('设备原值（元）', originalValue),
        h('div', { class: 'field span-2' }, h('label', { text: '备注' }), remark),
    ));

    const submit = h('button', {
        class: 'btn btn-primary', type: 'submit', text: editing ? '保存修改' : '创建设备',
    });
    const reset = h('button', {
        class: 'btn', type: 'button', text: '取消',
        onclick: () => ctx.navigate(editing ? `/equipment/${id}` : '/equipment'),
    });
    form.append(h('div', { class: 'form-actions' }, reset, submit));

    const alertBox = h('div', { class: 'alert alert-error', hidden: true });
    const card = h('div', { class: 'card' },
        h('div', { class: 'card-head' },
            h('h2', { text: '设备档案信息' }),
            h('span', { class: 'hint', text: editing ? `最后更新 ${esc(detail.updateTime || '—')}` : '带 * 为必填项' })),
        h('div', { class: 'card-body' }, alertBox, form));

    container.append(head, card);

    if (!auth.can(editing ? 'asset:equipment:update' : 'asset:equipment:create')) {
        alertBox.hidden = false;
        alertBox.textContent = '当前账号缺少该操作的权限码，提交会被后端拒绝。';
        submit.disabled = true;
    }

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        alertBox.hidden = true;

        if (!name.value.trim()) {
            return showError('请填写设备名称');
        }
        if (!category.value) {
            return showError('请选择设备分类');
        }
        if (!location.value) {
            return showError('请选择所在位置');
        }

        const payload = {
            assetNo: assetNo.value.trim(),
            equipmentName: name.value.trim(),
            categoryId: Number(category.value),
            locationId: Number(location.value),
            deptId: dept.value ? Number(dept.value) : null,
            responsibleUserId: responsible.value ? Number(responsible.value) : null,
            brand: blankToNull(brand.value),
            model: blankToNull(model.value),
            spec: blankToNull(spec.value),
            serialNo: blankToNull(serialNo.value),
            manufacturer: blankToNull(manufacturer.value),
            supplier: blankToNull(supplier.value),
            purchaseDate: blankToNull(purchaseDate.value),
            installDate: blankToNull(installDate.value),
            warrantyEndDate: blankToNull(warrantyEndDate.value),
            originalValue: originalValue.value === '' ? null : Number(originalValue.value),
            remark: blankToNull(remark.value),
        };

        await guard(submit, async () => {
            if (editing) {
                await api.put(`/api/asset/equipment/${id}`, payload);
                toast('设备信息已保存', 'success');
                ctx.navigate(`/equipment/${id}`);
            } else {
                const newId = await api.post('/api/asset/equipment', payload);
                toast('设备创建成功，可继续上传图片与附件', 'success');
                ctx.navigate(`/equipment/${newId}`);
            }
        });
    });

    function showError(message) {
        alertBox.textContent = message;
        alertBox.hidden = false;
        return false;
    }
}

/* ---------------- 小工具 ---------------- */

function field(label, control, { required = false, help } = {}) {
    return h('div', { class: 'field' },
        h('label', { class: required ? 'required' : '', text: label }),
        control,
        help ? h('span', { class: 'help', text: help }) : null);
}

function input({ name, value, type = 'text', placeholder, required = false, maxlength, step, min }) {
    const node = h('input', {
        class: 'input', name, type, placeholder,
        maxlength: maxlength || null, step: step || null, min: min || null,
        required: required ? true : null,
    });
    node.value = value === null || value === undefined ? '' : value;
    return node;
}

function select(name, options, current, required = false) {
    const node = h('select', { class: 'select', name, required: required ? true : null },
        h('option', { value: '', text: required ? '请选择' : '不指定' }));
    options.forEach((option) => {
        node.append(h('option', { value: option.value, text: option.label }));
    });
    if (current !== null && current !== undefined) {
        node.value = String(current);
    }
    return node;
}

function blankToNull(value) {
    const text = (value || '').trim();
    return text === '' ? null : text;
}
