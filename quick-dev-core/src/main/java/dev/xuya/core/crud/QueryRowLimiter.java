package dev.xuya.core.crud;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import dev.xuya.core.common.ParamException;
import dev.xuya.core.common.QuickDevLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 不分页查询的行数护栏：list/tree 这类"全量返回"接口若不加限制，
 * 一个请求就能把整表拉进堆内存并序列化成 JSON。
 *
 * <p>实现方式：借分页插件的 LIMIT 做<b>数据库侧</b>截断（{@code searchCount=false}
 * 时不发 count 查询，开销与普通查询同级），多取一行用于判定溢出，超限直接 400，
 * 而不是静默截断——调用方应当补查询条件，而不是拿到一份被悄悄裁掉的数据。</p>
 */
public final class QueryRowLimiter {

    private static final Logger log = LoggerFactory.getLogger(QueryRowLimiter.class);

    /**
     * 分页插件缺失的告警只打一次，避免每个请求刷屏
     */
    private static volatile boolean paginationMissingWarned = false;

    private QueryRowLimiter() {
    }

    /**
     * 按 {@link QuickDevLimits#getQueryMaxRows()} 护栏执行不分页查询
     *
     * @param mapper  目标 Mapper
     * @param wrapper 查询条件
     * @param scene   场景名（用于错误信息，如 "list"/"tree"）
     * @return 命中行（护栏生效时保证不超过上限）
     */
    public static List<Object> selectListLimited(BaseMapper<Object> mapper, Wrapper<Object> wrapper, String scene) {
        int maxRows = QuickDevLimits.getQueryMaxRows();
        // 多取一行：够判定"是否溢出"即可，无需知道真实总数
        Page<Object> page = new Page<>(1, (long) maxRows + 1, false);
        List<Object> rows;
        try {
            rows = mapper.selectPage(page, wrapper).getRecords();
        } catch (RuntimeException e) {
            // 无分页插件时 MyBatis-Plus 会忽略 Page 直接全量查询：护栏失效，
            // 降级放行（不破坏既有可用应用）但必须显式告警一次
            warnPaginationMissing(scene, e);
            return mapper.selectList(wrapper);
        }
        if (rows.size() > maxRows) {
            throw new ParamException("查询 " + scene + " 命中行数超过上限 " + maxRows
                    + "，请补充查询条件后重试（可通过 quick-dev.limits.query-max-rows 调整）");
        }
        return rows;
    }

    private static void warnPaginationMissing(String scene, RuntimeException cause) {
        if (paginationMissingWarned) {
            return;
        }
        paginationMissingWarned = true;
        log.warn("查询 {} 未能实施行数护栏（quick-dev.limits.query-max-rows={}）：容器中缺少 MyBatis-Plus "
                        + "PaginationInnerInterceptor，将退化为无界查询。请引入 quick-dev-spring-boot-starter "
                        + "或自行注册该插件。原因: {}",
                scene, QuickDevLimits.getQueryMaxRows(), cause.getMessage());
    }
}
