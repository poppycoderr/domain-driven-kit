package com.ddk.mybatis.page;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ddk.core.page.PageQuery;
import com.ddk.core.page.PageResponse;

import java.util.List;
import java.util.function.Function;

/**
 * MyBatis-Plus 分页适配器
 * <p>
 * 在 ddk-core 的纯 POJO 分页对象和 MyBatis-Plus 的 IPage 之间进行转换。
 *
 * @author Elijah Du
 * @date 2025/2/11
 */
public class MybatisPlusPageAdapter {

    /**
     * 将 PageQuery 转换为 MyBatis-Plus Page 对象
     */
    public static <P> Page<P> toPage(PageQuery query) {
        return new Page<>(query.getPageNum(), query.getPageSize());
    }

    /**
     * 将 MyBatis-Plus IPage 转换为 PageResponse
     *
     * @param page   MyBatis-Plus 分页结果
     * @param mapper 记录转换函数
     * @param <P>    MyBatis-Plus 记录类型
     * @param <E>    目标记录类型
     * @return PageResponse
     */
    public static <P, E> PageResponse<E> toPageResponse(IPage<P> page, Function<List<P>, List<E>> mapper) {
        return PageResponse.<E>builder()
                .records(mapper.apply(page.getRecords()))
                .total(page.getTotal())
                .pageNum(page.getCurrent())
                .pageSize(page.getSize())
                .pages(page.getPages())
                .hasPrevious(page.getCurrent() > 1)
                .hasNext(page.getCurrent() < page.getPages())
                .build();
    }

    /**
     * 将 MyBatis-Plus IPage 直接转换为 PageResponse（不做记录类型转换）
     */
    public static <E> PageResponse<E> toPageResponse(IPage<E> page) {
        return toPageResponse(page, records -> records);
    }
}
