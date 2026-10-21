package fr.barlords.dndgateapi.server.entity;

import fr.barlords.dndgateapi.domain.functional.model.adventure.master.MasterClaimStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "master_claims")
public class MasterClaimEntity implements Serializable {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "adventure_id", updatable = false, nullable = false)
    private UUID adventureId;

    @Column(name = "candidate_id", updatable = false, nullable = false)
    private UUID candidateId;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private MasterClaimStatus status;

    @ElementCollection
    @BatchSize(size = 50)
    @CollectionTable(name = "master_claim_votes", joinColumns = @JoinColumn(name = "claim_id"))
    @MapKeyColumn(name = "voter_id")
    @Column(name = "approve", nullable = false)
    @Builder.Default
    private Map<UUID, Boolean> votes = new HashMap<>();

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
