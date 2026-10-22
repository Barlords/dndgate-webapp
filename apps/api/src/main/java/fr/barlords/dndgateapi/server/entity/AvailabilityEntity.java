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
@Table(name = "availability")
public class AvailabilityEntity implements Serializable {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "planning_id", nullable = false)
    private UUID planningId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "availability_dates", joinColumns = @JoinColumn(name = "availability_id"))
    @Column(name = "availability_dates")
    private List<LocalDate> availabilityDates = new ArrayList<>();

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

}
