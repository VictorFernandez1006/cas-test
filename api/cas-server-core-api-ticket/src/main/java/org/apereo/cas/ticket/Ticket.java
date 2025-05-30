package org.apereo.cas.ticket;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.io.Serializable;
import java.time.ZonedDateTime;

/**
 * Interface for the generic concept of a ticket.
 *
 * @author Scott Battaglia
 * @since 3.0.0
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public interface Ticket extends Serializable, Comparable<Ticket> {

    /**
     * Method to retrieve the id.
     *
     * @return the id
     */
    String getId();

    /**
     * Method to return the time the Ticket was created.
     *
     * @return the time the ticket was created.
     */
    ZonedDateTime getCreationTime();

    /**
     * Gets count of uses.
     *
     * @return the number of times this ticket was used.
     */
    int getCountOfUses();

    /**
     * Gets prefix.
     *
     * @return the prefix
     */
    String getPrefix();

    /**
     * Determines if the ticket is expired. Most common implementations might
     * collaborate with <i>ExpirationPolicy</i> strategy.
     *
     * @return true, if the ticket is expired
     * @see ExpirationPolicy
     */
    boolean isExpired();

    /**
     * Get expiration policy associated with ticket.
     *
     * @return the expiration policy
     */
    ExpirationPolicy getExpirationPolicy();

    /**
     * Mark a ticket as expired.
     */
    void markTicketExpired();
}
