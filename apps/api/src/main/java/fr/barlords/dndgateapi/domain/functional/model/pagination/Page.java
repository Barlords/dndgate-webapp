package fr.barlords.dndgateapi.domain.functional.model.pagination;

import java.util.List;
import java.util.function.Function;

/** One slice of a list, with the size of the whole list. */
public record Page<T>(List<T> content, PageRequest request, long totalElements) {

    public int totalPages() {
        return (int) Math.ceil((double) totalElements / request.size());
    }

    public <R> Page<R> map(Function<T, R> mapper) {
        return new Page<>(content.stream().map(mapper).toList(), request, totalElements);
    }
}
