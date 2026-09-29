package dev.xuya.demo;

import cn.idev.excel.FastExcel;
import dev.xuya.core.log.LogRecord;
import dev.xuya.demo.entity.Product;
import dev.xuya.demo.log.MemoryLogSink;
import java.io.ByteArrayInputStream;
import java.time.Year;
import java.util.Arrays;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private MemoryLogSink memoryLogSink;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        // createTime/updateTime 自动填充 + 操作人自动填充并翻译为昵称（@Translate 关联翻译）
        assertThat(saved.get("createTime")).isNotNull();
        assertThat(saved.get("updateTime")).isNotNull();
        assertThat(saved.get("createBy")).isEqualTo("管理员"); // loginId "1" -> SysUser.nickname
        assertThat(saved.get("updateBy")).isEqualTo("管理员");
        // 字典翻译：status 1 -> 启用
        assertThat(saved.get("status")).isEqualTo("启用");

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
        assertThat(afterUpdate.get("updateBy")).isEqualTo("管理员");

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
        int year = Year.now().getValue();
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

        // translate = true：读回验证 @Translate 字段导出为中文标签
        List<Map<Integer, String>> rows = FastExcel.read(new ByteArrayInputStream(body))
                .sheet().headRowNumber(0).doReadSync();
        assertThat((String) rows.get(0).get(2)).isEqualTo("type");       // 表头第三列
        assertThat((String) rows.get(1).get(2)).isEqualTo("普通商品");   // 值 1 已翻译
        assertThat((String) rows.get(1).get(3)).isEqualTo("线上");       // channel 1 已翻译
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
        p1.setType(1);
        p1.setChannel(1);
        p1.setPrice(new BigDecimal("11.11"));
        p1.setStock(10);
        Product p2 = new Product();
        p2.setName("导入商品B");
        p2.setType(2);
        p2.setChannel(2);
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

        LogRecord record = memoryLogSink.lastRecord();
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
    // 字段翻译（@Translate）
    // ------------------------------------------------------------------

    @Test
    void translateShouldRenderDictAndRefLabels() {
        String token = login("admin", "admin123");
        // 种子数据 admin：status=1 -> "启用"；种子无 createBy -> 保持 null
        ResponseEntity<Map> detail = call(HttpMethod.GET, "/sys-user/1", token, null);
        assertThat(code(detail)).isEqualTo(200);
        Map<String, Object> admin = data(detail);
        assertThat(admin.get("status")).isEqualTo("启用");
        assertThat(admin.get("createBy")).isNull();

        // 分页列表同样生效
        ResponseEntity<Map> page = call(HttpMethod.GET, "/sys-user/page?username=viewer", token, null);
        List<Map<String, Object>> records = (List<Map<String, Object>>) data(page).get("records");
        assertThat(records.get(0).get("status")).isEqualTo("启用");

        // 枚举字典翻译（免建字典表）：product.type 1 -> 普通商品
        ResponseEntity<Map> product = call(HttpMethod.GET,
                "/product/p0000000000000000000000000000001", null, null);
        assertThat(code(product)).isEqualTo(200);
        assertThat(data(product).get("type")).isEqualTo("普通商品");

        // 未加翻译字段的实体不受影响
        assertThat(code(call(HttpMethod.GET, "/product/page", null, null))).isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // 行级数据权限（@DataScope + DataScopeResolver）
    // ------------------------------------------------------------------

    @Test
    void dataScopeShouldFilterRowsByDepartment() {
        // viewer（dept 2）：只能看到本部门的 admin/viewer，看不到财务部的 alice
        String viewerToken = login("viewer", "viewer123");
        ResponseEntity<Map> page = call(HttpMethod.GET, "/sys-user/page", viewerToken, null);
        assertThat(code(page)).isEqualTo(200);
        List<Map<String, Object>> records = (List<Map<String, Object>>) data(page).get("records");
        assertThat(records).extracting(r -> r.get("username")).containsExactlyInAnyOrder("admin", "viewer");

        // count 与分页一致
        ResponseEntity<Map> count = call(HttpMethod.GET, "/sys-user/count", viewerToken, null);
        assertThat(((Number) count.getBody().get("data")).intValue()).isEqualTo(2);

        // admin：不受数据权限限制，可看到全部
        String adminToken = login("admin", "admin123");
        ResponseEntity<Map> all = call(HttpMethod.GET, "/sys-user/page", adminToken, null);
        assertThat((List<Map<String, Object>>) data(all).get("records"))
                .extracting(r -> r.get("username")).containsExactlyInAnyOrder("admin", "viewer", "alice");
    }

    // ------------------------------------------------------------------
    // 字典缓存：内置刷新接口
    // ------------------------------------------------------------------

    @Test
    void dictRefreshShouldReloadCacheAndApplyChanges() {
        // 未登录 401；viewer 无 dict:refresh 权限 403
        assertThat(call(HttpMethod.POST, "/quick-dev/dict/refresh", null, null)
                .getStatusCode().value()).isEqualTo(401);
        String viewerToken = login("viewer", "viewer123");
        assertThat(call(HttpMethod.POST, "/quick-dev/dict/refresh", viewerToken, null)
                .getStatusCode().value()).isEqualTo(403);

        // 修改字典标签 -> 刷新 -> 新标签立即生效（不再受 TTL 结果缓存影响）
        String adminToken = login("admin", "admin123");
        jdbcTemplate.update("update sys_dict set dict_label = '在职' where dict_type = 'user_status' and dict_value = '1'");
        ResponseEntity<Map> refreshed = call(HttpMethod.POST, "/quick-dev/dict/refresh", adminToken, null);
        assertThat(code(refreshed)).isEqualTo(200);
        assertThat(((Number) data(refreshed).get("size")).intValue()).isGreaterThanOrEqualTo(4);

        ResponseEntity<Map> detail = call(HttpMethod.GET, "/sys-user/1", adminToken, null);
        assertThat(data(detail).get("status")).isEqualTo("在职");

        // 还原字典并刷新，保证其它测试稳定
        jdbcTemplate.update("update sys_dict set dict_label = '启用' where dict_type = 'user_status' and dict_value = '1'");
        assertThat(code(call(HttpMethod.POST, "/quick-dev/dict/refresh", adminToken, null))).isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // 字典管理接口（内置 CRUD + 写后自动刷新缓存）
    // ------------------------------------------------------------------

    @Test
    void dictAdminShouldSaveAndTakeEffectImmediately() {
        String adminToken = login("admin", "admin123");

        // 权限：匿名 401
        assertThat(call(HttpMethod.GET, "/quick-dev/dict/page?type=user_status", null, null)
                .getStatusCode().value()).isEqualTo(401);

        // 分页查询
        ResponseEntity<Map> page = call(HttpMethod.GET,
                "/quick-dev/dict/page?type=user_status&current=1&size=10", adminToken, null);
        assertThat(code(page)).isEqualTo(200);
        assertThat(((Number) data(page).get("total")).intValue()).isEqualTo(2);

        // 新增字典（user_status 9 -> 封禁），保存后缓存自动刷新
        ResponseEntity<Map> saved = call(HttpMethod.POST, "/quick-dev/dict", adminToken,
                Map.of("type", "user_status", "value", "9", "label", "封禁"));
        assertThat(code(saved)).isEqualTo(200);

        // 把种子用户 alice 的状态改为 9 -> 翻译立即生效为 "封禁"
        jdbcTemplate.update("update sys_user set status = 9 where username = 'alice'");
        try {
            ResponseEntity<Map> detail = call(HttpMethod.GET,
                    "/sys-user/page?username=alice", adminToken, null);
            List<Map<String, Object>> records =
                    (List<Map<String, Object>>) data(detail).get("records");
            assertThat(records.get(0).get("status")).isEqualTo("封禁");

            // 删除字典 -> 翻译回落为原始值 9
            ResponseEntity<Map> deleted = call(HttpMethod.DELETE,
                    "/quick-dev/dict?type=user_status&value=9", adminToken, null);
            assertThat(code(deleted)).isEqualTo(200);
            ResponseEntity<Map> afterDelete = call(HttpMethod.GET,
                    "/sys-user/page?username=alice", adminToken, null);
            List<Map<String, Object>> rows =
                    (List<Map<String, Object>>) data(afterDelete).get("records");
            assertThat(((Number) rows.get(0).get("status")).intValue()).isEqualTo(9);
        } finally {
            // 还原 alice 状态，保证其它测试稳定
            jdbcTemplate.update("update sys_user set status = 1 where username = 'alice'");
        }
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

    @Test
    void importShouldReverseTranslateChineseLabels() {
        // 用户上传的 Excel 填的是中文标签（type/channel 列），导入时自动反解回库值
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FastExcel.write(out)
                .head(List.of(List.of("商品名称"), List.of("type"), List.of("channel"),
                        List.of("价格"), List.of("库存")))
                .sheet("商品")
                .doWrite(List.of(
                        Arrays.asList("标签导入A", "普通商品", "线上", 11.11, 10),
                        Arrays.asList("标签导入B", "赠品", "线下", 22.22, 20)));
        byte[] excel = out.toByteArray();

        String adminToken = login("admin", "admin123");
        ResponseEntity<Map> imported = upload("/product/import", excel, adminToken);
        assertThat(code(imported)).isEqualTo(200);
        assertThat(((Number) data(imported).get("total")).intValue()).isEqualTo(2);

        // 枚举自动反解："普通商品" -> 1
        ResponseEntity<Map> byType = call(HttpMethod.GET,
                "/product/count?name=标签导入&type=1", null, null);
        assertThat(((Number) byType.getBody().get("data")).intValue()).isEqualTo(1);
        // 字典 SPI 反解（用户自主实现）："线上" -> 1
        ResponseEntity<Map> byChannel = call(HttpMethod.GET,
                "/product/count?name=标签导入&channel=1", null, null);
        assertThat(((Number) byChannel.getBody().get("data")).intValue()).isEqualTo(1);

        // 清理
        ResponseEntity<Map> page = call(HttpMethod.GET, "/product/page?name=标签导入", null, null);
        List<Map<String, Object>> records = (List<Map<String, Object>>) data(page).get("records");
        String ids = records.stream().map(r -> String.valueOf(r.get("id")))
                .reduce((a, b) -> a + "," + b).orElse("");
        if (!ids.isEmpty()) {
            assertThat(code(call(HttpMethod.DELETE, "/product/" + ids, null, null))).isEqualTo(200);
        }
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
