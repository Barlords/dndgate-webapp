package fr.barlords.dndgateapi.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
@Table(name = "refresh_tokens")
public class RefreshTokenEntity implements Serializable {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "session_id", updatable = false, nullable = false)
    private UUID sessionId;

    @Column(name = "token_hash", updatable = false, nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", updatable = false, nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

}
