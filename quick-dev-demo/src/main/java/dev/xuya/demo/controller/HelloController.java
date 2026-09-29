package dev.xuya.demo.controller;

import dev.xuya.core.auth.AuthContext;
import dev.xuya.core.auth.RequiresPerm;
import dev.xuya.core.common.R;
import dev.xuya.demo.entity.SysUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 演示 @RequiresPerm 在普通 Controller 上的用法（与 @QuickCrud 生成的接口共用同一套权限体系）。
 */
@RestController
public class HelloController {

    @RequiresPerm("demo:hello")
    @GetMapping("/hello")
    public R<Object> hello() {
        SysUser user = AuthContext.getUser();
        return R.ok(Map.of(
                "message", "hello " + user.getNickname(),
                "user", user.getUsername()
        ));
    }
}
