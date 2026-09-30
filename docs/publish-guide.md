# 发布到 Maven Central 操作手册

> 维护者指南：如何把 Quick-Dev 发布到 Maven Central（Central Portal，命名空间 `dev.xuya`）。
> 基础设施已全部就绪（`mvn deploy -Prelease` 一条命令），你需要一次性完成
> 第 1~3 步的账号/密钥准备，之后每次发版只需第 5 步。

## 0. 前提与总体流程

- 背景：旧 OSSRH（oss.sonatype.org）已于 2025-06-30 关停，发布统一走
  [Central Portal](https://central.sonatype.com)，使用 `central-publishing-maven-plugin`。
- groupId `dev.xuya` 对应域名 **xuya.dev**，需要在 Portal 完成域名验证（第 2 步）。
  Java 包名（`dev.xuya.core.*`）与 groupId 无强制绑定关系。
- 发布内容：core / autoconfigure / starter / redis-starter / codegen 五个模块
  （demo 演示模块已在 POM 中排除）；每个模块自动附带 sources + javadoc + GPG 签名。

## 1. 注册 Central Portal 账号

1. 打开 <https://central.sonatype.com>，建议直接 **Sign in with GitHub**。
2. 登录后进入 Account 页面备用（第 4 步要生成 User Token）。

## 2. 验证命名空间 dev.xuya（一次性）

1. Portal → **Namespaces** → **Add namespace** → 输入 `dev.xuya`。
2. Portal 会给出一条 **DNS TXT 记录**（主机名与值以页面提示为准），
   到 `xuya.dev` 域名的 DNS 服务商处添加该 TXT 记录。
3. 回到 Portal 点击 **Verify**（DNS 生效可能需要几分钟到几小时）。
4. 验证通过后 `dev.xuya` 状态变为 Verified，之后每次发布无需再验证。

## 3. 准备 GPG 密钥（一次性）

Central 要求所有构件带 GPG 签名，且公钥必须能在公钥服务器上查到。

```bash
# 1) 生成密钥（RSA 4096，姓名/邮箱随意但建议 xuya_dev@qq.com）
gpg --full-generate-key

# 2) 查看指纹（取后 8 位作为 keyID）
gpg --list-keys --keyid-format short

# 3) 发布公钥到至少一个公钥服务器（推荐前两个都发）
gpg --keyserver keyserver.ubuntu.com --send-keys <keyID>
gpg --keyserver openpgp.org --send-keys <keyID>
```

- 私钥千万别泄露；导出备份：`gpg --export-secret-keys -a <keyID> > private.asc`（妥善保管）。
- CI 用：`private.asc` 的内容（含 BEGIN/END 行）就是 GitHub Secrets 里 `GPG_PRIVATE_KEY` 的值。

## 4. 生成 Portal User Token 并配置凭据

1. Portal → Account → **Generate User Token**，得到一对 `用户名 / 密码`（长随机串）。
2. 本机发布：写入 `~/.m2/settings.xml`（Windows 在 `C:\Users\<你>\.m2\settings.xml`）：

```xml
<settings>
  <servers>
    <server>
      <id>central</id>              <!-- 必须叫 central，与父 POM publishingServerId 对应 -->
      <username>Portal Token 的用户名</username>
      <password>Portal Token 的密码</password>
    </server>
  </servers>
</settings>
```

3. CI 发布：在 GitHub 仓库 Settings → Secrets and variables → Actions 配置 4 个 Secret：

| Secret 名         | 值                                   |
|-------------------|--------------------------------------|
| `CENTRAL_USERNAME` | Portal Token 用户名                  |
| `CENTRAL_PASSWORD` | Portal Token 密码                    |
| `GPG_PRIVATE_KEY`  | GPG 私钥导出的 ASCII（private.asc）  |
| `GPG_PASSPHRASE`   | GPG 私钥口令                         |

## 5. 发布（每次发版）

### 本地发布

```bash
# （可选）本地只验证产物，不出签名、不发布
mvn clean package -Prelease -Dgpg.skip=true

# 正式发布：构建 + 测试 + 签名 + 上传
mvn clean deploy -Prelease
```

上传并校验通过后，因父 POM 配置了 `autoPublish=false`，需要到
Portal → **View Deployments** → 选中本次部署 → 检查无误后点 **Publish**；
随后组件会在约 10 分钟~2 小时内同步到中央仓库索引，之后即可搜索/下载，
且会收到 Portal 的确认邮件。

> 熟练后可以把父 POM 中 `autoPublish` 改为 `true`，校验通过即自动发布；
> 也可以直接跑 GitHub Actions 的 **Release to Maven Central** 工作流（手动触发）。

### CI 发布

GitHub 仓库页 → Actions → **Release to Maven Central** → Run workflow。
前提：第 4 步的 4 个 Secrets 已配置。

## 6. 发布后收尾

```bash
git tag v0.2.0 && git push origin v0.2.0     # 打版本标签
```

再在 GitHub 上基于 tag 创建 Release 并附 CHANGELOG 说明。
下一个版本开发时把 6 个 POM 的 `<version>` 一起升级（如 0.3.0）。

## 7. 常见问题

| 现象 | 处理 |
|------|------|
| `Missing signature` / `No public key` | 公钥未发布到公钥服务器，或签名用的 keyID 与公钥不一致；重新 `--send-keys` |
| `Namespace not verified` | `dev.xuya` 命名空间未通过验证，回第 2 步 |
| 401 Unauthorized | settings.xml 的 `server.id` 必须是 `central`；Token 是 Portal 生成的 User Token（不是网站登录密码） |
| Javadoc 校验失败 | 本地先 `mvn package -Prelease -Dgpg.skip=true` 看详情；当前已配 `doclint=none`，一般不会失败 |
| 部署校验失败想重传 | Portal 删掉失败的 deployment，修复后直接重跑 `mvn clean deploy -Prelease`（版本号不变即可） |
| 本机 Windows 提示 gpg 不存在 | 安装 Gpg4win 或 `winget install GnuPG.GnuPG` |
