package com.ddk.mybatis.starter.config;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MyBatis starter 默认填充与插件组合")
class MybatisPlusDefaultsTest {

    private final MybatisPlusAutoConfiguration configuration = new MybatisPlusAutoConfiguration();

    private final MetaObjectHandler handler = configuration.metaObjectHandler();

    @BeforeAll
    static void registerTableInfo() {
        MybatisConfiguration mybatis = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(mybatis, ""), OrderPo.class);
    }

    @Test
    @DisplayName("插入时填充 createTime 与 updateTime")
    void insertFillsBothTimestamps() {
        OrderPo po = new OrderPo();

        handler.insertFill(SystemMetaObject.forObject(po));

        assertThat(po.createTime).isNotNull();
        assertThat(po.updateTime).isNotNull();
    }

    @Test
    @DisplayName("插入时不覆盖调用方显式设置的时间")
    void insertKeepsExplicitValues() {
        LocalDateTime imported = LocalDateTime.of(2020, 1, 1, 0, 0);
        OrderPo po = new OrderPo();
        po.createTime = imported;

        handler.insertFill(SystemMetaObject.forObject(po));

        assertThat(po.createTime).isEqualTo(imported);
    }

    @Test
    @DisplayName("更新时只填充 updateTime")
    void updateFillsOnlyUpdateTime() {
        OrderPo po = new OrderPo();

        handler.updateFill(SystemMetaObject.forObject(po));

        assertThat(po.createTime).isNull();
        assertThat(po.updateTime).isNotNull();
    }

    @Test
    @DisplayName("默认插件：乐观锁、防全表更新删除、分页，分页排在最后")
    void defaultInterceptorsKeepPaginationLast() {
        MybatisPlusInterceptor interceptor = configuration.mybatisPlusInterceptor(new DdkMybatisProperties());

        assertThat(interceptor.getInterceptors()).extracting(Object::getClass).containsExactly(
                OptimisticLockerInnerInterceptor.class, BlockAttackInnerInterceptor.class, PaginationInnerInterceptor.class);
    }

    @Test
    @DisplayName("关闭乐观锁与防全表更新删除后只剩分页插件")
    void optionalInterceptorsCanBeDisabled() {
        DdkMybatisProperties properties = new DdkMybatisProperties();
        properties.setOptimisticLocker(false);
        properties.setBlockAttack(false);

        assertThat(configuration.mybatisPlusInterceptor(properties).getInterceptors())
                .singleElement().isInstanceOf(PaginationInnerInterceptor.class);
    }

    @Test
    @DisplayName("显式指定 worker-id 与 datacenter-id 时按它们生成递增 ID")
    void explicitSnowflakeIdsAreMonotonic() {
        DdkMybatisProperties properties = new DdkMybatisProperties();
        properties.setWorkerId(3L);
        properties.setDatacenterId(5L);

        var generator = configuration.idGenerator(properties);
        long first = generator.nextId(null).longValue();

        assertThat(generator.nextId(null).longValue()).isGreaterThan(first);
        assertThat((first >> 12) & 0x1F).isEqualTo(3L);
        assertThat((first >> 17) & 0x1F).isEqualTo(5L);
    }

    static class OrderPo {

        @TableId
        Long id;

        @TableField(fill = FieldFill.INSERT)
        LocalDateTime createTime;

        @TableField(fill = FieldFill.INSERT_UPDATE)
        LocalDateTime updateTime;
    }
}
