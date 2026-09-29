package dev.xuya.core.crud;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.xuya.core.annotation.CrudOp;
import dev.xuya.core.annotation.QuickCrud;
import dev.xuya.core.common.QuickDevException;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @QuickCrud 引擎：容器就绪后扫描所有标注 @QuickCrud 的 Bean，
 * 解析实体/Mapper/路径/权限，创建 {@link QuickCrudHandler} 并把 CRUD 方法
 * 动态注册到 {@link RequestMappingHandlerMapping}（Spring 官方支持的运行期注册）。
 */
public class QuickCrudRegistrar implements SmartInitializingSingleton, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(QuickCrudRegistrar.class);

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        RequestMappingHandlerMapping handlerMapping =
                applicationContext.getBean(RequestMappingHandlerMapping.class);
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(QuickCrud.class);
        if (beans.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            String beanName = entry.getKey();
            Class<?> beanClass = AopUtils.getTargetClass(entry.getValue());
            QuickCrud quickCrud = AnnotatedElementUtils.findMergedAnnotation(beanClass, QuickCrud.class);
            if (quickCrud == null) {
                continue;
            }
            register(beanName, beanClass, quickCrud, handlerMapping);
        }
    }

    @SuppressWarnings("unchecked")
    private void register(String beanName, Class<?> beanClass, QuickCrud quickCrud,
                          RequestMappingHandlerMapping handlerMapping) {
        try {
            Class<?> entityClass = quickCrud.entity();
            if (entityClass == Void.class) {
                throw new QuickDevException("必须在 @QuickCrud 中指定 entity 属性");
            }

            BaseMapper<Object> mapper = MapperResolver.resolve(applicationContext, entityClass, quickCrud.mapper());
            EntityMeta meta = EntityMeta.of(entityClass);
            String basePath = resolveBasePath(beanClass, entityClass, quickCrud);
            Set<CrudOp> ops = resolveOps(quickCrud);

            QuickCrudHandler handler = new QuickCrudHandler(
                    meta, mapper, objectMapper(), conversionService(), validator(),
                    quickCrud.loginRequired(), transactionOperations());

            for (CrudOp op : ops) {
                Method method = QuickCrudHandler.methodOf(op);
                String permission = quickCrud.permission().isEmpty()
                        ? null : quickCrud.permission() + ":" + op.getPermissionSuffix();
                handler.bindPermission(op, permission);

                RequestMappingInfo.BuilderConfiguration config = new RequestMappingInfo.BuilderConfiguration();
                if (handlerMapping.getPatternParser() != null) {
                    config.setPatternParser(handlerMapping.getPatternParser());
                }
                RequestMappingInfo info = RequestMappingInfo
                        .paths(basePath + op.getPath())
                        .methods(op.getRequestMethod())
                        .options(config)
                        .build();
                handlerMapping.registerMapping(info, handler, method);

                log.info("QuickCrud[{}] {} {} -> {} (perm: {})", beanName, op.getRequestMethod(),
                        basePath + op.getPath(), entityClass.getSimpleName(),
                        permission == null ? "-" : permission);
            }
        } catch (QuickDevException e) {
            throw new QuickDevException("注册 @QuickCrud 端点失败 [" + beanName + "]: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new QuickDevException("注册 @QuickCrud 端点失败 [" + beanName + "]", e);
        }
    }

    private String resolveBasePath(Class<?> beanClass, Class<?> entityClass, QuickCrud quickCrud) {
        if (!quickCrud.path().isEmpty()) {
            return normalize(quickCrud.path());
        }
        RequestMapping requestMapping = AnnotatedElementUtils.findMergedAnnotation(beanClass, RequestMapping.class);
        if (requestMapping != null) {
            String[] values = requestMapping.path().length > 0 ? requestMapping.path() : requestMapping.value();
            if (values.length > 0 && !values[0].isEmpty()) {
                return normalize(values[0]);
            }
        }
        // 实体名推导：SysUser -> /sys-user
        String simpleName = entityClass.getSimpleName();
        StringBuilder sb = new StringBuilder(simpleName.length() + 4);
        for (int i = 0; i < simpleName.length(); i++) {
            char c = simpleName.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('-');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return "/" + sb;
    }

    private Set<CrudOp> resolveOps(QuickCrud quickCrud) {
        Set<CrudOp> included = new LinkedHashSet<>(Arrays.asList(quickCrud.includes()));
        Set<CrudOp> excluded = Set.of(quickCrud.excludes());
        List<CrudOp> ops = new ArrayList<>();
        for (CrudOp op : included) {
            if (!excluded.contains(op)) {
                ops.add(op);
            }
        }
        if (ops.isEmpty()) {
            throw new QuickDevException("@QuickCrud 的 includes/excludes 组合后没有任何可注册的操作");
        }
        return new LinkedHashSet<>(ops);
    }

    private String normalize(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    private ObjectMapper objectMapper() {
        ObjectMapper mapper = applicationContext.getBeanProvider(ObjectMapper.class).getIfAvailable();
        return mapper != null ? mapper : new ObjectMapper();
    }

    private ConversionService conversionService() {
        try {
            return applicationContext.getBean("mvcConversionService", ConversionService.class);
        } catch (Exception e) {
            return new DefaultFormattingConversionService();
        }
    }

    private Validator validator() {
        if (!ClassUtils.isPresent("jakarta.validation.Validator", getClass().getClassLoader())) {
            return null;
        }
        try {
            return applicationContext.getBeanProvider(Validator.class).getIfAvailable();
        } catch (Exception e) {
            return null;
        }
    }

    private TransactionOperations transactionOperations() {
        try {
            return applicationContext.getBeanProvider(TransactionOperations.class).getIfAvailable();
        } catch (Exception e) {
            return null;
        }
    }
}
