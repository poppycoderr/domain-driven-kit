package com.ddk.mybatis.starter.config;

import com.baomidou.mybatisplus.annotation.DbType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MyBatis 相关的 DDK 配置。
 * <p>
 * 用独立的 {@code ddk.mybatis} 前缀，不复用 {@code mybatis-plus.*}——
 * 后者是 MyBatis-Plus 自己的命名空间，混进去会让「这个配置项归谁管」变得不清楚。
 *
 * @author Elijah Du
 */
@Data
@ConfigurationProperties(prefix = DdkMybatisProperties.PREFIX)
public class DdkMybatisProperties {

    public static final String PREFIX = "ddk.mybatis";

    /**
     * 分页插件的数据库方言。
     * <p>
     * 默认 MySQL。用 PostgreSQL、Oracle、达梦等数据库时必须改，
     * 否则会生成 MySQL 风格的分页 SQL。
     */
    private DbType dbType = DbType.MYSQL;

    /**
     * 单页最大条数，超过会被静默截断。
     * <p>
     * 这是最后一道防线，业务接口仍应按场景收得更窄。
     */
    private Long maxPageSize = 500L;

    /**
     * 页码超过总页数时是否回到第一页。
     * <p>
     * 默认 false：返回空结果。回到第一页容易让「翻到底」的调用方陷入死循环。
     */
    private boolean overflow = false;

    /**
     * 是否启用乐观锁插件。
     * <p>
     * 启用后，PO 上标了 {@code @Version} 的字段会在 update 时自动带上
     * {@code WHERE version = ?} 并自增。这是 {@code AggregateRoot.version()}
     * 真正生效的前提。
     */
    private boolean optimisticLocker = true;

    /**
     * 是否启用防全表更新删除插件。
     * <p>
     * 启用后，不带 WHERE 条件的 update / delete 会直接抛异常。
     * 确实需要全表操作时，用 Mapper XML 显式写，不要关掉这个开关。
     */
    private boolean blockAttack = true;

    /**
     * 雪花 ID 的机器号（0-31）。
     * <p>
     * 与 {@link #datacenterId} 必须同时设置，否则回落到 Hutool 按本机推导的默认值。
     * <b>多实例部署时应当显式指定</b>，推导值在容器环境下并不可靠。
     */
    private Long workerId;

    /**
     * 雪花 ID 的数据中心号（0-31）。
     */
    private Long datacenterId;
}
