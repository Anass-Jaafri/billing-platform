package com.anass.billing.common.messaging;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.GetResponse;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.support.DefaultMessagePropertiesConverter;
import org.springframework.amqp.rabbit.support.MessagePropertiesConverter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Component
public class DlqOperations {

    static final int MAX_SCAN = 500;

    private final ConnectionFactory connectionFactory;
    private final MessagePropertiesConverter propertiesConverter = new DefaultMessagePropertiesConverter();

    public DlqOperations(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * Lists up to {@code limit} dead-lettered messages without removing them.
     */
    public List<DlqMessage> list(DlqWorker worker, int limit) {
        int max = Math.min(Math.max(limit, 1), MAX_SCAN);
        return withChannel(channel -> {
            List<GetResponse> fetched = fetch(channel, worker.getDeadLetterQueue(), max);
            try {
                return fetched.stream().map(r -> toDlqMessage(worker, r)).toList();
            } finally {
                for (GetResponse r : fetched) {
                    requeue(channel, r);
                }
            }
        });
    }

    /**
     * Re-publishes one message to the worker's own queue and removes it from the DLQ.
     */
    public void replay(DlqWorker worker, String messageId) {
        withChannel(channel -> {
            ensureQueueExists(channel, worker.getWorkQueue());
            List<GetResponse> fetched = fetch(channel, worker.getDeadLetterQueue(), MAX_SCAN);
            GetResponse target = fetched.stream()
                    .filter(r -> messageId.equals(r.getProps().getMessageId()))
                    .findFirst().orElse(null);
            boolean replayed = false;
            try {
                if (target == null) {
                    throw new DlqMessageNotFoundException(worker, messageId);
                }
                // default exchange + queue name as key = straight into that one queue
                channel.basicPublish("", worker.getWorkQueue(), target.getProps(), target.getBody());
                channel.basicAck(target.getEnvelope().getDeliveryTag(), false);
                replayed = true;
            } finally {
                for (GetResponse r : fetched) {
                    if (!(replayed && r == target)) {
                        requeue(channel, r);
                    }
                }
            }
            return null;
        });
    }

    // --- helpers ---

    private List<GetResponse> fetch(Channel channel, String queue, int max) throws IOException {
        List<GetResponse> fetched = new ArrayList<>();
        while (fetched.size() < max) {
            GetResponse response = channel.basicGet(queue, false);   // false = we will ack/nack ourselves
            if (response == null) {
                break;
            }
            fetched.add(response);
        }
        return fetched;
    }

    private void requeue(Channel channel, GetResponse response) throws IOException {
        channel.basicNack(response.getEnvelope().getDeliveryTag(), false, true);
    }

    private void ensureQueueExists(Channel channel, String queue) {
        try {
            channel.queueDeclarePassive(queue);
        } catch (IOException e) {
            throw new IllegalStateException("Queue " + queue + " does not exist; start the workers once to declare it", e);
        }
    }

    private DlqMessage toDlqMessage(DlqWorker worker, GetResponse response) {
        MessageProperties props = propertiesConverter.toMessageProperties(
                response.getProps(), response.getEnvelope(), "UTF-8");
        return new DlqMessage(props.getMessageId(), failureCount(worker, props),
                new String(response.getBody(), StandardCharsets.UTF_8));
    }

    // RabbitMQ's x-death header counts how often the broker dead-lettered this message
    private long failureCount(DlqWorker worker, MessageProperties props) {
        List<Map<String, ?>> deaths = props.getXDeathHeader();
        if (deaths == null) {
            return 0;
        }
        return deaths.stream()
                .filter(d -> worker.getWorkQueue().equals(d.get("queue")))
                .map(d -> d.get("count"))
                .filter(Number.class::isInstance)
                .mapToLong(c -> ((Number) c).longValue())
                .sum();
    }

    private <T> T withChannel(ChannelWork<T> work) {
        Channel channel = connectionFactory.createConnection().createChannel(false);   // not transactional
        try {
            return work.run(channel);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            try {
                channel.close();
            } catch (IOException | TimeoutException ignored) {
                //every fetched message was acked or requeued above
            }
        }
    }

    @FunctionalInterface
    private interface ChannelWork<T> {
        T run(Channel channel) throws IOException;
    }
}