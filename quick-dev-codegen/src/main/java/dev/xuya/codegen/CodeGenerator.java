package dev.xuya.codegen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.*;

/**
 * 代码生成器：读取数据库表结构，生成 Quick Dev 三件套源码（实体 / Mapper / @QuickCrud Controller）。
 *
 * <p>零依赖纯 JDK（java.sql.DatabaseMetaData），可通过 {@link #main} 命令行使用，也可编程调用：</p>
 * <pre>
 * GenerateResult result = CodeGenerator.generate(connection, "t_order", "com.example.order", "xuya");
 * System.out.println(result.entitySource());
 * CodeGenerator.writeFiles(result, Path.of("src/main/java"));
 * </pre>
 *
 * <p>约定：主键为数值列生成 {@code @TableId(type = IdType.AUTO)}，字符列生成
 * {@code IdType.ASSIGN_UUID}；审计列（create_time/update_time/create_by/update_by）
 * 自动叠加 {@code @TableField(fill = ...)}；列注释生成字段 Javadoc。</p>
 */
public final class CodeGenerator {

    private static final Map<String, String> TYPE_MAPPING = Map.ofEntries(
            Map.entry("BIGINT", "Long"),
            Map.entry("INT", "Integer"), Map.entry("INTEGER", "Integer"),
            Map.entry("SMALLINT", "Integer"), Map.entry("TINYINT", "Integer"),
            Map.entry("VARCHAR", "String"), Map.entry("CHAR", "String"),
            Map.entry("TEXT", "String"), Map.entry("CLOB", "String"), Map.entry("LONGVARCHAR", "String"),
            Map.entry("DECIMAL", "BigDecimal"), Map.entry("NUMERIC", "BigDecimal"),
            Map.entry("DOUBLE", "Double"), Map.entry("FLOAT", "Double"),
            Map.entry("BOOLEAN", "Boolean"), Map.entry("BIT", "Boolean"),
            Map.entry("DATE", "LocalDate"),
            Map.entry("TIMESTAMP", "LocalDateTime"), Map.entry("DATETIME", "LocalDateTime"),
            Map.entry("TIME", "LocalTime"));

    private CodeGenerator() {
    }

    /**
     * 命令行入口
     */
    public static void main(String[] args) throws Exception {
        Map<String, String> params = parseArgs(args);
        String url = required(params, "url");
        String table = required(params, "table");
        String packageName = required(params, "package");
        String out = params.getOrDefault("out", "");
        try (Connection connection = DriverManager.getConnection(
                url, params.getOrDefault("user", ""), params.getOrDefault("password", ""))) {
            GenerateResult result = generate(connection, table, packageName, params.getOrDefault("author", "quick-dev"));
            if (out.isEmpty()) {
                System.out.println(result.entitySource());
                System.out.println(result.mapperSource());
                System.out.println(result.controllerSource());
            } else {
                Path base = Path.of(out);
                writeFiles(result, base);
                System.out.println("生成完成 -> " + base.toAbsolutePath());
            }
        }
    }

    /**
     * 生成三件套源码（不写文件）
     */
    public static GenerateResult generate(Connection connection, String tableName,
                                          String packageName, String author) throws Exception {
        List<ColumnMeta> columns = readColumns(connection, tableName);
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("表 " + tableName + " 不存在或没有列");
        }
        String entityName = toPascal(tableName);
        String idColumn = columns.stream().filter(ColumnMeta::primary).findFirst()
                .map(ColumnMeta::columnName).orElse("id");

        return new GenerateResult(
                entityName, entityName + "Mapper", entityName + "Controller",
                renderEntity(packageName, entityName, tableName, columns, idColumn, author),
                renderMapper(packageName, entityName, author),
                renderController(packageName, entityName, author));
    }

    /**
     * 按包路径写出三个源码文件
     */
    public static void writeFiles(GenerateResult result, Path sourceRoot) throws Exception {
        String packagePath = packageOf(result.entitySource());
        Path dir = sourceRoot.resolve(packagePath.replace('.', '/'));
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(result.entityClassName() + ".java"), result.entitySource());
        Files.writeString(dir.resolve(result.mapperClassName() + ".java"), result.mapperSource());
        Files.writeString(dir.resolve(result.controllerClassName() + ".java"), result.controllerSource());
    }

    private static List<ColumnMeta> readColumns(Connection connection, String tableName) throws Exception {
        // 不同库对未加引号标识符的大小写处理不同（H2/Oracle 大写、PostgreSQL 小写），
        // 依次按 原样/大写/小写 尝试
        for (String candidate : new String[]{tableName, tableName.toUpperCase(), tableName.toLowerCase()}) {
            List<ColumnMeta> columns = tryReadColumns(connection, candidate);
            if (!columns.isEmpty()) {
                return columns;
            }
        }
        return List.of();
    }

    private static List<ColumnMeta> tryReadColumns(Connection connection, String tableName) throws Exception {
        DatabaseMetaData meta = connection.getMetaData();
        Set<String> primaryKeys = new LinkedHashSet<>();
        try (ResultSet pk = meta.getPrimaryKeys(null, null, tableName)) {
            while (pk.next()) {
                primaryKeys.add(pk.getString("COLUMN_NAME").toLowerCase());
            }
        }
        List<ColumnMeta> columns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(null, null, tableName, "%")) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME").toLowerCase();
                String typeName = rs.getString("TYPE_NAME").toUpperCase();
                String remark = rs.getString("REMARKS");
                columns.add(new ColumnMeta(name, typeName, remark, primaryKeys.contains(name)));
            }
        }
        return columns;
    }

    // ------------------------------------------------------------------
    // 元数据读取
    // ------------------------------------------------------------------

    private static String renderEntity(String packageName, String entityName, String tableName,
                                       List<ColumnMeta> columns, String idColumn, String author) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("package ").append(packageName).append(".entity;\n\n");
        sb.append("import com.baomidou.mybatisplus.annotation.FieldFill;\n");
        sb.append("import com.baomidou.mybatisplus.annotation.IdType;\n");
        sb.append("import com.baomidou.mybatisplus.annotation.TableField;\n");
        sb.append("import com.baomidou.mybatisplus.annotation.TableId;\n");
        sb.append("import com.baomidou.mybatisplus.annotation.TableName;\n");
        sb.append("import java.math.BigDecimal;\n");
        sb.append("import java.time.LocalDate;\n");
        sb.append("import java.time.LocalDateTime;\n\n");
        sb.append("/**\n * ").append(tableName);
        if (columns.stream().anyMatch(c -> c.remark != null && !c.remark.isBlank())) {
            // 表级注释缺列级汇总，保持简洁
        }
        sb.append(" 表对应实体（由 quick-dev-codegen 生成，作者：").append(author).append("）\n */\n");
        sb.append("@TableName(\"").append(tableName).append("\")\n");
        sb.append("public class ").append(entityName).append(" {\n\n");

        for (ColumnMeta column : columns) {
            String field = toCamel(column.columnName());
            String type = TYPE_MAPPING.getOrDefault(column.typeName(), "String");
            if (column.remark() != null && !column.remark().isBlank()) {
                sb.append("    /** ").append(column.remark()).append(" */\n");
            }
            if (column.columnName().equals(idColumn)) {
                sb.append("    @TableId(type = ").append("Long".equals(type) ? "IdType.AUTO" : "IdType.ASSIGN_UUID")
                        .append(")\n");
            } else if (isAuditInsert(column.columnName())) {
                sb.append("    @TableField(fill = FieldFill.INSERT)\n");
            } else if (isAuditUpdate(column.columnName())) {
                sb.append("    @TableField(fill = FieldFill.INSERT_UPDATE)\n");
            }
            sb.append("    private ").append(type).append(' ').append(field).append(";\n\n");
        }

        for (ColumnMeta column : columns) {
            String field = toCamel(column.columnName());
            String type = TYPE_MAPPING.getOrDefault(column.typeName(), "String");
            String pascal = toPascal(field);
            sb.append("    public ").append(type).append(" get").append(pascal).append("() { return ")
                    .append(field).append("; }\n");
            sb.append("    public void set").append(pascal).append('(').append(type).append(' ').append(field)
                    .append(") { this.").append(field).append(" = ").append(field).append("; }\n\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private static String renderMapper(String packageName, String entityName, String author) {
        return "package " + packageName + ".mapper;\n\n"
                + "import com.baomidou.mybatisplus.core.mapper.BaseMapper;\n"
                + "import " + packageName + ".entity." + entityName + ";\n\n"
                + "/**\n * 由 quick-dev-codegen 生成，作者：" + author + "\n */\n"
                + "public interface " + entityName + "Mapper extends BaseMapper<" + entityName + "> {\n}\n";
    }

    // ------------------------------------------------------------------
    // 模板渲染
    // ------------------------------------------------------------------

    private static String renderController(String packageName, String entityName, String author) {
        String permission = toKebab(entityName).replace('-', ':');
        return "package " + packageName + ".controller;\n\n"
                + "import dev.xuya.core.annotation.QuickCrud;\n"
                + "import " + packageName + ".entity." + entityName + ";\n\n"
                + "/**\n * 由 quick-dev-codegen 生成，作者：" + author + "\n"
                + " * permission 为建议前缀，按业务调整；includes 可按需增删（IMPORT/EXPORT/TREE 等默认不注册）\n */\n"
                + "@QuickCrud(entity = " + entityName + ".class, permission = \"" + permission + "\")\n"
                + "public class " + entityName + "Controller {\n}\n";
    }

    private static boolean isAuditInsert(String column) {
        return column.equals("create_time") || column.equals("create_by");
    }

    private static boolean isAuditUpdate(String column) {
        return column.equals("update_time") || column.equals("update_by");
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /**
     * order_item -> OrderItem
     */
    static String toPascal(String name) {
        String[] parts = name.toLowerCase().split("[_\\-]");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.toString();
    }

    /**
     * order_no -> orderNo
     */
    static String toCamel(String name) {
        String pascal = toPascal(name);
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    /**
     * OrderItem -> order-item
     */
    static String toKebab(String pascal) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pascal.length(); i++) {
            char c = pascal.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('-');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private static String packageOf(String source) {
        int start = source.indexOf("package ") + 8;
        int end = source.indexOf(';', start);
        return source.substring(start, end);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> params = new HashMap<>();
        for (String arg : args) {
            int eq = arg.indexOf('=');
            if (arg.startsWith("--") && eq > 0) {
                params.put(arg.substring(2, eq), arg.substring(eq + 1));
            }
        }
        return params;
    }

    private static String required(Map<String, String> params, String key) {
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少必填参数 --" + key);
        }
        return value;
    }

    /**
     * 生成结果：三个源码文件的内容与类名
     */
    public record GenerateResult(String entityClassName, String mapperClassName, String controllerClassName,
                                 String entitySource, String mapperSource, String controllerSource) {
    }

    /**
     * 单列元数据
     */
    private record ColumnMeta(String columnName, String typeName, String remark, boolean primary) {
    }
}
