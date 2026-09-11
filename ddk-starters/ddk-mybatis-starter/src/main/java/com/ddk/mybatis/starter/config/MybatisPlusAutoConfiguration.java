package com.ddk.mybatis.starter.config;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.ddk.core.mapper.MapperProvider;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动配置
 * <p>
 * 提供分页插件、雪花 ID 生成器、自动填充处理器的默认实现，
 * 并注册通用仓储所依赖的 {@link MapperProvider}。
 * 用户可以通过自定义 Bean 覆盖这些默认配置。
 *
 * @author Elijah Du
 */
@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
@EnableConfigurationProperties(DdkMybatisProperties.class)
public class MybatisPlusAutoConfiguration {

    /**
     * 注册映射器注册表。
     * <p>
     * 它必须由自动配置提供，而不是靠 {@code @Component} 加使用方的 component scan：
     * 业务应用的启动类通常在自己的包下，扫不到 {@code com.ddk}，
     * 那样 {@code GenericRepositoryImpl} 里的注入会直接失败。
     */
    @Bean
    @ConditionalOnMissingBean
    public MapperProvider mapperProvider(ApplicationContext context) {
        return new MapperProvider(context);
    }

    /**
     * 雪花 ID 生成器，作用于 {@code @TableId(type = IdType.ASSIGN_ID)} 的主键。
     * <p>
     * 给聚合根预生成标识是 DDK 推荐的做法：聚合从诞生起就有身份，
     * 领域事件可以在工厂方法里就带上标识，仓储也不需要「写入后回填」这一步。
     * <p>
     * 多实例部署时应当显式指定 {@code ddk.mybatis.worker-id} 与 {@code datacenter-id}，
     * 否则 Hutool 会按本机 MAC / IP 推导，理论上存在冲突。
     */
    @Bean
    @ConditionalOnMissingBean
    public IdentifierGenerator idGenerator(DdkMybatisProperties properties) {
        Long workerId = properties.getWorkerId();
        Long datacenterId = properties.getDatacenterId();
        if (workerId == null || datacenterId == null) {
            return param -> IdUtil.getSnowflakeNextId();
        }
        Snowflake snowflake = IdUtil.getSnowflake(workerId, datacenterId);
        return param -> snowflake.nextId();
    }

    /**
     * 插件顺序是有讲究的：MyBatis-Plus 要求<b>分页插件放在最后</b>，
     * 否则它改写出的 count SQL 会绕过前面的插件。
     * <p>
     * 乐观锁插件让 {@code AggregateRoot.version()} 真正生效——把版本号映射到 PO 上
     * 标了 {@code @Version} 的字段即可，更新时自动带上 {@code WHERE version = ?}。
     * 防全表更新删除插件拦的是漏写 WHERE 条件的 update / delete。
     */
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(DdkMybatisProperties properties) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        if (properties.isOptimisticLocker()) {
            interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        }
        if (properties.isBlockAttack()) {
            interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        }

        // 分页插件必须最后加
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(properties.getDbType());
        pagination.setMaxLimit(properties.getMaxPageSize());
        pagination.setOverflow(properties.isOverflow());
        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }

    @Bean
    @ConditionalOnMissingBean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
