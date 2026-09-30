# Quick Dev 使用手册

> 场景化速查手册。体系化的完整手册见 [manual.md](manual.md)。

> 面向使用本框架的业务开发者。按场景组织，比 [README](../README.md) 更细；
> 快速上手请先读 README 的"快速开始"。
>
> 目录：[1 起步](#1-起步) · [2 类级 CRUD](#2-类级-quickcrud) · [3 方法级注解](#3-方法级注解) ·
> [4 查询与排序](#4-查询与排序) · [5 登录与鉴权](#5-登录与鉴权) · [6 字段翻译与字典](#6-字段翻译与字典) ·
> [7 Excel 导入导出](#7-excel-导入导出) · [8 审计填充](#8-审计填充) · [9 数据权限](#9-数据权限) ·
> [10 防重复提交与操作日志](#10-防重复提交与操作日志) · [11 Redis 部署](#11-redis-部署) ·
> [12 响应与异常](#12-响应与异常) · [13 FAQ](#13-faq)

## 1. 起步

### 1.1 引入依赖

```xml

<dependency>
    <groupId>dev.xuya</groupId>
    <artifactId>quick-dev-spring-boot-starter</artifactId>
    <version>0.4.0</version>
</dependency>
        <!-- 再加你的数据库驱动，例如： -->
<dependency>
<groupId>com.mysql</groupId>
<artifactId>mysql-connector-j</artifactId>
<scope>runtime</scope>
</dependency>
```

需要 Redis 时再加 `quick-dev-redis-spring-boot-starter`（见第 11 章）。

### 1.2 一个最小可运行应用

```java

@SpringBootApplication
@MapperScan("com.example.mapper")     // 你的 Mapper 包
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
```

```java

@TableName("t_user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    // getter/setter
}

public interface UserMapper extends BaseMapper<User> {
}
```

```java

@QuickCrud(entity = User.class)       // 到这里 /user/** 八个接口已可用
public class UserController {
}
```

启动日志会打印每个注册的端点与权限码，可据此核对。

### 1.3 配置数据源与 Sa-Token

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/demo
    username: root
    password: root

sa-token:
  token-name: Authorization    # 与框架 token 读取头保持一致
  timeout: 2592000
```

## 2. 类级 @QuickCrud

### 2.1 全部属性

| 属性            | 默认         | 说明                                                                    |
|-----------------|--------------|-------------------------------------------------------------------------|
| `entity`        | 必填         | 实体类                                                                  |
| `mapper`        | 自动         | 按 `BaseMapper<实体>` 泛型查找；多 Mapper 匹配时需显式指定              |
| `path`          | 推导         | 优先类上 `@RequestMapping`，否则实体名转 kebab（SysUser → `/sys-user`） |
| `permission`    | 空           | 权限码前缀；空 = 开放（配合 `loginRequired=true` 可仅要求登录）         |
| `loginRequired` | false        | 无权限码时仍要求登录                                                    |
| `includes`      | 8 个默认操作 | PAGE/LIST/COUNT/DETAIL/SAVE/SAVE_BATCH/UPDATE/REMOVE                    |
| `excludes`      | 空           | 在 includes 基础上排除                                                  |

### 2.2 可选操作（需加入 includes）

| CrudOp            | 端点                         | 权限后缀  | 说明                                     |
|-------------------|------------------------------|-----------|------------------------------------------|
| `IMPORT`          | `POST {base}/import`         | `:import` | multipart 字段 `file`；逐行校验+事务插入 |
| `EXPORT`          | `GET {base}/export`          | `:export` | 复用 page 查询条件导出 Excel             |
| `IMPORT_TEMPLATE` | `GET {base}/import-template` | `:import` | 下载仅表头的模板                         |
| `TREE`            | `GET {base}/tree`            | `:list`   | 树形结构（实体需 `parentId`+`children`） |

### 2.3 与手写接口混用

`@QuickCrud` 类可以照常写自定义方法，动态端点与手写端点共存：

```java

@QuickCrud(entity = User.class, permission = "sys:user")
public class UserController {

    @RequiresPerm("sys:user:resetPwd")
    @PostMapping("/{id}/resetPwd")
    public R<Object> resetPwd(@PathVariable Long id) {
        // 自定义业务逻辑
        return R.ok();
    }
}
```

### 2.4 分阶段校验（新增必填 / 修改部分校验）

```java
public class Goods {
    @NotBlank(groups = Create.class)     // 仅新增必填
    private String name;
    @Size(max = 200)                     // 两阶段都校验（修改时只对提交值生效）
    private String remark;
    @QuickRequire(dependField = "stock", dependValue = "0")   // stock=0 时 reason 必填
    private String reason;
}
```

- 新增：全实体校验，分组 `Default + Create`（`dev.xuya.core.validation.Create`）
- 修改：部分更新语义——只校验**提交值非空**的字段，分组 `Default + Update`
  （`dev.xuya.core.validation.Update`）；未提交字段不会误伤
- `@QuickRequire(dependField, dependValue)` 条件必填，可重复、支持 groups；
  修改阶段仅当依赖字段本次提交非空且匹配才生效
- 关闭修改校验：`quick-dev.crud.update-validate=false`

## 3. 方法级注解

适用场景：只想让某个方法具备 CRUD/导入导出能力，或需要在同Controller里精细控制每个端点。
方法体留空（`return null`），框架 AOP 接管：

```java

@RestController
@RequestMapping("/order")
public class OrderController {

    @QuickSave(entity = Order.class, permission = "order:add")
    @PostMapping
    public R<Object> save(@RequestBody Order order) {
        return null;
    }

    @QuickSave(entity = Order.class)                        // List 参数自动批量（Db.saveBatch）
    @PostMapping("/batch")
    public R<Object> saveBatch(@RequestBody List<Order> orders) {
        return null;
    }

    @QuickUpdate(entity = Order.class, permission = "order:edit")
    @PutMapping
    public R<Object> update(@RequestBody Order order) {
        return null;
    }

    @QuickRemove(entity = Order.class, permission = "order:remove")
    @DeleteMapping("/{ids}")                                // "1" 或 "1,2,3"
    public R<Object> remove(@PathVariable("ids") String ids) {
        return null;
    }

    @QuickExport(entity = Order.class, translate = true)    // translate: 导出翻译后的标签
    @GetMapping("/export")
    public void export(HttpServletResponse response) {
    }

    @QuickImport(entity = Order.class, permission = "order:import")
    @PostMapping("/import")
    public R<Object> importExcel(MultipartFile file) {
        return null;
    }
}
```

注意：方法级 `permission` 是 **完整权限码**（区别于 `@QuickCrud.permission` 的前缀语义）。

## 4. 查询与排序

### 4.1 条件注解 @QueryField

```java
public class Order {
    private String orderNo;                     // 默认 EQ
    @QueryField(QueryType.LIKE)
    private String buyerName;                   // ?buyerName=张 -> LIKE '%张%'
    @QueryField(QueryType.IN)
    private Integer status;                     // ?status=1,2 -> IN (1,2)
    @QueryField(QueryType.BETWEEN)
    private LocalDateTime createTime;           // ?createTime=起,止 -> BETWEEN
    @QueryField(QueryType.GE)
    private BigDecimal amount;                  // ?amount=100 -> >= 100
    // NE/GT/LT/LE 同理
}
```

- 请求参数名 = 实体属性名；值自动做类型转换（含 String→LocalDateTime/BigDecimal 等 ISO 格式）
- 非实体属性名的参数被忽略，不报错
- `IN` 值上限 1000（`quick-dev.limits.in-max-size`）

### 4.2 分页与排序参数

`GET {base}/page?current=1&size=20&orderBy=createTime&order=desc`

- `current`/`size` 默认 1/10，size 上限 1000
- `orderBy` 必须是 **实体属性名**（防注入），`order` 为 `asc|desc`

## 5. 登录与鉴权

### 5.1 内置 Sa-Token（推荐）

```java
// 登录
@PostMapping("/login")
public R<Object> login(@RequestBody LoginDTO dto) {
    User user = userService.verify(dto);
    StpUtil.login(user.getId());
    return R.ok(Map.of("token", StpUtil.getTokenValue()));
}

// 权限/角色数据源（查你自己的表）
@Component
public class MyStpInterface implements StpInterface {
    public List<String> getPermissionList(Object loginId, String loginType) { ...}

    public List<String> getRoleList(Object loginId, String loginType) { ...}
}
```

之后所有鉴权注解自动走 Sa-Token；`AuthContext.getUser()` 返回 loginId。

### 5.2 注解清单与优先级

`@RequiresPerm` > `@RequiresRole` > `@RequiresLogin` 可叠加使用；方法级注解优先于类级。
未登录抛 401（HTTP 401 + code 401），权限/角色不足 403。

### 5.3 不用 Sa-Token：自定义 SPI

实现 `UserResolver`（token→用户）+ `PermissionChecker` + 可选 `RoleChecker` 注册为 Bean，
框架自动切换，接口语义完全一致（token 默认从 `Authorization` 头读取，兼容 `Bearer` 前缀，
兜底 `?token=` 参数——`quick-dev.auth.*` 可改）。

## 6. 字段翻译与字典

### 6.1 三种模式

```java

@Translate(dict = "order_status")                        // 字典（表/枚举/远程 由你决定）
private Integer status;                                  // 1 -> "已支付"

@Translate(enumClass = OrderStatus.class)                // 枚举（实现 DictEnum，免建表）
private Integer type;                                    // 1 -> "普通商品"

@Translate(entity = User.class, field = "nickname")      // 关联表（值作为目标主键）
private String createBy;                                 // "1" -> "管理员"

// 附加模式：deptId 保留原值，翻译结果写到兄弟字段 deptName（编辑表单需要原始 ID 时用）
@Translate(entity = Dept.class, field = "deptName",
        mode = TranslateMode.APPEND, appendField = "deptName")
private Long deptId;                                     // {"deptId":103, "deptName":"研发部门"}
```

翻译发生在 JSON 序列化期：分页/详情/你的自定义接口 **全部自动生效**，无需调用任何方法。
失败（无字典/无记录/未实现）保留原值，不影响接口。APPEND 模式无翻译结果时兄弟字段输出 `null`；
`appendField` 缺省为「字段名 + Name」。

### 6.2 字典数据来源：DictLoader SPI（框架不查任何库）

框架本身不连任何数据库查字典——数据来源由你实现 `DictLoader` SPI 提供
（自有字典表 / 远程字典服务 / 配置中心任选），全量驻留内存（懒加载），翻译不查库：

```java
@Component
public class MyDictLoader implements DictLoader {
    @Override
    public List<DictEntry> loadAll() {
        // SELECT dict_type, dict_value, dict_label FROM sys_dict（表结构/来源完全自定）
        ...
    }
}
```

- 字典变更后调刷新接口：`POST /quick-dev/dict/refresh`（需 `dict:refresh` 权限）——重建缓存并清空翻译缓存，立即生效
- 多实例部署：开 `quick-dev.dict.refresh-interval-seconds` 定时刷新做最终一致，或引入 Redis 后各自调刷新接口
- 也可以不实现 DictLoader，编程式调 `DictCacheService#replaceAll` /
  `#replaceByJson`（标准 JSON：`[{"type","value","label"}]`）全量替换缓存

### 6.3 Excel 导入反解

导入时单元格填中文标签（"已支付"）或原始数字（1）都可以：`enumClass`/`entity` 模式框架自动反解，
`dict` 模式由内置字典缓存反解；自定义来源实现 `DictReverseResolver` Bean。

## 7. Excel 导入导出

- 列名：实体字段加 `@ExcelProperty("中文名")`，未加用字段名（导入导出模板两侧一致）
- 导出：复用 page 的查询条件（含 @QueryField 与数据权限），`@QuickExport(translate=true)` 输出翻译标签
- 导入策略： **任一行校验失败则整体不入库**（400 + 前 10 行错误明细），插入同一事务
- 导入返回 `{total, inserted}`；行数上限 1 万（`quick-dev.limits.import-max-rows`）
- 导出行数上限 10 万，超出截断并告警（`quick-dev.limits.export-max-rows`）
- `@JsonIgnore` 字段（如密码）不会出现在翻译导出中

## 8. 审计填充

```java

@TableField(fill = FieldFill.INSERT)
private LocalDateTime createTime;
@TableField(fill = FieldFill.INSERT_UPDATE)
private LocalDateTime updateTime;
@TableField(fill = FieldFill.INSERT)
private String createBy;
@TableField(fill = FieldFill.INSERT_UPDATE)
private String updateBy;
```

- 新增填四个，修改填 `updateTime`/`updateBy`；已有值不覆盖
- `createBy`/`updateBy` 取当前登录人（Sa-Token 模式为 loginId），字段须为 String，未登录跳过
- **必须加 `@TableField(fill=...)`**（原因见 FAQ-1）；关闭：`quick-dev.auto-fill.enabled=false`

## 9. 数据权限

```java

@DataScope(column = "dept_id")
@TableName("sys_user")
public class SysUser { ...
}

@Component
public class MyDataScopeResolver implements DataScopeResolver {
    public Collection<?> visibleScope(Class<?> entityClass, String column, Object currentUser) {
        if (isAdmin(currentUser)) return null;              // null = 不限制
        return deptService.visibleDeptIds(currentUser);     // 空集合 = 全不可见
    }
}
```

生效范围：page/list/count/tree/export 及方法级 `@QuickExport`；按主键的详情/删除不过滤。

## 10. 防重复提交与操作日志

```java

@NoRepeatSubmit(interval = 2000)                    // 同一用户+接口 2 秒窗口，重复返回 400
@PostMapping("/pay")
public R<Object> pay(@RequestBody PayDTO dto) { ...}

@QuickLog(module = "订单", description = "创建订单")  // 记录操作人/URI/入参/结果/耗时/异常
@PostMapping("/order")
public R<Object> create(@RequestBody OrderDTO dto) { ...}
```

操作日志落地：实现 `OperationLogSink` Bean（写库/ES/MQ，线程模型自行决定），默认输出 Slf4j。
防重存储：单实例内存；引入 redis starter 后自动切 Redis 原子实现（集群一致）。

## 11. Redis 部署

```xml

<dependency>
    <groupId>dev.xuya</groupId>
    <artifactId>quick-dev-redis-spring-boot-starter</artifactId>
    <version>0.4.0</version>
</dependency>
```

```yaml
spring:
  data:
    redis: { host: 127.0.0.1, port: 6379 }
```

效果：Sa-Token 登录态/权限缓存落 Redis（多实例共享、重启不丢）；防重复提交切 Redis。
注意： **内置字典缓存仍是单实例内存**——多实例下请在各实例调刷新接口，或自行实现共享。

## 12. 响应与异常

成功与业务错误统一 `R<T>`：`{code, msg, data, success}`。错误码：参数/校验 400、未登录 401（HTTP 401）、
无权限 403（HTTP 403）、记录不存在 404、其他 500。业务中直接 `throw new ParamException("...")`
（400）或 `QuickDevException("...")`（500）即可，无需 try-catch。

生产环境建议 `quick-dev.error-detail: false`——未预期异常只返回"系统繁忙"，不泄露内部信息。

## 13. FAQ（真实踩坑实录）

**1. 自动填充不生效？**
字段没加 `@TableField(fill = ...)`。MyBatis-Plus 生成 SQL 时就决定是否包含列，未标注的 null 列
在运行期赋值前已被跳过——这是 MP 机制，框架无法绕过。

**2. 字典翻译没输出，还是原值？**
按顺序排查：①`@Translate` 的 dict/enumClass/entity 是否配对；②字典缓存里有没有数据
（调 `POST /quick-dev/dict/refresh` 看返回的 size，或检查 DictLoader 数据源）；③自定义了
`DictResolver` 会导致内置方案让位；④翻译失败是静默降级，开 debug 日志看 `TranslateExecutor`。

**3. 更新接口为什么不做 @NotBlank 校验？**
更新是部分更新（null 字段跳过），整实体校验会强制全字段传值。需要强校验用新增接口或自定义。

**4. 排除了 LIST，访问 /list 返回"记录不存在"或参数错误？**
`/{id}` 详情路由模板接住了该路径（字面量路由未注册时）：主键为 String 的实体会返回"记录不存在"；
主键为数值型的实体 "list" 无法转成 ID，返回参数错误（400）。都属预期行为——请按注解声明的实际端点访问。

**5. 详情接口查 String 主键报类型错误？**
框架会按实体主键类型自动转换（`"1"`→`Long 1`）；若实体主键类型与表列不一致请先修正实体。

**6. 重启后登录失效？**
Sa-Token 默认内存存储。多实例/重启保留登录请引入 redis starter。

**7. 刷新了字典，翻译结果还是旧的？**
刷新接口会同时清空翻译缓存——如果你自己实现刷新逻辑，记得调用 `TranslateExecutor.clearCache()`
（曾有真实事故：只重建字典缓存，60 秒 TTL 的结果缓存里仍是旧标签）。

**8. 多个 Mapper 泛型相同导致注入异常？**
`@QuickCrud(mapper = XxxMapper.class)` 显式指定，或给 @Bean 加 `@Primary`。

**9. 导入时报"第 N 行 [列名] 的值无法转换"？**
单元格内容与字段类型不符且无法反解：确认 Excel 列名与 `@ExcelProperty` 值/字段名一致、
字典标签拼写与库中一致（反解不区分大小写前会 trim）。

**10. 为什么鉴权配置错误直接抛异常而不是放行？**
框架鉴权是 **失败关闭**设计：声明了权限要求但 SPI 缺失时快速暴露配置问题，避免裸奔上线。

---

更多：[README](../README.md)（总览与快速开始）· [配置项总表](../README.md#附录配置项总表前缀-quick-dev) ·
[贡献指南](../CONTRIBUTING.md) · [AGENT.md](AGENT.md)（AI 助手使用说明）
