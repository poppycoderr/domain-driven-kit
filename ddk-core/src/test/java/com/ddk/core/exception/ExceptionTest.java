package com.ddk.core.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("异常体系与错误码")
class ExceptionTest {

    @Getter
    @AllArgsConstructor
    enum TestError implements ErrorCode {
        USER_NOT_FOUND("用户不存在：{0}"),
        QUOTA_EXCEEDED("{0} 超出配额上限 {1}"),
        PLAIN("没有占位符"),
        ;

        private final String message;
    }

    @Test
    @DisplayName("错误码默认取枚举名，便于前端按码分支")
    void codeDefaultsToEnumName() {
        assertEquals("USER_NOT_FOUND", TestError.USER_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("消息里的占位符按顺序被填充")
    void formatsMessageArguments() {
        assertEquals("用户不存在：42", TestError.USER_NOT_FOUND.getMessage(42));
        assertEquals("下载 超出配额上限 100", TestError.QUOTA_EXCEEDED.getMessage("下载", 100));
    }

    @Test
    @DisplayName("没有占位符时原样返回，多传参数也不报错")
    void toleratesExtraArguments() {
        assertEquals("没有占位符", TestError.PLAIN.getMessage());
        assertEquals("没有占位符", TestError.PLAIN.getMessage("多余的"));
    }

    @Test
    @DisplayName("业务异常携带错误码与格式化后的消息")
    void businessExceptionCarriesCodeAndMessage() {
        BusinessException ex = new BusinessException(TestError.USER_NOT_FOUND, 42);

        assertEquals(TestError.USER_NOT_FOUND, ex.getErrorCode());
        assertEquals("用户不存在：42", ex.getMessage());
        assertArrayEquals(new Object[]{42}, ex.getArgs());
    }

    @Test
    @DisplayName("不传参数时消息保留原始占位符，不做格式化")
    void withoutArgumentsKeepsRawMessage() {
        assertEquals("用户不存在：{0}", new BusinessException(TestError.USER_NOT_FOUND).getMessage());
    }

    @Test
    @DisplayName("可以包装底层异常，保留原始堆栈")
    void wrapsCause() {
        RuntimeException cause = new RuntimeException("connection reset");

        SystemException ex = new SystemException(TestError.PLAIN, cause);
        assertSame(cause, ex.getCause());

        SystemException withArgs = new SystemException(TestError.USER_NOT_FOUND, cause, 42);
        assertSame(cause, withArgs.getCause());
        assertEquals("用户不存在：42", withArgs.getMessage());
    }

    @Test
    @DisplayName("业务异常与系统异常同源，可按类型分别处理")
    void bothExtendAbstractException() {
        assertInstanceOf(AbstractException.class, new BusinessException(TestError.PLAIN));
        assertInstanceOf(AbstractException.class, new SystemException(TestError.PLAIN));
        assertInstanceOf(RuntimeException.class, new BusinessException(TestError.PLAIN));
    }
}
