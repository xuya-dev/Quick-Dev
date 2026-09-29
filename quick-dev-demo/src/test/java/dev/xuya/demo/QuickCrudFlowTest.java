package dev.xuya.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端流程测试：登录 -> 鉴权 -> 动态 CRUD -> 权限拦截。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QuickCrudFlowTest {

    @Autowired
    private TestRestTemplate rest;

    // ------------------------------------------------------------------
    // 匿名访问
    // ------------------------------------------------------------------

    @Test
    void anonymousShouldGet401OnProtectedCrud() {
        ResponseEntity<Map> resp = call(HttpMethod.GET, "/sys-user/page", null, null);
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        assertThat(code(resp)).isEqualTo(401);
    }

    @Test
    void openCrudShouldWorkWithoutToken() {
        // product 未配置 permission：无需登录即可访问
        ResponseEntity<Map> resp = call(HttpMethod.GET, "/product/page", null, null);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(code(resp)).isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // admin 全流程 CRUD
    // ------------------------------------------------------------------

    @Test
    void adminShouldCompleteFullCrudFlow() {
        String token = login("admin", "admin123");

        // 分页 + LIKE 模糊查询（username 上标注了 @QueryField(LIKE)）
        ResponseEntity<Map> page = call(HttpMethod.GET, "/sys-user/page?current=1&size=10&username=ad", token, null);
        assertThat(code(page)).isEqualTo(200);
        Map<String, Object> pageData = data(page);
        assertThat((Integer) pageData.get("total")).isGreaterThanOrEqualTo(1);
        List<Map<String, Object>> records = (List<Map<String, Object>>) pageData.get("records");
        assertThat(records).isNotEmpty();
        assertThat(records.get(0).get("username")).isEqualTo("admin");
        assertThat((String) records.get(0).get("password")).isNull(); // @JsonIgnore 生效

        // 新增
        ResponseEntity<Map> save = call(HttpMethod.POST, "/sys-user", token,
                Map.of("username", "tester01", "nickname", "测试用户", "email", "tester01@quickdev.cn", "status", 1));
        assertThat(code(save)).isEqualTo(200);
        Map<String, Object> saved = data(save);
        Long newId = ((Number) saved.get("id")).longValue();
        assertThat(newId).isNotNull();

        // 详情
        ResponseEntity<Map> detail = call(HttpMethod.GET, "/sys-user/" + newId, token, null);
        assertThat(code(detail)).isEqualTo(200);
        assertThat(data(detail).get("username")).isEqualTo("tester01");

        // 修改（nickname）
        ResponseEntity<Map> update = call(HttpMethod.PUT, "/sys-user", token,
                Map.of("id", newId, "nickname", "改名之后"));
        assertThat(code(update)).isEqualTo(200);
        assertThat(data(call(HttpMethod.GET, "/sys-user/" + newId, token, null)).get("nickname")).isEqualTo("改名之后");

        // 删除 + 再查 404
        assertThat(code(call(HttpMethod.DELETE, "/sys-user/" + newId, token, null))).isEqualTo(200);
        assertThat(code(call(HttpMethod.GET, "/sys-user/" + newId, token, null))).isEqualTo(404);

        // 自定义权限接口
        ResponseEntity<Map> hello = call(HttpMethod.GET, "/hello", token, null);
        assertThat(code(hello)).isEqualTo(200);
        assertThat((String) data(hello).get("message")).contains("管理员");
    }

    // ------------------------------------------------------------------
    // viewer 权限不足
    // ------------------------------------------------------------------

    @Test
    void viewerShouldBeForbiddenOnWrite() {
        String token = login("viewer", "viewer123");

        // 只读权限可以查询
        assertThat(code(call(HttpMethod.GET, "/sys-user/page", token, null))).isEqualTo(200);

        // 无写权限 -> 403
        ResponseEntity<Map> save = call(HttpMethod.POST, "/sys-user", token,
                Map.of("username", "hack", "email", "hack@quickdev.cn"));
        assertThat(save.getStatusCode().value()).isEqualTo(403);
        assertThat(code(save)).isEqualTo(403);

        // 无 demo:hello 权限 -> 403
        ResponseEntity<Map> hello = call(HttpMethod.GET, "/hello", token, null);
        assertThat(hello.getStatusCode().value()).isEqualTo(403);
    }

    // ------------------------------------------------------------------
    // 参数校验 / 登录态
    // ------------------------------------------------------------------

    @Test
    void blankFieldShouldFailValidation() {
        String token = login("admin", "admin123");
        // 缺少 username/email（@NotBlank） -> 业务码 400
        ResponseEntity<Map> resp = call(HttpMethod.POST, "/sys-user", token, Map.of("nickname", "没有用户名"));
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(code(resp)).isEqualTo(400);
    }

    @Test
    void meShouldReflectLoginState() {
        assertThat(call(HttpMethod.GET, "/auth/me", null, null).getStatusCode().value()).isEqualTo(401);

        String token = login("viewer", "viewer123");
        ResponseEntity<Map> me = call(HttpMethod.GET, "/auth/me", token, null);
        assertThat(code(me)).isEqualTo(200);
        assertThat(data(me).get("username")).isEqualTo("viewer");
    }

    // ------------------------------------------------------------------
    // UUID 主键实体 + 排除操作
    // ------------------------------------------------------------------

    @Test
    void productCrudWithStringId() {
        // 新增：ASSIGN_UUID 自动回填 32 位主键
        ResponseEntity<Map> save = call(HttpMethod.POST, "/product", null,
                Map.of("name", "testbook", "price", 59.9, "stock", 200));
        assertThat(code(save)).isEqualTo(200);
        String id = (String) data(save).get("id");
        assertThat(id).hasSize(32);

        // LIKE + GE 条件查询：name=testb, stock>=150
        ResponseEntity<Map> page = call(HttpMethod.GET, "/product/page?name=testb&stock=150", null, null);
        assertThat(code(page)).isEqualTo(200);
        List<Map<String, Object>> records = (List<Map<String, Object>>) data(page).get("records");
        assertThat(records).hasSize(1);
        assertThat(records.get(0).get("name")).isEqualTo("testbook");

        // 更新 / 删除
        assertThat(code(call(HttpMethod.PUT, "/product", null,
                Map.of("id", id, "name", "testbook-v2")))).isEqualTo(200);
        assertThat(data(call(HttpMethod.GET, "/product/" + id, null, null)).get("name")).isEqualTo("testbook-v2");
        assertThat(code(call(HttpMethod.DELETE, "/product/" + id, null, null))).isEqualTo(200);

        // CrudOp.LIST 被排除：/product/list 未注册为列表接口，
        // 请求落入 GET /product/{id}（id="list"，记录不存在）-> body code 404
        ResponseEntity<Map> excluded = call(HttpMethod.GET, "/product/list", null, null);
        assertThat(excluded.getStatusCode().value()).isEqualTo(200);
        assertThat(code(excluded)).isEqualTo(404);
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private String login(String username, String password) {
        ResponseEntity<Map> resp = call(HttpMethod.POST, "/auth/login", null,
                Map.of("username", username, "password", password));
        assertThat(code(resp)).as("登录成功: %s", username).isEqualTo(200);
        return (String) data(resp).get("token");
    }

    private ResponseEntity<Map> call(HttpMethod method, String url, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.set("Authorization", token);
        }
        return rest.exchange(url, method, new HttpEntity<>(body, headers), Map.class);
    }

    private int code(ResponseEntity<Map> resp) {
        return ((Number) resp.getBody().get("code")).intValue();
    }

    private Map<String, Object> data(ResponseEntity<Map> resp) {
        return (Map<String, Object>) resp.getBody().get("data");
    }
}
