package com.rental.pms.common.event;

/**
 * Interface for publishing domain events.
 * MVP implementation uses Spring ApplicationEventPublisher.
 * Can be swapped for Kafka/SQS in future phases.
 */
public interface DomainEventPublisher {

    /**
     * Publishes a domain event to all registered listeners.
     *
     * @param event the domain event to publish
     */
    void publish(Object event);
}
