package dev.xuya.core.translate;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.*;

/**
 * 字段翻译：标注在实体/VO 字段上，JSON 序列化时把字段值翻译为可读文本。
 * 两种模式（二选一）：
 *
 * <pre>
 * // 字典翻译：值 -> 标签（DictResolver SPI 查字典表/枚举）
 * &#64;Translate(dict = "user_status")
 * private Integer status;              // 1 序列化为 "启用"
 *
 * // 枚举翻译：枚举类实现 DictEnum，免建字典表
 * &#64;Translate(enumClass = OrderStatus.class)
 * private Integer status;              // 1 序列化为 "已支付"
 *
 * // 关联翻译：字段值作为目标实体主键，取其某属性
 * &#64;Translate(entity = SysUser.class, field = "nickname")
 * private String createBy;             // "1" 序列化为 "管理员"
 * </pre>
 *
 * <p>翻译失败（无对应字典/记录、未实现 SPI）时保留原值输出，不影响接口；
 * 结果带 TTL 本地缓存（quick-dev.translate.cache-seconds，0 关闭）。</p>
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = TranslateSerializer.class)
public @interface Translate {

    /**
     * 字典模式：字典类型编码，由 DictResolver 解析
     */
    String dict() default "";

    /**
     * 枚举模式：实现 {@link DictEnum} 的枚举类（优先于 dict）
     */
    Class<?> enumClass() default Void.class;

    /**
     * 关联模式：目标实体类（字段值作为其主键查询）
     */
    Class<?> entity() default Void.class;

    /**
     * 关联模式：取目标实体的哪个属性作为翻译结果
     */
    String field() default "";
}
