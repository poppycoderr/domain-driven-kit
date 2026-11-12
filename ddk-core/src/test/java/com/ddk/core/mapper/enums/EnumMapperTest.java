package com.ddk.core.mapper.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("EnumMapper：值与枚举互转，不认识的值直接失败")
class EnumMapperTest {

    enum Status implements IEnum {
        ACTIVE(1),
        DISABLED(2);

        private final int value;

        Status(int value) {
            this.value = value;
        }

        @Override
        public Object getValue() {
            return value;
        }
    }

    private final EnumMapper mapper = new EnumMapper();

    @Test
    @DisplayName("认识的值映射到对应的常量")
    void knownValue() {
        assertEquals(Status.DISABLED, mapper.toEnum(2, Status.class));
    }

    @Test
    @DisplayName("列为空时仍是 null")
    void nullValue() {
        assertNull(mapper.toEnum(null, Status.class));
    }

    @Test
    @DisplayName("不认识的值抛异常，消息里有值和枚举类型，而不是静默返回 null")
    void unknownValue() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> mapper.toEnum(3, Status.class));
        assertTrue(ex.getMessage().contains(Status.class.getName()), ex.getMessage());
        assertTrue(ex.getMessage().contains("3"), ex.getMessage());
    }

    @Test
    @DisplayName("值的类型不一致也算不认识：Long 的 1 不等于 Integer 的 1")
    void valueOfAnotherType() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> mapper.toEnum(1L, Status.class));
        assertTrue(ex.getMessage().contains("java.lang.Long"), ex.getMessage());
    }

    @Test
    @DisplayName("枚举到值，null 进 null 出")
    void toValue() {
        assertEquals(1, mapper.toValue(Status.ACTIVE));
        assertNull(mapper.toValue(null));
    }
}
