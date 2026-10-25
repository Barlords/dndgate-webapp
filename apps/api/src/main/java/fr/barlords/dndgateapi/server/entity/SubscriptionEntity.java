package fr.barlords.dndgateapi.server.entity;

import fr.barlords.dndgateapi.domain.functional.model.plan.BillingInterval;
import fr.barlords.dndgateapi.domain.functional.model.plan.SubscriptionStatus;
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
@Table(name = "subscription")
public class SubscriptionEntity implements Serializable {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "account_id", updatable = false, nullable = false)
    private UUID accountId;

    @Column(name = "stripe_customer_id", nullable = false)
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;

    @Column(name = "plan_id", length = 50)
    private String planId;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_interval", length = 20)
    private BillingInterval billingInterval;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30)
    private SubscriptionStatus status;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "cancel_at_period_end", nullable = false)
    private boolean cancelAtPeriodEnd;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
