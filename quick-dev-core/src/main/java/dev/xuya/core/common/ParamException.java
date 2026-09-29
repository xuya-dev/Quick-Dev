package dev.xuya.core.common;

/**
 * 参数类错误（请求体解析失败、类型转换失败、校验失败等），HTTP 200 + code=400。
 */
public class ParamException extends QuickDevException {

    public ParamException(String message) {
        super(message);
    }

    public ParamException(String message, Throwable cause) {
        super(message, cause);
    }
}
