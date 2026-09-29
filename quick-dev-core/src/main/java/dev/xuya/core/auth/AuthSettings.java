package dev.xuya.core.auth;

/**
 * 鉴权设置（由自动配置模块从 quick-dev.auth.* 配置装配后传入）。
 * <p>core 不依赖 Spring Boot 的配置绑定，仅持有纯值，方便独立测试与复用。</p>
 */
public class AuthSettings {

    private boolean enabled = true;
    private String tokenHeader = "Authorization";
    private String tokenParam = "token";

    public AuthSettings() {
    }

    public AuthSettings(boolean enabled, String tokenHeader, String tokenParam) {
        this.enabled = enabled;
        this.tokenHeader = tokenHeader;
        this.tokenParam = tokenParam;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTokenHeader() {
        return tokenHeader;
    }

    public void setTokenHeader(String tokenHeader) {
        this.tokenHeader = tokenHeader;
    }

    public String getTokenParam() {
        return tokenParam;
    }

    public void setTokenParam(String tokenParam) {
        this.tokenParam = tokenParam;
    }
}
