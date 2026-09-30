package dev.xuya.core.translate;

/**
 * 关联翻译数据源 SPI（缓存优先）：实现并注册为 Spring Bean 后，
 * {@code @Translate(entity = X.class, field = "f")} 的关联翻译优先从本接口取值，
 * **不回源查库**；返回 null 时回退到默认的按主键查询。
 *
 * <pre>
 * &#64;Component
 * public class UserNicknameSource implements TranslateSource {
 *     &#64;Override
 *     public String translate(Class&lt;?&gt; entityType, String field, Object id) {
 *         if (entityType == SysUser.class &amp;&amp; "nickname".equals(field)) {
 *             return UserCache.nicknameOf(id);   // 全量内存缓存，零查库
 *         }
 *         return null;                            // 不处理则走默认查库
 *     }
 * }
 * </pre>
 *
 * <p>典型用途：把高频关联翻译（操作人昵称、部门名等）接入业务侧的全量内存缓存，
 * 与 DictLoader（字典）同一"缓存优先"理念。可注册多个 Bean，按顺序取第一个非 null 结果。</p>
 */
public interface TranslateSource {

    /**
     * @param entityType 被翻译注解声明的目标实体
     * @param field      目标实体上取值的属性名
     * @param id         被翻译字段的值（作为目标实体主键）
     * @return 翻译结果；返回 null 表示本 SPI 不处理或无数据（回退默认查询）
     */
    String translate(Class<?> entityType, String field, Object id);
}
