package fr.barlords.dndgateapi.server.mapper;

import fr.barlords.dndgateapi.domain.functional.model.pagination.Page;
import fr.barlords.dndgateapi.domain.functional.model.pagination.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.function.Function;

public interface PageMapper {

    static Pageable toPageable(PageRequest request, Sort sort) {
        return org.springframework.data.domain.PageRequest.of(request.page(), request.size(), sort);
    }

    static <E, D> Page<D> toDomain(org.springframework.data.domain.Page<E> page, PageRequest request, Function<E, D> mapper) {
        return new Page<>(page.getContent().stream().map(mapper).toList(), request, page.getTotalElements());
    }
}
