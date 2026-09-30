package dev.xuya.core.crud;

import java.util.List;

/**
 * CRUD 生命周期钩子 SPI：实现并注册为 Spring Bean 即可介入
 * {@link QuickCrud}（及方法级注解之外的全部内置写操作）的写流程，
 * 完成多表绑定、缓存刷新、审计等横切逻辑——无需再为聚合写手写 Controller。
 *
 * <pre>
 * &#64;Component
 * public class DictDataHook implements CrudHook {
 *     &#64;Override public Class&lt;?&gt; entityType() { return SysDictData.class; }
 *
 *     &#64;Override public void beforeSave(Object entity) { /* 校验、补默认值 *&#47; }
 *     &#64;Override public void afterSave(Object entity)  { /* 刷新缓存、发事件 *&#47; }
 * }
 * </pre>
 *
 * <p><b>事务语义</b>（写操作已统一纳入事务，见下）：</p>
 * <ul>
 *   <li>{@code before*}：在写事务<b>内</b>执行，抛出异常将回滚本次写操作并使请求失败；</li>
 *   <li>{@code after*}：在事务<b>提交后</b>执行（数据对其他连接可见），抛出异常仅记录 WARN
 *       日志、不影响本次请求的响应——缓存刷新等副作用失败不应让已成功的写操作报错。</li>
 * </ul>
 * <p>同一实体可注册多个 Hook，按 Spring Bean 的顺序执行；方法均为 default 空实现，
 * 按需覆盖。非 Web 容器/无事务基础设施时写操作直接执行，钩子语义不变。</p>
 */
public interface CrudHook {

    /**
     * 本钩子服务的实体类型（与 @QuickCrud.entity 对应）
     */
    Class<?> entityType();

    /**
     * 新增前（事务内；含批量新增的逐条调用与 saveOrUpdate 的新增分支）
     */
    default void beforeSave(Object entity) {
    }

    /**
     * 新增后（事务提交后；entity 已回填生成的主键）
     */
    default void afterSave(Object entity) {
    }

    /**
     * 修改前（事务内；含 saveOrUpdate 的更新分支；entity 为部分更新载荷）
     */
    default void beforeUpdate(Object entity) {
    }

    /**
     * 修改后（事务提交后；仅在实际更新到行时回调）
     */
    default void afterUpdate(Object entity) {
    }

    /**
     * 删除前（事务内；ids 为转换为主键类型后的 ID 列表）
     */
    default void beforeRemove(List<Object> ids) {
    }

    /**
     * 删除后（事务提交后）
     */
    default void afterRemove(List<Object> ids) {
    }
}
