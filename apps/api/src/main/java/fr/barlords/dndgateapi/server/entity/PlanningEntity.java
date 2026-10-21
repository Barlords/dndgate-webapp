package fr.barlords.dndgateapi.server.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;
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
@Table(name = "planning")
public class PlanningEntity implements Serializable {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "adventure_id", nullable = false, unique = true)
    private UUID adventureId;

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "propositions", joinColumns = @JoinColumn(name = "planning_id"))
    @Column(name = "proposition_dates")
    private List<LocalDate> propositionDates = new ArrayList<>();

}
