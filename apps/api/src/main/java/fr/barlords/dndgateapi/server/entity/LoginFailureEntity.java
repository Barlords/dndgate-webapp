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
@Table(name = "login_failures")
public class LoginFailureEntity implements Serializable {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "account_id", updatable = false, nullable = false)
    private UUID accountId;

    /** SHA-256 of the client address: the address itself is personal data and is not kept. */
    @Column(name = "client_hash", updatable = false, nullable = false)
    private String clientHash;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_failed_at", nullable = false)
    private Instant lastFailedAt;
}
