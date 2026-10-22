package fr.barlords.dndgateapi.client.dto.spellbook;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * The file of an import or export. The constraints are those of {@link SpellBookCreationRequest} and
 * {@link AddSpellToSpellBookRequest}; the number of spells is bounded by the domain limit, not here.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record SpellBookImportExportDto (
    @NotBlank @Size(max = 255) String name,
    @Size(max = 5000) String description,
    @NotNull Set<@NotBlank @Size(max = 255) String> spellList
) {}
