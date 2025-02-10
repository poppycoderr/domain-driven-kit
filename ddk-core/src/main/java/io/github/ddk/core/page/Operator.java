package io.github.ddk.core.page;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.List;

/**
 * 查询操作符
 *
 * @author Elijah Du
 * @date 2025/2/8
 */
@Getter
@RequiredArgsConstructor
public enum Operator {

    EQ("等于") {
        @Override
        public <T> void apply(QueryWrapper<T> wrapper, String column, Object value) {
            wrapper.eq(value != null, column, value);
        }
    },
    NE("不等于") {
        @Override
        public <T> void apply(QueryWrapper<T> wrapper, String column, Object value) {
            wrapper.ne(value != null, column, value);
        }
    },
    GT("大于") {
        @Override
        public <T> void apply(QueryWrapper<T> wrapper, String column, Object value) {
            wrapper.gt(value != null, column, value);
        }
    },
    LT("小于") {
        @Override
        public <T> void apply(QueryWrapper<T> wrapper, String column, Object value) {
            wrapper.lt(value != null, column, value);
        }
    },
    GE("大于等于") {
        @Override
        public <T> void apply(QueryWrapper<T> wrapper, String column, Object value) {
            wrapper.ge(value != null, column, value);
        }
    },
    LE("小于等于") {
        @Override
        public <T> void apply(QueryWrapper<T> wrapper, String column, Object value) {
            wrapper.le(value != null, column, value);
        }
    },
    LIKE("模糊查询") {
        @Override
        public <T> void apply(QueryWrapper<T> wrapper, String column, Object value) {
            wrapper.like(value != null, column, value);
        }
    },
    NOT_LIKE("不模糊查询") {
        @Override
        public <T> void apply(QueryWrapper<T> wrapper, String column, Object value) {
            wrapper.notLike(value != null, column, value);
        }
    },
    LIKE_LEFT("左模糊") {
        @Override
        public <T> void apply(QueryWrapper<T> wrapper, String column, Object value) {
            wrapper.likeLeft(value != null, column, value);
        }
    },
    LIKE_RIGHT("右模糊") {
        @Override
        public <T> void apply(QueryWrapper<T> wrapper, String column, Object value) {
            wrapper.likeRight(value != null, column, value);
        }
    },
    IN("IN查询") {
        @Override
        @SuppressWarnings("rawtypes")
        public <T> void apply(QueryWrapper<T> wrapper, String column, Object value) {
            if (value instanceof Collection) {
                Collection coll = (Collection<?>) value;
                wrapper.in(CollUtil.isNotEmpty(coll), column, coll);
            }
        }
    },
    NOT_IN("不包含") {
        @Override
        @SuppressWarnings("rawtypes")
        public <T> void apply(QueryWrapper<T> wrapper, String column, Object value) {
            if (value instanceof Collection) {
                Collection coll = (Collection<?>) value;
                wrapper.notIn(CollUtil.isNotEmpty(coll), column, coll);
            }
        }
    },
    BETWEEN("在区间") {
        @Override
        @SuppressWarnings("rawtypes")
        public <T> void apply(QueryWrapper<T> wrapper, String column, Object value) {
            if (value instanceof Collection) {
                List list = (List) value;
                wrapper.between(CollUtil.isNotEmpty(list), column, list.getFirst(), list.getLast());
            }
        }
    },
    NOT_BETWEEN("不在区间") {
        @Override
        @SuppressWarnings("rawtypes")
        public <T> void apply(QueryWrapper<T> wrapper, String column, Object value) {
            if (value instanceof Collection) {
                List list = (List) value;
                wrapper.notBetween(CollUtil.isNotEmpty(list), column, list.getFirst(), list.getLast());
            }
        }
    },
    IS_NULL("为空") {
        @Override
        public <T> void apply(QueryWrapper<T> wrapper, String column, Object value) {
            wrapper.isNull(column);
        }
    },
    IS_NOT_NULL("不为空") {
        @Override
        public <T> void apply(QueryWrapper<T> wrapper, String column, Object value) {
            wrapper.isNotNull(column);
        }
    };

    /**
     * 描述
     */
    private final String description;

    /**
     * 应用查询条件
     *
     * @param wrapper 查询包装器
     * @param column  列名
     * @param value   值
     */
    public <T> void apply(QueryWrapper<T> wrapper, String column, Object value) {
    }
}