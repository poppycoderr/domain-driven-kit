package com.ddk.core.exception;

import java.text.Format;
import java.text.MessageFormat;

/**
 * 错误码接口
 *
 * @author Elijah Du
 * @date 2025/2/7
 */
public interface ErrorCode {

    /**
     * 错误信息
     *
     * @return 错误信息
     */
    String getMessage();

    default String getCode() {
        return this.toString();
    }

    /**
     * 用 {@link MessageFormat} 填充消息里的 {@code {0}} 占位符。
     * <p>
     * 不传参数时直接返回原始消息：{@code MessageFormat} 遇到 null 参数数组会抛
     * {@code NullPointerException}，而「没有参数」是完全正常的调用方式。
     * <p>
     * 数字按原样写出：{@code MessageFormat} 默认会给数字加千分位，订单号 {@code 1234567} 会变成 {@code 1,234,567}，
     * 用户拿着它去查就查不到了。占位符里明确写了格式的（如 {@code {0,number,#.##}}）仍按写的格式来。
     */
    default String getMessage(Object... args) {
        if (args == null || args.length == 0) {
            return getMessage();
        }
        MessageFormat format = new MessageFormat(this.getMessage());
        Format[] formats = format.getFormatsByArgumentIndex();
        Object[] values = args.clone();
        for (int i = 0; i < values.length; i++) {
            if (values[i] instanceof Number && (i >= formats.length || formats[i] == null)) {
                values[i] = String.valueOf(values[i]);
            }
        }
        return format.format(values);
    }
}
