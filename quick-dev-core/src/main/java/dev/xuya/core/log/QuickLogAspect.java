package dev.xuya.core.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.xuya.core.auth.AuthContext;
import dev.xuya.core.common.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 操作日志切面：拦截 {@link QuickLog} 方法，记录调用上下文并交给 {@link OperationLogSink}。
 * <p>日志落地失败只告警、不影响业务；sink 由自动配置注入（默认 Slf4j）。</p>
 *
 * <p><b>敏感信息</b>：默认不采集登录令牌（明文凭据落日志是常见泄露源）；参数 JSON 中的
 * password/token/secret 等键值会被替换为 ***。参数序列化会跳过 Servlet 请求/响应与
 * 上传文件（含嵌套在数组/集合中的文件），避免把整个文件读进内存。</p>
 */
@Aspect
public class QuickLogAspect {

    private static final Logger log = LoggerFactory.getLogger(QuickLogAspect.class);
    private static final int MAX_PARAM_LENGTH = 2000;

    /**
     * 常见敏感键（不区分大小写）：值统一替换为 ***
     */
    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(\"(?:password|passwd|pwd|secret|token|accessToken|refreshToken|authorization|"
                    + "creditCard|idCard)\"\\s*:\\s*\")((?:[^\"\\\\]|\\\\.)*)(\")");

    private static final String MASK = "$1***$3";

    private final OperationLogSink sink;
    private final ObjectMapper objectMapper;

    public QuickLogAspect(OperationLogSink sink, ObjectMapper objectMapper) {
        this.sink = sink;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(quickLog)")
    public Object around(ProceedingJoinPoint pjp, QuickLog quickLog) throws Throwable {
        long start = System.currentTimeMillis();
        LogRecord record = new LogRecord();
        record.setModule(quickLog.module());
        record.setDescription(quickLog.description());
        record.setOperator(AuthContext.getUser());
        // 有意不采集 AuthContext.getToken()：token 明文落日志/Sink 存储是泄露源
        record.setParams(serialize(pjp.getArgs()));

        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            record.setUri(request.getRequestURI());
            record.setHttpMethod(request.getMethod());
            record.setIp(request.getRemoteAddr());
        }

        try {
            Object result = pjp.proceed();
            record.setSuccess(true);
            if (result instanceof R<?> r) {
                record.setResultCode(r.getCode());
            }
            return result;
        } catch (Throwable e) {
            record.setSuccess(false);
            record.setErrorMessage(e.getMessage());
            throw e;
        } finally {
            record.setCostMs(System.currentTimeMillis() - start);
            try {
                sink.save(record);
            } catch (Exception e) {
                log.warn("操作日志落地失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 参数序列化：跳过 Servlet 对象与上传文件（含容器内嵌套），整体截断到上限并脱敏
     */
    private String serialize(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        List<Object> safe = new ArrayList<>(args.length);
        for (Object arg : args) {
            addIfSafe(safe, arg);
        }
        if (safe.isEmpty()) {
            return null;
        }
        String json = safe.stream()
                .map(this::toJson)
                .collect(Collectors.joining(","));
        json = SENSITIVE_KEY.matcher(json).replaceAll(MASK);
        return json.length() > MAX_PARAM_LENGTH ? json.substring(0, MAX_PARAM_LENGTH) + "...(截断)" : json;
    }

    /**
     * 递归一层展开数组/集合，过滤其中的上传文件与 Servlet 对象
     */
    private void addIfSafe(List<Object> out, Object arg) {
        if (arg == null || isSkipped(arg)) {
            return;
        }
        if (arg instanceof Collection<?> collection) {
            collection.stream().filter(item -> item != null && !isSkipped(item)).forEach(out::add);
        } else if (arg.getClass().isArray() && !arg.getClass().getComponentType().isPrimitive()) {
            Object[] items = new Object[Array.getLength(arg)];
            for (int i = 0; i < items.length; i++) {
                Object item = Array.get(arg, i);
                if (item != null && !isSkipped(item)) {
                    items[i] = item;
                }
            }
            out.add(Arrays.asList(items));
        } else {
            out.add(arg);
        }
    }

    private boolean isSkipped(Object arg) {
        return arg instanceof HttpServletRequest || arg instanceof HttpServletResponse
                || arg instanceof MultipartFile;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
