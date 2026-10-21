package fr.barlords.dndgateapi.domain.port.out;

import fr.barlords.dndgateapi.domain.ApplicationError;
import io.vavr.control.Either;

import java.util.Optional;
import java.util.UUID;

public interface PersistenceSpi<T, ID> {

    Either<ApplicationError, T> save(T o);

    Optional<T> findById(ID id);

    void deleteById(UUID id);

}
