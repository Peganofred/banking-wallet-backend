package com.wallet.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <S, T> PagedResponse<T> from(Page<S> source, Function<S, T> mapper) {
        List<T> content = source.getContent().stream().map(mapper).toList();
        return new PagedResponse<>(content, source.getNumber(), source.getSize(),
                source.getTotalElements(), source.getTotalPages());
    }
}
