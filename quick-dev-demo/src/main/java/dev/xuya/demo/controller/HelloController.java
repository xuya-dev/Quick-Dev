package dev.xuya.demo.controller;

import dev.xuya.core.auth.NoRepeatSubmit;
import dev.xuya.core.auth.RequiresPerm;
import dev.xuya.core.auth.RequiresRole;
import dev.xuya.core.common.R;
import dev.xuya.core.log.QuickLog;
import dev.xuya.demo.auth.DbAuthService;
import dev.xuya.demo.entity.SysUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 演示 @RequiresPerm / @RequiresRole 在普通 Controller 上的用法（与 @QuickCrud 共用同一套体系）。
 */
@RestController
public class HelloController {

    private final DbAuthService authService;

    public HelloController(DbAuthService authService) {
        this.authService = authService;
    }

    @QuickLog(module = "演示", description = "打招呼")
    @RequiresPerm("demo:hello")
    @GetMapping("/hello")
    public R<Object> hello() {
        SysUser user = authService.currentUser();
        return R.ok(Map.of(
                "message", "hello " + user.getNickname(),
                "user", user.getUsername()
        ));
    }

    /** 角色注解：仅 admin 角色可访问（viewer 无角色 -> 403） */
    @RequiresRole("admin")
    @GetMapping("/admin/summary")
    public R<Object> adminSummary() {
        SysUser user = authService.currentUser();
        return R.ok(Map.of(
                "message", "welcome back, " + user.getNickname(),
                "endpoint", "admin only"
        ));
    }

    /** 防重复提交演示：3 秒内同一用户重复点击会被拒绝 */
    @NoRepeatSubmit(interval = 3000)
    @PostMapping("/repeat/submit")
    public R<Object> repeatSubmit() {
        return R.ok("提交成功", java.time.LocalDateTime.now());
    }
}
