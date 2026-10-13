package com.diezaster.ledger.worker;

import com.diezaster.ledger.domain.OutboxEvent;
import com.diezaster.ledger.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxEventPublisherWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisherWorker.class);
    private static final String TOPIC = "ledger.transfers.v1";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxEventPublisherWorker(OutboxEventRepository outboxEventRepository,
                                      KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Polls pending outbox records using PostgreSQL SKIP LOCKED every 1000ms.
     */
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEventsSkipLocked();
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("Processing {} outbox events via SKIP LOCKED worker", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                kafkaTemplate.send(TOPIC, event.getAggregateId().toString(), event.getPayload())
                        .whenComplete((result, ex) -> {
                            if (ex == null) {
                                event.setStatus(OutboxEvent.Status.PUBLISHED);
                                outboxEventRepository.save(event);
                                log.debug("Published outbox event: {}", event.getId());
                            } else {
                                log.error("Failed to publish outbox event: {}", event.getId(), ex);
                            }
                        });
            } catch (Exception ex) {
                log.error("Exception dispatching event to Kafka: {}", event.getId(), ex);
            }
        }
    }
}
