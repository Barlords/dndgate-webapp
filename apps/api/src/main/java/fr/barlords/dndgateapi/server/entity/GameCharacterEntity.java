package fr.barlords.dndgateapi.server.entity;

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
    @JoinColumn(name = "adventure_id", updatable = false)
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

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

}
