package fr.barlords.dndgateapi.domain.functional.model.pagination;

import fr.barlords.dndgateapi.domain.functional.exception.InvalidPageRequestException;

/** Which slice of a list is wanted: a page number (from 0) and a page size. */
public record PageRequest(int page, int size) {

    public static final int DEFAULT_SIZE = 20;

    public static final int MAX_SIZE = 100;

    public PageRequest {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new InvalidPageRequestException(MAX_SIZE);
        }
    }

    public static PageRequest of(Integer page, Integer size) {
        return new PageRequest(page == null ? 0 : page, size == null ? DEFAULT_SIZE : size);
    }

    public long offset() {
        return (long) page * size;
    }
}
