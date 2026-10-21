package fr.barlords.dndgateapi.server.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "master_invitations")
public class MasterInvitationEntity implements Serializable {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "adventure_id", updatable = false, nullable = false)
    private UUID adventureId;

    @Column(name = "code_hash", updatable = false, nullable = false)
    private String codeHash;

    @Column(name = "created_by", updatable = false, nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", updatable = false, nullable = false)
    private Instant expiresAt;
}
