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
@Table(name = "sent_invitations")
public class SentInvitationEntity implements Serializable {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "sender_id", updatable = false, nullable = false)
    private UUID senderId;

    @Column(name = "recipient_id", updatable = false, nullable = false)
    private UUID recipientId;

    @Column(name = "adventure_id", updatable = false, nullable = false)
    private UUID adventureId;

    @Column(name = "sent_at", updatable = false, nullable = false)
    private Instant sentAt;
}
