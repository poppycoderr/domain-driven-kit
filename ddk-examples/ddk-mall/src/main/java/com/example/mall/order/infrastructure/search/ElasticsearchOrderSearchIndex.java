package com.example.mall.order.infrastructure.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.ddk.core.page.PageQuery;
import com.ddk.core.page.PageResponse;
import com.example.mall.order.domain.acl.OrderSearchIndex;
import com.example.mall.order.domain.model.OrderId;
import com.example.mall.order.domain.model.OrderStatus;
import com.example.mall.order.domain.model.OrderSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;

/**
 * 订单搜索读模型的 Elasticsearch 实现，{@code mall.search.store=elasticsearch} 时生效，替换掉关系库里的那张宽表。
 * <p>
 * 订单上下文的其余部分不知道换了存储：投影照样调用 {@link #save}，查询照样调用 {@link #search}。
 * 访问失败时抛出异常，由投影 starter 稍后重试，所以 Elasticsearch 短暂不可用不会丢更新。
 * <p>
 * Elasticsearch 是近实时的：写入之后默认要等一次刷新（约一秒）才搜得到，比关系库的实现多一小段延迟。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mall.search.store", havingValue = "elasticsearch")
public class ElasticsearchOrderSearchIndex implements OrderSearchIndex, InitializingBean {

    private final ElasticsearchClient client;

    private final String index;

    public ElasticsearchOrderSearchIndex(ElasticsearchClient client, @Value("${mall.search.index:mall-orders}") String index) {
        this.client = client;
        this.index = index;
    }

    /**
     * 索引不存在时按固定的映射创建。不能依赖自动映射：状态会被当成全文字段分词，精确筛选就不准了。
     */
    @Override
    public void afterPropertiesSet() throws IOException {
        if (client.indices().exists(request -> request.index(index)).value()) {
            return;
        }
        client.indices().create(request -> request.index(index).mappings(mappings -> mappings
                .properties("orderId", property -> property.long_(type -> type))
                .properties("customerId", property -> property.long_(type -> type))
                .properties("status", property -> property.keyword(type -> type))
                .properties("totalAmount", property -> property.keyword(type -> type.index(false)))
                .properties("itemCount", property -> property.integer(type -> type))
                .properties("productNames", property -> property.text(type -> type))
                .properties("expiresAt", property -> property.date(type -> type.format("epoch_millis")))));
        log.info("Created Elasticsearch index {}", index);
    }

    @Override
    public void save(OrderSummary summary) {
        try {
            client.index(request -> request.index(index).id(String.valueOf(summary.orderId())).document(OrderDocument.from(summary)));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not index order " + summary.orderId(), e);
        }
    }

    @Override
    public void remove(OrderId orderId) {
        try {
            client.delete(request -> request.index(index).id(String.valueOf(orderId.value())));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not remove order " + orderId.value() + " from the index", e);
        }
    }

    @Override
    public PageResponse<OrderSummary> search(Long customerId, String keyword, OrderStatus status, PageQuery page) {
        BoolQuery.Builder query = new BoolQuery.Builder().filter(filter -> filter.term(term -> term.field("customerId").value(customerId)));
        if (status != null) {
            query.filter(filter -> filter.term(term -> term.field("status").value(status.name())));
        }
        if (keyword != null && !keyword.isBlank()) {
            query.must(must -> must.matchPhrase(match -> match.field("productNames").query(keyword.trim())));
        }
        long from = (page.getPageNum() - 1) * page.getPageSize();
        try {
            SearchResponse<OrderDocument> response = client.search(request -> request.index(index)
                    .query(builder -> builder.bool(query.build()))
                    .sort(sort -> sort.field(field -> field.field("orderId").order(SortOrder.Desc)))
                    .from(Math.toIntExact(from))
                    .size(Math.toIntExact(page.getPageSize()))
                    .trackTotalHits(track -> track.enabled(true)), OrderDocument.class);
            List<OrderSummary> records = response.hits().hits().stream().map(Hit::source).filter(Objects::nonNull)
                    .map(OrderDocument::toSummary).toList();
            long total = response.hits().total() == null ? records.size() : response.hits().total().value();
            return PageResponse.of(records, total, page.getPageNum(), page.getPageSize());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not search orders", e);
        }
    }
}
