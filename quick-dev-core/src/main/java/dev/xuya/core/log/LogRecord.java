package dev.xuya.core.log;

import java.time.LocalDateTime;

/**
 * 一次被 @QuickLog 标注的接口调用的审计记录。
 */
public class LogRecord {

    /**
     * 业务模块，如"用户管理"
     */
    private String module;
    /**
     * 操作描述，如"新增用户"
     */
    private String description;
    /**
     * 操作人（Sa-Token 模式下为 loginId），匿名为 null
     */
    private Object operator;
    /**
     * 请求令牌
     */
    private String token;
    /**
     * 请求 URI
     */
    private String uri;
    /**
     * HTTP 方法
     */
    private String httpMethod;
    /**
     * 客户端 IP
     */
    private String ip;
    /**
     * 入参 JSON（已截断）
     */
    private String params;
    /**
     * 出参业务码（R.code），异常时为空
     */
    private Integer resultCode;
    /**
     * 是否成功（未抛异常）
     */
    private boolean success;
    /**
     * 异常信息
     */
    private String errorMessage;
    /**
     * 耗时毫秒
     */
    private long costMs;
    /**
     * 调用时间
     */
    private LocalDateTime timestamp = LocalDateTime.now();

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Object getOperator() {
        return operator;
    }

    public void setOperator(Object operator) {
        this.operator = operator;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public Integer getResultCode() {
        return resultCode;
    }

    public void setResultCode(Integer resultCode) {
        this.resultCode = resultCode;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getCostMs() {
        return costMs;
    }

    public void setCostMs(long costMs) {
        this.costMs = costMs;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
