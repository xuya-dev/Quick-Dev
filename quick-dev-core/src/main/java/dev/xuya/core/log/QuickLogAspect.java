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

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 操作日志切面：拦截 {@link QuickLog} 方法，记录调用上下文并交给 {@link OperationLogSink}。
 * <p>日志落地失败只告警、不影响业务；sink 由自动配置注入（默认 Slf4j）。</p>
 */
@Aspect
public class QuickLogAspect {

    private static final Logger log = LoggerFactory.getLogger(QuickLogAspect.class);
    private static final int MAX_PARAM_LENGTH = 2000;

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
        record.setToken(AuthContext.getToken());
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

    private String serialize(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        String json = Arrays.stream(args)
                .filter(arg -> !(arg instanceof HttpServletRequest)
                        && !(arg instanceof HttpServletResponse)
                        && !(arg instanceof MultipartFile))
                .map(this::toJson)
                .collect(Collectors.joining(","));
        return json.length() > MAX_PARAM_LENGTH ? json.substring(0, MAX_PARAM_LENGTH) + "...(截断)" : json;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
