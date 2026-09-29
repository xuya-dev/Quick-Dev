package dev.xuya.core.methodop;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.xuya.core.common.ParamException;
import dev.xuya.core.common.R;
import dev.xuya.core.crud.EntityMeta;
import dev.xuya.core.crud.MapperResolver;
import dev.xuya.core.crud.QueryHelper;
import dev.xuya.core.excel.ExcelImportExecutor;
import dev.xuya.core.excel.ExcelSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 方法级注解切面：拦截 {@link QuickSave}/{@link QuickUpdate}/{@link QuickRemove}/
 * {@link QuickExport}/{@link QuickImport}，接管方法执行（原方法体不调用）。
 *
 * <p>参数约定：从方法参数中按类型提取所需数据——
 * 新增/修改取实体类型（或 List&lt;实体&gt;）参数；删除取 String/Number/List（ids，支持逗号分隔）；
 * 导入取 MultipartFile；导出取 HttpServletResponse（无该参数时自动从请求上下文获取）。</p>
 *
 * <p>权限由 {@link dev.xuya.core.auth.AuthInterceptor} 依据注解的 permission 统一校验。</p>
 */
@Aspect
public class QuickOpAspect {

    private static final Logger log = LoggerFactory.getLogger(QuickOpAspect.class);

    private final ApplicationContext applicationContext;
    private volatile ConversionService conversionService;
    private volatile Validator validator;
    private volatile TransactionOperations transactionOperations;

    public QuickOpAspect(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Around("@annotation(dev.xuya.core.methodop.QuickSave) || "
            + "@annotation(dev.xuya.core.methodop.QuickUpdate) || "
            + "@annotation(dev.xuya.core.methodop.QuickRemove) || "
            + "@annotation(dev.xuya.core.methodop.QuickExport) || "
            + "@annotation(dev.xuya.core.methodop.QuickImport)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Object[] args = pjp.getArgs();

        QuickSave save = method.getAnnotation(QuickSave.class);
        if (save != null) {
            return doSave(save, args);
        }
        QuickUpdate update = method.getAnnotation(QuickUpdate.class);
        if (update != null) {
            return doUpdate(update, args);
        }
        QuickRemove remove = method.getAnnotation(QuickRemove.class);
        if (remove != null) {
            return doRemove(remove, args);
        }
        QuickExport export = method.getAnnotation(QuickExport.class);
        if (export != null) {
            return doExport(export, args);
        }
        QuickImport importExcel = method.getAnnotation(QuickImport.class);
        if (importExcel != null) {
            return doImport(importExcel, args);
        }
        return pjp.proceed();
    }

    // ------------------------------------------------------------------

    private Object doSave(QuickSave annotation, Object[] args) {
        Class<?> entityClass = annotation.entity();
        BaseMapper<Object> mapper = mapper(entityClass);
        List<Object> batch = findListArg(args, entityClass);
        if (batch != null) {
            batch.forEach(this::validate);
            Db.saveBatch(batch);
            return R.ok("批量新增成功", batch.size());
        }
        Object entity = findEntityArg(args, entityClass);
        if (entity == null) {
            throw new ParamException("@QuickSave 方法需要声明 " + entityClass.getSimpleName()
                    + "（或 List<" + entityClass.getSimpleName() + ">）类型的参数");
        }
        validate(entity);
        mapper.insert(entity);
        return R.ok("新增成功", entity);
    }

    private Object doUpdate(QuickUpdate annotation, Object[] args) {
        Class<?> entityClass = annotation.entity();
        Object entity = findEntityArg(args, entityClass);
        if (entity == null) {
            throw new ParamException("@QuickUpdate 方法需要声明 " + entityClass.getSimpleName() + " 类型的参数");
        }
        EntityMeta meta = EntityMeta.of(entityClass);
        Object id = idValue(meta, entity);
        if (id == null || String.valueOf(id).isEmpty()) {
            throw new ParamException("更新时主键 " + meta.getIdProperty() + " 不能为空");
        }
        BaseMapper<Object> mapper = mapper(entityClass);
        return R.ok("更新成功", mapper.updateById(entity) > 0);
    }

    private Object doRemove(QuickRemove annotation, Object[] args) {
        Class<?> entityClass = annotation.entity();
        EntityMeta meta = EntityMeta.of(entityClass);
        List<Object> idList = new ArrayList<>();
        for (Object arg : args) {
            if (arg instanceof MultipartFile || arg instanceof HttpServletRequest
                    || arg instanceof HttpServletResponse) {
                continue;
            }
            if (arg instanceof String text && !text.isBlank()) {
                for (String item : text.split(",")) {
                    idList.add(convertId(meta, item.trim()));
                }
            } else if (arg instanceof Number number) {
                idList.add(convertId(meta, String.valueOf(number)));
            } else if (arg instanceof Collection<?> ids) {
                ids.forEach(id -> idList.add(convertId(meta, String.valueOf(id))));
            }
        }
        if (idList.isEmpty()) {
            throw new ParamException("@QuickRemove 方法需要声明 ids 参数（单个、List 或逗号分隔字符串）");
        }
        BaseMapper<Object> mapper = mapper(entityClass);
        return R.ok("删除成功", mapper.deleteBatchIds(idList));
    }

    private Object doExport(QuickExport annotation, Object[] args) throws Exception {
        Class<?> entityClass = annotation.entity();
        BaseMapper<Object> mapper = mapper(entityClass);
        EntityMeta meta = EntityMeta.of(entityClass);

        HttpServletResponse response = findArg(args, HttpServletResponse.class);
        if (response == null) {
            response = currentResponse();
        }
        if (response == null) {
            throw new ParamException("@QuickExport 方法需要声明 HttpServletResponse 参数");
        }
        HttpServletRequest request = currentRequest();
        Map<String, String> params = new HashMap<>();
        if (request != null) {
            request.getParameterMap().forEach((k, v) -> {
                if (v != null && v.length > 0) {
                    params.put(k, v[0]);
                }
            });
        }
        List<Object> data = mapper.selectList(QueryHelper.build(meta, params, conversionService()));
        if (annotation.translate()) {
            ExcelSupport.writeTranslated(response, entityClass, data, objectMapper());
            log.info("QuickExport[{}] 导出 {} 行（已翻译）", entityClass.getSimpleName(), data.size());
        } else {
            ExcelSupport.write(response, entityClass, data);
            log.info("QuickExport[{}] 导出 {} 行", entityClass.getSimpleName(), data.size());
        }
        return null;
    }

    private Object doImport(QuickImport annotation, Object[] args) {
        Class<?> entityClass = annotation.entity();
        MultipartFile file = findArg(args, MultipartFile.class);
        if (file == null) {
            throw new ParamException("@QuickImport 方法需要声明 MultipartFile 参数");
        }
        BaseMapper<Object> mapper = mapper(entityClass);
        Map<String, Object> result = ExcelImportExecutor.execute(
                mapper, entityClass, file, validator(), transactionOperations());
        return R.ok("导入成功", result);
    }

    // ------------------------------------------------------------------

    private BaseMapper<Object> mapper(Class<?> entityClass) {
        return MapperResolver.resolve(applicationContext, entityClass, Void.class);
    }

    private void validate(Object entity) {
        Validator v = validator();
        if (v == null) {
            return;
        }
        Set<ConstraintViolation<Object>> violations = v.validate(entity);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(x -> x.getPropertyPath() + " " + x.getMessage())
                    .collect(Collectors.joining("; "));
            throw new ParamException("参数校验失败: " + message);
        }
    }

    private <T> T findArg(Object[] args, Class<T> type) {
        for (Object arg : args) {
            if (type.isInstance(arg)) {
                return type.cast(arg);
            }
        }
        return null;
    }

    private Object findEntityArg(Object[] args, Class<?> entityClass) {
        for (Object arg : args) {
            if (entityClass.isInstance(arg)) {
                return arg;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Object> findListArg(Object[] args, Class<?> entityClass) {
        for (Object arg : args) {
            if (arg instanceof List<?> list && !list.isEmpty() && entityClass.isInstance(list.get(0))) {
                return (List<Object>) list;
            }
        }
        return null;
    }

    private Object idValue(EntityMeta meta, Object entity) {
        try {
            return meta.getIdField().get(entity);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private Serializable convertId(EntityMeta meta, String id) {
        Object converted = conversionService().convert(id, meta.getIdType());
        if (converted instanceof Serializable serializable) {
            return serializable;
        }
        throw new ParamException("ID \"" + id + "\" 类型不受支持");
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private HttpServletResponse currentResponse() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getResponse() : null;
    }

    private ConversionService conversionService() {
        if (conversionService == null) {
            try {
                conversionService = applicationContext.getBean("mvcConversionService", ConversionService.class);
            } catch (Exception e) {
                conversionService = new DefaultFormattingConversionService();
            }
        }
        return conversionService;
    }

    private ObjectMapper objectMapper() {
        return applicationContext.getBeanProvider(ObjectMapper.class)
                .getIfAvailable(ObjectMapper::new);
    }

    private Validator validator() {
        if (validator == null) {
            try {
                validator = applicationContext.getBeanProvider(Validator.class).getIfAvailable();
            } catch (Exception e) {
                validator = null;
            }
        }
        return validator;
    }

    private TransactionOperations transactionOperations() {
        if (transactionOperations == null) {
            try {
                transactionOperations = applicationContext.getBeanProvider(TransactionOperations.class)
                        .getIfAvailable();
            } catch (Exception e) {
                transactionOperations = null;
            }
        }
        return transactionOperations;
    }
}
