package com.rental.pms.common.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * MVP implementation of DomainEventPublisher using Spring's ApplicationEventPublisher.
 * All domain events are published synchronously within the current transaction boundary.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(Object event) {
        log.debug("Publishing domain event: {}", event.getClass().getSimpleName());
        applicationEventPublisher.publishEvent(event);
    }
}
