package com.ddk.core.repository;

import com.ddk.core.exception.BusinessException;
import com.ddk.core.exception.CommonError;

/**
 * 仓储保存聚合时，数据库中的记录已被其他操作修改或删除。
 * <p>
 * 典型原因是乐观锁：两个请求从同一版本加载聚合，先提交的推进了版本，后提交的 {@code UPDATE ... WHERE version = ?}
 * 影响 0 行。这时后者的修改与事件都不能生效，否则会覆盖前者的结果。调用方应当重新加载聚合后重试，或把冲突告诉用户；
 * Web 层把它映射为 409 Conflict。
 */
public class ConcurrentUpdateException extends BusinessException {

    public ConcurrentUpdateException(String aggregate) {
        super(CommonError.CONCURRENT_UPDATE, aggregate);
    }
}
