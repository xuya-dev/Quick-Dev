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

## Navigation

- [Features](#features)
- [Module Layout](#module-layout) / [Quick Start](#quick-start)
- [Query Parameter Conventions](#query-parameter-conventions-page--list) / [Permission Annotations on Regular Endpoints](#permission-annotations-on-regular-endpoints)
- [Method-level annotations](#4-method-level-annotations-when-you-dont-want-the-whole-class-taken-over) (section 4)
- [Field translation (@Translate)](#field-translation-translate) (incl. dict cache/management/import reverse resolution)
- [Row-level data permission (@DataScope)](#row-level-data-permission-datascope) / [Repeat-submit & operation log](#repeat-submit-protection--operation-log)
- [Redis support](#redis-support-optional) / [Running the Demo](#running-the-demo)
- [Appendix: Configuration Reference](#appendix-configuration-reference-prefix-quick-dev)
- [License & Contributing](#license--contributing)
- Detailed docs (Chinese): [Full manual](docs/manual.md) · [User guide](docs/user-guide.md) · [AI assistant guide](docs/AGENT.md) · [Publish guide](docs/publish-guide.md)

```java

@QuickCrud(entity = SysUser.class, permission = "sys:user")
public class SysUserController {
    // empty — no methods needed
}
```

Nine endpoints are registered at startup:

| Method | Path                       | Permission        | Description                                                        |
|--------|----------------------------|-------------------|--------------------------------------------------------------------|
| GET    | `/sys-user/page`           | `sys:user:list`   | Pagination (current/size + dynamic conditions + sorting)           |
| GET    | `/sys-user/list`           | `sys:user:list`   | List query (no pagination)                                         |
| GET    | `/sys-user/count`          | `sys:user:list`   | Count by conditions                                                |
| GET    | `/sys-user/{id}`           | `sys:user:detail` | Detail                                                             |
| POST   | `/sys-user`                | `sys:user:add`    | Create (with Bean Validation)                                      |
| POST   | `/sys-user/batch`          | `sys:user:add`    | Batch create (JSON array, per-row validation + Db.saveBatch)       |
| POST   | `/sys-user/save-or-update` | `sys:user:add`    | Upsert: updates with an ID, creates without (update branch skips whole-entity validation) |
| PUT    | `/sys-user`                | `sys:user:edit`   | Update (by ID, null fields skipped)                                |
| DELETE | `/sys-user/{ids}`          | `sys:user:remove` | Delete, comma-separated `ids` for batch                            |

Optional operations (add to `includes`): `POST {base}/import` Excel import (`:import`),
`GET {base}/export` Excel export (`:export`), `GET {base}/import-template` template download (`:import`),
`GET {base}/tree` tree query (`:list`; entity needs `parentId` + `children` fields,
`children` annotated with `@TableField(exist = false)`, null/0 parentId means root;
`parentId` cycles and self-references are detected and rejected with an error instead of recursing forever,
orphan nodes whose parent is missing are returned as roots).

## Features

- **One annotation for full CRUD**: `@QuickCrud` on a controller registers endpoints at runtime via
  `RequestMappingHandlerMapping` (the officially supported way) and coexists freely with hand-written endpoints
- **Method-level annotations**: `@QuickSave` / `@QuickUpdate` / `@QuickRemove` / `@QuickExport` / `@QuickImport`
  annotated directly on methods — leave the body empty and framework AOP takes over; no need to hand the whole
  class to `@QuickCrud`
- **Built-in Sa-Token**: auto-bridges login state and permission checks when on the classpath (`StpUtil.login`
  to sign in, implement `StpInterface` to supply permission data) — zero config to wire
  `@RequiresPerm` / `@QuickCrud` permission codes
- **Excel import/export**: FastExcel-based; export reuses query conditions, import validates automatically +
  inserts transactionally in batch
- **Annotation-driven access control**: `@RequiresPerm` / `@RequiresLogin` work on any controller;
  `@QuickCrud` endpoints enforce the `prefix:action` convention automatically; method-level annotations
  enforce the full `permission` code
- **Replaceable auth**: beyond built-in Sa-Token, implement the `UserResolver` (token→user) and
  `PermissionChecker` (user→permission codes) SPIs to integrate any system
- **Declarative query conditions**: `@QueryField(LIKE/GT/IN/BETWEEN/...)` on entity fields; same-named request
  parameters become query conditions with automatic type conversion
- **Audit auto-fill**: `createTime`/`updateTime` + `createBy`/`updateBy` (current login user) filled on
  insert/update (just annotate the fields with `@TableField(fill = ...)`, see below)
- **Field translation (VO Translation)**: `@Translate` on fields translates IDs/status codes into readable text
  in JSON output (dict / enum / entity-ref modes) with TTL caching; **Excel import reverses labels back to
  values automatically** (label -> stored value); dict data is supplied by your own `DictLoader` SPI —
  **the framework never queries the DB**; the APPEND mode keeps the raw value and writes the translation into a
  sibling field (edit forms that need the raw ID while lists show readable text)
- **Row-level data permission**: `@DataScope(column = "dept_id")` on an entity auto-filters page/list/count/tree/export
  by the visible scope returned from `DataScopeResolver` ("see only my department")
- **Optional Redis**: adding `quick-dev-redis-spring-boot-starter` moves Sa-Token login state / permission cache
  to Redis (shared across instances, survives restarts) and switches repeat-submit protection to an atomic
  Redis implementation
- **Tree query**: `CrudOp.TREE` outputs dept/menu/category trees with one annotation (entity declares
  `parentId` + `children` and that's it)
- **Repeat-submit protection**: `@NoRepeatSubmit(interval)` blocks repeated clicks by user + endpoint fingerprint
- **Multi-column sorting**: `?orderBy=createTime,id&order=desc,asc` maps columns one to one (names are
  entity-property whitelisted; non-table fields are skipped automatically)
- **In-memory relation translation (TranslateSource SPI)**: `@Translate(entity=...)` prefers values from a
  business-side full in-memory cache and only falls back to the database on miss; dicts accept a standard JSON
  upload (`DictJson.parse` + `DictCacheService.replaceByJson`) — zero DB queries across the whole chain
- **Security hardening**: unique-key conflicts become friendly 400s; an empty data scope means nothing is
  visible; the write path force-clears logic-delete and audit fields (clients cannot delete out of scope or
  forge ownership); repeat-submit fingerprints hash the token; URL token passing is disabled by default;
  LIKE supports a wildcard-escape switch
- **Staged parameter validation**: create validates the whole entity (`Default + Create` groups); update follows
  partial-update semantics — only submitted non-null fields are validated (`Default + Update` groups), so
  `@NotBlank` still rejects empty strings while unsubmitted fields are untouched; built-in `@QuickRequire`
  conditional-required annotation (this field becomes required when a dependent field matches; supports groups,
  repeatable); for complex cross-field rules write a custom constraint or a CrudHook
- **CRUD lifecycle hooks**: implement the `CrudHook` SPI (beforeSave/afterSave/beforeUpdate/afterUpdate/
  beforeRemove/afterRemove) to join the built-in write flows — multi-table binding and cache refresh no longer
  need handwritten controllers for aggregates; writes run inside one transaction (a before-hook exception rolls
  back; after hooks run post-commit, failures only warn)
- **Operation log**: `@QuickLog` records operator/params/result/duration; persistence via the `OperationLogSink`
  SPI (prints to Slf4j by default)
- **Unified response & exceptions**: `R<T>` structure + global handler (401 unauthenticated, 403 forbidden,
  400 param/validation)
- All existing MyBatis-Plus capabilities remain available: logic delete, optimistic locking, multi-tenancy,
  `@TableName` mapping, etc.

## Module Layout

```
quick-dev
├── quick-dev-core                      Core library: annotations / CRUD engine / auth / R / exceptions (all deps optional)
├── quick-dev-spring-boot-autoconfigure Auto-configuration (Properties + AutoConfiguration + Redis conditions)
├── quick-dev-spring-boot-starter       ★ The only dependency users need (aggregates core + web + validation + MyBatis-Plus + pagination)
├── quick-dev-redis-spring-boot-starter Optional Redis support (Sa-Token storage + atomic repeat-submit)
├── quick-dev-codegen                   Code generator (table schema -> entity/mapper/controller, zero-dep pure JDK)
└── quick-dev-demo                      Demo application (H2 in-memory DB + built-in accounts, ready to run)
```

## Quick Start

### 1. Add the Starter (the only dependency you need)

```xml
<dependency>
    <groupId>dev.xuya</groupId>
    <artifactId>quick-dev-spring-boot-starter</artifactId>
    <version>0.4.0</version>
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
// includes/excludes: register/exclude specific ops; IMPORT/EXPORT/IMPORT_TEMPLATE are opt-in via includes
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

Leave the method body empty (`return null`); framework AOP takes over. Mix freely with `@QuickCrud`
and hand-written methods:

```java

@RestController
@RequestMapping("/product")
public class ProductController {

    @QuickSave(entity = Product.class)                          // create (entity or List<entity> for batch)
    @PostMapping
    public R<Object> save(@RequestBody Product product) { return null; }

    @QuickUpdate(entity = Product.class)                        // update (by ID, null fields skipped)
    @PutMapping
    public R<Object> update(@RequestBody Product product) { return null; }

    @QuickRemove(entity = Product.class)                        // delete (single/List/comma-separated ids)
    @DeleteMapping("/{ids}")
    public R<Object> remove(@PathVariable("ids") String ids) { return null; }

    @QuickExport(entity = Product.class)                        // export: reuses page conditions, Excel download
    @GetMapping("/export")
    public void export(HttpServletResponse response) { }

    @QuickImport(entity = Product.class, permission = "product:import")  // import: validate + transactional insert
    @PostMapping("/import")
    public R<Object> importExcel(MultipartFile file) { return null; }
}
```

- Permission: the annotation's `permission` is a **full permission code** (empty = no check), enforced by the unified
  interceptor
- Excel headers: add FastExcel's `@ExcelProperty("Name")` on entity fields, otherwise the field name is used
- Import strategy: if any row fails Bean Validation nothing is inserted (400 + per-row error details); inserts share
  one transaction. Validation applies the Create group plus `@QuickRequire`; a file whose headers cannot be matched
  at all is rejected with 400 instead of silently inserting empty records; the `limits.import-max-rows` cap is
  enforced at parse time

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

The token is read from the `Authorization` header by default (a `Bearer` prefix is tolerated).
A `?token=xxx` request parameter works only when `auth.token-param` is configured explicitly —
URL tokens are disabled by default since 0.4.0 (they leak into access logs).

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
  crud:
    update-validate: true   # update (partial) validates submitted non-null fields + applies @QuickRequire
    default-includes: PAGE,LIST,DETAIL,SAVE,UPDATE,REMOVE   # global default ops (when the annotation omits includes)
    default-excludes: SAVE_BATCH,SAVE_OR_UPDATE             # global ops excluded (subtracted from all controllers)
  translate:
    enabled: true          # @Translate field translation switch
    cache-seconds: 60      # local cache TTL for translation results (0 = no caching, translate on every call)
  auth:
    enabled: true          # auth master switch
    token-header: Authorization
    token-param: token      # URL tokens are disabled by default since 0.4.0; opt in explicitly like this
```

## Query Parameter Conventions (page / list)

| Parameter                    | Description                                                                               |
|------------------------------|-------------------------------------------------------------------------------------------|
| `current` / `size`           | Pagination, defaults 1 / 10, size capped at `limits.query-max-rows` (default 1000)        |
| Same name as entity property | Becomes a condition per `@QueryField` (EQ by default), value type-converted automatically |
| `orderBy` / `order`          | Sort fields (**entity property names** whitelist, comma-separated for multiple columns; a non-table field is rejected); `order` maps `asc`/`desc` column by column |

Example: `GET /sys-user/page?current=1&size=10&username=ad&status=1&orderBy=createTime&order=desc`
Multi-column: `orderBy=createTime,id&order=desc,asc` (mapped one to one; missing entries default to asc)

> Note: `orderBy` takes **entity property names** (e.g. `createTime`); a column name (`create_time`) is rejected.
> Unpaginated `list`/`tree` queries are capped by `limits.query-max-rows` — beyond it the request fails with 400
> instead of being silently truncated.

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

### Staged Parameter Validation (Create / Update)

Create validates the whole entity; update follows **partial-update semantics** (null fields are not updated) —
the framework validates only the submitted non-null fields one by one, so unsubmitted fields are never hit:

```java
public class Goods {
    @NotBlank(groups = Create.class)                 // required on create only (not enforced on update)
    private String name;

    @Size(max = 200)                                 // validated on both create and update (submitted values)
    private String remark;

    @QuickRequire(dependField = "stock", dependValue = "0",
            groups = {Create.class, Update.class})   // conditional required: reason is required when stock = 0 (sold out)
    private String reason;
}
```

| Stage                                                             | Validation scope               | Groups             |
|-------------------------------------------------------------------|--------------------------------|--------------------|
| Create (POST / the create branch of save-or-update)               | whole entity                   | `Default + Create` |
| Update (PUT / the update branch of save-or-update / @QuickUpdate) | only submitted non-null fields | `Default + Update` |

- Groups are standard jakarta Bean Validation groups: the framework ships `Create`/`Update` marker interfaces
  (`dev.xuya.core.validation`); custom constraints can declare a group to join either stage
- `@QuickRequire(dependField, dependValue)`: this field becomes required when the dependent field's value matches
  (string comparison); repeatable. On the update stage it only fires when the dependent field is present in the
  payload and matches — for rules that need the full database state write a `CrudHook` instead
- Excel import applies the Create group plus `@QuickRequire` as well
- Switch: `quick-dev.crud.update-validate=false` disables update validation (create validation is always on)

### CRUD Lifecycle Hooks (CrudHook)

Implement the `CrudHook` SPI and register it as a Spring bean to join every write flow generated by `@QuickCrud`,
covering "validate + multi-table binding + cache refresh" aggregate logic — no more handwritten controllers
for aggregates:

```java
@Component
public class DictDataHook implements CrudHook {

    @Override
    public Class<?> entityType() {
        return SysDictData.class;
    }

    @Override
    public void beforeSave(Object entity) { /* inside the transaction: validate, fill defaults; throw to roll back */ }

    @Override
    public void afterSave(Object entity) { /* after commit: refresh caches, publish events; failures only warn */ }
}
```

| Hook                           | Timing                                      | Exception semantics                               |
|--------------------------------|---------------------------------------------|---------------------------------------------------|
| `beforeSave`                   | create, inside the transaction              | throw to roll back the write and fail the request |
| `afterSave`                    | create, after commit                        | failures only log WARN, the response is unaffected |
| `beforeUpdate` / `afterUpdate` | update (after fires only when rows changed) | same as above                                     |
| `beforeRemove` / `afterRemove` | delete (ids is a list of primary-key type)  | same as above                                     |

- Multiple hooks per entity run in Spring bean order; all methods are default no-ops, override what you need
- `saveBatch` calls `beforeSave` per row inside the same transaction; `saveOrUpdate` dispatches Save/Update hooks
  by branch
- Writes (save/saveBatch/saveOrUpdate/update/remove) run inside one transaction: without transaction infrastructure
  they execute directly with unchanged semantics

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
result code, success flag, error message, and duration. Persistence is up to you (the framework does not write to
the DB directly):

1. Register a custom `OperationLogSink` bean (DB/ES/MQ; choose your own threading model: synchronous writes or an
   internal async batcher)
2. Without one, output goes to the Slf4j logger `quick-dev.operation-log` by default

### Code Generator (quick-dev-codegen)

Generate the Quick Dev trio (entity/mapper/@QuickCrud controller) from database table schemas in one command,
zero dependencies, pure JDK:

```bash
java -cp quick-dev-codegen-0.4.0.jar dev.xuya.codegen.CodeGenerator \
  --url=jdbc:mysql://localhost:3306/demo --user=root --password=root \
  --table=t_order --package=com.example.order --out=src/main/java
```

Conventions: numeric primary-key columns produce `IdType.AUTO`, character columns produce `ASSIGN_UUID`; audit
columns (create_time/update_time/create_by/update_by) automatically get `@TableField(fill=...)`; column comments
become field Javadoc; the controller's `permission` is a suggested prefix (e.g. `t:order`) — adjust it to your
business. Also callable programmatically via `CodeGenerator.generate(...)`.

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
    <version>0.4.0</version>
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
  logins survive restarts
- **Repeat-submit on Redis**: atomic `setIfAbsent + TTL` placeholder, effective across a cluster (replaces the in-memory
  store automatically; implement a `RepeatSubmitStore` bean to customize)

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

    /** Entity reference: the field value is the target entity's primary key, one of its properties is read */
    @Translate(entity = SysUser.class, field = "nickname")
    private String createBy;                 // "1" serialized as "Admin"

    /** Append mode (APPEND): deptId keeps its raw value and a sibling field deptName="R&D" is added
     *  — for edit forms that need the raw ID while lists show readable text */
    @Translate(entity = SysDept.class, field = "deptName",
            mode = TranslateMode.APPEND, appendField = "deptName")
    private Long deptId;
}
```

- Translation happens at serialization time: **zero intrusion** — every endpoint that returns JSON (page/detail/export)
  just works
- On failure (no dict entry, no record, no SPI) the **original value is kept**; endpoints never break
- Results are TTL-cached locally (default 60s) to avoid repeated lookups for the same value on list pages:
  `quick-dev.translate.cache-seconds` (0 = no caching, translate on every call), `quick-dev.translate.enabled=false`
  disables translation entirely
- Dictionary sources: implement a `DictResolver` bean (dict table / enum / remote service);
  for fixed enums use `enumClass` directly (implement the `DictEnum` interface)
- APPEND mode: with `quick-dev.translate.enabled=true` the framework auto-registers `TranslateAppendModule`, which
  appends a sibling property for APPEND fields during serialization (default name "fieldName+Name", override via
  `appendField`); when no translation result exists the property is serialized as null, REPLACE keeps its default
  behavior

### Dictionary Data Source: DictLoader SPI (framework never queries the DB)

Dictionary data is entirely supplied by the user — implement the `DictLoader` interface to return all entries:

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
(re-runs loadAll, requires the `dict:refresh` permission), and periodic auto-refresh via
`quick-dev.dict.refresh-interval-seconds` (eventually consistent across instances).
You can also inject `DictCacheService` and call `replaceAll(entries)` programmatically
(full replacement, takes effect immediately) — handy for syncing the framework cache after saving dicts in your
own admin UI.

Standard JSON dict upload (zero DB queries): `DictJson.parse(json)` builds entries, or call
`DictCacheService.replaceByJson(json)` directly with a standard JSON array:

```java
dictCacheService.replaceByJson("""
    [{"type":"sys_yes_no","value":"Y","label":"Yes"},
     {"type":"sys_yes_no","value":"N","label":"No"}]
    """);
```

`@Translate(dict = "user_status")` then works in both directions automatically, including Excel
import reverse resolution; when you provide your own `DictResolver` / `DictReverseResolver` bean, the built-in
resolver steps aside for that direction.

### Import Reverse Translation (Upload Conversion)

Users often type readable labels in uploaded Excel files ("Enabled"/"Online"/"Admin"); the framework **resolves them
back to stored values before type conversion and validation**:

| @Translate mode          | Reverse resolution                                                                  |
|--------------------------|-------------------------------------------------------------------------------------|
| `enumClass = ...` enum   | Automatic: match `DictEnum.getLabel()`, return the value                            |
| `dict = ...` dictionary  | **User-provided** `DictReverseResolver` SPI (label -> value)                        |
| `entity = ...` reference | Automatic: look up the primary key by the target property (first match if multiple) |

Additionally `@QuickExport(translate = true)` makes exported Excel files contain translated labels (going through the
Jackson pipeline so `@JsonIgnore` applies too), closing the loop with import reverse resolution:
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

- On reverse failure (no matching label) the raw text is kept and goes through type conversion;
  a type mismatch reports "row N [column] value cannot be converted"
- Raw numeric values in cells also work (both `1` and `"Enabled"` import fine)

### Row-Level Data Permission (@DataScope)

Annotate the entity and implement one `DataScopeResolver`, and every query endpoint filters rows automatically:

```java

@DataScope(column = "dept_id")     // this column is filtered by the visible scope
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
- An **empty collection** from the resolver means nothing is visible (safe default, expressed as an always-false
  condition); no resolver = no filtering
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

Unique-key conflicts (`DuplicateKeyException`) are translated into a friendly 400 without exposing SQL details.

## How It Works

```
Application startup
  └─ quick-dev-spring-boot-autoconfigure (Spring Boot 3 auto-configuration via AutoConfiguration.imports)
       ├─ QuickCrudRegistrar (SmartInitializingSingleton)
       │    └─ scan @QuickCrud beans -> resolve entity/mapper/path/permissions
       │         └─ RequestMappingHandlerMapping.registerMapping(...)
       │              dynamically register the enabled CRUD endpoints
       │              (page/list/count/{id}/save/batch/save-or-update/update/{ids}, plus opt-in tree/import/export)
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
  `UserResolver`/`PermissionChecker` implementation raises a configuration error instead of passing through;
  a rejected request no longer leaves the previous identity bound to the thread, so it cannot leak into other requests
- Unpaginated `list`/`tree` queries are capped by `limits.query-max-rows` (400 beyond, no silent truncation);
  export fetches rows in batches (`limits.export-batch-size`) and stops at `limits.export-max-rows`
- Excel imports whose headers cannot be matched at all are rejected with 400 (no silent empty inserts);
  tree data with `parentId` cycles or self-references returns an error
- The auto-configured pagination interceptor only applies when no custom `MybatisPlusInterceptor` exists

## Appendix: Configuration Reference (prefix quick-dev)

| Property                        | Default                   | Description                                                                             |
|---------------------------------|---------------------------|-----------------------------------------------------------------------------------------|
| `enabled`                       | `true`                    | Master switch for @QuickCrud dynamic endpoint registration                              |
| `error-detail`                  | `false`                   | Expose unexpected exception details to clients (true for debugging; business exceptions always pass through) |
| `db-type`                       | -                         | Pagination dialect (mysql/h2/postgresql…; ignored when the user defines a MybatisPlusInterceptor) |
| `auth.enabled`                  | `true`                    | Auth master switch (false makes all auth annotations pass)                              |
| `auth.token-header`             | `Authorization`           | Token header (Bearer prefix tolerated)                                                  |
| `auth.token-param`              | - (disabled)              | Fallback token request parameter (URL tokens leak into access logs; configure explicitly when needed) |
| `method-op.enabled`             | `true`                    | Method-level annotation (@QuickSave etc.) AOP switch                                    |
| `auto-fill.enabled`             | `true`                    | Time/operator auto-fill switch                                                          |
| `repeat-submit.enabled`         | `true`                    | @NoRepeatSubmit switch                                                                  |
| `log.enabled`                   | `true`                    | @QuickLog operation log switch                                                          |
| `crud.update-validate`          | `true`                    | Whether updates (partial) validate submitted non-null fields and apply @QuickRequire conditional required |
| `crud.default-includes`         | -                         | Global default registered ops (applies when the annotation omits `includes`; e.g. PAGE,LIST,DETAIL,SAVE,UPDATE,REMOVE) |
| `crud.default-excludes`         | -                         | Global ops excluded (subtracted from all @QuickCrud controllers; e.g. SAVE_BATCH,SAVE_OR_UPDATE) |
| `translate.enabled`             | `true`                    | @Translate field translation switch                                                     |
| `translate.cache-seconds`       | `60`                      | Local cache TTL for translation results (0 = no caching, translate on every call)       |
| `dict.enabled`                  | `true`                    | Dict in-memory cache/translation switch (data source: DictLoader SPI)                   |
| `dict.refresh-endpoint-enabled` | `true`                    | Dict cache refresh endpoint switch                                                      |
| `dict.refresh-interval-seconds` | `0`                       | Periodic dict auto-refresh interval in seconds (0 disables)                             |
| `dict.refresh-path`             | `/quick-dev/dict/refresh` | Refresh endpoint path (requires the dict:refresh permission)                            |
| `limits.query-max-rows`         | `1000`                    | Max rows returned per unpaginated list/tree query (400 beyond, no silent truncation); also caps the page `size` |
| `limits.export-max-rows`        | `100000`                  | Max rows per export (fetched in batches; stops and warns at the cap)                    |
| `limits.export-batch-size`      | `1000`                    | Batch size when export fetches rows from the database                                   |
| `limits.import-max-rows`        | `10000`                   | Max rows per import (rejected beyond; exactly at the cap is accepted)                   |
| `limits.in-max-size`            | `1000`                    | Max values per single-field IN condition (400 beyond)                                   |

See the Sa-Token docs for `sa-token.*` (token-name, timeout, …) and Spring Boot docs for `spring.data.redis.*`.

## License & Contributing

- Licensed under [Apache License 2.0](LICENSE); third-party dependencies are listed in [NOTICE](NOTICE)
- Contributions are welcome — see the [Contributing Guide](CONTRIBUTING.md) (commit format, tests, module
  responsibilities)
- Community standards: [Code of Conduct](CODE_OF_CONDUCT.md)
- Bugs and ideas: [open an issue](https://github.com/xuya-dev/Quick-Dev/issues) (bug report / feature request templates
  provided)
- Maintainer releases: [Maven Central publish guide](docs/publish-guide.md) (Portal namespace verification / GPG /
  mvn deploy -Prelease)
- Detailed docs (Chinese): [Full manual](docs/manual.md) · [User Guide](docs/user-guide.md) · [AI assistant guide](docs/AGENT.md)

## Requirements

- JDK 17+, Spring Boot 3.3+, MyBatis-Plus 3.5.9+ (the pagination artifact `mybatis-plus-jsqlparser`
  is already pulled in transitively by the framework)
