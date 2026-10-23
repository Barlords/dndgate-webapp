package fr.barlords.dndgateapi.server.entity;

import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItemRarity;
import fr.barlords.dndgateapi.domain.functional.model.magicitem.MagicItemType;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "magic_item")
public class MagicItemEntity implements Serializable {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", updatable = false, nullable = false)
    private AccountEntity owner;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private MagicItemType type;

    @Column(name = "rarity", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private MagicItemRarity rarity;

    @Column(name = "requires_attunement", nullable = false)
    private boolean requiresAttunement;

    @Column(name = "description", nullable = false, length = 5000)
    private String description;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

}
