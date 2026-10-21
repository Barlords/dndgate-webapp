package fr.barlords.dndgateapi.server.entity;


import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "historical_note")
public class HistoricalNoteEntity implements Serializable {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adventure_id", updatable = false, nullable = false)
    private AdventureEntity adventure;

    @Column(name = "content", nullable = false, length = 5000)
    private String content;

    @Column(name = "date", nullable = false)
    private LocalDate date;
}
