package com.seregamazur.pulse.shared.outbox;

import java.util.List;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.seregamazur.pulse.shared.event.EventEnvelope;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;


    @Scheduled(fixedDelay = 5_000)
    @Transactional
    public void publish() {
        List<OutboxRecord> events = repository.findAll();
        for (OutboxRecord event : events) {
            rabbitTemplate.convertAndSend("outbox-exchange", event.getEventType().name(),
                EventEnvelope.fromOutboxRecord(event));
            System.out.println("SUCCESSFULY PUBLISHED EVENT TO RABBIT!!!!");
        }
        repository.deleteAllById(events.stream().map(OutboxRecord::getId).toList());
    }
}
