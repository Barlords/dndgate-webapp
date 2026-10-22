package fr.barlords.dndgateapi.domain.port.out;


import java.util.Optional;
import java.util.UUID;

public interface PersistenceSpi<T, ID> {

    T save(T o);

    Optional<T> findById(ID id);

    void deleteById(UUID id);

}
