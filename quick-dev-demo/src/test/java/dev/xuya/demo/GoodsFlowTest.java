package dev.xuya.demo;

import dev.xuya.demo.entity.Goods;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 0.4.0 特性端到端：分阶段校验（Create/Update 分组）、@QuickRequire 条件必填、
 * CrudHook 规范化、delFlag 保护、APPEND 翻译、多列排序。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GoodsFlowTest {

    @Autowired
    private TestRestTemplate rest;

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private Map<String, Object> post(String json) {
        HttpEntity<String> entity = new HttpEntity<>(json, jsonHeaders());
        return rest.exchange("/goods", HttpMethod.POST, entity, Map.class).getBody();
    }

    private Map<String, Object> put(String json) {
        HttpEntity<String> entity = new HttpEntity<>(json, jsonHeaders());
        return rest.exchange("/goods", HttpMethod.PUT, entity, Map.class).getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> detail(long id) {
        Map<String, Object> body = rest.exchange("/goods/" + id, HttpMethod.GET, null, Map.class).getBody();
        return (Map<String, Object>) body.get("data");
    }

    private void cleanup(long id) {
        rest.exchange("/goods/" + id, HttpMethod.DELETE, null, Map.class);
    }

    @Test
    void saveShouldEnforceCreateGroupAndQuickRequire() {
        // name 缺失：Create 组 @NotBlank 拦截
        Map<String, Object> body = post("{\"stock\":5}");
        assertThat(body.get("code")).as(String.valueOf(body)).isEqualTo(400);
        assertThat((String) body.get("msg")).contains("name");

        // 售罄（status=0）未填 reason：@QuickRequire 拦截
        body = post("{\"name\":\"G1\",\"stock\":0,\"status\":0}");
        assertThat(body.get("code")).as(String.valueOf(body)).isEqualTo(400);
        assertThat((String) body.get("msg")).contains("售罄");
    }

    @Test
    void hookShouldNormalizeReasonForOnSaleGoods() {
        Map<String, Object> body = post("{\"name\":\"G2\",\"stock\":5,\"reason\":\"不应有原因\",\"status\":1}");
        assertThat(body.get("code")).as(String.valueOf(body)).isEqualTo(200);
        long id = ((Number) ((Map<String, Object>) body.get("data")).get("id")).longValue();
        try {
            Map<String, Object> goods = detail(id);
            assertThat(goods.get("reason")).isNull();
            assertThat(goods.get("status")).isEqualTo(1);
            assertThat(goods.get("statusName")).isEqualTo("在售");
        } finally {
            cleanup(id);
        }
    }

    @Test
    void updateShouldValidateProvidedFieldsOnly() {
        Map<String, Object> body = post("{\"name\":\"G3\",\"stock\":0,\"reason\":\"清仓\",\"status\":0}");
        long id = ((Number) ((Map<String, Object>) body.get("data")).get("id")).longValue();
        try {
            // 合法部分更新：仅改 remark
            body = put("{\"id\":" + id + ",\"remark\":\"部分更新\"}");
            assertThat(body.get("code")).as(String.valueOf(body)).isEqualTo(200);

            // 提交非法值：stock=-1 触发 Default 组 @Min，部分校验拦截
            body = put("{\"id\":" + id + ",\"stock\":-1}");
            assertThat(body.get("code")).as(String.valueOf(body)).isEqualTo(400);
            assertThat((String) body.get("msg")).contains("修改参数校验失败");
        } finally {
            cleanup(id);
        }
    }

    @Test
    void delFlagShouldBeProtectedFromClients() {
        Map<String, Object> body = post("{\"name\":\"G4\",\"stock\":1,\"delFlag\":2}");
        assertThat(body.get("code")).as(String.valueOf(body)).isEqualTo(200);
        long id = ((Number) ((Map<String, Object>) body.get("data")).get("id")).longValue();
        try {
            Map<String, Object> goods = detail(id);
            // @JsonIgnore：delFlag 键不输出 = 客户端无法感知/操纵逻辑删除
            assertThat(goods).doesNotContainKey("delFlag");
            assertThat(goods.get("id")).isNotNull();
        } finally {
            cleanup(id);
        }
    }

    @Test
    void pageShouldSupportMultiColumnSort() {
        Map<String, Object> body = rest.exchange(
                        "/goods/page?orderBy=stock,name&order=desc",
                        HttpMethod.GET, null, Map.class)
                .getBody();
        assertThat(body.get("code")).as(String.valueOf(body)).isEqualTo(200);
    }
}
