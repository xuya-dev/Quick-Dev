# 贡献指南（Contributing Guide）

感谢你关注 Quick Dev！欢迎通过 Issue 与 Pull Request 参与贡献。

[English](CONTRIBUTING.en.md) | 简体中文

## 环境要求

- JDK 17+（构建目标 17，兼容 21）
- Maven Wrapper 已随仓库提供（`mvnw`/`mvnw.cmd`），无需单独安装 Maven
- Git

## 快速开始

```bash
git clone https://github.com/xuya-dev/Quick-Dev.git
cd Quick-Dev
./mvnw clean verify     # Windows 用 mvnw.cmd；Wrapper 会自动下载指定版本的 Maven
```

## 项目结构

| 模块                                  | 职责                                                                |
|---------------------------------------|---------------------------------------------------------------------|
| `quick-dev-core`                      | 核心库：注解 / CRUD 引擎 / 权限 / 翻译 / Excel（依赖全部 optional） |
| `quick-dev-spring-boot-autoconfigure` | Spring Boot 自动配置                                                |
| `quick-dev-spring-boot-starter`       | 主 Starter（使用方唯一需要引入的依赖）                              |
| `quick-dev-redis-spring-boot-starter` | 可选 Redis 支持                                                     |
| `quick-dev-demo`                      | 演示应用（H2 内存库，含全部集成测试）                               |

## 提交 Issue

- **Bug 报告**：请使用 Bug Report 模板，附复现步骤、Spring Boot / MyBatis-Plus / Quick Dev 版本
- **功能建议**：请描述使用场景与期望 API，最好附带示例代码

## 提交 Pull Request

1. Fork 仓库并从 `main` 切出特性分支（`feat/xxx`、`fix/xxx`）
2. 保证 `mvn clean verify` 通过；新功能请附带测试（core 单测或 demo 集成测试）
3. 遵守提交信息格式：`<emoji> <类型>|<Type> <简短中文描述>`，例如：

   ```
   ✨ Features|新功能 新增TREE树形查询接口
   🐛 Bug Fixes|Bug 修复 修复分页参数为空时的NPE
   ♻️ Refactoring|代码重构 内联全限定类名统一改为import导入
   📝 Documentation|文档 补充配置项总表
   ✅ Tests|测试 补充TreeBuilder单元测试
   🔧 Chores|杂务 升级依赖版本
   ```

4. 同步更新 `CHANGELOG.md`（新条目在前）
5. 公共 API 变更请同时更新 `README.md` 与 `README.en.md`

## 代码风格

- Java 17 语法，禁止在 core 中引入强依赖（新依赖必须 `optional`，由 starter 聚合）
- 注释与 Javadoc 使用中文；对外 API 保持清晰示例
- 单个 PR 控制在一件事内，便于 review

## 开源协议

提交即表示你同意贡献内容遵循 [Apache License 2.0](LICENSE) 授权。
