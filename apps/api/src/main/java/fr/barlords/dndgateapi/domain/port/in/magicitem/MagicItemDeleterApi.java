package fr.barlords.dndgateapi.domain.port.in.magicitem;

import java.util.UUID;

public interface MagicItemDeleterApi {

    /** Only the owner of the item can delete it, and only by retyping its name. */
    void deleteById(UUID accountId, UUID magicItemId, String confirmation);

}
