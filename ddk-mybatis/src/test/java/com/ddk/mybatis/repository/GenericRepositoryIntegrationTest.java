package com.ddk.mybatis.repository;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.ddk.core.domain.DomainEvent;
import com.ddk.core.domain.DomainEventPublisher;
import com.ddk.core.mapper.MapperProvider;
import com.ddk.core.page.PageResponse;
import com.ddk.mybatis.repository.IntegrationFixtures.User;
import com.ddk.mybatis.repository.IntegrationFixtures.UserDisabledEvent;
import com.ddk.mybatis.repository.IntegrationFixtures.UserPageQuery;
import com.ddk.mybatis.repository.IntegrationFixtures.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通用仓储的端到端集成测试：真的建表、真的执行 SQL、真的读回聚合根。
 * <p>
 * 单元测试验证不了「SQL 拼对了没有」，而通用仓储的价值恰好一半在这里。
 */
@SpringBootTest(classes = GenericRepositoryIntegrationTest.TestApp.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ddk;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema.sql",
})
@DisplayName("通用仓储集成测试（H2）")
class GenericRepositoryIntegrationTest {

    @Configuration
    @MapperScan("com.ddk.mybatis.repository")
    @ImportAutoConfiguration({
            DataSourceAutoConfiguration.class,
            com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class,
            org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration.class,
            org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration.class,
            org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration.class,
            org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration.class,
    })
    static class TestApp {

        @Bean
        MapperProvider mapperProvider(org.springframework.context.ApplicationContext context) {
            return new MapperProvider(context);
        }

        @Bean
        IntegrationFixtures.UserPoConverter userPoConverter() {
            return new IntegrationFixtures.UserPoConverter();
        }

        @Bean
        IntegrationFixtures.UserEntityConverter userEntityConverter() {
            return new IntegrationFixtures.UserEntityConverter();
        }

        @Bean
        UserRepository userRepository() {
            return new UserRepository();
        }

        @Bean
        RecordingPublisher recordingPublisher() {
            return new RecordingPublisher();
        }

        @Bean
        MybatisPlusInterceptor mybatisPlusInterceptor() {
            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
            return interceptor;
        }
    }

    static class RecordingPublisher implements DomainEventPublisher {
        final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }

    @Autowired
    private UserRepository repository;

    @Autowired
    private RecordingPublisher publisher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void reset() {
        jdbcTemplate.execute("DELETE FROM ddk_user");
        publisher.published.clear();
    }

    private User persistedUser(String name, int gender) {
        return repository.create(User.register(name, gender, name + "@example.com"));
    }

    @Test
    @DisplayName("create 返回的实例带上了数据库生成的标识")
    void createReturnsEntityWithGeneratedId() {
        User draft = User.register("alice", 0, "alice@example.com");
        assertTrue(draft.isNew());

        User saved = repository.create(draft);

        assertFalse(saved.isNew(), "返回的实例应当带有标识");
        assertNotNull(saved.id().value());
        assertTrue(draft.isNew(), "传入的实例不会被就地修改，这是契约里写明的");
        assertEquals("alice", saved.username());
    }

    @Test
    @DisplayName("聚合根经过一次落库再读回，字段和值对象都还原得回来")
    void roundTripsAggregate() {
        User saved = persistedUser("bob", 1);

        Optional<User> found = repository.find(saved.id().value());

        assertTrue(found.isPresent());
        User user = found.get();
        assertEquals("bob", user.username());
        assertEquals(1, user.gender());
        assertEquals("bob@example.com", user.email());
        assertTrue(user.enabled());
        assertEquals(saved.id(), user.id());
    }

    @Test
    @DisplayName("find 查不到时返回 empty，而不是 null")
    void findReturnsEmptyWhenMissing() {
        assertTrue(repository.find(999_999L).isEmpty());
    }

    @Test
    @DisplayName("批量写入与批量读取")
    void createAllAndFindAll() {
        List<User> saved = repository.createAll(List.of(
                User.register("u1", 0, "u1@example.com"),
                User.register("u2", 1, "u2@example.com")));

        assertEquals(2, saved.size());
        saved.forEach(u -> assertFalse(u.isNew()));

        List<Long> ids = saved.stream().map(u -> u.id().value()).toList();
        assertEquals(2, repository.findAll(ids).size());
    }

    @Test
    @DisplayName("空集合不触发 SQL，直接返回空")
    void emptyInputsAreShortCircuited() {
        assertTrue(repository.createAll(List.of()).isEmpty());
        assertTrue(repository.updateAll(List.of()).isEmpty());
        assertTrue(repository.findAll(List.of()).isEmpty());
        assertEquals(0L, repository.removeAll(List.of()));
    }

    @Test
    @DisplayName("update 写回领域方法造成的变更")
    void updatePersistsDomainChanges() {
        User saved = persistedUser("carol", 0);

        User reloaded = repository.find(saved.id().value()).orElseThrow();
        reloaded.rename("carol-renamed");
        repository.update(reloaded);

        assertEquals("carol-renamed", repository.find(saved.id().value()).orElseThrow().username());
    }

    @Test
    @DisplayName("乐观锁：版本号随更新自增，拿旧版本再更新会失败")
    void optimisticLocking() {
        User saved = persistedUser("dave", 0);
        Long id = saved.id().value();

        User first = repository.find(id).orElseThrow();
        User second = repository.find(id).orElseThrow();
        assertEquals(0L, first.version());

        first.rename("dave-1");
        repository.update(first);
        assertEquals(1L, repository.find(id).orElseThrow().version(), "更新后版本号应当自增");

        // second 还持有旧版本号，它的更新不应生效
        second.rename("dave-2");
        repository.update(second);

        assertEquals("dave-1", repository.find(id).orElseThrow().username(),
                "基于旧版本的更新被乐观锁挡下，不应覆盖");
    }

    @Test
    @DisplayName("写入成功后自动发布聚合累积的领域事件")
    void publishesDomainEventsOnWrite() {
        User saved = persistedUser("erin", 0);
        publisher.published.clear();

        User reloaded = repository.find(saved.id().value()).orElseThrow();
        reloaded.disable();
        assertTrue(reloaded.hasDomainEvents());

        repository.update(reloaded);

        assertEquals(1, publisher.published.size());
        assertInstanceOf(UserDisabledEvent.class, publisher.published.getFirst());
        assertFalse(reloaded.hasDomainEvents(), "发布后聚合上不应再有待发布事件");
    }

    @Test
    @DisplayName("幂等的领域方法不会产生重复事件")
    void idempotentBehaviorProducesNoDuplicateEvents() {
        User saved = persistedUser("frank", 0);
        User reloaded = repository.find(saved.id().value()).orElseThrow();

        reloaded.disable();
        repository.update(reloaded);
        reloaded.disable();
        repository.update(reloaded);

        assertEquals(1, publisher.published.size());
    }

    @Test
    @DisplayName("remove 返回是否真的删掉了行")
    void removeReportsWhetherRowWasDeleted() {
        User saved = persistedUser("grace", 0);

        assertTrue(repository.remove(saved.id().value()));
        assertFalse(repository.remove(saved.id().value()), "已经删掉了，再删应返回 false");
        assertTrue(repository.find(saved.id().value()).isEmpty());
    }

    @Test
    @DisplayName("removeAll 返回实际删除行数")
    void removeAllReturnsDeletedCount() {
        List<Long> ids = repository.createAll(List.of(
                        User.register("h1", 0, "h1@example.com"),
                        User.register("h2", 0, "h2@example.com")))
                .stream().map(u -> u.id().value()).toList();

        assertEquals(2L, repository.removeAll(ids));
        assertEquals(0L, repository.count());
    }

    @Test
    @DisplayName("existsById 不把整行查出来，但结论要正确")
    void existsById() {
        User saved = persistedUser("ivan", 0);

        assertTrue(repository.existsById(saved.id().value()));
        assertFalse(repository.existsById(999_999L));
        assertFalse(repository.existsById(null), "null 标识直接返回 false，不去查库");
    }

    @Test
    @DisplayName("分页返回正确的总数与翻页标志")
    void paginates() {
        for (int i = 1; i <= 5; i++) {
            persistedUser("user" + i, i % 2);
        }

        UserPageQuery query = new UserPageQuery();
        query.setPageNum(2L);
        query.setPageSize(2L);

        PageResponse<User> page = repository.page(query);

        assertEquals(5L, page.getTotal());
        assertEquals(3L, page.getPages());
        assertEquals(2, page.getRecords().size());
        assertTrue(page.getHasPrevious());
        assertTrue(page.getHasNext());
    }

    @Test
    @DisplayName("@Query 条件真的进了 WHERE 子句")
    void appliesQueryConditions() {
        persistedUser("alice", 0);
        persistedUser("alicia", 0);
        persistedUser("bob", 1);

        UserPageQuery query = new UserPageQuery();
        query.setUsername("ali");

        assertEquals(2L, repository.page(query).getTotal());

        UserPageQuery byGender = new UserPageQuery();
        byGender.setGenders(List.of(1));

        assertEquals(1L, repository.page(byGender).getTotal());
    }

    @Test
    @DisplayName("addSort 的排序真的进了 ORDER BY")
    void appliesSorting() {
        persistedUser("charlie", 0);
        persistedUser("alice", 0);
        persistedUser("bob", 0);

        UserPageQuery query = new UserPageQuery();
        query.addSort("username", "ASC");

        List<String> names = repository.page(query).getRecords().stream().map(User::username).toList();
        assertEquals(List.of("alice", "bob", "charlie"), names);

        UserPageQuery desc = new UserPageQuery();
        desc.addSort("username", "DESC");

        assertEquals("charlie", repository.page(desc).getRecords().getFirst().username());
    }

    @Test
    @DisplayName("count 统计全表")
    void counts() {
        assertEquals(0L, repository.count());
        persistedUser("jane", 0);
        persistedUser("john", 1);
        assertEquals(2L, repository.count());
    }
}
