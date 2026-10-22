package fr.barlords.dndgateapi.server.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "signup_notices")
public class SignupNoticeEntity implements Serializable {

    /** SHA-256 of the address: the address itself is never stored here. */
    @Id
    @EqualsAndHashCode.Include
    @Column(name = "email_hash", updatable = false, nullable = false, length = 64)
    private String emailHash;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;
}
