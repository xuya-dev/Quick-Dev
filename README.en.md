# Quick Dev

[![CI](https://github.com/xuya-dev/Quick-Dev/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/xuya-dev/Quick-Dev/actions/workflows/ci.yml)
[![JDK](https://img.shields.io/badge/JDK-17%2B-blue)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.x-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.x-red)](https://baomidou.com/)
[![License](https://img.shields.io/badge/License-Apache--2.0-green)](LICENSE)

[简体中文](README.md) | **English**

A rapid development framework based on **Spring Boot 3 + MyBatis-Plus** (standard Spring Boot Starter layout):
add the `quick-dev-spring-boot-starter` dependency, annotate a controller with `@QuickCrud`, and get a
full set of permission-protected CRUD endpoints automatically.

```java
@QuickCrud(entity = SysUser.class, permission = "sys:user")
public class SysUserController {
    // empty — no methods needed
}
```

Eight endpoints are registered at startup:

| Method | Path              | Permission        | Description                                              |
|--------|-------------------|-------------------|----------------------------------------------------------|
| GET    | `/sys-user/page`  | `sys:user:list`   | Pagination (current/size + dynamic conditions + sorting) |
| GET    | `/sys-user/list`  | `sys:user:list`   | List query (no pagination)                               |
| GET    | `/sys-user/count` | `sys:user:list`   | Count by conditions                                      |
| GET    | `/sys-user/{id}`  | `sys:user:detail` | Detail                                                   |
| POST   | `/sys-user`       | `sys:user:add`    | Create (with Bean Validation)                            |
| POST   | `/sys-user/batch` | `sys:user:add`    | Batch create (JSON array, validated + Db.saveBatch)      |
| PUT    | `/sys-user`       | `sys:user:edit`   | Update (by ID, null fields skipped)                      |
| DELETE | `/sys-user/{ids}` | `sys:user:remove` | Delete, comma-separated `ids` for batch                  |

Optional operations (add to `includes`): `POST {base}/import` Excel import (`:import`),
`GET {base}/export` Excel export (`:export`), `GET {base}/import-template` template download (`:import`),
`GET {base}/tree` tree query (`:list`; entity needs `parentId` + `children` fields,
`children` annotated with `@TableField(exist = false)`, null/0 parentId means root).

## Features

- **One annotation for full CRUD**: `@QuickCrud` on a controller registers endpoints at runtime via
  `RequestMappingHandlerMapping` (the officially supported way) and coexists with hand-written endpoints
- **Method-level annotations**: `@QuickSave` / `@QuickUpdate` / `@QuickRemove` / `@QuickExport` / `@QuickImport`
  annotated directly on methods — leave the body empty, AOP takes over; no need to hand the whole class to `@QuickCrud`
- **Built-in Sa-Token**: auto-bridges login state and permission checks when on the classpath (login via
  `StpUtil.login`, supply data via `StpInterface`)
- **Excel import/export**: FastExcel-based; export reuses query conditions, import validates + inserts transactionally
- **Annotation-driven access control**: `@RequiresPerm` / `@RequiresLogin` on any controller;
  `@QuickCrud` endpoints enforce `prefix:action` permission codes; method annotations enforce full codes
- **Replaceable auth**: beyond built-in Sa-Token, implement `UserResolver` / `PermissionChecker` SPIs for any system
- **Declarative query conditions**: `@QueryField(LIKE/GT/IN/BETWEEN/...)` on entity fields; same-named request
  parameters become conditions with automatic type conversion
- **Audit auto-fill**: `createTime`/`updateTime` + `createBy`/`updateBy` (current login id) filled on insert/update
- **Field translation (VO Translation)**: `@Translate` on fields translates IDs/status codes to readable text
  in JSON output (dict / enum / entity-ref modes, TTL-cached); **Excel import reverses labels back to values**;
  **database-backed dictionaries work with zero code** (built-in `JdbcDictProvider`, both directions)
- **Row-level data permission**: `@DataScope(column = "dept_id")` on an entity auto-filters page/list/count/tree/export
  by the visible scope returned from `DataScopeResolver` ("see only my department")
- **Optional Redis**: `quick-dev-redis-spring-boot-starter` puts Sa-Token state in Redis (shared across instances,
  survives restarts) and switches repeat-submit protection to an atomic Redis implementation
- **Tree query**: `CrudOp.TREE` outputs dept/menu/category trees in one line
- **Repeat-submit protection**: `@NoRepeatSubmit(interval)` keyed by user + endpoint fingerprint
- **Operation log**: `@QuickLog` records operator/params/result/duration; persist via `OperationLogSink` SPI
- **Unified response & exceptions**: `R<T>` structure + global handler (401 unauthenticated, 403 forbidden, 400 param)
- All MyBatis-Plus features still work: logic delete, optimistic locking, multi-tenancy, `@TableName`, etc.

## Module Layout

```
quick-dev
├── quick-dev-core                      Core library: annotations / CRUD engine / auth / R / exceptions (all deps optional)
├── quick-dev-spring-boot-autoconfigure Auto-configuration (Properties + AutoConfiguration + Redis conditions)
├── quick-dev-spring-boot-starter       ★ The only dependency users need (aggregates core + web + validation + MyBatis-Plus + pagination)
├── quick-dev-redis-spring-boot-starter Optional Redis support (Sa-Token storage + atomic repeat-submit)
├── quick-dev-codegen                   Code generator (table schema -> entity/mapper/controller, zero-dep JDK)
└── quick-dev-demo                      Demo application (H2 in-memory DB + built-in accounts, ready to run)
```

## Quick Start

### 1. Add the Starter (the only dependency you need)

```xml
<dependency>
    <groupId>dev.xuya</groupId>
    <artifactId>quick-dev-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

The starter transitively brings: `quick-dev-core` + auto-configuration, `spring-boot-starter-web`,
`spring-boot-starter-validation`, `spring-boot-starter-aop`, `sa-token-spring-boot3-starter`, `fastexcel`,
`mybatis-plus-spring-boot3-starter`, `mybatis-plus-jsqlparser` (pagination).
Just add your own database driver (e.g. `mysql-connector-j`, `h2`).

### 2. Define Entity & Mapper

```java
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;                    // exact match by default
    @QueryField(QueryType.LIKE)
    private String nickname;                    // ?nickname=tom -> LIKE '%tom%'
    @QueryField(QueryType.IN)
    private Integer type;                       // ?type=1,2 -> IN (1,2)
    @QueryField(QueryType.BETWEEN)
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;           // ?createTime=2026-01-01T00:00:00,2026-12-31T23:59:59 -> BETWEEN; auto-filled on insert
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;           // auto-filled on insert/update
    // getters/setters omitted
}

public interface SysUserMapper extends BaseMapper<SysUser> { }
```

> Auto-fill is a built-in `MetaObjectHandler` (convention: `createTime`/`updateTime`/`createBy`/`updateBy`,
> existing values are never overwritten; `createBy`/`updateBy` take the current login id as String, skipped when
> anonymous).
> Fields MUST carry `@TableField(fill = ...)` — otherwise MyBatis-Plus omits null columns when generating SQL and fill
> won't apply.
> Disable via `quick-dev.auto-fill.enabled=false`, or register your own `MetaObjectHandler` bean to override.

### 3. One Annotation, Full API

```java
// path omitted: falls back to class-level @RequestMapping, then entity name (SysUser -> /sys-user)
// permission omitted: endpoints are open (use loginRequired = true to require login only)
// includes/excludes: choose operations; IMPORT/EXPORT/IMPORT_TEMPLATE are opt-in
@QuickCrud(entity = SysUser.class, permission = "sys:user",
        excludes = CrudOp.LIST,
        includes = {CrudOp.PAGE, CrudOp.COUNT, CrudOp.DETAIL, CrudOp.SAVE, CrudOp.SAVE_BATCH,
                CrudOp.UPDATE, CrudOp.REMOVE, CrudOp.IMPORT, CrudOp.EXPORT, CrudOp.IMPORT_TEMPLATE})
public class SysUserController { }
```

Enabling `CrudOp.IMPORT` / `CrudOp.EXPORT` / `CrudOp.IMPORT_TEMPLATE` adds three endpoints (permission suffixes
`:import` / `:export` / `:import`):
`POST {base}/import` (multipart field `file`, row-validated + transactional insert),
`GET {base}/export` (Excel attachment reusing page query conditions),
`GET {base}/import-template` (header-only template download).

### 4. Method-Level Annotations: When You Don't Want the Whole Class Taken Over

Leave the method body empty (`return null`); AOP takes over. Mix freely with `@QuickCrud` and hand-written methods:

```java

@RestController
@RequestMapping("/product")
public class ProductController {

    @QuickSave(entity = Product.class)                          // create (entity or List<entity> for batch)
    @PostMapping
    public R<Object> save(@RequestBody Product product) {
        return null;
    }

    @QuickUpdate(entity = Product.class)                        // update (by ID, null fields skipped)
    @PutMapping
    public R<Object> update(@RequestBody Product product) {
        return null;
    }

    @QuickRemove(entity = Product.class)                        // delete (single/List/comma-separated ids)
    @DeleteMapping("/{ids}")
    public R<Object> remove(@PathVariable("ids") String ids) {
        return null;
    }

    @QuickExport(entity = Product.class)                        // export: reuses page conditions, Excel download
    @GetMapping("/export")
    public void export(HttpServletResponse response) {
    }

    @QuickImport(entity = Product.class, permission = "product:import")  // import: validate + transactional insert
    @PostMapping("/import")
    public R<Object> importExcel(MultipartFile file) {
        return null;
    }
}
```

- Permission: the annotation's `permission` is a **full permission code** (empty = no check), enforced by the unified
  interceptor
- Excel headers: add FastExcel's `@ExcelProperty("Name")` on entity fields, otherwise the field name is used
- Import strategy: if any row fails Bean Validation nothing is inserted (400 + per-row error details); inserts share one
  transaction

### 5. Login & Permissions: Built-in Sa-Token (or Custom SPIs)

With the starter on the classpath and no custom `UserResolver`/`PermissionChecker`, Sa-Token is bridged automatically:

```java
// Login: use Sa-Token directly (recommend sa-token.token-name=Authorization to align with the framework)
StpUtil.login(user.getId());

// Permission data: implement Sa-Token's StpInterface (query your own RBAC tables)
@Component
public class MyStpInterface implements StpInterface {
    public List<String> getPermissionList(Object loginId, String loginType) { ... }
    public List<String> getRoleList(Object loginId, String loginType) { ... }
}
```

After that, `@RequiresPerm` / `@QuickCrud` codes / method-annotation `permission` all go through Sa-Token,
and `AuthContext.getUser()` returns the loginId. You can also skip Sa-Token entirely by providing your own SPIs:

```java
@Component
public class MyAuthService implements UserResolver, PermissionChecker {

    @Override
    public Object getUser(String token) {        // token -> current user; null means not logged in
        return tokenStore.get(token);
    }

    @Override
    public boolean hasPermission(Object user, String permission) {
        return rbacService.codesOf(user).contains(permission);
    }
}
```

The token is read from the `Authorization` header by default (a `Bearer` prefix is tolerated),
or from the `?token=` parameter.

### 6. Configuration (all optional)

```yaml
quick-dev:
  enabled: true            # master switch for @QuickCrud endpoint registration
  db-type: mysql           # pagination dialect (generic if omitted; ignored when a custom MybatisPlusInterceptor exists)
  method-op:
    enabled: true          # method-level annotations (@QuickSave etc.) AOP switch
  auto-fill:
    enabled: true          # createTime/updateTime auto-fill switch
  repeat-submit:
    enabled: true          # @NoRepeatSubmit switch
  log:
    enabled: true          # @QuickLog operation log switch
  translate:
    enabled: true          # @Translate field translation switch
    cache-seconds: 60      # translation result cache TTL seconds (0 disables)
  auth:
    enabled: true          # auth master switch
    token-header: Authorization
    token-param: token
```

## Query Parameter Conventions (page / list)

| Parameter                    | Description                                                                               |
|------------------------------|-------------------------------------------------------------------------------------------|
| `current` / `size`           | Pagination, defaults 1 / 10, size capped at 1000                                          |
| Same name as entity property | Becomes a condition per `@QueryField` (EQ by default), value type-converted automatically |
| `orderBy` / `order`          | Sort field (must be an entity property name, injection-safe) + `asc`/`desc`               |

Example: `GET /sys-user/page?current=1&size=10&username=ad&status=1&orderBy=create_time&order=desc`

> Note: `orderBy` takes the **entity property name** (createTime); the framework maps it to the column internally.

## Permission Annotations on Regular Endpoints

```java
@RestController
public class ReportController {

    @RequiresPerm("report:export")            // multiple codes are AND-ed
    @GetMapping("/report/export")
    public R<Object> export() {
        SysUser current = AuthContext.getUser(); // current user (loginId under Sa-Token)
        ...
    }

    @RequiresRole("admin")                    // role check, supports logical = Logical.OR
    @RequiresRole(value = {"admin", "auditor"}, logical = Logical.OR)
    @GetMapping("/report/audit")
    public R<Object> audit() { ... }

    @RequiresLogin                            // login only
    @GetMapping("/report/mine")
    public R<Object> mine() { ... }
}
```

> Role data source mirrors permissions: implement `StpInterface.getRoleList` under Sa-Token,
> or a `RoleChecker` bean with custom SPIs.

### Repeat-Submit Protection / Operation Log

```java

@NoRepeatSubmit(interval = 2000)                 // same user hitting twice within 2s -> 400
@PostMapping("/order")
public R<Object> create(@RequestBody Order order) { ...}

@QuickLog(module = "Orders", description = "Create order")   // audit log
@NoRepeatSubmit(interval = 2000)
@PostMapping("/order")
public R<Object> create(@RequestBody Order order) { ...}
```

`@QuickLog` records: module/description, operator (loginId), URI, HTTP method, IP, params JSON (truncated),
result code, success flag, error message, and duration. Persistence is up to the `OperationLogSink` SPI (register a bean
to take over; default logs to Slf4j logger `quick-dev.operation-log`; async persistence recommended in production).

### OpenAPI Docs (Optional, springdoc)

Add the springdoc dependency and the framework automatically **injects dynamic CRUD endpoints
into the Swagger docs** (springdoc does not natively recognize runtime-registered endpoints):

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.17</version>
</dependency>
```

Visit `/swagger-ui.html`: dynamic endpoints carry `[QuickCrud]` summaries with query parameter
descriptions; method-annotation endpoints carry `[QuickSave]`-style markers.

### Redis Support (Optional)

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

Works immediately with zero code:

- **Sa-Token state in Redis** (jackson serialization): login state and permission cache shared across instances,
  survives restarts
- **Repeat-submit on Redis**: atomic `setIfAbsent + TTL` placeholder, effective across a cluster (replaces the in-memory
  store automatically; implement `RepeatSubmitStore` to customize)

### Field Translation (@Translate)

Annotate entity/VO fields to translate values into readable text during JSON serialization — great for list pages:

```java
public class OrderVO {

    /** Dictionary: value -> label (DictResolver SPI queries dict tables/enums) */
    @Translate(dict = "order_status")
    private Integer status;                  // 1 serialized as "Paid"

    /** Enum: enum implements DictEnum, no dict table needed */
    @Translate(enumClass = OrderStatus.class)
    private Integer type;                    // 1 serialized as "Standard"

    /** Entity reference: field value is the target's primary key */
    @Translate(entity = SysUser.class, field = "nickname")
    private String createBy;                 // "1" serialized as "Admin"
}
```

- Translation happens at serialization time: **zero intrusion** — every JSON endpoint (page/detail/export) just works
- On failure (no dict entry, no record, no SPI) the **original value is kept**; endpoints never break
- Results are TTL-cached locally (default 60s): `quick-dev.translate.cache-seconds` (0 disables),
  `quick-dev.translate.enabled=false` disables entirely
- Dictionary sources: implement a `DictResolver` bean (dict table / enum / remote service);
  for fixed enums use `enumClass` directly (implement the `DictEnum` interface)

### Dictionary Data Sources: DictLoader SPI or Import Endpoint (framework never queries the DB)

Dictionary data is entirely supplied by the user — choose either path (they can coexist):

**Path 1: implement DictLoader (remote dict service / config center / your own tables)**

```java

@Component
public class RemoteDictLoader implements DictLoader {
    @Override
    public List<DictEntry> loadAll() {
        return remoteDictClient.fetchAll().stream()
                .map(d -> new DictEntry(d.type(), d.value(), d.label()))
                .toList();
    }
}
```

Once registered: lazy-load on first access, rebuild via `POST /quick-dev/dict/refresh`
(`dict:refresh` permission), and periodic auto-refresh via `quick-dev.dict.refresh-interval-seconds`.

**Path 2: import endpoint (upload full data in the prescribed format, zero external dependencies)**

```bash
POST /quick-dev/dict/import        # requires dict:import permission
[{"type":"user_status","value":"1","label":"Enabled"},
 {"type":"user_status","value":"0","label":"Disabled"}]
```

- **Replaces** the whole cache (not a merge); takes effect **immediately** (translation cache cleared)
- Entries with a null type/value/label are skipped; the response carries the valid `size`

Both paths make `@Translate(dict = "user_status")` work in both directions automatically,
including Excel import reverse resolution; custom `DictResolver`/`DictReverseResolver` beans
still take precedence.

### Import Reverse Translation (Upload Conversion)

Users often type readable labels in uploaded Excel files ("Enabled"/"Online"); the framework **resolves them back to
stored values before type conversion and validation**:

| @Translate mode          | Reverse resolution                                                                  |
|--------------------------|-------------------------------------------------------------------------------------|
| `enumClass = ...` enum   | Automatic: match `DictEnum.getLabel()`, return the value                            |
| `dict = ...` dictionary  | **User-provided** `DictReverseResolver` SPI (label -> value)                        |
| `entity = ...` reference | Automatic: look up the primary key by the target property (first match if multiple) |

Additionally `@QuickExport(translate = true)` makes exported Excel files contain translated labels (going through the
Jackson pipeline so `@JsonIgnore` applies too), closing the loop:
**export -> edit -> import back**.

```java
/** Reverse SPI (symmetric to DictResolver): "Online" in Excel -> 1 */
@Component
public class MyDictReverseResolver implements DictReverseResolver {
    @Override
    public Object reverse(String dictType, String label) {
        return dictMapper.selectOne(...).getDictValue();
    }
}
```

- On reverse failure the raw text is kept and goes through type conversion;
  a type mismatch reports "row N [column] value cannot be converted"
- Raw numeric values also work (both `1` and `"Enabled"` import fine)

### Row-Level Data Permission (@DataScope)

Annotate the entity, implement one `DataScopeResolver`, and every query endpoint filters rows automatically:

```java

@DataScope(column = "dept_id")     // this column is filtered by visible scope
@TableName("sys_user")
public class SysUser { ...
}

@Component
public class MyDataScopeResolver implements DataScopeResolver {
    @Override
    public Collection<?> visibleScope(Class<?> entityClass, String column, Object currentUser) {
        if (isAdmin(currentUser)) {
            return null;                       // null = no restriction
        }
        return deptService.deptIdsOf(currentUser); // only my department (include children as your query decides)
    }
}
```

- Scope: page / list / count / tree / export (anything built through the query condition layer,
  including method-level `@QuickExport`)
- An **empty collection** from the resolver means nothing is visible (safe default); no resolver = no filtering
- Detail/delete by primary key bypass the condition layer and are not row-filtered (enforce strict isolation in your
  business layer if needed)

## Running the Demo

```bash
cd quick-dev-demo
mvn spring-boot:run
```

Built-in accounts (H2 in-memory DB, see `data.sql` for seeds):

| Account | Password  | Permissions                              | Role  |
|---------|-----------|------------------------------------------|-------|
| admin   | admin123  | `*` (all)                                | admin |
| viewer  | viewer123 | only `sys:user:list` / `sys:user:detail` | none  |

```bash
# Login for a token
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# Pagination + fuzzy search (token in the Authorization header)
curl "http://localhost:8080/sys-user/page?current=1&size=10&username=ad" \
  -H "Authorization: {token}"

# Create / update / delete
curl -X POST http://localhost:8080/sys-user -H "Authorization: {token}" \
  -H "Content-Type: application/json" \
  -d '{"username":"tester","nickname":"Test","email":"t@q.cn","status":1}'

curl -X PUT http://localhost:8080/sys-user -H "Authorization: {token}" \
  -H "Content-Type: application/json" \
  -d '{"id":4,"nickname":"Renamed"}'

curl -X DELETE http://localhost:8080/sys-user/4 -H "Authorization: {token}"

# /product has no permission configured — accessible without login (UUID string PK demo)
curl http://localhost:8080/product/page
```

## Unified Response

```json
{ "code": 200, "msg": "success", "data": { ... }, "success": true }
```

| Scenario                      | HTTP status | code |
|-------------------------------|-------------|------|
| Success                       | 200         | 200  |
| Param/validation error        | 200         | 400  |
| Not logged in / token expired | 401         | 401  |
| Insufficient permission       | 403         | 403  |
| Record not found (detail)     | 200         | 404  |
| Other business/system error   | 200         | 500  |

## How It Works

```
Application startup
  └─ quick-dev-spring-boot-autoconfigure (Spring Boot 3 auto-configuration via AutoConfiguration.imports)
       ├─ QuickCrudRegistrar (SmartInitializingSingleton)
       │    └─ scan @QuickCrud beans -> resolve entity/mapper/path/permissions
       │         └─ RequestMappingHandlerMapping.registerMapping(...)
       │              dynamically register page/list/{id}/save/update/{ids} endpoints
       ├─ AuthInterceptor (intercepts /**)
       │    └─ @RequiresPerm / @RequiresLogin / CRUD permission codes
       │         -> UserResolver (token->user) -> PermissionChecker (user->permission)
       └─ GlobalExceptionHandler (exceptions -> R)
```

Key point: dynamically registered handlers deliberately bind parameters as concrete types (`String`/`Map`) and resolve
entity types internally via Jackson / ConversionService,
avoiding the generic-erasure type resolution problem. Path conflicts follow Spring rules (literals win over `{id}`
templates).

## Known Boundaries & Conventions

- Update is a partial update: no whole-entity Bean Validation (create only); `null` fields are not updated
- An excluded operation whose path overlaps a kept template (e.g. LIST excluded, `/list` requested)
  falls through to the `/{id}` detail route and returns "record not found"
- Auth fails **closed**: an endpoint that declares a permission requirement without a
  `UserResolver`/`PermissionChecker` implementation raises a configuration error instead of passing through
- The auto-configured pagination interceptor only applies when no custom `MybatisPlusInterceptor` exists

## Appendix: Configuration Reference (prefix quick-dev)

| Property                        | Default                   | Description                                                                             |
|---------------------------------|---------------------------|-----------------------------------------------------------------------------------------|
| `enabled`                       | `true`                    | Master switch for @QuickCrud endpoint registration                                      |
| `error-detail`                  | `true`                    | Expose unexpected exception details to clients (false returns a generic message)        |
| `db-type`                       | -                         | Pagination dialect (mysql/h2/postgresql…; ignored with a custom MybatisPlusInterceptor) |
| `auth.enabled`                  | `true`                    | Auth master switch (false makes all auth annotations pass)                              |
| `auth.token-header`             | `Authorization`           | Token header (Bearer prefix tolerated)                                                  |
| `auth.token-param`              | `token`                   | Fallback token request parameter                                                        |
| `method-op.enabled`             | `true`                    | Method-level annotation (@QuickSave etc.) AOP switch                                    |
| `auto-fill.enabled`             | `true`                    | Time/operator auto-fill switch                                                          |
| `repeat-submit.enabled`         | `true`                    | @NoRepeatSubmit switch                                                                  |
| `log.enabled`                   | `true`                    | @QuickLog operation log switch                                                          |
| `translate.enabled`             | `true`                    | @Translate field translation switch                                                     |
| `translate.cache-seconds`       | `60`                      | Translation cache TTL seconds (0 disables)                                              |
| `dict.enabled`                  | `true`                    | Built-in DB dictionary switch (effective with JdbcTemplate on classpath)                |
| `dict.table`                    | `sys_dict`                | Dictionary table name                                                                   |
| `dict.type-column`              | `dict_type`               | Type column                                                                             |
| `dict.value-column`             | `dict_value`              | Value column                                                                            |
| `dict.label-column`             | `dict_label`              | Label column                                                                            |
| `dict.refresh-endpoint-enabled` | `true`                    | Dict cache refresh endpoint switch                                                      |
| `dict.refresh-path`             | `/quick-dev/dict/refresh` | Refresh endpoint path (dict:refresh permission)                                         |
| `dict.admin-endpoint-enabled`   | `true`                    | Dict admin endpoints switch                                                             |
| `dict.admin-path`               | `/quick-dev/dict`         | Admin endpoint prefix (dict:manage permission)                                          |
| `limits.export-max-rows`        | `100000`                  | Max rows per export (truncated with a warning beyond)                                   |
| `limits.import-max-rows`        | `10000`                   | Max rows per import (rejected beyond)                                                   |
| `limits.in-max-size`            | `1000`                    | Max values per IN condition (400 beyond)                                                |

See the Sa-Token docs for `sa-token.*` (token-name, timeout, …) and Spring Boot docs for `spring.data.redis.*`.

## License & Contributing

- Licensed under [Apache License 2.0](LICENSE); third-party dependencies are listed in [NOTICE](NOTICE)
- Contributions are welcome — see the [Contributing Guide](CONTRIBUTING.md) (commit format, tests, module
  responsibilities)
- Community standards: [Code of Conduct](CODE_OF_CONDUCT.md)
- Bugs and ideas: [open an issue](https://github.com/xuya-dev/Quick-Dev/issues) (bug report / feature request templates
  provided)
- Detailed docs (Chinese): [User Guide](docs/user-guide.md) · [AI assistant guide](docs/AGENT.md)

## Requirements

- JDK 17+, Spring Boot 3.3+, MyBatis-Plus 3.5.9+ (the pagination artifact `mybatis-plus-jsqlparser`
  is already pulled in transitively by the framework)
