package dev.xuya.core.crud;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.xuya.core.annotation.CrudOp;
import dev.xuya.core.common.ParamException;
import dev.xuya.core.common.QuickDevException;
import dev.xuya.core.common.R;
import dev.xuya.core.excel.ExcelImportExecutor;
import dev.xuya.core.excel.ExcelSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 动态 CRUD 处理器。启动时由 {@link QuickCrudRegistrar} 为每个 @QuickCrud 控制器
 * 创建一个实例，并把这里的处理方法按需注册到 RequestMappingHandlerMapping。
 *
 * <p>注意：参数刻意使用 String/Map 等具体类型，避免泛型擦除导致 Spring 参数绑定、
 * Jackson 反序列化拿不到实体类型的问题；实体类型在构造时已知，由内部自行转换。</p>
 */
@ResponseBody
public class QuickCrudHandler {

    private static final Logger log = LoggerFactory.getLogger(QuickCrudHandler.class);

    private final EntityMeta meta;
    private final BaseMapper<Object> mapper;
    private final ObjectMapper objectMapper;
    private final ConversionService conversionService;
    private final Validator validator;
    private final boolean loginRequired;
    private final TransactionOperations transactionOperations;
    private final List<CrudHook> hooks;
    private final Map<Method, String> requiredPermissions = new HashMap<>();

    public QuickCrudHandler(EntityMeta meta, BaseMapper<Object> mapper, ObjectMapper objectMapper,
                            ConversionService conversionService, Validator validator, boolean loginRequired,
                            TransactionOperations transactionOperations, List<CrudHook> hooks) {
        this.meta = meta;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.conversionService = conversionService;
        this.validator = validator;
        this.loginRequired = loginRequired;
        this.transactionOperations = transactionOperations;
        this.hooks = hooks == null ? List.of() : hooks;
    }

    static Method methodOf(CrudOp op) throws NoSuchMethodException {
        return switch (op) {
            case PAGE -> QuickCrudHandler.class.getMethod("page", Map.class);
            case LIST -> QuickCrudHandler.class.getMethod("list", Map.class);
            case COUNT -> QuickCrudHandler.class.getMethod("count", Map.class);
            case TREE -> QuickCrudHandler.class.getMethod("tree", Map.class);
            case DETAIL -> QuickCrudHandler.class.getMethod("detail", String.class);
            case SAVE -> QuickCrudHandler.class.getMethod("save", String.class);
            case SAVE_BATCH -> QuickCrudHandler.class.getMethod("saveBatch", String.class);
            case SAVE_OR_UPDATE -> QuickCrudHandler.class.getMethod("saveOrUpdate", String.class);
            case UPDATE -> QuickCrudHandler.class.getMethod("update", String.class);
            case REMOVE -> QuickCrudHandler.class.getMethod("remove", String.class);
            case IMPORT -> QuickCrudHandler.class.getMethod("importExcel", MultipartFile.class);
            case EXPORT -> QuickCrudHandler.class.getMethod("export", HttpServletResponse.class);
            case IMPORT_TEMPLATE -> QuickCrudHandler.class.getMethod("importTemplate", HttpServletResponse.class);
        };
    }

    private static long parseLong(String value, long defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 注册端点时记录：该方法需要哪个权限码（null 表示无权限要求）
     */
    void bindPermission(CrudOp op, String permission) {
        try {
            Method method = methodOf(op);
            requiredPermissions.put(method, permission);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 拦截器回调：当前请求的 CRUD 方法所需权限码
     */
    public String getRequiredPermission(Method method) {
        return requiredPermissions.get(method);
    }

    public boolean isLoginRequired() {
        return loginRequired;
    }

    // ---------------------------------------------------------------------
    // 分页查询：GET {base}/page?current=1&size=10&username=张&status=1&orderBy=id&order=desc
    // ---------------------------------------------------------------------
    public R<Object> page(@RequestParam Map<String, String> params) {
        long current = parseLong(params.get("current"), 1);
        long size = Math.min(parseLong(params.get("size"), 10), 1000);
        Page<Object> page = new Page<>(current, size);
        return R.ok(mapper.selectPage(page, QueryHelper.build(meta, params, conversionService)));
    }

    // ---------------------------------------------------------------------
    // 列表查询（不分页）：GET {base}/list?status=1
    // ---------------------------------------------------------------------
    public R<Object> list(@RequestParam Map<String, String> params) {
        return R.ok(mapper.selectList(QueryHelper.build(meta, params, conversionService)));
    }

    // ---------------------------------------------------------------------
    // 按条件统计数量：GET {base}/count?status=1
    // ---------------------------------------------------------------------
    public R<Object> count(@RequestParam Map<String, String> params) {
        return R.ok(mapper.selectCount(QueryHelper.build(meta, params, conversionService)));
    }

    // ---------------------------------------------------------------------
    // 树形查询：GET {base}/tree（实体需有 parentId + children 字段）
    // ---------------------------------------------------------------------
    public R<Object> tree(@RequestParam Map<String, String> params) {
        List<Object> all = mapper.selectList(QueryHelper.build(meta, params, conversionService));
        return R.ok(TreeBuilder.build(meta, all));
    }

    // ---------------------------------------------------------------------
    // 详情：GET {base}/{id}
    // ---------------------------------------------------------------------
    public R<Object> detail(@PathVariable("id") String id) {
        Object entity = mapper.selectById((Serializable) convertId(id));
        if (entity == null) {
            return R.fail(404, "记录不存在");
        }
        return R.ok(entity);
    }

    // ---------------------------------------------------------------------
    // 新增：POST {base}（事务内执行 beforeSave 钩子，提交后执行 afterSave）
    // ---------------------------------------------------------------------
    public R<Object> save(@RequestBody String body) {
        Object entity = parseEntity(body);
        meta.clearSystemFields(entity);
        EntityValidator.validateSave(entity, validator);
        inTx(() -> {
            callBefore("beforeSave", h -> h.beforeSave(entity));
            mapper.insert(entity);
        });
        callAfter("afterSave", h -> h.afterSave(entity));
        return R.ok("新增成功", entity);
    }

    // ---------------------------------------------------------------------
    // 批量新增：POST {base}/batch（JSON 数组，逐条校验后批量插入，事务原子）
    // ---------------------------------------------------------------------
    public R<Object> saveBatch(@RequestBody String body) {
        List<Object> list;
        try {
            list = objectMapper.readValue(body, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, meta.getEntityClass()));
        } catch (Exception e) {
            throw new ParamException("请求体解析失败（需要 JSON 数组）: " + e.getLocalizedMessage(), e);
        }
        if (list.isEmpty()) {
            throw new ParamException("批量新增列表不能为空");
        }
        list.forEach(meta::clearSystemFields);
        if (validator != null) {
            for (int i = 0; i < list.size(); i++) {
                try {
                    EntityValidator.validateSave(list.get(i), validator);
                } catch (ParamException e) {
                    throw new ParamException("第 " + (i + 1) + " 条: " + e.getMessage());
                }
            }
        }
        inTx(() -> {
            for (Object entity : list) {
                callBefore("beforeSave", h -> h.beforeSave(entity));
            }
            Db.saveBatch(list);
        });
        for (Object entity : list) {
            callAfter("afterSave", h -> h.afterSave(entity));
        }
        return R.ok("批量新增成功", list.size());
    }

    // ---------------------------------------------------------------------
    // 新增或修改：POST {base}/save-or-update（有 ID 走更新、无 ID 走新增；入库前校验）
    // ---------------------------------------------------------------------
    public R<Object> saveOrUpdate(@RequestBody String body) {
        // 先解析不校验：按有无 ID 分支决定校验策略（更新是部分更新，只校验提交的非空字段）
        Object entity = parseEntity(body);
        meta.clearSystemFields(entity);
        Object id = idValue(entity);
        if (id != null && !String.valueOf(id).isEmpty()) {
            boolean[] updated = {false};
            inTx(() -> {
                EntityValidator.validateUpdate(entity, validator);
                callBefore("beforeUpdate", h -> h.beforeUpdate(entity));
                updated[0] = mapper.updateById(entity) > 0;
            });
            if (updated[0]) {
                callAfter("afterUpdate", h -> h.afterUpdate(entity));
            }
            return R.ok("更新成功", updated[0]);
        }
        EntityValidator.validateSave(entity, validator);
        inTx(() -> {
            callBefore("beforeSave", h -> h.beforeSave(entity));
            mapper.insert(entity);
        });
        callAfter("afterSave", h -> h.afterSave(entity));
        return R.ok("新增成功", entity);
    }

    // ---------------------------------------------------------------------
    // 修改：PUT {base}（ID 必填，null 字段不更新；对提交的非空字段做约束校验）
    // ---------------------------------------------------------------------
    public R<Object> update(@RequestBody String body) {
        Object entity = parseEntity(body);
        meta.clearSystemFields(entity);
        Object id = idValue(entity);
        if (id == null || String.valueOf(id).isEmpty()) {
            throw new ParamException("更新时主键 " + meta.getIdProperty() + " 不能为空");
        }
        EntityValidator.validateUpdate(entity, validator);
        boolean[] updated = {false};
        inTx(() -> {
            callBefore("beforeUpdate", h -> h.beforeUpdate(entity));
            updated[0] = mapper.updateById(entity) > 0;
        });
        if (updated[0]) {
            callAfter("afterUpdate", h -> h.afterUpdate(entity));
        }
        return R.ok("更新成功", updated[0]);
    }

    // ---------------------------------------------------------------------
    // 删除（支持批量，事务原子）：DELETE {base}/1 或 DELETE {base}/1,2,3
    // ---------------------------------------------------------------------
    public R<Object> remove(@PathVariable("ids") String ids) {
        List<Object> idList = new ArrayList<>();
        for (String item : ids.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                idList.add(convertId(trimmed));
            }
        }
        if (idList.isEmpty()) {
            throw new ParamException("请指定要删除的ID");
        }
        long[] removed = {0};
        inTx(() -> {
            callBefore("beforeRemove", h -> h.beforeRemove(idList));
            removed[0] = mapper.deleteByIds(idList);
        });
        callAfter("afterRemove", h -> h.afterRemove(idList));
        return R.ok("删除成功", removed[0]);
    }

    // ---------------------------------------------------------------------
    // Excel 导入：POST {base}/import（multipart 字段名 file）
    // ---------------------------------------------------------------------
    public R<Object> importExcel(@RequestParam("file") MultipartFile file) {
        return R.ok("导入成功", ExcelImportExecutor.execute(
                mapper, meta.getEntityClass(), file, validator, transactionOperations));
    }

    // ---------------------------------------------------------------------
    // Excel 导出：GET {base}/export（复用 page 的查询条件）
    // ---------------------------------------------------------------------
    public void export(HttpServletResponse response) throws IOException {
        Map<String, String> params = new HashMap<>();
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        request.getParameterMap().forEach((k, v) -> {
            if (v != null && v.length > 0) {
                params.put(k, v[0]);
            }
        });
        List<Object> data = mapper.selectList(QueryHelper.build(meta, params, conversionService));
        ExcelSupport.write(response, meta.getEntityClass(), data);
    }

    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // Excel 导入模板：GET {base}/import-template（仅表头，供导入方填写）
    // ---------------------------------------------------------------------
    public void importTemplate(HttpServletResponse response) throws IOException {
        ExcelSupport.writeTemplate(response, meta.getEntityClass());
    }

    // ---------------------------------------------------------------------
    // 事务与 CrudHook 钩子
    // ---------------------------------------------------------------------

    /**
     * 写操作统一包事务：有事务基础设施（TransactionOperations）时在事务内执行，
     * 保证批量写入/删除的原子性；before 钩子在事务内执行（异常回滚）。
     */
    private void inTx(Runnable action) {
        if (transactionOperations == null) {
            action.run();
            return;
        }
        try {
            transactionOperations.executeWithoutResult(status -> action.run());
        } catch (TransactionException e) {
            throw new QuickDevException("写操作事务执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * before 钩子：事务内执行，异常直接抛出（回滚本次写操作）
     */
    private void callBefore(String phase, Consumer<CrudHook> action) {
        for (CrudHook hook : hooks) {
            action.accept(hook);
        }
    }

    /**
     * after 钩子：事务提交后执行，失败仅告警——数据已提交，缓存刷新等
     * 副作用失败不应让已成功的写操作返回错误。
     */
    private void callAfter(String phase, Consumer<CrudHook> action) {
        for (CrudHook hook : hooks) {
            try {
                action.accept(hook);
            } catch (Exception e) {
                log.warn("CrudHook[{}] {} 执行失败(数据已提交,不影响响应): {}",
                        hook.getClass().getSimpleName(), phase, e.getMessage());
            }
        }
    }

    private Object parseEntity(String body) {
        Object entity;
        try {
            entity = objectMapper.readValue(body, meta.getEntityClass());
        } catch (Exception e) {
            throw new ParamException("请求体解析失败: " + e.getLocalizedMessage(), e);
        }
        return entity;
    }

    private Object convertId(String id) {
        try {
            return conversionService.convert(id, meta.getIdType());
        } catch (Exception e) {
            throw new ParamException("ID \"" + id + "\" 无法转换为 " + meta.getIdType().getSimpleName() + " 类型");
        }
    }

    private Object idValue(Object entity) {
        try {
            return meta.getIdField().get(entity);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
