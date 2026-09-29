# Quick Dev

基于 **Spring Boot 3 + MyBatis-Plus** 的快速开发框架（标准 Spring Boot Starter 结构）：引入一个 `quick-dev-spring-boot-starter` 依赖，在 Controller 上贴一个 `@QuickCrud` 注解，即可自动获得一组带权限控制的 CRUD 接口。

```java
@QuickCrud(entity = SysUser.class, permission = "sys:user")
public class SysUserController {
    // 空的，不需要写任何方法
}
```

启动后自动注册 8 个接口：

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | `/sys-user/page` | `sys:user:list` | 分页查询（current/size + 动态条件 + 排序） |
| GET | `/sys-user/list` | `sys:user:list` | 列表查询（不分页） |
| GET | `/sys-user/count` | `sys:user:list` | 按条件统计数量 |
| GET | `/sys-user/{id}` | `sys:user:detail` | 详情 |
| POST | `/sys-user` | `sys:user:add` | 新增（支持 Bean Validation 校验） |
| POST | `/sys-user/batch` | `sys:user:add` | 批量新增（JSON 数组，逐条校验 + Db.saveBatch） |
| PUT | `/sys-user` | `sys:user:edit` | 修改（按 ID，null 字段不更新） |
| DELETE | `/sys-user/{ids}` | `sys:user:remove` | 删除，`ids` 逗号分隔支持批量 |

可选开启（加入 `includes`）：`POST {base}/import` Excel 导入（`:import`）、`GET {base}/export` Excel 导出（`:export`）、`GET {base}/import-template` 下载导入模板（`:import`）、`GET {base}/tree` 树形查询（`:list`，实体需声明 `parentId` 与 `children` 字段，children 标注 `@TableField(exist = false)`，parentId 为 null 或 0 视为根）。

## 特性

- **一个注解完成 CRUD**：`@QuickCrud` 标注在 Controller 上，启动时通过 `RequestMappingHandlerMapping` 运行期注册端点（Spring 官方支持的方式），与手写接口完全共存
- **方法级注解**：`@QuickSave` / `@QuickUpdate` / `@QuickRemove` / `@QuickExport` / `@QuickImport` 直接标在方法上，方法体留空由框架 AOP 接管——不必把整个类交给 `@QuickCrud`
- **内置 Sa-Token**：classpath 自动桥接登录态与权限校验（`StpUtil.login` 登录、实现 `StpInterface` 供权限数据），零配置打通 `@RequiresPerm` / `@QuickCrud` 权限码
- **Excel 导入导出**：FastExcel 封装，导出复用查询条件、导入自动校验 + 事务批量入库
- **注解式权限控制**：`@RequiresPerm` / `@RequiresLogin` 可用在任何 Controller 上；`@QuickCrud` 生成的接口按 `权限前缀:操作` 约定自动鉴权；方法级注解按 `permission` 完整权限码鉴权
- **可替换权限实现**：内置 Sa-Token 之外，也可自定义 `UserResolver`（token→用户）与 `PermissionChecker`（用户→权限码）SPI 对接任意体系
- **声明式查询条件**：实体字段标注 `@QueryField(LIKE/GT/IN/BETWEEN/...)`，同名请求参数自动变查询条件并做类型转换
- **时间与操作人自动填充**：`createTime`/`updateTime` + `createBy`/`updateBy`（当前登录人）新增/修改时自动填充（字段加 `@TableField(fill = ...)` 即可，见下文）
- **字段翻译（VO Translation）**：`@Translate` 标注在字段上，JSON 输出时自动把 ID/状态码翻译为可读文本（字典、枚举、关联表三种模式），带 TTL 缓存；**Excel 导入时反向自动转换**（中文标签 -> 库值）；**字典在数据库表时零代码接入**（内置 `JdbcDictProvider`，正反双向）
- **行级数据权限**：`@DataScope(column = "dept_id")` 标注实体，分页/列表/统计/树/导出自动按 `DataScopeResolver` 返回的可见范围过滤（"只看本部门"）
- **可选 Redis**：引入 `quick-dev-redis-spring-boot-starter` 后，Sa-Token 登录态/权限缓存到 Redis（多实例共享、重启不失效），防重复提交自动切换为 Redis 原子实现
- **树形查询**：`CrudOp.TREE` 一行注解输出部门/菜单/分类树（实体声明 `parentId` + `children` 即可）
- **防重复提交**：`@NoRepeatSubmit(interval)` 按用户+接口指纹拦截重复点击
- **操作日志**：`@QuickLog` 记录操作人/入参/结果/耗时，`OperationLogSink` SPI 异步落库即可
- **统一响应与异常**：`R<T>` 结构 + 全局异常处理（未登录 401、无权限 403、参数/校验错误 400）
- MyBatis-Plus 既有能力全部可用：逻辑删除、乐观锁、多租户、`@TableName` 映射等

## 模块结构

```
quick-dev
├── quick-dev-core                      核心库：注解 / CRUD 引擎 / 权限 / R / 异常（依赖全部 optional）
├── quick-dev-spring-boot-autoconfigure 自动配置模块（Properties + AutoConfiguration + Redis 条件装配）
├── quick-dev-spring-boot-starter       ★ 使用方唯一需要引入的依赖（聚合 core + web + validation + MyBatis-Plus + 分页插件）
├── quick-dev-redis-spring-boot-starter 可选 Redis 支持（Sa-Token 缓存 + 防重复提交 Redis 原子实现）
└── quick-dev-demo                      演示应用（H2 内存库 + 内置账号，可直接跑）
```

## 快速开始

### 1. 引入 Starter（唯一需要的一步依赖）

```xml
<dependency>
    <groupId>dev.xuya</groupId>
    <artifactId>quick-dev-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

starter 会传递引入：`quick-dev-core` + 自动配置、`spring-boot-starter-web`、`spring-boot-starter-validation`、`spring-boot-starter-aop`、`sa-token-spring-boot3-starter`、`fastexcel`、`mybatis-plus-spring-boot3-starter`、`mybatis-plus-jsqlparser`（分页插件）。只需再自备一个数据库驱动（如 `mysql-connector-j`、`h2`）。

### 2. 定义实体与 Mapper

```java
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;                    // 默认精确匹配
    @QueryField(QueryType.LIKE)
    private String nickname;                    // ?nickname=张 -> LIKE '%张%'
    @QueryField(QueryType.IN)
    private Integer type;                       // ?type=1,2 -> IN (1,2)
    @QueryField(QueryType.BETWEEN)
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;           // ?createTime=2026-01-01T00:00:00,2026-12-31T23:59:59 -> BETWEEN；新增时自动填充
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;           // 新增/修改时自动填充
    // getter/setter 略
}

public interface SysUserMapper extends BaseMapper<SysUser> { }
```

> 自动填充是框架内置的 `MetaObjectHandler`（按属性名 `createTime`/`updateTime`/`createBy`/`updateBy` 约定，
> 已有值不覆盖；`createBy`/`updateBy` 取当前登录人 loginId，须为 String 类型，未登录不填），
> 字段必须加 `@TableField(fill = ...)`，否则 MyBatis-Plus 生成 SQL 时会跳过 null 列导致填充不生效。
> 不想用可设置 `quick-dev.auto-fill.enabled=false`，或注册自己的 `MetaObjectHandler` Bean 覆盖。

### 3. 一个注解出接口

```java
// path 不写：优先取类上 @RequestMapping，否则实体名推导（SysUser -> /sys-user）
// permission 不写：接口开放（可用 loginRequired = true 仅要求登录）
// includes/excludes：只注册/排除部分操作；IMPORT/EXPORT/IMPORT_TEMPLATE 默认不注册，可加入 includes 开启
@QuickCrud(entity = SysUser.class, permission = "sys:user",
        excludes = CrudOp.LIST,
        includes = {CrudOp.PAGE, CrudOp.COUNT, CrudOp.DETAIL, CrudOp.SAVE, CrudOp.SAVE_BATCH,
                CrudOp.UPDATE, CrudOp.REMOVE, CrudOp.IMPORT, CrudOp.EXPORT, CrudOp.IMPORT_TEMPLATE})
public class SysUserController { }
```

开启 `CrudOp.IMPORT` / `CrudOp.EXPORT` / `CrudOp.IMPORT_TEMPLATE` 后额外获得三个接口
（权限码后缀 `:import` / `:export` / `:import`）：
`POST {base}/import`（multipart 字段 `file`，逐行校验 + 事务批量插入）、
`GET {base}/export`（复用 page 的查询条件导出 Excel 附件）、
`GET {base}/import-template`（下载仅含表头的导入模板）。

### 4. 方法级注解：不想整类接管时，直接标注在方法上

方法体留空（`return null`），框架 AOP 自动接管执行；可与 `@QuickCrud`、手写方法自由混用：

```java
@RestController
@RequestMapping("/product")
public class ProductController {

    @QuickSave(entity = Product.class)                          // 新增（参数为实体或 List<实体> 批量）
    @PostMapping
    public R<Object> save(@RequestBody Product product) { return null; }

    @QuickUpdate(entity = Product.class)                        // 修改（按 ID，null 字段不更新）
    @PutMapping
    public R<Object> update(@RequestBody Product product) { return null; }

    @QuickRemove(entity = Product.class)                        // 删除（ids 支持单个/List/逗号分隔）
    @DeleteMapping("/{ids}")
    public R<Object> remove(@PathVariable("ids") String ids) { return null; }

    @QuickExport(entity = Product.class)                        // 导出：复用 page 查询条件，Excel 附件下载
    @GetMapping("/export")
    public void export(HttpServletResponse response) { }

    @QuickImport(entity = Product.class, permission = "product:import")  // 导入：校验 + 事务入库
    @PostMapping("/import")
    public R<Object> importExcel(MultipartFile file) { return null; }
}
```

- 权限：注解的 `permission` 为**完整权限码**（空 = 不鉴权），由统一拦截器校验
- Excel 列名：实体字段加 FastExcel 的 `@ExcelProperty("中文名")`，未加按字段名
- 导入策略：任一行 Bean Validation 校验失败则整体不入库（返回 400 + 行级错误明细），插入阶段同一事务

### 5. 登录与权限：内置 Sa-Token（或自定义 SPI）

引入 starter 后，Sa-Token 在 classpath 上且未自定义 `UserResolver`/`PermissionChecker` 时自动桥接：

```java
// 登录：直接用 Sa-Token（token 建议 sa-token.token-name 配置为 Authorization，与框架读取一致）
StpUtil.login(user.getId());

// 权限数据：实现 Sa-Token 的 StpInterface（查询你自己的 RBAC 表）
@Component
public class MyStpInterface implements StpInterface {
    public List<String> getPermissionList(Object loginId, String loginType) { ... }
    public List<String> getRoleList(Object loginId, String loginType) { ... }
}
```

此后 `@RequiresPerm` / `@QuickCrud` 权限码 / 方法级注解 `permission` 全部走 Sa-Token 校验，
`AuthContext.getUser()` 返回 loginId。也可完全不用 Sa-Token——自定义 SPI 覆盖：

```java
@Component
public class MyAuthService implements UserResolver, PermissionChecker {

    @Override
    public Object getUser(String token) {        // token -> 当前用户；null 表示未登录
        return tokenStore.get(token);
    }

    @Override
    public boolean hasPermission(Object user, String permission) {
        return rbacService.codesOf(user).contains(permission); // 权限码比对
    }
}
```

请求头默认从 `Authorization`（兼容 `Bearer` 前缀）读取 token，也可用 `?token=xxx` 参数。

### 6. 配置（全部可选）

```yaml
quick-dev:
  enabled: true            # 关闭 @QuickCrud 端点注册
  db-type: mysql           # 分页插件方言（不配则通用模式；用户已定义 MybatisPlusInterceptor 时不生效）
  method-op:
    enabled: true          # 方法级注解（@QuickSave 等 AOP 接管）开关
  auto-fill:
    enabled: true          # createTime/updateTime 自动填充开关
  repeat-submit:
    enabled: true          # @NoRepeatSubmit 防重复提交开关
  log:
    enabled: true          # @QuickLog 操作日志开关
  translate:
    enabled: true          # @Translate 字段翻译开关
    cache-seconds: 60      # 翻译结果本地缓存秒数（0 禁用）
  auth:
    enabled: true          # 鉴权总开关
    token-header: Authorization
    token-param: token
```

## 查询参数约定（page / list 接口）

| 参数 | 说明 |
|---|---|
| `current` / `size` | 分页参数，默认 1 / 10，size 上限 1000 |
| 与实体属性同名 | 生成查询条件，方式由 `@QueryField` 决定（默认 EQ），值自动转换类型 |
| `orderBy` / `order` | 排序字段（必须是实体属性名，防注入）+ `asc`/`desc` |

示例：`GET /sys-user/page?current=1&size=10&username=ad&status=1&orderBy=create_time&order=desc`

> 注意：`orderBy` 传的是**实体属性名**（createTime），框架内部映射为列名。

## 在普通接口上使用权限注解

```java
@RestController
public class ReportController {

    @RequiresPerm("report:export")            // 多个权限码为 AND 关系
    @GetMapping("/report/export")
    public R<Object> export() {
        SysUser current = AuthContext.getUser(); // 当前登录用户（Sa-Token 模式下为 loginId）
        ...
    }

    @RequiresRole("admin")                    // 角色校验，支持 logical = Logical.OR
    @RequiresRole(value = {"admin", "auditor"}, logical = Logical.OR)
    @GetMapping("/report/audit")
    public R<Object> audit() { ... }

    @RequiresLogin                            // 仅要求登录
    @GetMapping("/report/mine")
    public R<Object> mine() { ... }
}
```

> 角色数据来源与权限一致：Sa-Token 模式下实现 `StpInterface.getRoleList`；自定义模式下实现 `RoleChecker` Bean。

### 防重复提交 / 操作日志

```java
@NoRepeatSubmit(interval = 2000)                 // 同一用户 2 秒内重复请求 -> 400
@PostMapping("/order")
public R<Object> create(@RequestBody Order order) { ... }

@QuickLog(module = "订单管理", description = "创建订单")   // 审计日志
@NoRepeatSubmit(interval = 2000)
@PostMapping("/order")
public R<Object> create(@RequestBody Order order) { ... }
```

`@QuickLog` 记录：模块/描述、操作人（loginId）、URI、HTTP 方法、IP、入参 JSON（截断）、
结果码、是否成功、异常信息、耗时。落地由 `OperationLogSink` SPI 决定（实现 Bean 即接管，
默认输出到 Slf4g logger `quick-dev.operation-log`；生产建议异步写库）。

### Redis 支持（可选）

```xml
<dependency>
    <groupId>dev.xuya</groupId>
    <artifactId>quick-dev-redis-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
```

引入即生效，无需代码：

- **Sa-Token 数据落 Redis**（jackson 序列化）：登录态、权限缓存多实例共享，应用重启不丢登录
- **防重复提交切 Redis**：`setIfAbsent + 过期` 原子占位，集群部署下多实例同样生效（自动替换内存实现，也可实现 `RepeatSubmitStore` Bean 自定义）

### 字段翻译（@Translate）

标注在实体/VO 字段上，JSON 序列化时把值翻译为可读文本，适合列表页展示：

```java
public class OrderVO {

    /** 字典翻译：值 -> 标签（DictResolver SPI 查字典表/枚举） */
    @Translate(dict = "order_status")
    private Integer status;                  // 1 序列化为 "已支付"

    /** 枚举翻译：枚举实现 DictEnum 接口，免建字典表 */
    @Translate(enumClass = OrderStatus.class)
    private Integer type;                    // 1 序列化为 "普通商品"

    /** 关联翻译：字段值作为目标实体主键，取其某属性 */
    @Translate(entity = SysUser.class, field = "nickname")
    private String createBy;                 // "1" 序列化为 "管理员"
}
```

- 翻译发生在序列化期：**零侵入**，分页/详情/导出等一切返回 JSON 的接口自动生效
- 翻译失败（无字典、无记录、未实现 SPI）**保留原值**输出，不影响接口
- 结果带 TTL 本地缓存（默认 60 秒），避免列表页同值重复查库：`quick-dev.translate.cache-seconds`（0 关闭）、`quick-dev.translate.enabled=false` 可整体停用
- 字典数据源：实现 `DictResolver` Bean（查字典表/枚举/远程服务均可）；固定枚举直接 `enumClass` 引用（实现 `DictEnum` 接口）

### 字典在数据库表：内置方案（零代码）

字典不存在枚举里、而是维护在数据库表（常见做法）时，无需实现任何 SPI，配置表名即可：

```yaml
quick-dev:
  dict:
    enabled: true            # classpath 有 JdbcTemplate 时自动生效（默认 true）
    table: sys_dict          # 字典表
    type-column: dict_type
    value-column: dict_value
    label-column: dict_label
```

内置 `JdbcDictProvider` 同时提供正向（值-&gt;标签）与导入反向（标签-&gt;值）解析：
`@Translate(dict = "user_status")` 正反双向全自动，查询结果由 TTL 缓存兜底。
已自定义 `DictResolver` / `DictReverseResolver` 任一实现时，内置方案自动让位。

### 导入反向转换（上传转换）

Excel 导入时用户填的往往是中文标签（"启用"/"线上"/"管理员"），框架会**先反解为库值再做类型转换与校验**：

| @Translate 模式 | 反解方式 |
|---|---|
| `enumClass = ...` 枚举 | 自动：按 `DictEnum.getLabel()` 匹配返回值 |
| `dict = ...` 字典 | **用户自主实现** `DictReverseResolver` SPI（标签 -> 值） |
| `entity = ...` 关联 | 自动：按目标属性值反查主键（多条取第一条） |

```java
/** 字典反解 SPI（与 DictResolver 对称）：Excel 里的 "线上" -> 1 */
@Component
public class MyDictReverseResolver implements DictReverseResolver {
    @Override
    public Object reverse(String dictType, String label) {
        return dictMapper.selectOne(...).getDictValue();
    }
}
```

- 反解失败（无匹配标签）保留原文本，随后按字段类型转换；类型不符会报"第 N 行 [列名] 的值无法转换"
- 单元格直接填数字原值同样支持（1 和 "启用" 都能导入）

### 行级数据权限（@DataScope）

标注在实体类上，实现一个 `DataScopeResolver` 即可让所有查询类接口自动过滤行级数据：

```java
@DataScope(column = "dept_id")     // 该列按可见范围过滤
@TableName("sys_user")
public class SysUser { ... }

@Component
public class MyDataScopeResolver implements DataScopeResolver {
    @Override
    public Collection<?> visibleScope(Class<?> entityClass, String column, Object currentUser) {
        if (isAdmin(currentUser)) {
            return null;                       // null = 不限制
        }
        return deptService.deptIdsOf(currentUser); // 只看本部门（含子部门由你的查询决定）
    }
}
```

- 生效范围：page / list / count / tree / export（一切走查询条件的接口，方法级 `@QuickExport` 同样生效）
- resolver 返回**空集合** = 查不到任何数据（安全默认）；未注册 resolver = 不过滤
- 按主键的详情/删除不经过查询条件，不做行级过滤（如需严格隔离请在业务层校验）

## 运行演示应用

```bash
cd quick-dev-demo
mvn spring-boot:run
```

内置账号（H2 内存库，种子数据见 `data.sql`）：

| 账号 | 密码 | 权限 | 角色 |
|---|---|---|---|
| admin | admin123 | `*`（全部） | admin |
| viewer | viewer123 | 仅 `sys:user:list` / `sys:user:detail` | 无 |

```bash
# 登录拿 token
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 分页 + 模糊查询（token 放 Authorization 头）
curl "http://localhost:8080/sys-user/page?current=1&size=10&username=ad" \
  -H "Authorization: {token}"

# 新增 / 修改 / 删除
curl -X POST http://localhost:8080/sys-user -H "Authorization: {token}" \
  -H "Content-Type: application/json" \
  -d '{"username":"tester","nickname":"测试","email":"t@q.cn","status":1}'

curl -X PUT http://localhost:8080/sys-user -H "Authorization: {token}" \
  -H "Content-Type: application/json" \
  -d '{"id":4,"nickname":"改名"}'

curl -X DELETE http://localhost:8080/sys-user/4 -H "Authorization: {token}"

# /product 未配置 permission，无需登录即可访问（UUID 字符串主键演示）
curl http://localhost:8080/product/page
```

## 统一响应结构

```json
{ "code": 200, "msg": "success", "data": { ... }, "success": true }
```

| 场景 | HTTP 状态 | code |
|---|---|---|
| 成功 | 200 | 200 |
| 参数/校验错误 | 200 | 400 |
| 未登录或 token 失效 | 401 | 401 |
| 权限不足 | 403 | 403 |
| 记录不存在（详情） | 200 | 404 |
| 其他业务/系统异常 | 200 | 500 |

## 实现原理

```
应用启动
  └─ quick-dev-spring-boot-autoconfigure（Spring Boot 3 自动配置，AutoConfiguration.imports 注册）
       ├─ QuickCrudRegistrar（SmartInitializingSingleton）
       │    └─ 扫描 @QuickCrud Bean -> 解析实体/Mapper/路径/权限
       │         └─ RequestMappingHandlerMapping.registerMapping(...)
       │              动态注册 page/list/{id}/save/update/{ids} 六个端点
       ├─ AuthInterceptor（拦截 /**）
       │    └─ @RequiresPerm / @RequiresLogin / CRUD 权限码
       │         -> UserResolver（token→用户）-> PermissionChecker（用户→权限）
       └─ GlobalExceptionHandler（异常 -> R）
```

关键点：动态注册的 handler 刻意使用 `String/Map` 等具体类型做参数绑定，实体类型由处理器内部通过 Jackson / ConversionService 处理，从而绕开泛型擦除导致的类型解析问题；路径冲突遵循 Spring 规则（字面量优先于 `{id}` 模板）。

## 已知边界与约定

- 更新接口为部分更新：不做整实体 Bean Validation 校验（新增才校验），`null` 字段不更新
- 被排除的操作若与保留操作的路径模板重叠（如排除 LIST 后访问 `/list`），会被 `/{id}` 详情路由接住，返回"记录不存在"
- 鉴权失败是**失败关闭**：接口声明了权限要求但没有 `UserResolver`/`PermissionChecker` 实现时，直接报配置错误而非放行
- 框架自动配置的分页插件仅在用户未自定义 `MybatisPlusInterceptor` 时生效

## 环境要求

- JDK 17+，Spring Boot 3.3+，MyBatis-Plus 3.5.9+（分页插件构件 `mybatis-plus-jsqlparser` 已由框架传递引入）
