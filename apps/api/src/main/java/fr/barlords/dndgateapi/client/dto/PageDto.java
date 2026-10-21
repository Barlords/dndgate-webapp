package fr.barlords.dndgateapi.client.dto;

import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;

import java.util.List;
import java.util.function.Function;

public record PageDto<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <D, T> PageDto<T> of(Page<D> page, Function<D, T> mapper) {
        Page<T> mapped = page.map(mapper);
        return new PageDto<>(mapped.content(), page.request().page(), page.request().size(), page.totalElements(), page.totalPages());
    }
}
