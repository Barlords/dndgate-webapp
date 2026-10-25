package fr.barlords.dndgateapi.server.entity;

import fr.barlords.dndgateapi.domain.functional.model.CharacterVisibility;
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
@Table(name = "`character`")
public class GameCharacterEntity implements Serializable {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", updatable = false, nullable = false)
    private AccountEntity owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adventure_id")
    private AdventureEntity adventure;

    @Column(name = "`name`",  nullable = false)
    private String name;

    @Column(name = "race", nullable = false)
    private String race;

    @Column(name = "martial_class", nullable = false)
    private String martialClass;

    @Column(name = "personality", nullable = false, length = 5000)
    private String personality;

    @Column(name = "background", nullable = false, length = 80000)
    private String background;

    @Column(name = "picture_path")
    private String picturePath;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "npc", nullable = false)
    private boolean npc;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 20)
    private CharacterVisibility visibility;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

}
