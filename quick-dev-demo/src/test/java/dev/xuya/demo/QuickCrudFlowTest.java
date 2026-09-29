package dev.xuya.demo;

import cn.idev.excel.FastExcel;
import dev.xuya.demo.entity.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端流程测试：登录 -> 鉴权 -> 动态 CRUD -> 方法级注解 -> Excel 导入导出。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QuickCrudFlowTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private dev.xuya.demo.log.MemoryLogSink memoryLogSink;

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

        // 统计接口（与分页共用同一套查询条件）
        ResponseEntity<Map> count = call(HttpMethod.GET, "/sys-user/count?username=ad", token, null);
        assertThat(code(count)).isEqualTo(200);
        assertThat(((Number) count.getBody().get("data")).intValue()).isEqualTo(1);

        // 新增
        ResponseEntity<Map> save = call(HttpMethod.POST, "/sys-user", token,
                Map.of("username", "tester01", "nickname", "测试用户", "email", "tester01@quickdev.cn", "status", 1));
        assertThat(code(save)).isEqualTo(200);
        Map<String, Object> saved = data(save);
        Long newId = ((Number) saved.get("id")).longValue();
        assertThat(newId).isNotNull();
        // createTime/updateTime 自动填充 + 操作人（Sa-Token loginId）自动填充
        assertThat(saved.get("createTime")).isNotNull();
        assertThat(saved.get("updateTime")).isNotNull();
        assertThat(saved.get("createBy")).isEqualTo("1"); // admin 的 loginId
        assertThat(saved.get("updateBy")).isEqualTo("1");

        // 详情
        ResponseEntity<Map> detail = call(HttpMethod.GET, "/sys-user/" + newId, token, null);
        assertThat(code(detail)).isEqualTo(200);
        assertThat(data(detail).get("username")).isEqualTo("tester01");

        // 修改（nickname），updateTime 自动填充
        ResponseEntity<Map> update = call(HttpMethod.PUT, "/sys-user", token,
                Map.of("id", newId, "nickname", "改名之后"));
        assertThat(code(update)).isEqualTo(200);
        Map<String, Object> afterUpdate = data(call(HttpMethod.GET, "/sys-user/" + newId, token, null));
        assertThat(afterUpdate.get("nickname")).isEqualTo("改名之后");
        assertThat(afterUpdate.get("updateTime")).isNotNull();
        assertThat(afterUpdate.get("updateBy")).isEqualTo("1");

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

        // 无 admin 角色 -> 403
        ResponseEntity<Map> summary = call(HttpMethod.GET, "/admin/summary", token, null);
        assertThat(summary.getStatusCode().value()).isEqualTo(403);
    }

    // ------------------------------------------------------------------
    // 角色注解
    // ------------------------------------------------------------------

    @Test
    void adminRoleShouldPassRoleCheck() {
        String token = login("admin", "admin123");
        ResponseEntity<Map> resp = call(HttpMethod.GET, "/admin/summary", token, null);
        assertThat(code(resp)).isEqualTo(200);
        assertThat((String) data(resp).get("message")).contains("管理员");

        // 未登录 -> 401
        assertThat(call(HttpMethod.GET, "/admin/summary", null, null).getStatusCode().value()).isEqualTo(401);
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
        // 新增：ASSIGN_UUID 自动回填 32 位主键，时间字段自动填充
        ResponseEntity<Map> save = call(HttpMethod.POST, "/product", null,
                Map.of("name", "testbook", "price", 59.9, "stock", 200));
        assertThat(code(save)).isEqualTo(200);
        Map<String, Object> saved = data(save);
        String id = (String) saved.get("id");
        assertThat(id).hasSize(32);
        assertThat(saved.get("createTime")).isNotNull();
        assertThat(saved.get("updateTime")).isNotNull();

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

        // BETWEEN 范围查询 + COUNT 统计（删除 testbook 后剩两粒今年创建的种子数据）
        int year = java.time.Year.now().getValue();
        String createTimeRange = (year - 1) + "-01-01T00:00:00," + year + "-12-31T23:59:59";
        ResponseEntity<Map> range = call(HttpMethod.GET,
                "/product/page?createTime=" + createTimeRange, null, null);
        assertThat(code(range)).isEqualTo(200);
        assertThat((List<Map<String, Object>>) data(range).get("records")).hasSize(2);
        ResponseEntity<Map> count = call(HttpMethod.GET,
                "/product/count?createTime=" + createTimeRange, null, null);
        assertThat(code(count)).isEqualTo(200);
        assertThat(((Number) count.getBody().get("data")).intValue()).isEqualTo(2);

        // CrudOp.LIST 被排除：/product/list 未注册为列表接口，
        // 请求落入 GET /product/{id}（id="list"，记录不存在）-> body code 404
        ResponseEntity<Map> excluded = call(HttpMethod.GET, "/product/list", null, null);
        assertThat(excluded.getStatusCode().value()).isEqualTo(200);
        assertThat(code(excluded)).isEqualTo(404);
    }

    // ------------------------------------------------------------------
    // 方法级注解：Excel 导出 / 导入
    // ------------------------------------------------------------------

    @Test
    void exportShouldDownloadExcel() {
        HttpHeaders headers = new HttpHeaders();
        ResponseEntity<byte[]> resp = rest.exchange("/product/export?name=键盘", HttpMethod.GET,
                new HttpEntity<>(headers), byte[].class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getHeaders().getContentType().toString())
                .contains("spreadsheetml");
        byte[] body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.length).isGreaterThan(100);
        assertThat(body[0]).isEqualTo((byte) 'P'); // xlsx 即 zip，魔数 PK
        assertThat(body[1]).isEqualTo((byte) 'K');
    }

    @Test
    void importTemplateShouldDownloadHeaderOnlyExcel() {
        // 类级 CrudOp.IMPORT_TEMPLATE：下载仅含表头的导入模板
        ResponseEntity<byte[]> resp = rest.getForEntity("/product/import-template", byte[].class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getHeaders().getContentType().toString()).contains("spreadsheetml");
        byte[] body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.length).isGreaterThan(100);
        assertThat(body[0]).isEqualTo((byte) 'P');
        assertThat(body[1]).isEqualTo((byte) 'K');
    }

    @Test
    void importShouldRespectPermissionAndInsertRows() {
        // 生成两行商品的 Excel
        Product p1 = new Product();
        p1.setName("导入商品A");
        p1.setPrice(new BigDecimal("11.11"));
        p1.setStock(10);
        Product p2 = new Product();
        p2.setName("导入商品B");
        p2.setPrice(new BigDecimal("22.22"));
        p2.setStock(20);
        byte[] excel = writeExcel(List.of(p1, p2));

        // 方法级权限码 product:import：匿名 -> 401
        ResponseEntity<Map> anonymous = upload("/product/import", excel, null);
        assertThat(anonymous.getStatusCode().value()).isEqualTo(401);

        // admin（* 权限）导入成功
        String adminToken = login("admin", "admin123");
        ResponseEntity<Map> imported = upload("/product/import", excel, adminToken);
        assertThat(code(imported)).isEqualTo(200);
        Map<String, Object> stat = data(imported);
        assertThat(((Number) stat.get("total")).intValue()).isEqualTo(2);
        assertThat(((Number) stat.get("inserted")).intValue()).isEqualTo(2);

        // 校验失败：一行名称为空 -> 全部不入库
        Product bad = new Product();
        bad.setPrice(new BigDecimal("1.00"));
        ResponseEntity<Map> rejected = upload("/product/import", writeExcel(List.of(bad)), adminToken);
        assertThat(code(rejected)).isEqualTo(400);

        // 清理导入的两行，保证其它测试的种子数据稳定
        ResponseEntity<Map> page = call(HttpMethod.GET, "/product/page?name=导入商品&current=1&size=10", null, null);
        List<Map<String, Object>> records = (List<Map<String, Object>>) data(page).get("records");
        String ids = records.stream().map(r -> String.valueOf(r.get("id")))
                .reduce((a, b) -> a + "," + b).orElse("");
        if (!ids.isEmpty()) {
            assertThat(code(call(HttpMethod.DELETE, "/product/" + ids, null, null))).isEqualTo(200);
        }
    }

    // ------------------------------------------------------------------
    // 树形查询（CrudOp.TREE）
    // ------------------------------------------------------------------

    @Test
    void treeShouldBuildHierarchyFromParentId() {
        ResponseEntity<Map> resp = call(HttpMethod.GET, "/sys-dept/tree", null, null);
        assertThat(code(resp)).isEqualTo(200);
        List<Map<String, Object>> roots = (List<Map<String, Object>>) resp.getBody().get("data");
        assertThat(roots).hasSize(1);
        Map<String, Object> company = roots.get(0);
        assertThat(company.get("name")).isEqualTo("总公司");

        List<Map<String, Object>> level2 = (List<Map<String, Object>>) company.get("children");
        assertThat(level2).extracting(d -> d.get("name")).containsExactlyInAnyOrder("研发部", "财务部");

        Map<String, Object> devDept = level2.stream()
                .filter(d -> "研发部".equals(d.get("name"))).findFirst().orElseThrow();
        List<Map<String, Object>> level3 = (List<Map<String, Object>>) devDept.get("children");
        assertThat(level3).extracting(d -> d.get("name")).containsExactlyInAnyOrder("前端组", "后端组");

        // 财务部为叶子节点，children 应为空列表
        Map<String, Object> financeDept = level2.stream()
                .filter(d -> "财务部".equals(d.get("name"))).findFirst().orElseThrow();
        assertThat((List<?>) financeDept.get("children")).isEmpty();
    }

    // ------------------------------------------------------------------
    // 防重复提交（@NoRepeatSubmit）
    // ------------------------------------------------------------------

    @Test
    void repeatSubmitShouldBlockSecondCallInInterval() {
        // 第一次成功
        ResponseEntity<Map> first = call(HttpMethod.POST, "/repeat/submit", null, null);
        assertThat(code(first)).isEqualTo(200);
        // 3 秒内重复提交 -> 400
        ResponseEntity<Map> second = call(HttpMethod.POST, "/repeat/submit", null, null);
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        assertThat(code(second)).isEqualTo(400);
        assertThat((String) second.getBody().get("msg")).contains("重复提交");
    }

    // ------------------------------------------------------------------
    // 操作日志（@QuickLog -> OperationLogSink）
    // ------------------------------------------------------------------

    @Test
    void quickLogShouldRecordOperation() {
        String token = login("admin", "admin123");
        ResponseEntity<Map> resp = call(HttpMethod.GET, "/hello", token, null);
        assertThat(code(resp)).isEqualTo(200);

        dev.xuya.core.log.LogRecord record = memoryLogSink.lastRecord();
        assertThat(record).isNotNull();
        assertThat(record.getModule()).isEqualTo("演示");
        assertThat(record.getDescription()).isEqualTo("打招呼");
        assertThat(record.getUri()).isEqualTo("/hello");
        assertThat(String.valueOf(record.getOperator())).isEqualTo("1"); // Sa-Token loginId
        assertThat(record.isSuccess()).isTrue();
        assertThat(record.getResultCode()).isEqualTo(200);
        assertThat(record.getCostMs()).isGreaterThanOrEqualTo(0);
    }

    // ------------------------------------------------------------------
    // 批量新增（CrudOp.SAVE_BATCH，Db.saveBatch）
    // ------------------------------------------------------------------

    @Test
    void saveBatchShouldInsertAndCleanUp() {
        String token = login("admin", "admin123");
        String body = "[" +
                "{\"username\":\"batch01\",\"nickname\":\"批量一\",\"email\":\"batch01@quickdev.cn\",\"status\":1}," +
                "{\"username\":\"batch02\",\"nickname\":\"批量二\",\"email\":\"batch02@quickdev.cn\",\"status\":1}]";
        ResponseEntity<Map> resp = call(HttpMethod.POST, "/sys-user/batch", token, body);
        assertThat(code(resp)).isEqualTo(200);
        assertThat(((Number) resp.getBody().get("data")).intValue()).isEqualTo(2);

        // 逐条校验：第二条缺 username/email -> 400 且不入库
        String bad = "[{\"username\":\"batch03\",\"email\":\"batch03@quickdev.cn\"},{\"nickname\":\"缺字段\"}]";
        assertThat(code(call(HttpMethod.POST, "/sys-user/batch", token, bad))).isEqualTo(400);

        // 清理 batch01/batch02
        ResponseEntity<Map> page = call(HttpMethod.GET, "/sys-user/page?username=batch&current=1&size=10", token, null);
        List<Map<String, Object>> records = (List<Map<String, Object>>) data(page).get("records");
        String ids = records.stream().map(r -> String.valueOf(r.get("id")))
                .reduce((a, b) -> a + "," + b).orElse("");
        assertThat(ids).isNotEmpty();
        assertThat(code(call(HttpMethod.DELETE, "/sys-user/" + ids, token, null))).isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private ResponseEntity<Map> upload(String url, byte[] excel, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (token != null) {
            headers.set("Authorization", token);
        }
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(excel) {
            @Override
            public String getFilename() {
                return "products.xlsx";
            }
        });
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private byte[] writeExcel(List<Product> products) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FastExcel.write(out, Product.class).sheet("商品").doWrite(products);
        return out.toByteArray();
    }

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
