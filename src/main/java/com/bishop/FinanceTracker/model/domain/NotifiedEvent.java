package com.bishop.FinanceTracker.model.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * De-dupe marker for the notification sweep (feature doc §2A.1): a stable
 * {@code eventKey} (type + entity + period) recorded once a notification fires,
 * so the daily sweep never re-alerts the same charge/threshold.
 */
@Data
@Entity
@Table(name = "notified_events")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotifiedEvent {

    @Id
    @Column(name = "event_key", length = 300)
    private String eventKey;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;
}
