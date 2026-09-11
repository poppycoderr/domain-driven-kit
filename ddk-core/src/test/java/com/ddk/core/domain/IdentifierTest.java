package com.ddk.core.domain;

import com.ddk.core.domain.TestFixtures.OrderId;
import com.ddk.core.domain.TestFixtures.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Identifier：类型化标识")
class IdentifierTest {

    @Test
    @DisplayName("同类型同值相等，并且 hashCode 一致")
    void equalsBySameTypeAndValue() {
        assertEquals(UserId.of(1L), UserId.of(1L));
        assertEquals(UserId.of(1L).hashCode(), UserId.of(1L).hashCode());
    }

    @Test
    @DisplayName("同类型不同值不相等")
    void notEqualsByDifferentValue() {
        assertNotEquals(UserId.of(1L), UserId.of(2L));
    }

    @Test
    @DisplayName("不同类型即使值相同也不相等——这是类型化标识的全部意义")
    void notEqualsAcrossTypes() {
        assertNotEquals(UserId.of(1L), OrderId.of(1L));
        assertNotEquals(OrderId.of(1L), UserId.of(1L));
    }

    @Test
    @DisplayName("可以安全地作为 Map / Set 的 key")
    void usableAsHashKey() {
        Set<Identifier<?>> ids = new HashSet<>();
        ids.add(UserId.of(1L));
        ids.add(UserId.of(1L));
        ids.add(OrderId.of(1L));

        assertEquals(2, ids.size());
        assertTrue(ids.contains(UserId.of(1L)));
    }

    @Test
    @DisplayName("null 值在构造期就被拒绝")
    void rejectsNullValue() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> UserId.of(null));
        assertTrue(ex.getMessage().contains("UserId"));
    }

    @Test
    @DisplayName("子类可以在构造器里追加自己的不变量")
    void subclassCanEnforceInvariant() {
        assertThrows(IllegalArgumentException.class, () -> UserId.of(0L));
        assertThrows(IllegalArgumentException.class, () -> UserId.of(-1L));
    }

    @Test
    @DisplayName("和 null、其他类型比较不会抛异常")
    void nullSafeEquals() {
        UserId id = UserId.of(1L);
        assertNotEquals(null, id);
        assertNotEquals("1", id);
        assertEquals(id, id);
    }

    @Test
    @DisplayName("value() 取出原始值，toString() 直接展示值，便于日志")
    void exposesRawValue() {
        assertEquals(42L, UserId.of(42L).value());
        assertEquals("42", UserId.of(42L).toString());
    }
}
