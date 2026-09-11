package com.ddk.core.domain;

import com.ddk.core.domain.TestFixtures.Account;
import com.ddk.core.domain.TestFixtures.User;
import com.ddk.core.domain.TestFixtures.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Entity：按身份相等")
class EntityTest {

    @Test
    @DisplayName("ID 相同即为同一实体，与字段值无关")
    void equalsById() {
        User a = new User(UserId.of(1L), "alice");
        User b = new User(UserId.of(1L), "alice-renamed");

        assertEquals(a, b, "改了名字仍是同一个用户");
    }

    @Test
    @DisplayName("ID 不同即为不同实体，即使字段值完全一致")
    void notEqualsByDifferentId() {
        assertNotEquals(new User(UserId.of(1L), "alice"), new User(UserId.of(2L), "alice"));
    }

    @Test
    @DisplayName("不同实体类型即使标识相同也不相等")
    void notEqualsAcrossEntityTypes() {
        assertNotEquals(new User(UserId.of(1L), "alice"), new Account(UserId.of(1L)));
    }

    @Test
    @DisplayName("两个都没落库的实体只在引用相同时相等")
    void transientEntitiesAreIdentityCompared() {
        User a = new User("alice");
        User b = new User("alice");

        assertTrue(a.isNew());
        assertNotEquals(a, b, "都没有 ID，不应被判定为同一个");
        assertEquals(a, a);
    }

    @Test
    @DisplayName("hashCode 在回填 ID 前后保持稳定，实体不会在 HashSet 里丢失")
    void hashCodeStableAcrossIdAssignment() {
        User user = new User("alice");
        Set<User> set = new HashSet<>();
        set.add(user);

        int before = user.hashCode();
        user.persistedAs(UserId.of(1L), 0L);

        assertEquals(before, user.hashCode(), "hashCode 变化会破坏 HashSet 契约");
        assertTrue(set.contains(user), "回填 ID 后仍应能在集合中找到");
    }

    @Test
    @DisplayName("身份一旦确定就不允许被覆盖")
    void idIsAssignedOnlyOnce() {
        User user = new User("alice");
        user.persistedAs(UserId.of(1L), 0L);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> user.persistedAs(UserId.of(2L), 0L));
        assertTrue(ex.getMessage().contains("1"));
        assertEquals(UserId.of(1L), user.id());
    }

    @Test
    @DisplayName("isNew 只看标识是否存在")
    void isNewReflectsIdPresence() {
        assertTrue(new User("alice").isNew());
        assertFalse(new User(UserId.of(1L), "alice").isNew());
    }
}
