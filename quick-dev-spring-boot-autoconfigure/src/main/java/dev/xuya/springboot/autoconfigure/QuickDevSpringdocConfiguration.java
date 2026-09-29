package dev.xuya.springboot.autoconfigure;

import dev.xuya.core.crud.QuickCrudHandler;
import dev.xuya.core.methodop.*;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * springdoc 集成（可选）：classpath 引入 springdoc 后自动生效。
 *
 * <p>springdoc 只扫描注解式 Controller，无法识别框架运行期 registerMapping 注册的动态
 * CRUD 端点（springdoc-openapi#1560）。这里通过 {@link OpenApiCustomizer} 从
 * RequestMappingHandlerMapping 读取 QuickCrudHandler 的全部动态端点，手动注入
 * PathItem（含摘要与查询参数说明），使其出现在 Swagger UI；方法级注解端点
 * （普通 Controller，springdoc 本就收录）由 {@link OperationCustomizer} 补充摘要。</p>
 */
@AutoConfiguration
@ConditionalOnClass(OpenApiCustomizer.class)
public class QuickDevSpringdocConfiguration {

    private static final Map<String, String> CRUD_SUMMARIES = Map.ofEntries(
            Map.entry("page", "分页查询（current/size + 动态条件 + 排序）"),
            Map.entry("list", "列表查询（不分页）"),
            Map.entry("count", "按条件统计数量"),
            Map.entry("detail", "按主键查询详情"),
            Map.entry("save", "新增（Bean Validation 校验）"),
            Map.entry("saveBatch", "批量新增（JSON 数组）"),
            Map.entry("update", "按主键修改（null 字段不更新）"),
            Map.entry("remove", "删除（ids 逗号分隔支持批量）"),
            Map.entry("importExcel", "Excel 导入（multipart 字段 file）"),
            Map.entry("export", "Excel 导出（复用分页查询条件）"),
            Map.entry("importTemplate", "下载导入模板（仅表头）"),
            Map.entry("tree", "树形查询（parentId 构树）"));

    private static final String CRUD_DESCRIPTION =
            "Quick Dev 动态注册的端点。查询类参数与实体属性同名，条件方式由实体上的 @QueryField 决定"
                    + "（默认 EQ）；分页参数 current/size；排序 orderBy（实体属性名）+ order（asc/desc）。"
                    + "写入类请求体为实体 JSON。权限码见框架文档。";

    /**
     * 查询类端点的通用参数说明
     */
    private static List<Parameter> queryParameters() {
        return List.of(
                new Parameter().in("query").name("current").description("页码，默认 1").schema(numSchema()),
                new Parameter().in("query").name("size").description("每页条数，默认 10，上限 1000").schema(numSchema()),
                new Parameter().in("query").name("orderBy").description("排序字段（实体属性名）"),
                new Parameter().in("query").name("order").description("asc / desc"));
    }

    private static io.swagger.v3.oas.models.media.NumberSchema numSchema() {
        return new io.swagger.v3.oas.models.media.NumberSchema();
    }

    private static String resolvePath(org.springframework.web.servlet.mvc.method.RequestMappingInfo info) {
        if (info.getPathPatternsCondition() != null) {
            return info.getPathPatternsCondition().getPatternValues().stream().findFirst().orElse(null);
        }
        if (info.getPatternsCondition() != null) {
            return info.getPatternsCondition().getPatterns().stream().findFirst().orElse(null);
        }
        return null;
    }

    private static RequestMethod firstMethod(org.springframework.web.servlet.mvc.method.RequestMappingInfo info) {
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        return methods.isEmpty() ? RequestMethod.GET : methods.iterator().next();
    }

    /**
     * 把动态 CRUD 端点注入 OpenAPI paths
     */
    @Bean
    public OpenApiCustomizer quickCrudOpenApiCustomizer(RequestMappingHandlerMapping handlerMapping) {
        return openApi -> {
            if (openApi.getPaths() == null) {
                openApi.setPaths(new io.swagger.v3.oas.models.Paths());
            }
            handlerMapping.getHandlerMethods().forEach((info, handlerMethod) -> {
                if (!(handlerMethod.getBean() instanceof QuickCrudHandler)) {
                    return;
                }
                String path = resolvePath(info);
                RequestMethod requestMethod = firstMethod(info);
                if (path == null || requestMethod == null) {
                    return;
                }
                String handlerName = handlerMethod.getMethod().getName();
                Operation operation = new Operation()
                        .summary("[QuickCrud] " + CRUD_SUMMARIES.getOrDefault(handlerName, handlerName))
                        .description(CRUD_DESCRIPTION)
                        .responses(new ApiResponses().addApiResponse("200",
                                new ApiResponse().description("统一响应 R")));
                boolean isQuery = handlerName.equals("page") || handlerName.equals("list")
                        || handlerName.equals("count") || handlerName.equals("tree");
                if (isQuery) {
                    operation.setParameters(queryParameters());
                }
                PathItem item = openApi.getPaths().getOrDefault(path, new PathItem());
                switch (requestMethod) {
                    case GET -> item.setGet(operation);
                    case POST -> item.setPost(operation);
                    case PUT -> item.setPut(operation);
                    case DELETE -> item.setDelete(operation);
                    default -> { /* 其它方法不处理 */ }
                }
                openApi.getPaths().put(path, item);
            });
        };
    }

    /**
     * 给方法级注解端点（普通 Controller）补充摘要
     */
    @Bean
    public OperationCustomizer quickDevMethodOpOperationCustomizer() {
        return (operation, handlerMethod) -> {
            decorateMethodOp(operation, handlerMethod.getMethod());
            return operation;
        };
    }

    private void decorateMethodOp(Operation operation, Method method) {
        if (method.isAnnotationPresent(QuickSave.class)) {
            operation.setSummary("[QuickSave] 新增（框架 AOP 接管，方法体不执行）");
        } else if (method.isAnnotationPresent(QuickUpdate.class)) {
            operation.setSummary("[QuickUpdate] 修改（框架 AOP 接管，方法体不执行）");
        } else if (method.isAnnotationPresent(QuickRemove.class)) {
            operation.setSummary("[QuickRemove] 删除（框架 AOP 接管，方法体不执行）");
        } else if (method.isAnnotationPresent(QuickExport.class)) {
            operation.setSummary("[QuickExport] Excel 导出（框架 AOP 接管，方法体不执行）");
        } else if (method.isAnnotationPresent(QuickImport.class)) {
            operation.setSummary("[QuickImport] Excel 导入（框架 AOP 接管，方法体不执行）");
        }
    }
}
