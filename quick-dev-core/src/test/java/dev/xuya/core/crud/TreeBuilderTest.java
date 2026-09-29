package dev.xuya.core.crud;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import dev.xuya.core.common.QuickDevException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TreeBuilderTest {

    public static class Dept {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String name;
        private Long parentId;

        @TableField(exist = false)
        private List<Dept> children;

        public Dept(Long id, String name, Long parentId) {
            this.id = id;
            this.name = name;
            this.parentId = parentId;
        }

        public Long getId() { return id; }
        public String getName() { return name; }
        public Long getParentId() { return parentId; }
        public List<Dept> getChildren() { return children; }
    }

    public static class NoParentField {
        @TableId
        private Long id;
        private String name;
    }

    @Test
    void buildShouldNestChildrenUnderRoots() {
        Dept root = new Dept(1L, "总公司", 0L);
        Dept dev = new Dept(2L, "研发部", 1L);
        Dept fe = new Dept(3L, "前端组", 2L);
        Dept finance = new Dept(4L, "财务部", 1L);

        List<Object> tree = TreeBuilder.build(EntityMeta.of(Dept.class), List.of(fe, finance, root, dev));

        assertThat(tree).hasSize(1);
        Dept builtRoot = (Dept) tree.get(0);
        assertThat(builtRoot.getName()).isEqualTo("总公司");
        assertThat(builtRoot.getChildren()).extracting(Dept::getName).containsExactlyInAnyOrder("研发部", "财务部");
        Dept builtDev = builtRoot.getChildren().stream()
                .filter(d -> d.getName().equals("研发部")).findFirst().orElseThrow();
        assertThat(builtDev.getChildren()).extracting(Dept::getName).containsExactly("前端组");
    }

    @Test
    void nullParentIdShouldAlsoBeRootAndLeafChildrenShouldBeEmpty() {
        Dept root = new Dept(1L, "根", null);
        Dept child = new Dept(2L, "子", 1L);

        List<Object> tree = TreeBuilder.build(EntityMeta.of(Dept.class), List.of(child, root));

        assertThat(tree).hasSize(1);
        Dept builtRoot = (Dept) tree.get(0);
        assertThat(builtRoot.getChildren()).hasSize(1);
        assertThat(((Dept) builtRoot.getChildren().get(0)).getChildren()).isEmpty();
    }

    @Test
    void entityWithoutTreeFieldsShouldFailFast() {
        assertThatThrownBy(() -> TreeBuilder.build(EntityMeta.of(NoParentField.class), List.of()))
                .isInstanceOf(QuickDevException.class)
                .hasMessageContaining("parentId");
    }

    @Test
    void nonColumnFieldShouldNotAppearInColumnMap() {
        EntityMeta meta = EntityMeta.of(Dept.class);
        assertThat(meta.getColumn("children")).isNull();      // exist=false 不参与查询条件
        assertThat(meta.getColumn("parentId")).isEqualTo("parent_id");
        assertThat(meta.getField("children")).isNotNull();     // 但树构建可用
    }
}
