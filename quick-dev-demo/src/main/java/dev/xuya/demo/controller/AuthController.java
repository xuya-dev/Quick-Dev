package dev.xuya.demo.controller;

import dev.xuya.core.auth.AuthContext;
import dev.xuya.core.auth.RequiresLogin;
import dev.xuya.core.common.R;
import dev.xuya.core.common.QuickDevException;
import dev.xuya.demo.auth.DbAuthService;import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final DbAuthService authService;

    public AuthController(DbAuthService authService) {
        this.authService = authService;
    }

    /** 登录换取 token，后续请求放在请求头 Authorization: {token} */
    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) {
            throw new QuickDevException("用户名和密码不能为空");
        }
        return R.ok(authService.login(username, password));
    }

    @RequiresLogin
    @GetMapping("/me")
    public R<Object> me() {
        return R.ok(authService.currentUser());
    }

    @GetMapping("/logout")
    public R<Void> logout() {
        authService.logout(AuthContext.getToken());
        return R.ok();
    }
}
