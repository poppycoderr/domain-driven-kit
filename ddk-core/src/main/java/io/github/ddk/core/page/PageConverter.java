package io.github.ddk.core.page;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Elijah Du
 * @date 2025/2/8
 */
public class PageConverter {

    // MyBatis-Plus 的 IPage 转换为 PageResult
    public static <T, R> PageResponse<R> toPageResponse(IPage<T> page, Function<List<T>, List<R>> mapper) {
        return PageResponse.<R>builder()
                .records(mapper.apply(page.getRecords()))
                .total(page.getTotal())
                .pageNum(page.getCurrent())
                .pageSize(page.getSize())
                .pages(page.getPages())
                .hasPrevious(page.getCurrent() > 1)
                .hasNext(page.getCurrent() < page.getPages())
                .build();
    }

    // PageQuery 转换为 MyBatis-Plus 的 Page 对象
    public static <T> Page<T> toPage(PageQuery query) {
        Page<T> page = new Page<>(query.getPageNum(), query.getPageSize());

        // 处理排序条件
        if (CollUtil.isNotEmpty(query.getSorts())) {
            List<OrderItem> orderItems = query.getSorts().stream()
                    .map(sort -> {
                        OrderItem item = new OrderItem();
                        item.setColumn(StrUtil.toUnderlineCase(sort.getSortField()));
                        item.setAsc("ASC".equals(sort.getSortOrder()));
                        return item;
                    })
                    .collect(Collectors.toList());
            page.addOrder(orderItems);
        }
        return page;
    }
}
