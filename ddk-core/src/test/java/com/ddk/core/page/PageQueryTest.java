package com.ddk.core.page;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PageQuery：分页入参与排序")
class PageQueryTest {

    @Test
    @DisplayName("默认第 1 页、每页 10 条")
    void hasSaneDefaults() {
        PageQuery query = new PageQuery();
        assertEquals(1L, query.getPageNum());
        assertEquals(10L, query.getPageSize());
        assertNull(query.getSorts());
    }

    @Test
    @DisplayName("pageSize 被钳制在 [1, 200]，外部传超大值不会拖垮数据库")
    void clampsPageSize() {
        PageQuery query = new PageQuery();

        query.setPageSize(999_999L);
        assertEquals(PageQuery.DEFAULT_MAX_PAGE_SIZE, query.getPageSize());

        query.setPageSize(0L);
        assertEquals(1L, query.getPageSize());

        query.setPageSize(-5L);
        assertEquals(1L, query.getPageSize());

        query.setPageSize(50L);
        assertEquals(50L, query.getPageSize());
    }

    @Test
    @DisplayName("pageSize 传 null 回落到默认值，而不是抛空指针")
    void nullPageSizeFallsBackToDefault() {
        PageQuery query = new PageQuery();
        query.setPageSize(null);
        assertEquals(10L, query.getPageSize());
    }

    @Test
    @DisplayName("addSort 支持链式调用，按添加顺序累积")
    void addSortIsChainableAndOrdered() {
        PageQuery query = new PageQuery()
                .addSort("createTime", "DESC")
                .addSort("id", "asc");

        assertEquals(2, query.getSorts().size());
        assertEquals("createTime", query.getSorts().get(0).getSortField());
        assertEquals("DESC", query.getSorts().get(0).getSortOrder());
        assertEquals("ASC", query.getSorts().get(1).getSortOrder(), "排序方向统一大写");
    }

    @Test
    @DisplayName("排序方向缺省为 ASC")
    void sortOrderDefaultsToAsc() {
        PageQuery query = new PageQuery().addSort("id", null);
        assertEquals("ASC", query.getSorts().get(0).getSortOrder());
    }
}
