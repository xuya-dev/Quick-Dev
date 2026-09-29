package dev.xuya.core.common;

import java.io.Serializable;

/**
 * 统一响应结构。
 * <pre>
 * {
 *   "code": 200,
 *   "msg": "success",
 *   "data": ...
 * }
 * </pre>
 */
public class R<T> implements Serializable {

    public static final int SUCCESS = 200;

    private int code;
    private String msg;
    private T data;

    public R() {
    }

    public R(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> R<T> ok() {
        return new R<>(SUCCESS, "success", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(SUCCESS, "success", data);
    }

    public static <T> R<T> ok(String msg, T data) {
        return new R<>(SUCCESS, msg, data);
    }

    public static <T> R<T> fail(int code, String msg) {
        return new R<>(code, msg, null);
    }

    public static <T> R<T> fail(String msg) {
        return new R<>(500, msg, null);
    }

    /**
     * 是否成功，便于前端判断
     */
    public boolean isSuccess() {
        return code == SUCCESS;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
