package fr.barlords.dndgateapi.server.entity;

import org.hibernate.annotations.BatchSize;

import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureStatus;
import fr.barlords.dndgateapi.domain.functional.model.adventure.AdventureVisibility;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "adventure")
public class AdventureEntity implements Serializable {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", nullable = false, length = 5000)
    private String description;

    @Column(name = "max_player", nullable = false)
    private int maxPlayer;

    @ElementCollection
    @BatchSize(size = 100)
    @CollectionTable(name = "adventure_players", joinColumns = @JoinColumn(name = "adventure_id"))
    @Column(name = "player_id")
    @Builder.Default
    private List<UUID> players = new ArrayList<>();

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private AdventureStatus status;

    @Column(name = "visibility")
    @Enumerated(EnumType.STRING)
    private AdventureVisibility visibility;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "city")
    private String city;

    @Column(name = "invitation_code")
    private String invitationCode;

    @Column(name = "master_vacant_since")
    private Instant masterVacantSince;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

}
