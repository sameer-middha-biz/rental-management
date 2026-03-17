package com.rental.pms.common.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SpringDomainEventPublisherTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private SpringDomainEventPublisher publisher;

    @Test
    void publish_ShouldDelegateToApplicationEventPublisher() {
        DomainEvent event = DomainEvent.now(UUID.randomUUID());

        publisher.publish(event);

        verify(applicationEventPublisher).publishEvent(event);
    }

    @Test
    void publish_WithArbitraryObject_ShouldDelegateToApplicationEventPublisher() {
        String event = "test-event";

        publisher.publish(event);

        verify(applicationEventPublisher).publishEvent(event);
    }

    @Test
    void publish_WithRecord_ShouldDelegateToApplicationEventPublisher() {
        record TestEvent(UUID id, String action) {}
        TestEvent event = new TestEvent(UUID.randomUUID(), "CREATED");

        publisher.publish(event);

        verify(applicationEventPublisher).publishEvent(event);
    }
}
