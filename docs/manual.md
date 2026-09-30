# Quick Dev 完整使用手册

> 版本：0.4.0 · 适用于 Spring Boot 3.5.x / JDK 17+
> 本手册是框架的**完整**使用说明：从安装到每个注解、每个配置、每条边界约定。
> 快速入门可先读 [README](../README.md)；本手册覆盖全部细节。

---

## 目录

1. [框架概述](#1-框架概述)
2. [环境与安装](#2-环境与安装)
3. [五分钟快速开始](#3-五分钟快速开始)
4. [注解式 CRUD](#4-注解式-crud)
5. [查询与排序](#5-查询与排序)
6. [登录鉴权与权限码](#6-登录鉴权与权限码)
7. [分阶段参数校验](#7-分阶段参数校验)
8. [字段翻译](#8-字段翻译)
9. [字典体系](#9-字典体系)
10. [CRUD 生命周期钩子](#10-crud-生命周期钩子)
11. [行级数据权限](#11-行级数据权限)
12. [Excel 导入导出](#12-excel-导入导出)
13. [操作日志与防重复提交](#13-操作日志与防重复提交)
14. [统一响应与异常](#14-统一响应与异常)
15. [配置总表](#15-配置总表)
16. [发布到 Maven Central](#16-发布到-maven-central)
17. [常见问题 FAQ](#17-常见问题-faq)

---

## 1. 框架概述

Quick Dev 解决一个问题：**在 Controller 上贴一个注解，获得一组带权限控制的 CRUD 接口**。

| 模块 | 说明 |
|------|------|
| `quick-dev-core` | 核心库（注解 / CRUD 引擎 / 校验 / 翻译 / 权限），依赖全部 optional |
| `quick-dev-spring-boot-autoconfigure` | 自动配置（装配 + Properties） |
| `quick-dev-spring-boot-starter` | **使用方唯一需要引入的依赖**（聚合 web/validation/aop/Sa-Token/FastExcel/MP） |
| `quick-dev-redis-spring-boot-starter` | 可选：Sa-Token 会话入 Redis + 防重原子实现 |
| `quick-dev-codegen` | 代码生成器（表结构 → 三件套，零依赖纯 JDK） |
| `quick-dev-demo` | H2 演示应用（含 0.4.0 全特性示例） |

设计原则：**框架不查任何业务数据库**（字典/日志数据源由使用方提供）；**约定优于配置**（零注解可用，注解可覆盖）；**可整体替换**（每个能力都有 SPI 逃生口）。

## 2. 环境与安装

```xml
<dependency>
    <groupId>dev.xuya</groupId>
    <artifactId>quick-dev-spring-boot-starter</artifactId>
    <version>0.4.0</version>
</dependency>
<!-- 可选：Redis 支持 -->
<dependency>
    <groupId>dev.xuya</groupId>
    <artifactId>quick-dev-redis-spring-boot-starter</artifactId>
    <version>0.4.0</version>
</dependency>
<!-- 自备数据库驱动，如 mysql-connector-j / h2 -->
```

要求：JDK 17+、Spring Boot 3.5.x。中央仓库地址：<https://central.sonatype.com/artifact/dev.xuya/quick-dev-spring-boot-starter>

## 3. 五分钟快速开始

```java
// 1) 实体
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    @QueryField(QueryType.LIKE)
    private String username;      // ?username=张 -> LIKE '%张%'
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;  // 自动填充
    // getter/setter
}

// 2) Mapper
public interface SysUserMapper extends BaseMapper<SysUser> { }

// 3) Controller：一行注解，9 个端点
@QuickCrud(entity = SysUser.class, permission = "sys:user")
public class SysUserController { }
```

启动即获得：`GET /sys-user/page`、`GET /sys-user/list`、`GET /sys-user/count`、
`GET /sys-user/{id}`、`POST /sys-user`、`POST /sys-user/batch`、`POST /sys-user/save-or-update`、
`PUT /sys-user`、`DELETE /sys-user/{ids}`（未配置 permission 前缀时接口开放）。

## 4. 注解式 CRUD

### 4.1 @QuickCrud 属性

| 属性 | 说明 |
|------|------|
| `entity` | 必填，实体类 |
| `mapper` | 默认按实体泛型自动查找 |
| `path` | 默认：类上 @RequestMapping > 实体名推导（SysUser→`/sys-user`） |
| `permission` | 权限前缀；端点权限码 = 前缀 + `:list/:detail/:add/:edit/:remove/:import/:export` |
| `loginRequired` | 无权限码时是否仍要求登录 |
| `includes` / `excludes` | 注册哪些操作；IMPORT/EXPORT/IMPORT_TEMPLATE/TREE 需显式加入 |

全局默认端点集（注解未显式指定 includes 时生效）：

```yaml
quick-dev:
  crud:
    default-includes: PAGE,LIST,DETAIL,SAVE,UPDATE,REMOVE
    default-excludes: SAVE_BATCH,SAVE_OR_UPDATE
```

### 4.2 方法级注解（方法体留空，AOP 接管）

| 注解 | 用途 | 参数 |
|------|------|------|
| `@QuickSave(entity=X)` | 新增（单个/批量） | 实体或 `List<实体>` |
| `@QuickUpdate(entity=X)` | 部分更新 | 实体（id 必填） |
| `@QuickRemove(entity=X)` | 删除（批量） | `String ids` / `Number` / `List` |
| `@QuickExport(entity=X, translate=false)` | Excel 导出 | 可选 `HttpServletResponse` |
| `@QuickImport(entity=X)` | Excel 导入 | `MultipartFile file` |

### 4.3 写操作语义（0.3.0 起）

- **全部写操作纳入事务**：saveBatch/删除具备原子性；
- **保护字段**：逻辑删除字段与 createBy/updateBy 在写路径被框架强制清空，
  客户端无法越权删除/复活记录、无法伪造审计归属；
- **唯一索引冲突**：自动转 400 友好提示（不透出 SQL）。

## 5. 查询与排序

| 参数 | 说明 |
|------|------|
| `current` / `size` | 分页，默认 1/10，size 上限 1000 |
| 与实体属性同名 | 生成条件，方式由 `@QueryField` 决定，值自动转换类型 |
| `orderBy` | 排序属性名，**逗号分隔支持多列**（白名单防注入，非表字段自动跳过） |
| `order` | 与 orderBy 逐列对应 `desc`/`asc`，缺省 asc |

```java
@QueryField(QueryType.EQ|NE|LIKE|GT|GE|LT|LE|IN|BETWEEN)   // 未标注默认 EQ
@QueryField(QueryType.LIKE, escapeWildcard = true)          // 0.4.0：LIKE 按字面量匹配
private String username;
```

- IN：`?type=1,2,3`（数量受 `limits.in-max-size` 限制，超出 400）
- BETWEEN：`?createTime=2026-01-01T00:00:00,2026-12-31T23:59:59`
- 多列排序：`?orderBy=createTime,id&order=desc,asc`

## 6. 登录鉴权与权限码

内置 Sa-Token 桥接：登录用 `StpUtil.login(userId)`，权限/角色数据实现
`StpInterface` 提供；框架自动接管 `@RequiresPerm` / `@RequiresRole` / `@RequiresLogin`
与 `@QuickCrud` 权限码校验（未登录 401、无权限 403，真实 HTTP 状态码）。

```java
@RequiresPerm("sys:user:add")
@RequiresRole(value = "admin", logical = Logical.OR)
@PostMapping
public R<Object> create(@RequestBody SysUser user) { ... }
```

- 0.4.0 起 **URL 传 token 默认禁用**（防泄露访问日志），需要时 `auth.token-param: token` 显式开启
- 防重复提交指纹对 token 做 **哈希存储**，不落明文凭据

## 7. 分阶段参数校验

| 阶段 | 校验范围 | 分组 |
|------|----------|------|
| 新增（POST / save-or-update 新增分支） | 全实体 | `Default + Create` |
| 修改（PUT / save-or-update 修改分支 / @QuickUpdate） | 仅提交值非空的字段 | `Default + Update` |

```java
public class Goods {
    @NotBlank(groups = Create.class)             // 仅新增必填
    private String name;
    @Size(max = 200)                             // 两阶段都校验（修改时只对提交值生效）
    private String remark;
    @QuickRequire(dependField = "stock", dependValue = "0",
            groups = {Create.class, Update.class},
            message = "售罄时必须填写下架原因")
    private String reason;                        // stock=0 时 reason 必填
}
```

- 分组标记接口：`dev.xuya.core.validation.Create / Update`，兼容标准 jakarta groups
- `@QuickRequire` 可重复；修改阶段仅当依赖字段本次提交非空且匹配才生效
- Excel 导入同样应用 Create 组 + `@QuickRequire`
- 开关：`quick-dev.crud.update-validate=false` 关闭修改校验

## 8. 字段翻译

三种模式 + 附加模式，序列化期零侵入：

```java
@Translate(dict = "order_status")                          // 字典（DictResolver SPI）
private Integer status;                                    // 1 -> "已支付"

@Translate(enumClass = OrderStatus.class)                  // 枚举（实现 DictEnum）
private Integer type;                                      // 1 -> "普通商品"

@Translate(entity = SysUser.class, field = "nickname")     // 关联（按主键查）
private String createBy;                                   // "1" -> "管理员"

@Translate(entity = SysDept.class, field = "deptName",
        mode = TranslateMode.APPEND, appendField = "deptName")
private Long deptId;   // APPEND：deptId 保留原值 + 附带 deptName="研发部门"
```

- TTL 本地缓存（`translate.cache-seconds`，默认 60，0 关闭）
- 关联翻译可注册 **`TranslateSource` SPI** 优先走内存（缓存优先，miss 才回源）：

```java
@Component
public class UserNicknameSource implements TranslateSource {
    @Override
    public String translate(Class<?> entityType, String field, Object id) {
        if (entityType == SysUser.class && "nickname".equals(field)) {
            return UserCache.nicknameOf(id);   // 全量内存，零查库
        }
        return null;                            // miss 回源
    }
}
```

## 9. 字典体系

框架不查任何字典库，数据由使用方提供（`DictLoader` SPI），全量驻内存：

```java
@Component
public class MyDictLoader implements DictLoader {
    @Override
    public List<DictEntry> loadAll() {
        return remoteClient.fetchAll().stream()
                .map(d -> new DictEntry(d.type(), d.value(), d.label())).toList();
    }
}
```

- 刷新：`POST /quick-dev/dict/refresh`（需 `dict:refresh` 权限）或
  `quick-dev.dict.refresh-interval-seconds` 定时刷新；编程式 `dictCacheService.refresh()`
- **标准 JSON 上传（零查库）**：

```java
dictCacheService.replaceByJson("""
    [{"type":"sys_yes_no","value":"Y","label":"是"},
     {"type":"sys_yes_no","value":"N","label":"否"}]
    """);
```

- Excel 导入时字典标签自动反解为库值（枚举自动、关联自动、字典走 `DictReverseResolver` SPI）

## 10. CRUD 生命周期钩子

```java
@Component
public class GoodsHook implements CrudHook {
    @Override public Class<?> entityType() { return Goods.class; }

    @Override public void beforeSave(Object entity) { /* 事务内：校验/规范化，异常回滚 */ }
    @Override public void afterSave(Object entity)  { /* 提交后：刷缓存/发事件，失败仅告警 */ }
    // beforeUpdate/afterUpdate/beforeRemove(List ids)/afterRemove(List ids) 同理
}
```

| 钩子 | 时机 | 异常语义 |
|------|------|----------|
| beforeSave / beforeUpdate / beforeRemove | 事务内 | 抛异常回滚本次写、请求失败 |
| afterSave / afterUpdate / afterRemove | 事务提交后 | 失败仅 WARN，不影响响应 |

修改阶段的 `@QuickRequire` 与 beforeUpdate 钩子仅做"载荷级"判断；
依赖数据库完整状态的校验请在 Hook 中自行查询。

## 11. 行级数据权限

```java
@DataScope(column = "dept_id")     // 实体类级标注
```

实现 `DataScopeResolver` SPI 返回当前用户可见范围：`null`=不限、集合=IN 条件、
**空集合=一行都看不到**（框架用恒假条件表达，不会产生非法 SQL）。
作用范围：page/list/count/tree/export；按主键的 detail/remove 不做行级过滤（边界）。

## 12. Excel 导入导出

- 导出：`@QuickExport` / CrudOp.EXPORT，复用查询条件，支持 `translate` 翻译列；
  超出 `limits.export-max-rows` 截断并告警
- 导入：`@QuickImport` / CrudOp.IMPORT，表头匹配 `@ExcelProperty` 值或字段名；
  单元格填中文标签或原始值均可（自动反解）；逐行校验（Create 组 + `@QuickRequire`），
  任一行失败整体不入库并返回前 10 行明细
- 解析阶段即强制 `limits.import-max-rows` 上限（含表头），超大文件不会耗尽内存
- 模板下载：CrudOp.IMPORT_TEMPLATE

## 13. 操作日志与防重复提交

```java
@QuickLog(module = "订单管理", description = "创建订单")
@NoRepeatSubmit(interval = 2000)
@PostMapping("/order")
public R<Object> create(@RequestBody Order order) { ... }
```

- 日志记录模块/操作人/URI/入参（截断 2000）/结果码/耗时/异常；
  落地由 `OperationLogSink` SPI 决定（默认 Slf4j，写库自实现并自行决定线程模型）
- 防重指纹 = 用户标识（哈希存储）+ 方法 + URI；被拒绝的重试**不续期窗口**；
  匿名按 IP；集群引入 redis starter 自动切 Redis 原子实现

## 14. 统一响应与异常

```json
{ "code": 200, "msg": "success", "data": { }, "success": true }
```

| 场景 | 返回 |
|------|------|
| 校验失败 / 业务异常 | HTTP 200 + `code=400/500` |
| 唯一键冲突 | HTTP 200 + `code=400`（不透出 SQL） |
| 未登录 / 无权限 | HTTP 401 / 403 |
| 未预期异常 | `error-detail=true` 透出详情（默认 false 返回"系统繁忙"） |

## 15. 配置总表（前缀 quick-dev）

| 配置 | 默认 | 说明 |
|------|------|------|
| `enabled` | true | @QuickCrud 总开关 |
| `error-detail` | false | 未预期异常是否透出详情（调试期可开） |
| `db-type` | - | 分页方言 |
| `auth.enabled` / `token-header` / `token-param` | true / Authorization / -（禁用） | 鉴权 |
| `crud.update-validate` | true | 修改部分校验开关 |
| `crud.default-includes` / `default-excludes` | - | 全局默认端点集 |
| `method-op.enabled` / `auto-fill.enabled` / `repeat-submit.enabled` | true | 能力开关 |
| `log.enabled` | true | @QuickLog 开关 |
| `translate.enabled` / `cache-seconds` | true / 60 | 翻译开关/缓存 |
| `dict.refresh-endpoint-enabled` / `refresh-path` / `refresh-interval-seconds` | true / /quick-dev/dict/refresh / 0 | 字典缓存刷新 |
| `limits.export-max-rows` / `import-max-rows` / `in-max-size` | 100000 / 10000 / 1000 | 防御上限 |

## 16. 发布到 Maven Central

维护者发布见 [publish-guide.md](publish-guide.md)：命名空间验证（dev.xuya ← xuya.dev DNS TXT）、
GPG 密钥、`mvn clean deploy -Prelease` + Portal 点 Publish。

## 17. 常见问题 FAQ

1. **自动填充不生效** → 字段必须加 `@TableField(fill = ...)`（MP 生成 SQL 时会跳过未标注的 null 列）
2. **更新想强制必填** → 部分更新语义下"未提交=不更新"，用 `groups = Update.class` 的约束
   校验显式提交的值；要求"字段必须始终存在"请用全量更新约定
3. **翻译不生效** → 先确认 DictResolver/字典缓存数据；翻译失败保留原值不报错
4. **修改字典后接口仍返回旧标签** → 刷新字典缓存（refresh 会同时清空翻译缓存）
5. **多实例部署** → 字典缓存/防重内存实现是单实例的：引入 redis starter 或自定义 Store/定时刷新
6. **`/list` 被接住返回"记录不存在"** → 排除 LIST 后 `/{id}` 路由接住的预期行为
7. **数据权限对 detail/remove 不生效** → 已知边界，严格隔离需业务层自查
8. **客户端伪造 createBy / 提交 delFlag** → 0.4.0 起写路径强制清空，无需处理
9. **Excel 列名对不上** → 列名需与 `@ExcelProperty` 值或字段名一致
10. **实体上没有的字段传了参数** → 自动忽略，不会生成非法 SQL

---

> 手册版本随框架发布更新；框架内部实现细节见各模块 Javadoc。
