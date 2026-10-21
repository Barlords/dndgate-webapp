package fr.barlords.dndgateapi.domain.functional.model.pagination;

import fr.barlords.dndgateapi.domain.functional.exception.InvalidPageRequestException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class PageTest {

    @Test
    void the_defaults_are_the_first_page_of_twenty() {
        PageRequest request = PageRequest.of(null, null);

        Assertions.assertEquals(0, request.page());
        Assertions.assertEquals(20, request.size());
    }

    @Test
    void the_limits_are_enforced() {
        Assertions.assertThrows(InvalidPageRequestException.class, () -> PageRequest.of(-1, 10));
        Assertions.assertThrows(InvalidPageRequestException.class, () -> PageRequest.of(0, 0));
        Assertions.assertThrows(InvalidPageRequestException.class, () -> PageRequest.of(0, 101));
        Assertions.assertEquals(100, PageRequest.of(0, 100).size());
        Assertions.assertEquals(1, PageRequest.of(0, 1).size());
    }

    @Test
    void the_number_of_pages_rounds_up() {
        Assertions.assertEquals(3, new Page<>(List.of(), new PageRequest(0, 2), 5).totalPages());
        Assertions.assertEquals(2, new Page<>(List.of(), new PageRequest(0, 2), 4).totalPages());
        Assertions.assertEquals(0, new Page<>(List.of(), new PageRequest(0, 2), 0).totalPages());
    }

    @Test
    void mapping_keeps_the_page_and_the_total() {
        Page<Integer> page = new Page<>(List.of(1, 2), new PageRequest(1, 2), 7);

        Page<String> mapped = page.map(String::valueOf);

        Assertions.assertEquals(List.of("1", "2"), mapped.content());
        Assertions.assertEquals(7, mapped.totalElements());
        Assertions.assertEquals(page.request(), mapped.request());
    }
}
