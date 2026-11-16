# 发布准备指南

本指南说明如何通过 GitHub 标签自动发布 **Agent Terminal** 插件版本。

## 发布前检查清单

### 1. 更新版本与变更说明（推荐）

- [ ] 更新 `build.gradle` 中的默认版本号（`version`）
- [ ] 更新 `src/main/resources/META-INF/plugin.xml` 中的 `<version>` 与 `<change-notes>`
- [ ] 如有新功能或变更，同步更新 `README.md`

> **提示**：CI 发布工作流会自动从标签名（如 `v1.3.1` 去掉前缀 `v`）通过 `-PpluginVersion` 覆盖构建版本号，因此即使直接在 GitHub 网页端打标签，产出的安装包和内置 `plugin.xml` 版本号也会与标签保持一致。但仍建议在发版前同步更新源码中的版本号和 `<change-notes>`。

### 2. 本地验证构建

```bash
# 校验配置、插件结构并构建安装包
./gradlew verifyPluginProjectConfiguration verifyPluginStructure buildPlugin

# 在沙盒 IDE 中运行测试（可选）
./gradlew runIde
```

构建完成后，安装包会生成在 `build/distributions/` 目录下。

## 自动发布流程（GitHub Actions）

仓库已配置 [.github/workflows/release.yml](workflows/release.yml)，支持以下任意一种方式触发自动发布：

### 方式一：本地打标签并推送

```bash
git tag -a v1.3.1 -m "Release v1.3.1"
git push origin v1.3.1
```

### 方式二：直接在 GitHub 网页端创建标签或 Release

- **创建标签**：在仓库的 **Tags / Branches** 下拉菜单中新建 `v*` 格式标签（例如 `v1.3.1`），工作流会自动创建对应 Release、生成变更说明并上传 `.zip` 安装包。
- **创建 Release**：在 **Releases → Draft a new release** 中填写标题与发布说明，选择新建 `v*` 标签并发布，工作流会自动向该 Release 上传并附带构建好的 `.zip` 安装包（保留你填写的发布说明）。
- **手动触发工作流**：在 **Actions → Release Plugin → Run workflow** 中输入标签名（例如 `v1.3.1`）即可触发构建与发布。

> 预发布版本（标签包含 `-`，如 `v1.4.0-beta.1`）会自动标记为 GitHub Pre-release，且不会自动推送到 JetBrains Marketplace 正式渠道。

## 自动发布到 JetBrains Marketplace（可选）

如果希望打标签时同步上架 JetBrains 插件市场：

1. 前往 [JetBrains Marketplace Tokens](https://plugins.jetbrains.com/author/me/tokens) 生成发布令牌。
2. 在 GitHub 仓库的 **Settings → Secrets and variables → Actions** 中新增 Repository Secret：
   - Name: `PUBLISH_TOKEN`
   - Value: 你的发布令牌
3. 配置完成后，推送正式版标签（不含 `-`）时，工作流会在创建 GitHub Release 后自动执行 `./gradlew publishPlugin`。
4. 如需仅在本地手动发布：
   ```bash
   export PUBLISH_TOKEN=your_token_here
   ./gradlew publishPlugin
   ```

## 版本号规范

遵循语义化版本（Semantic Versioning）：
- **主版本 (`X.0.0`)**：不兼容的重大变更
- **次版本 (`1.X.0`)**：向下兼容的新功能
- **修订版本 (`1.0.X`)**：问题修复与细节优化
