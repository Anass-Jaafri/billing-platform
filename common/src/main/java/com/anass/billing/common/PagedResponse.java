package com.anass.billing.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PagedResponse<T>
        (List<T> content,
                                int page,
                                int size,
                                long totalElements,
                                int totalPages){
    public static <T, R> PagedResponse<R> from(Page<T> page, Function<T, R> mapper) {
        return new PagedResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }


}
