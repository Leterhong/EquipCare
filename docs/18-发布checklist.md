# 发布 checklist（转公开 / 新版本 tag）

## 发布前
- [ ] `mvn clean test` 全绿（含 ArchUnit 架构守护）
- [ ] `cd equipcare-web && npm run test && npm run build` 全绿
- [ ] `mvn verify`（可选，需 MySQL+Redis；9 个 IT）
- [ ] CHANGELOG.md 更新本版本条目，确认无遗留 P0/P1
- [ ] `grep -rn "TODO\|FIXME" equipcare-server/equipcare-*/src` 清点未竟事项
- [ ] 确认仓库内无敏感信息：`.env`/日志/演示数据/本机路径（`git log -p` 抽查）
- [ ] 依赖漏洞：Dependabot 告警清零或已知确认

## 转 Public 步骤
1. Settings → General → Danger Zone → Change visibility → Public
2. Settings → General：About 描述 + Topics（见 README 顶部简介）
3. Settings → Pages → Source 选 GitHub Actions → 手动跑 Pages 工作流
   → 展示站 https://leterhong.github.io/EquipCare/ 上线
4. 发布 Release：Tag v0.2.0 + CHANGELOG 对应段落作为说明
5. 社区文件确认：LICENSE / CONTRIBUTING / SECURITY / Issue 模板可见

## 发布后
- [ ] Actions 三流水线（CI / Pages / e2e）全绿
- [ ] README 徽章（CI 状态/stars）正确显示
- [ ] 提交 awesome-cmms / awesome-java 清单收录 PR
- [ ] 发架构文（素材：docs/12、docs/15、docs/17）
