package fr.barlords.dndgateapi.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "message")
public class MessageEntity implements Serializable {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "adventure_id", updatable = false, nullable = false)
    private UUID adventureId;

    @Column(name = "sender_id", updatable = false, nullable = false)
    private UUID senderId;

    @Column(name = "content", updatable = false, nullable = false, length = 5000)
    private String content;

    @Column(name = "send_date", updatable = false, nullable = false)
    private Instant sendDate;

}
