package com.ddk.core.domain;

import com.ddk.core.domain.TestFixtures.Email;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ValueObject：按值相等 + 构造期自我校验")
class ValueObjectTest {

    @Test
    @DisplayName("值相同即相等，可直接用 record 实现")
    void equalsByValue() {
        assertEquals(new Email("a@b.com"), new Email("a@b.com"));
        assertEquals(new Email("a@b.com").hashCode(), new Email("a@b.com").hashCode());
        assertNotEquals(new Email("a@b.com"), new Email("c@d.com"));
    }

    @Test
    @DisplayName("非法状态在构造期就被拒绝，不存在「先构造再检查」的中间态")
    void selfValidating() {
        assertThrows(IllegalArgumentException.class, () -> new Email("not-an-email"));
        assertThrows(IllegalArgumentException.class, () -> new Email(null));
    }

    @Test
    @DisplayName("值对象也是 ValueObject 的实例，可被 ArchUnit 规则识别")
    void isValueObject() {
        assertInstanceOf(ValueObject.class, new Email("a@b.com"));
    }
}
