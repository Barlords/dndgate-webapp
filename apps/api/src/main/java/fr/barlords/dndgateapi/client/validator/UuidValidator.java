package fr.barlords.dndgateapi.client.validator;

import fr.barlords.dndgateapi.domain.functional.exception.UuidFormatNotValidException;

import java.util.UUID;

public interface UuidValidator {

    static UUID validate(String id) {
        try {
            return UUID.fromString(id);
        }
        catch(Exception e) {
            throw new UuidFormatNotValidException();
        }
    }

}
