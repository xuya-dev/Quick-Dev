package dev.xuya.core.web;

import dev.xuya.core.common.R;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(true);

    @Test
    void duplicateKeyShouldReturnFriendly400WithoutSqlDetail() {
        R<Void> result = handler.handleDuplicateKey(
                new DuplicateKeyException("Duplicate entry 'admin' for key 'uk_username'"));
        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMsg()).contains("唯一键冲突");
        // 不透出 SQL/索引细节
        assertThat(result.getMsg()).doesNotContain("Duplicate entry");
    }
}
