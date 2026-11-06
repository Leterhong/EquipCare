/**
 * 个人资料：查看账号信息、修改资料、修改密码。
 *
 * 修改密码成功后后端会强制下线全部会话，这里主动清理本地令牌并回到登录页。
 */

import { api, auth, redirectToLogin } from '../api.js';
import { esc, guard, h, modal, toast, toastError } from '../ui.js';

const ROLE_LABEL = {
    ADMIN: '系统管理员',
    EQUIP_ADMIN: '设备管理员',
    INSPECTOR: '巡检人员',
    REPAIRMAN: '维修人员',
    VERIFIER: '验收人员',
    MANAGER: '企业负责人',
};

export async function render(container, ctx) {
    ctx.setTitle('个人资料');
    let me;
    try {
        me = await api.get('/api/auth/me');
        auth.saveUser(me);
    } catch (error) {
        toastError(error);
        return;
    }

    const name = h('input', { class: 'input', maxlength: 50 });
    name.value = me.realName || '';
    const phone = h('input', { class: 'input', maxlength: 11, placeholder: '11 位手机号' });
    phone.value = me.phone || '';
    const email = h('input', { class: 'input', type: 'email', maxlength: 100 });
    email.value = me.email || '';

    const form = h('form', { novalidate: true },
        h('div', { class: 'form-grid' },
            h('div', { class: 'field' },
                h('label', { text: '登录名' }),
                h('input', { class: 'input', value: me.username, readonly: true })),
            h('div', { class: 'field' },
                h('label', { text: '所属部门' }),
                h('input', { class: 'input', value: me.deptName || '未分配', readonly: true })),
            h('div', { class: 'field' }, h('label', { class: 'required', text: '姓名' }), name),
            h('div', { class: 'field' }, h('label', { text: '手机号' }), phone),
            h('div', { class: 'field' }, h('label', { text: '邮箱' }), email),
        ),
        h('div', { class: 'form-actions' },
            h('button', {
                class: 'btn', type: 'button', text: '修改密码',
                onclick: () => openChangePassword(),
            }),
            h('button', { class: 'btn btn-primary', type: 'submit', text: '保存资料' })),
    );

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        if (!name.value.trim()) {
            toast('请填写姓名', 'error');
            return;
        }
        const button = form.querySelector('button[type="submit"]');
        await guard(button, async () => {
            const updated = await api.put('/api/auth/profile', {
                realName: name.value.trim(),
                phone: phone.value.trim(),
                email: email.value.trim(),
                avatar: me.avatar || null,
            });
            auth.saveUser(updated);
            toast('资料已保存', 'success');
        });
    });

    const roleTags = (me.roles || []).map((role) => `<span class="tag tag-blue plain">${esc(ROLE_LABEL[role] || role)}</span>`).join(' ');
    const permCount = (me.permissions || []).length;

    const info = h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '账号信息' })),
        h('div', { class: 'card-body' },
            h('dl', { class: 'kv', style: 'grid-template-columns:96px 1fr' },
                h('dt', { text: '账号 ID' }), h('dd', { class: 'mono', text: me.userId }),
                h('dt', { text: '登录名' }), h('dd', { class: 'mono', text: me.username }),
                h('dt', { text: '角色' }), h('dd', { html: roleTags || '—' }),
                h('dt', { text: '权限数' }), h('dd', { text: `${permCount} 项` }),
                h('dt', { text: '最近登录' }), h('dd', { text: me.lastLoginTime || '—' })),
            h('div', { style: 'margin-top:14px;font-size:12.5px;color:var(--text-muted);line-height:1.9' },
                h('div', { text: '· 登录名与角色只能由管理员修改；' }),
                h('div', { text: '· 修改密码后全部会话立即失效，需要使用新密码重新登录；' }),
                h('div', { text: '· 权限由后端校验，界面隐藏按钮只是体验优化。' }))),
    );

    const permList = h('div', { class: 'card' },
        h('div', { class: 'card-head' }, h('h2', { text: '我的权限码' })),
        h('div', { class: 'card-body' },
            h('div', { style: 'display:flex;flex-wrap:wrap;gap:6px' },
                ...(me.permissions || []).map((code) => h('span', { class: 'tag tag-gray plain mono', text: code })))),
    );

    container.append(
        h('div', { class: 'page-head' },
            h('div', {},
                h('h1', { text: '个人资料' }),
                h('p', { text: '维护本人联系方式；账号与角色信息由管理员维护。' }))),
        h('div', { class: 'split' },
            h('div', { class: 'card' },
                h('div', { class: 'card-head' }, h('h2', { text: '基本资料' })),
                h('div', { class: 'card-body' }, form)),
            h('div', {}, info, permList)),
    );

    function openChangePassword() {
        const oldPassword = h('input', { class: 'input', type: 'password', placeholder: '当前密码' });
        const newPassword = h('input', { class: 'input', type: 'password', placeholder: '8-32 位，含字母与数字' });
        const confirmPassword = h('input', { class: 'input', type: 'password', placeholder: '再次输入新密码' });

        const body = h('div', {},
            h('div', { class: 'alert alert-warn', text: '修改成功后全部会话立即失效，需要重新登录。' }),
            h('div', { class: 'field' }, h('label', { class: 'required', text: '当前密码' }), oldPassword),
            h('div', { class: 'field' }, h('label', { class: 'required', text: '新密码' }), newPassword),
            h('div', { class: 'field' }, h('label', { class: 'required', text: '确认新密码' }), confirmPassword));

        modal({
            title: '修改密码',
            body,
            footer: [
                { label: '取消' },
                {
                    label: '确认修改',
                    type: 'primary',
                    onClick: async ({ button }) => {
                        if (!/^(?=.*[A-Za-z])(?=.*\d).{8,32}$/.test(newPassword.value)) {
                            toast('新密码需 8-32 位且同时包含字母与数字', 'error');
                            return false;
                        }
                        if (newPassword.value !== confirmPassword.value) {
                            toast('两次输入的新密码不一致', 'error');
                            return false;
                        }
                        let ok = false;
                        await guard(button, async () => {
                            await api.put('/api/auth/password', {
                                oldPassword: oldPassword.value,
                                newPassword: newPassword.value,
                            });
                            ok = true;
                        });
                        if (ok) {
                            toast('密码已修改，请重新登录', 'success');
                            auth.clear();
                            setTimeout(() => redirectToLogin(), 800);
                        }
                        return ok;
                    },
                },
            ],
        });
    }
}
