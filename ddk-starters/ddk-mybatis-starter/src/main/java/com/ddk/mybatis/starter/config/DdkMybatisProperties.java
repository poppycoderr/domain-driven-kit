package com.ddk.mybatis.starter.config;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import com.baomidou.mybatisplus.annotation.DbType;
import lombok.Data;
import org.jspecify.annotations.Nullable;
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
    private @Nullable Long workerId;

    /**
     * 雪花 ID 的数据中心号（0-31）。
     */
    private @Nullable Long datacenterId;

    /**
     * 多租户。
     */
    private Tenant tenant = new Tenant();

    /**
     * 行级数据权限。
     */
    private DataScope dataScope = new DataScope();

    @Data
    public static class DataScope {

        /**
         * 是否启用行级数据权限。启用后，受控表的查询、更新、删除都会按操作者的数据范围追加条件；需要应用提供一个
         * {@code DataScopeResolver}。
         */
        private boolean enabled = false;

        /**
         * 受数据权限控制的表。只有列在这里的表才追加条件。不区分大小写。
         */
        private Set<String> tables = new LinkedHashSet<>();

        /**
         * 记录数据属于哪个组的列。
         */
        private String groupColumn = "dept_id";

        /**
         * 记录数据由谁创建的列。
         */
        private String ownerColumn = "create_by";

        /**
         * 组的标识是否为数值类型。为 false 时按字符串比较。
         */
        private boolean numericGroupId = true;

        /**
         * 创建人的标识是否为数值类型。为 false 时按字符串比较。
         */
        private boolean numericOwnerId = true;

        /**
         * 个别表的列名或列类型与默认值不同时在这里覆盖，key 是表名。列名设成空字符串表示这张表没有这个维度。
         */
        private Map<String, Columns> columns = new LinkedHashMap<>();

        @Data
        public static class Columns {

            private @Nullable String groupColumn;

            private @Nullable String ownerColumn;

            private @Nullable Boolean numericGroupId;

            private @Nullable Boolean numericOwnerId;
        }
    }

    @Data
    public static class Tenant {

        /**
         * 是否启用多租户。启用后每条 SQL 都会追加租户条件，租户取自 {@code OperatorContext}；取不到时语句直接失败。
         */
        private boolean enabled = false;

        /**
         * 租户列名。
         */
        private String column = "tenant_id";

        /**
         * 租户列是否为数值类型。为 false 时按字符串比较。
         */
        private boolean numericId = true;

        /**
         * 不分租户的表，例如字典表、租户表本身。不区分大小写。
         */
        private Set<String> ignoreTables = new LinkedHashSet<>();
    }
}
