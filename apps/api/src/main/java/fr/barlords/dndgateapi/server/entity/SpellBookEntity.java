package fr.barlords.dndgateapi.server.entity;


import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "spellbook")
public class SpellBookEntity implements Serializable {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity owner;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", length = 5000)
    private String description;

    @ElementCollection
    @CollectionTable(name = "spellbook_spells",
            joinColumns = @JoinColumn(name = "spellbook_id"))
    @Column(name = "spell")
    private Set<String> spellList;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

}
