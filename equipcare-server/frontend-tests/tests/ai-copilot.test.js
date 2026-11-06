/**
 * AI 副驾前端单元测试：轻量 Markdown 渲染（含 XSS 转义）。
 */
import { describe, expect, it } from 'vitest';
import { renderMarkdownLite } from '@static/pages/ai-copilot.js';

describe('renderMarkdownLite', () => {
    it('渲染标题、列表、加粗与行内代码', () => {
        const md = '## 可能的根因分析\n- 轴承**磨损**严重\n- 使用 `M8` 扳手';
        const html = renderMarkdownLite(md);
        expect(html).toContain('<h3 class="ai-h">');
        expect(html).toContain('<ul class="ai-ul">');
        expect(html).toContain('<strong>磨损</strong>');
        expect(html).toContain('<code class="ai-inline">M8</code>');
    });

    it('渲染代码块', () => {
        const html = renderMarkdownLite('```\ntorque = 45 N·m\n```');
        expect(html).toContain('<pre class="ai-code">');
        expect(html).toContain('torque = 45 N·m');
    });

    it('转义 HTML 防注入（模型输出不可信）', () => {
        const html = renderMarkdownLite('<script>alert(1)</script> & <img src=x onerror=alert(1)>');
        expect(html).not.toContain('<script>');
        expect(html).not.toContain('<img');
        expect(html).toContain('&lt;script&gt;');
        expect(html).toContain('&amp;');
    });

    it('空输入输出空串', () => {
        expect(renderMarkdownLite(null)).toBe('');
        expect(renderMarkdownLite('')).toBe('');
    });

    it('有序列表与普通段落', () => {
        const html = renderMarkdownLite('1. 第一步\n2. 第二步\n\n普通段落');
        expect(html).toContain('<li>第一步</li>');
        expect(html).toContain('<li>第二步</li>');
        expect(html).toContain('<p class="ai-p">普通段落</p>');
    });
});
