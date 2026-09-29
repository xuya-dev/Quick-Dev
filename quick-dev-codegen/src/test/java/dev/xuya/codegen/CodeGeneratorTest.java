package dev.xuya.codegen;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class CodeGeneratorTest {

    private static Connection connection;

    @BeforeAll
    static void init() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:codegen;DB_CLOSE_DELAY=-1", "sa", "");
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    create table t_order (
                        id          bigint primary key,
                        order_no    varchar(64) comment '订单编号',
                        amount      decimal(10,2),
                        status      int comment '状态',
                        buyer_id    bigint,
                        paid_time   timestamp,
                        create_time timestamp,
                        update_time timestamp,
                        create_by   varchar(64),
                        update_by   varchar(64)
                    )""");
        }
    }

    @AfterAll
    static void close() throws Exception {
        connection.close();
    }

    @Test
    void shouldGenerateEntityMapperController() throws Exception {
        CodeGenerator.GenerateResult result =
                CodeGenerator.generate(connection, "t_order", "com.example.order", "tester");

        // 实体：表名映射、主键、类型映射、审计列 fill、注释 Javadoc、getter/setter
        String entity = result.entitySource();
        assertThat(entity).contains("package com.example.order.entity;");
        assertThat(entity).contains("@TableName(\"t_order\")");
        assertThat(entity).contains("public class TOrder {");
        assertThat(entity).contains("@TableId(type = IdType.AUTO)");
        assertThat(entity).contains("private Long id;");
        assertThat(entity).contains("private String orderNo;");
        assertThat(entity).contains("/** 订单编号 */");
        assertThat(entity).contains("private BigDecimal amount;");
        assertThat(entity).contains("private Integer status;");
        assertThat(entity).contains("private LocalDateTime paidTime;");
        // 审计列自动叠加 fill
        assertThat(entity).contains("@TableField(fill = FieldFill.INSERT)\n    private LocalDateTime createTime;");
        assertThat(entity).contains("@TableField(fill = FieldFill.INSERT_UPDATE)\n    private LocalDateTime updateTime;");
        assertThat(entity).contains("@TableField(fill = FieldFill.INSERT)\n    private String createBy;");
        assertThat(entity).contains("public BigDecimal getAmount() { return amount; }");

        // Mapper
        assertThat(result.mapperSource())
                .contains("public interface TOrderMapper extends BaseMapper<TOrder> {");

        // Controller：权限码建议前缀（t_order -> t:order，注释说明按业务调整）
        assertThat(result.controllerSource())
                .contains("package com.example.order.controller;")
                .contains("@QuickCrud(entity = TOrder.class, permission = \"t:order\")")
                .contains("public class TOrderController {");
    }

    @Test
    void stringPrimaryKeyShouldUseAssignUuid() throws Exception {
        try (Statement st = connection.createStatement()) {
            st.execute("create table t_gift (id varchar(32) primary key, name varchar(100))");
        }
        CodeGenerator.GenerateResult result =
                CodeGenerator.generate(connection, "t_gift", "com.example.gift", "tester");
        assertThat(result.entitySource()).contains("@TableId(type = IdType.ASSIGN_UUID)");
        assertThat(result.entitySource()).contains("private String id;");
    }

    @Test
    void namingHelpersShouldBehave() {
        assertThat(CodeGenerator.toPascal("sys_user_role")).isEqualTo("SysUserRole");
        assertThat(CodeGenerator.toCamel("order_no")).isEqualTo("orderNo");
        assertThat(CodeGenerator.toKebab("SysUserRole")).isEqualTo("sys-user-role");
    }
}
