package com.ddk.mybatis.children;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.ddk.core.mapper.EnhancedMapper;
import com.ddk.core.mapper.MapperProvider;
import com.ddk.core.mapper.ObjectMapper;
import com.ddk.core.page.PageQuery;
import com.ddk.core.repository.ConcurrentUpdateException;
import com.ddk.mybatis.repository.GenericRepositoryImpl;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 聚合由根表和子表组成时，仓储子类用扩展点处理子表。这里对真实执行的 SQL 验证四个扩展点各自的调用时机。
 */
@SpringBootTest(classes = AggregateChildTablesIntegrationTest.TestApp.class, properties = {
        "spring.datasource.url=jdbc:h2:mem:children;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:children-schema.sql"
})
@DisplayName("通用仓储：聚合带子表")
class AggregateChildTablesIntegrationTest {

    @Autowired
    private BasketRepository baskets;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM ddk_basket_item");
        jdbc.update("DELETE FROM ddk_basket");
    }

    @Test
    @DisplayName("创建时写入子表，加载时整体读回，返回值也带着子对象")
    void createAndFindCarryChildren() {
        Basket created = baskets.create(new Basket(1L, "alice", List.of(new Item("apple", 2), new Item("pear", 1)), null));

        assertThat(created.items()).containsExactly(new Item("apple", 2), new Item("pear", 1));
        assertThat(baskets.find(1L)).get().extracting(Basket::items).isEqualTo(created.items());
        assertThat(baskets.find(404L)).isEmpty();
    }

    @Test
    @DisplayName("批量加载与分页时，子表只查一次并分配给各自的根对象")
    void batchLoadsAssignChildrenToTheirRoots() {
        baskets.createAll(List.of(
                new Basket(1L, "alice", List.of(new Item("apple", 2)), null),
                new Basket(2L, "bob", List.of(new Item("pear", 1), new Item("plum", 3)), null)));
        baskets.childQueries = 0;

        Map<Long, Integer> sizes = baskets.findAll(List.of(1L, 2L)).stream().collect(Collectors.toMap(Basket::id, b -> b.items().size()));

        assertThat(sizes).containsEntry(1L, 1).containsEntry(2L, 2);
        assertThat(baskets.childQueries).isEqualTo(1);
        assertThat(baskets.page(new PageQuery()).getRecords()).allSatisfy(basket -> assertThat(basket.items()).isNotEmpty());
    }

    @Test
    @DisplayName("更新时同步子表；版本冲突时不碰子表")
    void updateSyncsChildrenUnlessTheVersionIsStale() {
        baskets.create(new Basket(1L, "alice", List.of(new Item("apple", 2)), null));
        Basket loaded = baskets.find(1L).orElseThrow();
        Basket stale = baskets.find(1L).orElseThrow();

        baskets.update(new Basket(1L, "alice", List.of(new Item("apple", 5), new Item("kiwi", 1)), loaded.version()));
        assertThat(baskets.find(1L).orElseThrow().items()).containsExactly(new Item("apple", 5), new Item("kiwi", 1));

        assertThatThrownBy(() -> baskets.update(new Basket(1L, "alice", List.of(), stale.version())))
                .isInstanceOf(ConcurrentUpdateException.class);
        assertThat(baskets.find(1L).orElseThrow().items()).hasSize(2);
    }

    @Test
    @DisplayName("删除根对象时一并删除子表")
    void removeDeletesChildren() {
        baskets.createAll(List.of(
                new Basket(1L, "alice", List.of(new Item("apple", 2)), null),
                new Basket(2L, "bob", List.of(new Item("pear", 1)), null),
                new Basket(3L, "carol", List.of(new Item("plum", 1)), null)));

        assertThat(baskets.remove(1L)).isTrue();
        assertThat(baskets.removeAll(List.of(2L))).isEqualTo(1);

        assertThat(jdbc.queryForList("SELECT basket_id FROM ddk_basket_item", Long.class)).containsExactly(3L);
    }

    record Item(
            String sku,

            int quantity
    ) {
    }

    record Basket(
            Long id,

            String owner,

            List<Item> items,

            Long version
    ) {
    }

    @Data
    @TableName("ddk_basket")
    static class BasketPO {

        @TableId(type = IdType.INPUT)
        private Long id;

        private String owner;

        @Version
        private Long version;

        @TableField(exist = false)
        private List<BasketItemPO> items = new ArrayList<>();
    }

    @Data
    @TableName("ddk_basket_item")
    static class BasketItemPO {

        @TableId(type = IdType.AUTO)
        private Long id;

        private Long basketId;

        private String sku;

        private Integer quantity;
    }

    @Mapper
    interface BasketMapper extends BaseMapper<BasketPO> {
    }

    @Mapper
    interface BasketItemMapper extends BaseMapper<BasketItemPO> {
    }

    @EnhancedMapper(source = Basket.class, target = BasketPO.class, description = "Basket -> BasketPO")
    static class BasketPoConverter implements ObjectMapper<Basket, BasketPO> {

        @Override
        public BasketPO map(Basket source) {
            BasketPO po = new BasketPO();
            po.setId(source.id());
            po.setOwner(source.owner());
            po.setVersion(source.version());
            po.setItems(source.items().stream().map(item -> {
                BasketItemPO itemPo = new BasketItemPO();
                itemPo.setBasketId(source.id());
                itemPo.setSku(item.sku());
                itemPo.setQuantity(item.quantity());
                return itemPo;
            }).toList());
            return po;
        }

        @Override
        public List<BasketPO> map(List<Basket> sources) {
            return sources.stream().map(this::map).toList();
        }
    }

    @EnhancedMapper(source = BasketPO.class, target = Basket.class, description = "BasketPO -> Basket")
    static class BasketEntityConverter implements ObjectMapper<BasketPO, Basket> {

        @Override
        public Basket map(BasketPO source) {
            return new Basket(source.getId(), source.getOwner(),
                    source.getItems().stream().map(item -> new Item(item.getSku(), item.getQuantity())).toList(), source.getVersion());
        }

        @Override
        public List<Basket> map(List<BasketPO> sources) {
            return sources.stream().map(this::map).toList();
        }
    }

    static class BasketRepository extends GenericRepositoryImpl<Basket, Long, BasketPO, BasketMapper> {

        @Autowired
        private BasketItemMapper items;

        int childQueries;

        @Override
        protected void afterInsert(BasketPO po) {
            po.getItems().forEach(items::insert);
        }

        @Override
        protected void afterUpdate(BasketPO po) {
            items.delete(Wrappers.lambdaQuery(BasketItemPO.class).eq(BasketItemPO::getBasketId, po.getId()));
            po.getItems().forEach(items::insert);
        }

        @Override
        protected void afterLoad(List<BasketPO> pos) {
            if (pos.isEmpty()) {
                return;
            }
            childQueries++;
            Map<Long, List<BasketItemPO>> byBasket = items.selectList(Wrappers.lambdaQuery(BasketItemPO.class)
                            .in(BasketItemPO::getBasketId, pos.stream().map(BasketPO::getId).toList())
                            .orderByAsc(BasketItemPO::getId))
                    .stream().collect(Collectors.groupingBy(BasketItemPO::getBasketId));
            pos.forEach(po -> po.setItems(byBasket.getOrDefault(po.getId(), List.of())));
        }

        @Override
        protected void beforeRemove(List<Serializable> keys) {
            items.delete(Wrappers.lambdaQuery(BasketItemPO.class).in(BasketItemPO::getBasketId, keys));
        }
    }

    @Configuration
    @MapperScan(basePackageClasses = AggregateChildTablesIntegrationTest.class)
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            DataSourceInitializationAutoConfiguration.class,
            JdbcTemplateAutoConfiguration.class,
            com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class,
    })
    static class TestApp {

        @Bean
        MapperProvider mapperProvider(ApplicationContext context) {
            return new MapperProvider(context);
        }

        @Bean
        BasketPoConverter basketPoConverter() {
            return new BasketPoConverter();
        }

        @Bean
        BasketEntityConverter basketEntityConverter() {
            return new BasketEntityConverter();
        }

        @Bean
        BasketRepository basketRepository() {
            return new BasketRepository();
        }

        @Bean
        MybatisPlusInterceptor mybatisPlusInterceptor() {
            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
            return interceptor;
        }
    }
}
