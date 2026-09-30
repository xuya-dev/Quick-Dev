package dev.xuya.core.crud;

import dev.xuya.core.annotation.CrudOp;
import dev.xuya.core.annotation.QuickCrud;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * quick-dev.crud 全局默认 includes/excludes 与注解的组合规则。
 */
class QuickCrudRegistrarOpsTest {

    private static final CrudOp[] GLOBAL_INCLUDES = {
            CrudOp.PAGE, CrudOp.LIST, CrudOp.DETAIL, CrudOp.SAVE, CrudOp.UPDATE, CrudOp.REMOVE};

    private final QuickCrudRegistrar registrar =
            new QuickCrudRegistrar(GLOBAL_INCLUDES, new CrudOp[]{CrudOp.SAVE_BATCH, CrudOp.SAVE_OR_UPDATE});

    @Test
    void globalDefaultsShouldApplyWhenAnnotationUnspecified() {
        QuickCrud anno = anno(DefaultController.class);
        assertThat(registrar.resolveOps(anno)).containsExactly(
                CrudOp.PAGE, CrudOp.LIST, CrudOp.DETAIL, CrudOp.SAVE, CrudOp.UPDATE, CrudOp.REMOVE);
    }

    @Test
    void explicitIncludesShouldIgnoreGlobalDefaults() {
        QuickCrud anno = anno(ExplicitController.class);
        assertThat(registrar.resolveOps(anno)).containsExactly(CrudOp.PAGE, CrudOp.COUNT, CrudOp.LIST);
    }

    @Test
    void globalExcludesShouldSubtractAlways() {
        QuickCrudRegistrar excluding = new QuickCrudRegistrar(
                new CrudOp[0], new CrudOp[]{CrudOp.COUNT, CrudOp.LIST});
        assertThat(excluding.resolveOps(anno(ExplicitController.class)))
                .containsExactly(CrudOp.PAGE);
        // 注解默认 includes 全量 - 全局排除
        assertThat(excluding.resolveOps(anno(DefaultController.class)))
                .containsExactly(CrudOp.PAGE, CrudOp.DETAIL, CrudOp.SAVE, CrudOp.SAVE_BATCH,
                        CrudOp.SAVE_OR_UPDATE, CrudOp.UPDATE, CrudOp.REMOVE);
    }

    private QuickCrud anno(Class<?> controller) {
        return AnnotatedElementUtils.findMergedAnnotation(controller, QuickCrud.class);
    }

    @QuickCrud(entity = String.class)
    static class DefaultController {
    }

    @QuickCrud(entity = String.class, includes = {CrudOp.PAGE, CrudOp.COUNT, CrudOp.LIST})
    static class ExplicitController {
    }
}
