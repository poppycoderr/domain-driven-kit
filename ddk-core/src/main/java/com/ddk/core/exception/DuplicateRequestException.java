package com.ddk.core.exception;

/**
 * 同一个请求在有效期内被再次提交。Web 层映射为 409 Conflict。
 */
public class DuplicateRequestException extends BusinessException {

    public DuplicateRequestException() {
        super(CommonError.DUPLICATE_REQUEST);
    }
}
