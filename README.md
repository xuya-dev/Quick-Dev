# Quick Dev

基于 **Spring Boot 3 + MyBatis-Plus** 的快速开发框架（标准 Spring Boot Starter 结构）：引入一个 `quick-dev-spring-boot-starter` 依赖，在 Controller 上贴一个 `@QuickCrud` 注解，即可自动获得一组带权限控制的 CRUD 接口。

```java
@QuickCrud(entity = SysUser.class, permission = "sys:user")
public class SysUserController {
    // 空的，不需要写任何方法
}
```

启动后自动注册 7 个接口：

| 方法 | 路径 | 权限码 | 说明 |
|---|---|---|---|
| GET | `/sys-user/page` | `sys:user:list` | 分页查询（current/size + 动态条件 + 排序） |
| GET | `/sys-user/list` | `sys:user:list` | 列表查询（不分页） |
| GET | `/sys-user/count` | `sys:user:list` | 按条件统计数量 |
| GET | `/sys-user/{id}` | `sys:user:detail` | 详情 |
| POST | `/sys-user` | `sys:user:add` | 新增（支持 Bean Validation 校验） |
| PUT | `/sys-user` | `sys:user:edit` | 修改（按 ID，null 字段不更新） |
| DELETE | `/sys-user/{ids}` | `sys:user:remove` | 删除，`ids` 逗号分隔支持批量 |

## 特性

- **一个注解完成 CRUD**：`@QuickCrud` 标注在 Controller 上，启动时通过 `RequestMappingHandlerMapping` 运行期注册端点（Spring 官方支持的方式），与手写接口完全共存
- **注解式权限控制**：`@RequiresPerm` / `@RequiresLogin` 可用在任何 Controller 上；`@QuickCrud` 生成的接口按 `权限前缀:操作` 约定自动鉴权
- **零绑定权限实现**：框架只定义 `UserResolver`（token→用户）与 `PermissionChecker`（用户→权限码）两个 SPI，对接你自己的 RBAC / SSO / 网关鉴权均可
- **声明式查询条件**：实体字段标注 `@QueryField(LIKE/GT/IN/BETWEEN/...)`，同名请求参数自动变查询条件并做类型转换
- **时间字段自动填充**：`createTime`/`updateTime` 新增/修改时自动填充（字段加 `@TableField(fill = ...)` 即可，见下文）
- **统一响应与异常**：`R<T>` 结构 + 全局异常处理（未登录 401、无权限 403、参数/校验错误 400）
- MyBatis-Plus 既有能力全部可用：逻辑删除、乐观锁、多租户、`@TableName` 映射等

## 模块结构

```
quick-dev
├── quick-dev-core                      核心库：注解 / CRUD 引擎 / 权限 / R / 异常（依赖全部 optional）
├── quick-dev-spring-boot-autoconfigure 自动配置模块（Properties + AutoConfiguration）
├── quick-dev-spring-boot-starter       ★ 使用方唯一需要引入的依赖（聚合 core + web + validation + MyBatis-Plus + 分页插件）
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

starter 会传递引入：`quick-dev-core` + 自动配置、`spring-boot-starter-web`、`spring-boot-starter-validation`、`mybatis-plus-spring-boot3-starter`、`mybatis-plus-jsqlparser`（分页插件）。只需再自备一个数据库驱动（如 `mysql-connector-j`、`h2`）。

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

> 自动填充是框架内置的 `MetaObjectHandler`（按属性名 `createTime`/`updateTime` 约定，已有值不覆盖），
> 字段必须加 `@TableField(fill = ...)`，否则 MyBatis-Plus 生成 SQL 时会跳过 null 列导致填充不生效。
> 不想用可设置 `quick-dev.auto-fill.enabled=false`，或注册自己的 `MetaObjectHandler` Bean 覆盖。

### 3. 一个注解出接口

```java
// path 不写：优先取类上 @RequestMapping，否则实体名推导（SysUser -> /sys-user）
// permission 不写：接口开放（可用 loginRequired = true 仅要求登录）
// includes/excludes：只注册/排除部分操作
@QuickCrud(entity = SysUser.class, permission = "sys:user", excludes = CrudOp.LIST)
public class SysUserController { }
```

### 4. 实现两个鉴权 SPI（框架不关心你的用户体系）

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

### 5. 配置（全部可选）

```yaml
quick-dev:
  enabled: true            # 关闭 @QuickCrud 端点注册
  db-type: mysql           # 分页插件方言（不配则通用模式；用户已定义 MybatisPlusInterceptor 时不生效）
  auto-fill:
    enabled: true          # createTime/updateTime 自动填充开关
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
        SysUser current = AuthContext.getUser(); // 当前登录用户
        ...
    }

    @RequiresLogin                            // 仅要求登录
    @GetMapping("/report/mine")
    public R<Object> mine() { ... }
}
```

## 运行演示应用

```bash
cd quick-dev-demo
mvn spring-boot:run
```

内置账号（H2 内存库，种子数据见 `data.sql`）：

| 账号 | 密码 | 权限 |
|---|---|---|
| admin | admin123 | `*`（全部） |
| viewer | viewer123 | 仅 `sys:user:list` / `sys:user:detail` |

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
