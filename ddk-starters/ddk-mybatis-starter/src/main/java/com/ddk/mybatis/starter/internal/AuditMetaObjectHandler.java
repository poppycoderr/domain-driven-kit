package com.ddk.mybatis.starter.internal;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.ddk.core.context.Operator;
import com.ddk.core.context.OperatorContext;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * 写入时自动填充审计字段：{@code createTime}、{@code updateTime}，以及当前操作者写入的 {@code createBy}、{@code updateBy}。
 * <p>
 * 只填 PO 上存在、并且标了 {@code @TableField(fill = ...)} 的字段。操作者来自 {@link OperatorContext}；没有操作者时
 * （例如启动时的数据初始化）两个操作者字段保持原值，不会用占位值冒充。字段可以是 {@code String} 或 {@code Long}。
 */
public class AuditMetaObjectHandler implements MetaObjectHandler {

    static final String CREATE_BY = "createBy";

    static final String UPDATE_BY = "updateBy";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        OperatorContext.current().ifPresent(operator -> {
            fillOperator(metaObject, CREATE_BY, operator, false);
            fillOperator(metaObject, UPDATE_BY, operator, false);
        });
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        OperatorContext.current().ifPresent(operator -> fillOperator(metaObject, UPDATE_BY, operator, true));
    }

    /**
     * 创建人只在为空时填写；修改人在更新时总是换成当前操作者。
     */
    private void fillOperator(MetaObject metaObject, String field, Operator operator, boolean overwrite) {
        if (!metaObject.hasSetter(field) || (!overwrite && metaObject.getValue(field) != null)) {
            return;
        }
        Class<?> type = metaObject.getSetterType(field);
        if (type == String.class) {
            metaObject.setValue(field, operator.id());
        } else if (type == Long.class) {
            metaObject.setValue(field, Long.valueOf(operator.id()));
        } else {
            throw new IllegalStateException("Audit field " + field + " on " + metaObject.getOriginalObject().getClass().getSimpleName()
                    + " must be String or Long, but is " + type.getSimpleName());
        }
    }
}
