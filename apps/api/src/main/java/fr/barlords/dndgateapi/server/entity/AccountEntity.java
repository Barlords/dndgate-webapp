package fr.barlords.dndgateapi.server.entity;


import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "accounts")
public class AccountEntity implements Serializable {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", unique = true, nullable = false)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "pseudo", unique = true, nullable = false)
    private String pseudo;

    @Column(name = "activated", nullable = false)
    private boolean activated;


    @Column(name = "pending_email")
    private String pendingEmail;

    @Column(name = "pending_email_code")
    private String pendingEmailCode;

    @Column(name = "pending_email_expires_at")
    private Instant pendingEmailExpiresAt;

    @Column(name = "founder", nullable = false)
    private boolean founder;

    @Column(name = "anonymized_at")
    private Instant anonymizedAt;

    @OneToMany(mappedBy = "owner",
            cascade = CascadeType.ALL)
    private List<SpellBookEntity> spellBookList;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

}
