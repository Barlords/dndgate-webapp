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
@Table(name = "rating")
public class RatingEntity implements Serializable {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "emitter_id", nullable = false)
    private UUID emitterId;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "rate", nullable = false)
    private int rate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

}
