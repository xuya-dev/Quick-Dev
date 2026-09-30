# AGENT.md — Quick Dev 框架使用说明（供 AI 编码助手）

> 本文档面向 AI 编码助手（Claude Code / Copilot / Cursor / ZCode 等）。
> 目标：当用户要求你编写使用 **Quick Dev** 框架的业务代码时，你能生成 **正确的**用法。
> 重点是如何 **使用**框架，而非框架内部实现。

## 项目事实（上下文）

- 框架定位：Spring Boot 3 + MyBatis-Plus 快速开发框架（注解式 CRUD + 权限控制）
- 坐标：`dev.xuya:quick-dev-spring-boot-starter:0.4.0`（可选 `dev.xuya:quick-dev-redis-spring-boot-starter`）
- 版本基线：JDK 17+ / Spring Boot 3.5.x / MyBatis-Plus 3.5.12 / Sa-Token 1.42.0 / FastExcel 1.2.0
- 构建验证：`mvn clean verify`（6 模块 reactor；core 36 + autoconfigure 4 + codegen 3 + demo 24 个测试）
- 用户文档：[README.md](../README.md)（中文）/ [README.en.md](../README.en.md) / [完整使用手册](manual.md) / [docs/user-guide.md](user-guide.md)（详细手册）
- 生成业务代码时 **不需要**引入框架内部类以外的任何新依赖（starter 已传递 web/validation/aop/Sa-Token/FastExcel/MP）

## 使用速查：注解清单

### 类级 CRUD：`@QuickCrud`（标注在 Controller 类上，类体可留空）

```java
@QuickCrud(entity = SysUser.class, permission = "sys:user")   // 自动注册 9 个端点
public class SysUserController { }
```

属性：`entity`（必填）· `mapper`（默认按泛型自动查找）· `path`（默认：类上 @RequestMapping > 实体名推导
SysUser→`/sys-user`）· `permission`（前缀，端点权限码 = 前缀+`:list/:detail/:add/:edit/:remove/:import/:export`）
· `loginRequired`（无权限码时仍要求登录）· `includes`/`excludes`（默认注册
PAGE/LIST/COUNT/DETAIL/SAVE/SAVE_BATCH/SAVE_OR_UPDATE/UPDATE/REMOVE；IMPORT/EXPORT/IMPORT_TEMPLATE/TREE 需显式加入）。
全局默认：`quick-dev.crud.default-includes/default-excludes`（注解未显式指定 includes 时生效 / 恒做减法）。

### 方法级注解（标注在 Controller 方法上，方法体留空 `return null`，AOP 接管）

| 注解                                            | 用途                             | 方法参数要求                             |
|-------------------------------------------------|----------------------------------|------------------------------------------|
| `@QuickSave(entity=X.class)`                    | 新增（单个或批量）               | 实体或 `List<实体>`（配 `@RequestBody`） |
| `@QuickUpdate(entity=X.class)`                  | 按 ID 部分更新（null 字段跳过）  | 实体（id 必填）                          |
| `@QuickRemove(entity=X.class)`                  | 删除（支持批量）                 | `String ids`（逗号分隔）/`Number`/`List` |
| `@QuickExport(entity=X.class, translate=false)` | Excel 导出（复用 page 查询条件） | 可选 `HttpServletResponse`               |
| `@QuickImport(entity=X.class)`                  | Excel 导入（校验+事务入库）      | `MultipartFile file`                     |

共同属性 `permission`： **完整权限码**（如 `"product:import"`），空串 = 不鉴权。

### 鉴权注解（任意 Controller 方法/类）

- `@RequiresPerm("a:b")` / `@RequiresPerm({"a:b","c:d"})`（多码 AND）
- `@RequiresRole("admin")` / `@RequiresRole(value={"a","b"}, logical=Logical.OR)`
- `@RequiresLogin`
- `@NoRepeatSubmit(interval = 1000)` 防重复提交（同一用户+接口毫秒窗口）
- `@QuickLog(module="订单", description="创建订单")` 操作审计日志

### 实体侧注解

- `@QueryField(QueryType.EQ|NE|LIKE|GT|GE|LT|LE|IN|BETWEEN)` —— 字段同名请求参数变查询条件
- `@TableField(fill = FieldFill.INSERT)`（createTime/createBy）、`FieldFill.INSERT_UPDATE`（updateTime/updateBy）
  —— **必须加**，否则自动填充不生效（MP 生成 SQL 会跳过未标注的 null 列）
- `@Translate(dict="type")` / `@Translate(enumClass=X.class)` / `@Translate(entity=X.class, field="nickname")`
  —— JSON 序列化时翻译为标签；Excel 导入时反向转回库值
- `@Translate(..., mode = TranslateMode.APPEND, appendField = "deptName")` —— **附加模式**：
  字段保留原值（编辑表单需要 ID），翻译结果写到兄弟字段（缺省「字段名+Name」），
  由 `TranslateAppendModule` 自动装配生效；无翻译结果时兄弟字段输出 null
- 字典枚举需实现 `DictEnum`（`getValue()`/`getLabel()`）
- 树形实体：`parentId` 字段 + `children` 字段（`@TableField(exist = false)`）+ 类级 `@DataScope` 见下
- `@DataScope(column = "dept_id")` —— 行级数据权限（类级标注）

### 分阶段参数校验（0.3.0）

- **新增**：全实体 Bean Validation，分组 `Default + Create`（标记接口 `dev.xuya.core.validation.Create`）
- **修改**：部分更新语义——只校验**提交值非空**的字段，分组 `Default + Update`
  （标记接口 `dev.xuya.core.validation.Update`）；未提交字段不误伤；
  `@NotBlank` 显式提交空串时照常拦截
- **`@QuickRequire(dependField = "stock", dependValue = "0")`** 条件必填：依赖字段值匹配时本字段
  不能为空；可重复、支持 groups；修改阶段仅当依赖字段本次提交非空且匹配才生效
- 关闭修改校验：`quick-dev.crud.update-validate=false`
- 复杂跨字段/依赖数据库状态的校验：写 `CrudHook.beforeSave/beforeUpdate`

## 使用速查：SPI（用户实现的 Bean）

| SPI                             | 方法                                                            | 用途                                   | 不实现时                                                 |
|---------------------------------|-----------------------------------------------------------------|----------------------------------------|----------------------------------------------------------|
| `StpInterface`（Sa-Token 原生） | `getPermissionList` / `getRoleList`                             | Sa-Token 模式的权限/角色数据源         | 权限校验恒 false                                         |
| `UserResolver`                  | `Object getUser(String token)`                                  | 自定义 token→用户（null=未登录）       | 有 Sa-Token 则自动桥接                                   |
| `PermissionChecker`             | `boolean hasPermission(Object user, String code)`               | 自定义权限判定                         | 同上                                                     |
| `RoleChecker`                   | `boolean hasRole(Object user, String role)`                     | 自定义角色判定                         | 同上                                                     |
| `CrudHook`                      | `entityType()` + beforeSave/afterSave/beforeUpdate/afterUpdate/beforeRemove/afterRemove（default 空实现） | 写流程聚合逻辑（多表绑定/缓存刷新） | 无钩子行为 |
| `DictResolver` | `String resolve(String dictType, Object value)` | 字典正向（值→标签） | DictCacheProvider（数据来自 DictLoader） |
| `DictLoader` | `List<DictEntry> loadAll()` | 字典全量数据源（远程/配置中心/自有表），必实现 | 无字典数据（刷新跳过） |
| `DictReverseResolver`           | `Object reverse(String dictType, String label)`                 | 字典反向（标签→值，导入用）            | 同上                                                     |
| `DataScopeResolver`             | `Collection<?> visibleScope(Class, String column, Object user)` | 行级数据范围（null=不限，空=全不可见） | 不过滤                                                   |
| `OperationLogSink`              | `void save(LogRecord)`                                          | 操作日志落地（线程模型自行决定）       | 输出 Slf4j                                               |
| `RepeatSubmitStore`             | `boolean tryAcquire(String key, long ms)`                       | 防重指纹存储                           | 内存；有 Redis 自动切换                                  |

## 使用速查：登录与当前用户

```java
StpUtil.login(user.getId());                  // 登录（推荐 sa-token.token-name 配为 Authorization）
Object loginId = AuthContext.getUser();       // 任意业务代码取当前登录人（Sa-Token 模式下是 loginId）
```

## 常见误用（生成代码时务必避免）

1. **自动填充字段忘加 `@TableField(fill=...)`** —— 最常见错误，填充静默不生效
2. `@QuickCrud` 类上 **不需要**（但可以）加 `@RestController`；空类即可
3. 方法级注解的方法体 **不要写业务逻辑**——AOP 不调用原方法，写了也不执行；需要自定义逻辑就别用注解
4. 排除 LIST 后访问 `/list` 会被 `/{id}` 路由接住返回"记录不存在"——这是预期行为，不是 bug
5. `orderBy` 参数传 **实体属性名**（createTime），不是列名
6. 更新是部分更新 + **部分校验**：只提交需要改的字段即可；未提交字段不校验不更新；
   `@NotBlank` 字段更新时可省略（除非显式提交空值）
7. 需要区分新增/修改行为的约束用 **groups**：`@NotBlank(groups = Create.class)`；
   标记接口 `dev.xuya.core.validation.Create / Update`
8. Sa-Token 模式下 `AuthContext.getUser()` 返回的是 **loginId（String）**，不是用户实体
9. 字典翻译失败会 **保留原值**输出，不会报错——排查时先确认 DictResolver/字典缓存数据
10. Excel 导入单元格填 **中文标签或原始数字**都可以（框架自动反解）；但列名需与 `@ExcelProperty` 值或字段名一致
11. 数据权限对 **按主键的详情/删除不生效**（只过滤查询类接口）；严格隔离需业务层自查
12. 多实例部署时：内置字典缓存/防重内存实现是 **单实例**的，需引入 redis starter 或自定义 Store
13. 唯一索引冲突已被框架转为 **400 友好提示**（不透出 SQL）；需要精确定位冲突字段请在
    service 层先行查重
14. `@Translate` 默认 **替换**原值——编辑表单需要原始 ID 时用
    `mode = TranslateMode.APPEND, appendField = "xxxName"`

## 关键配置（前缀 quick-dev，完整表见 README 附录）

高频项：`quick-dev.crud.update-validate`（修改部分校验开关）、`quick-dev.crud.default-includes/excludes`
（全局默认端点集）、`quick-dev.auth.enabled`（鉴权总开关）、`quick-dev.limits.*`
（导出 10 万/导入 1 万/IN 1000 上限）、`quick-dev.translate.cache-seconds`、
`quick-dev.dict.refresh-interval-seconds`（字典缓存定时刷新）。
Sa-Token 与 Redis 用各自原生配置（`sa-token.*`、`spring.data.redis.*`）。

## 给 Agent 的操作约定（在此仓库内工作时）

- 提交信息格式：`<emoji> <English Type>|<中文类型> <简短中文描述>`，如 `✨ Features|新功能 xxx`
  （全量示例见 [CONTRIBUTING.md](../CONTRIBUTING.md)）
- 改动公共 API 必须同步 `README.md` + `README.en.md` + `CHANGELOG.md`（新条目在前）
- 提交前 `mvn clean verify` 必须全绿；demo 集成测试要求 **每个测试自清理数据**（H2 全局共享）
- core 模块新依赖必须 `optional=true`（由 starter 聚合），否则破坏分层
- 注释/Javadoc 用中文；类内联全限定类名一律改为 import（pointcut 字符串与 javadoc link 除外）
- 发布到 Maven Central：见 [docs/publish-guide.md](publish-guide.md)（`mvn clean deploy -Prelease`）
