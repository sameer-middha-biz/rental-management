package com.rental.pms.common.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditEventPublisherTest {

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private AuditEventPublisher auditEventPublisher;

    @Test
    void publish_ShouldDelegateToApplicationEventPublisher() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        auditEventPublisher.publish(tenantId, userId, "BOOKING_CREATED", "Booking", entityId, "New booking");

        verify(applicationEventPublisher).publishEvent(org.mockito.ArgumentMatchers.any(AuditEvent.class));
    }

    @Test
    void publish_ShouldCreateEventWithCorrectFields() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        String action = "PROPERTY_UPDATED";
        String entityType = "Property";
        String details = "Name changed";

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);

        auditEventPublisher.publish(tenantId, userId, action, entityType, entityId, details);

        verify(applicationEventPublisher).publishEvent(captor.capture());
        AuditEvent event = captor.getValue();

        assertThat(event.tenantId()).isEqualTo(tenantId);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.action()).isEqualTo(action);
        assertThat(event.entityType()).isEqualTo(entityType);
        assertThat(event.entityId()).isEqualTo(entityId);
        assertThat(event.details()).isEqualTo(details);
    }

    @Test
    void publish_ShouldAutoGenerateEventIdAndTimestamp() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);

        auditEventPublisher.publish(
                UUID.randomUUID(), UUID.randomUUID(), "TEST_ACTION",
                "TestEntity", UUID.randomUUID(), "details");

        verify(applicationEventPublisher).publishEvent(captor.capture());
        AuditEvent event = captor.getValue();

        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
    }
}
