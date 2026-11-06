/**
 * 设备二维码页：查看、下载、打印、重置。
 *
 * 二维码内容 = 扫码地址 + 设备随机令牌，不含账号、口令、JWT 等敏感信息；
 * 扫描后必须登录并具备 asset:equipment:view 才能取到设备档案。
 */

import { api, auth } from '../api.js';
import { confirmDialog, esc, guard, h, toast, toastError } from '../ui.js';

export async function render(container, ctx) {
    const id = ctx.params.id;
    const canManage = auth.can('asset:equipment:qrcode');

    let data = await api.get(`/api/asset/equipment/${id}/qrcode`);
    ctx.setTitle(`二维码 · ${data.equipmentName}`);

    const head = h('div', { class: 'page-head' },
        h('div', {},
            h('h1', { text: '设备二维码' }),
            h('p', { text: `${data.assetNo} · ${data.equipmentName}` })),
        h('div', { class: 'page-actions' },
            h('button', { class: 'btn', type: 'button', text: '返回详情', onclick: () => ctx.navigate(`/equipment/${id}`) }),
            h('button', { class: 'btn', type: 'button', text: '打印档案（含二维码）', onclick: () => ctx.navigate(`/equipment/${id}/print`) }),
        ),
    );

    const image = h('img', { alt: '设备二维码', style: 'background:#fff' });
    const caption = h('div', { class: 'caption', text: '扫码后需登录并具备设备查看权限' });
    const contentBox = h('div', { class: 'qr-content', text: data.content || data.scanUrl });

    const preview = h('div', { class: 'qr-preview' },
        image,
        caption,
        h('div', { style: 'display:flex;gap:8px;justify-content:center;margin-top:12px' },
            h('button', { class: 'btn btn-sm', type: 'button', text: '下载 PNG', onclick: () => download() }),
            h('button', { class: 'btn btn-sm', type: 'button', text: '打印标签', onclick: () => printLabel() }),
            canManage ? h('button', {
                class: 'btn btn-sm btn-danger', type: 'button', text: '重新生成',
                onclick: () => regenerate(),
            }) : null),
    );

    const info = h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '二维码说明' })),
        h('div', { class: 'card-body' },
            h('div', { class: 'alert alert-info', text: '二维码中只包含扫码地址与 32 位随机令牌，不包含密码、JWT、手机号或任何账号信息。' }),
            h('dl', { class: 'kv', style: 'grid-template-columns:110px 1fr' },
                h('dt', { text: '资产编号' }), h('dd', { class: 'mono', text: data.assetNo }),
                h('dt', { text: '设备名称' }), h('dd', { text: data.equipmentName }),
                h('dt', { text: '二维码令牌' }), h('dd', { class: 'mono', text: data.qrToken }),
                h('dt', { text: '扫码地址' }), h('dd', { text: data.scanUrl }),
            ),
            h('div', { style: 'margin-top:14px' },
                h('div', { style: 'font-size:12.5px;color:var(--text-muted);margin-bottom:6px', text: '编码内容' }),
                contentBox),
            h('div', { style: 'margin-top:14px;font-size:12.5px;color:var(--text-secondary);line-height:1.9' },
                h('div', { text: '· 重新生成后旧二维码立即失效，已张贴的标签需重新打印；' }),
                h('div', { text: '· 建议用 40mm 以上尺寸打印，贴在设备铭牌附近、避免遮挡与油污；' }),
                h('div', { text: '· 扫码人若无设备查看权限，接口会返回 403，不会泄露设备信息。' })),
        ),
    );

    const layout = h('div', { class: 'qr-layout' }, preview, info);
    container.append(head, layout);

    const labelHost = h('div', { class: 'qr-label-print' },
        h('div', { class: 'qr-label' },
            h('img', { alt: '设备二维码' }),
            h('div', { class: 'no', text: data.assetNo }),
            h('div', { class: 'name', text: data.equipmentName }),
            h('div', { class: 'tips', text: '巡维云 EquipCare · 扫码查看设备档案' }),
        ));
    container.append(labelHost);

    await loadImage();

    async function loadImage() {
        try {
            const url = await api.objectUrl(`/api/asset/equipment/${id}/qrcode.png`, { size: 420, margin: 2 });
            image.src = url;
            labelHost.querySelector('img').src = url;
        } catch (error) {
            toastError(error);
        }
    }

    async function download() {
        try {
            const url = await api.objectUrl(`/api/asset/equipment/${id}/qrcode.png`, { size: 640, margin: 2 });
            const link = h('a', { href: url, download: `QR-${data.assetNo}.png` });
            document.body.append(link);
            link.click();
            link.remove();
            setTimeout(() => URL.revokeObjectURL(url), 4000);
            toast('二维码已下载', 'success');
        } catch (error) {
            toastError(error);
        }
    }

    function printLabel() {
        document.body.classList.add('print-qr-only');
        window.print();
        setTimeout(() => document.body.classList.remove('print-qr-only'), 600);
    }

    async function regenerate() {
        const ok = await confirmDialog({
            title: '重新生成二维码',
            message: '重新生成后 <b>旧二维码立即失效</b>，已张贴的标签需要重新打印。<br>确认继续？',
            confirmText: '确认重新生成',
            danger: true,
        });
        if (!ok) {
            return;
        }
        try {
            data = await api.post(`/api/asset/equipment/${id}/qrcode/regenerate`);
            contentBox.textContent = data.content || data.scanUrl;
            const dl = info.querySelector('.kv');
            dl.innerHTML = `
                <dt>资产编号</dt><dd class="mono">${esc(data.assetNo)}</dd>
                <dt>设备名称</dt><dd>${esc(data.equipmentName)}</dd>
                <dt>二维码令牌</dt><dd class="mono">${esc(data.qrToken)}</dd>
                <dt>扫码地址</dt><dd>${esc(data.scanUrl)}</dd>`;
            await loadImage();
            toast('二维码已重新生成，请重新打印张贴', 'success');
        } catch (error) {
            toastError(error);
        }
    }
}
