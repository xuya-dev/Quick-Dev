package dev.xuya.core.methodop;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class QuickMethodOpsTest {

    private static Method method(String name) throws NoSuchMethodException {
        return FakeController.class.getDeclaredMethod(name);
    }

    @Test
    void shouldReadPermissionFromEachAnnotation() throws Exception {
        assertThat(QuickMethodOps.permissionOf(method("withPermission"))).isEqualTo("order:add");
        // 未配置 permission（空串）视为不鉴权 -> null
        assertThat(QuickMethodOps.permissionOf(method("withoutPermission"))).isNull();
        // 无注解方法 -> null
        assertThat(QuickMethodOps.permissionOf(method("plain"))).isNull();
    }

    static class FakeController {
        @QuickSave(entity = String.class, permission = "order:add")
        void withPermission() {
        }

        @QuickExport(entity = String.class)
        void withoutPermission() {
        }

        void plain() {
        }
    }
}
