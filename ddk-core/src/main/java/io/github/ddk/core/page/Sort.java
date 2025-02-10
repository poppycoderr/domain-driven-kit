package io.github.ddk.core.page;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Pattern;

/**
 * @author Elijah Du
 * @date 2025/2/8
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Sort {

    // 排序字段
    private String sortField;

    // 排序方向
    @Pattern(regexp = "^(ASC|DESC)$", message = "排序方向必须为 ASC 或 DESC")
    private String sortOrder;

    public static Sort of(String sortField, String sortOrder) {
        return Sort.builder()
                .sortField(sortField)
                .sortOrder(sortOrder)
                .build();
    }
}
