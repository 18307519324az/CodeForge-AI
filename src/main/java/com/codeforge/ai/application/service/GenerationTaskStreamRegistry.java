package com.codeforge.ai.application.service;

import com.codeforge.ai.application.dto.task.PublicGenerationStreamEvent;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Component
public class GenerationTaskStreamRegistry {

    private static final long DEFAULT_TIMEOUT_MS = 30L * 60L * 1000L;
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15L;
    private static final String HEARTBEAT_EVENT_NAME = "HEARTBEAT";

    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(
            1,
            runnable -> {
                Thread thread = new Thread(runnable, "generation-sse-heartbeat");
                thread.setDaemon(true);
                return thread;
            });
    private final PublicGenerationStreamEventMapper publicGenerationStreamEventMapper;
    private final ScheduledFuture<?> heartbeatSweepFuture;

    private final Map<Long, Object> taskLocks = new ConcurrentHashMap<>();
    private final Map<Long, CopyOnWriteArrayList<TaskSubscriber>> subscribers = new ConcurrentHashMap<>();

    public GenerationTaskStreamRegistry(PublicGenerationStreamEventMapper publicGenerationStreamEventMapper) {
        this.publicGenerationStreamEventMapper = publicGenerationStreamEventMapper;
        this.heartbeatSweepFuture = heartbeatExecutor.scheduleAtFixedRate(
                this::runHeartbeatSweep,
                HEARTBEAT_INTERVAL_SECONDS,
                HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    public SseEmitter subscribe(Long taskId,
                                Long afterEventId,
                                Supplier<List<PublicGenerationStreamEvent>> fullHistorySupplier,
                                Function<Long, List<PublicGenerationStreamEvent>> eventsAfterIdSupplier,
                                BooleanSupplier terminalSupplier) {
        return subscribeInternal(taskId, new SseEmitter(DEFAULT_TIMEOUT_MS), afterEventId,
                fullHistorySupplier, eventsAfterIdSupplier, terminalSupplier);
    }

    SseEmitter subscribeWithEmitter(Long taskId,
                                    SseEmitter emitter,
                                    Long afterEventId,
                                    Supplier<List<PublicGenerationStreamEvent>> fullHistorySupplier,
                                    Function<Long, List<PublicGenerationStreamEvent>> eventsAfterIdSupplier,
                                    BooleanSupplier terminalSupplier) {
        return subscribeInternal(taskId, emitter, afterEventId,
                fullHistorySupplier, eventsAfterIdSupplier, terminalSupplier);
    }

    private SseEmitter subscribeInternal(Long taskId,
                                         SseEmitter emitter,
                                         Long afterEventId,
                                         Supplier<List<PublicGenerationStreamEvent>> fullHistorySupplier,
                                         Function<Long, List<PublicGenerationStreamEvent>> eventsAfterIdSupplier,
                                         BooleanSupplier terminalSupplier) {
        TaskSubscriber subscriber = new TaskSubscriber(emitter);
        bindLifecycle(taskId, subscriber);

        synchronized (lockFor(taskId)) {
            List<PublicGenerationStreamEvent> history = resolveInitialHistory(
                    afterEventId,
                    fullHistorySupplier,
                    eventsAfterIdSupplier);
            try {
                replayEvents(subscriber, history);
                reconcileMissedEvents(subscriber, eventsAfterIdSupplier);
                if (terminalSupplier.getAsBoolean()) {
                    closeSubscriberQuietly(taskId, subscriber, "terminal-replay");
                } else {
                    subscribers.computeIfAbsent(taskId, ignored -> new CopyOnWriteArrayList<>()).add(subscriber);
                }
            } catch (IOException exception) {
                closeSubscriberQuietly(taskId, subscriber, "subscribe-replay");
            } catch (RuntimeException exception) {
                if (isTransportOrSendFailure(exception)) {
                    closeSubscriberQuietly(taskId, subscriber, "subscribe-replay");
                } else {
                    throw exception;
                }
            }
        }
        return emitter;
    }

    public void publish(Long taskId, PublicGenerationStreamEvent event, boolean terminalEvent) {
        synchronized (lockFor(taskId)) {
            CopyOnWriteArrayList<TaskSubscriber> taskSubscribers = subscribers.get(taskId);
            if (taskSubscribers == null || taskSubscribers.isEmpty()) {
                return;
            }
            for (TaskSubscriber subscriber : List.copyOf(taskSubscribers)) {
                deliverToSubscriber(taskId, subscriber, event, terminalEvent);
            }
        }
    }

    int subscriberCount(Long taskId) {
        CopyOnWriteArrayList<TaskSubscriber> taskSubscribers = subscribers.get(taskId);
        return taskSubscribers == null ? 0 : taskSubscribers.size();
    }

    int scheduledHeartbeatTaskCount() {
        return heartbeatSweepFuture.isCancelled() ? 0 : 1;
    }

    void runHeartbeatSweepForTest() {
        runHeartbeatSweep();
    }

    private void deliverToSubscriber(Long taskId,
                                     TaskSubscriber subscriber,
                                     PublicGenerationStreamEvent event,
                                     boolean terminalEvent) {
        if (subscriber.isClosed()) {
            return;
        }
        try {
            send(subscriber, event);
            if (terminalEvent) {
                closeSubscriberQuietly(taskId, subscriber, "terminal-publish");
            }
        } catch (Exception exception) {
            if (isTransportOrSendFailure(exception)) {
                log.warn("SSE transport failure while publishing to task {} subscriber, closing quietly: {}",
                        taskId, exception.toString());
                closeSubscriberQuietly(taskId, subscriber, "publish-send");
            } else {
                log.warn("Unexpected SSE publish failure for task {}, closing subscriber: {}",
                        taskId, exception.toString());
                closeSubscriberQuietly(taskId, subscriber, "publish-unexpected");
            }
        }
    }

    private void runHeartbeatSweep() {
        for (Map.Entry<Long, CopyOnWriteArrayList<TaskSubscriber>> entry : subscribers.entrySet()) {
            Long taskId = entry.getKey();
            for (TaskSubscriber subscriber : List.copyOf(entry.getValue())) {
                if (subscriber.isClosed()) {
                    continue;
                }
                try {
                    sendHeartbeat(taskId, subscriber);
                } catch (Exception exception) {
                    if (isTransportOrSendFailure(exception)) {
                        log.debug("Heartbeat transport failure for task {}, closing subscriber: {}",
                                taskId, exception.toString());
                        closeSubscriberQuietly(taskId, subscriber, "heartbeat");
                    }
                }
            }
        }
    }

    private void sendHeartbeat(Long taskId, TaskSubscriber subscriber) throws IOException {
        if (subscriber.isClosed()) {
            return;
        }
        PublicGenerationStreamEvent heartbeat = publicGenerationStreamEventMapper.heartbeat(taskId);
        subscriber.emitter().send(SseEmitter.event()
                .name(HEARTBEAT_EVENT_NAME)
                .comment("heartbeat")
                .data(heartbeat));
    }

    private List<PublicGenerationStreamEvent> resolveInitialHistory(
            Long afterEventId,
            Supplier<List<PublicGenerationStreamEvent>> fullHistorySupplier,
            Function<Long, List<PublicGenerationStreamEvent>> eventsAfterIdSupplier) {
        if (afterEventId != null && afterEventId > 0) {
            return eventsAfterIdSupplier.apply(afterEventId);
        }
        return fullHistorySupplier.get();
    }

    private void replayEvents(TaskSubscriber subscriber, List<PublicGenerationStreamEvent> history) throws IOException {
        for (PublicGenerationStreamEvent event : history) {
            if (subscriber.isClosed()) {
                return;
            }
            send(subscriber, event);
        }
    }

    private void reconcileMissedEvents(TaskSubscriber subscriber,
                                       Function<Long, List<PublicGenerationStreamEvent>> eventsAfterIdSupplier)
            throws IOException {
        long lastSentEventId = subscriber.lastEventId().get();
        if (lastSentEventId <= 0) {
            return;
        }
        List<PublicGenerationStreamEvent> missedEvents = eventsAfterIdSupplier.apply(lastSentEventId);
        for (PublicGenerationStreamEvent event : missedEvents) {
            if (subscriber.isClosed()) {
                return;
            }
            send(subscriber, event);
        }
    }

    private void send(TaskSubscriber subscriber, PublicGenerationStreamEvent event) throws IOException {
        if (subscriber.isClosed()) {
            return;
        }
        if (event.eventId() != null) {
            long eventId = Long.parseLong(event.eventId());
            if (eventId <= subscriber.lastEventId().get()) {
                return;
            }
            subscriber.lastEventId().set(eventId);
        }
        SseEmitter.SseEventBuilder builder = SseEmitter.event()
                .name(event.type())
                .data(event);
        if (event.eventId() != null) {
            builder.id(event.eventId());
        }
        subscriber.emitter().send(builder);
    }

    private void bindLifecycle(Long taskId, TaskSubscriber subscriber) {
        subscriber.emitter().onCompletion(() -> closeSubscriberQuietly(taskId, subscriber, "onCompletion"));
        subscriber.emitter().onTimeout(() -> closeSubscriberQuietly(taskId, subscriber, "onTimeout"));
        subscriber.emitter().onError(ignored -> closeSubscriberQuietly(taskId, subscriber, "onError"));
    }

    private void closeSubscriberQuietly(Long taskId, TaskSubscriber subscriber, String reason) {
        if (!subscriber.markClosed()) {
            return;
        }
        safeComplete(subscriber);
        removeSubscriber(taskId, subscriber);
        log.debug("Closed SSE subscriber for task {} ({})", taskId, reason);
    }

    private void safeComplete(TaskSubscriber subscriber) {
        try {
            subscriber.emitter().complete();
        } catch (Exception exception) {
            if (!isTransportOrSendFailure(exception)) {
                log.debug("Ignored exception while completing SSE emitter: {}", exception.toString());
            }
        }
    }

    private void removeSubscriber(Long taskId, TaskSubscriber subscriber) {
        synchronized (lockFor(taskId)) {
            CopyOnWriteArrayList<TaskSubscriber> taskSubscribers = subscribers.get(taskId);
            if (taskSubscribers == null) {
                return;
            }
            taskSubscribers.remove(subscriber);
            if (taskSubscribers.isEmpty()) {
                subscribers.remove(taskId);
                taskLocks.remove(taskId);
            }
        }
    }

    private boolean isTransportOrSendFailure(Throwable failure) {
        return failure instanceof IOException || SseTransportFailures.isTransportFailure(failure);
    }

    private Object lockFor(Long taskId) {
        return taskLocks.computeIfAbsent(taskId, ignored -> new Object());
    }

    static final class TaskSubscriber {

        private final SseEmitter emitter;
        private final AtomicLong lastEventId;
        private final AtomicBoolean closed;

        private TaskSubscriber(SseEmitter emitter) {
            this.emitter = emitter;
            this.lastEventId = new AtomicLong(0L);
            this.closed = new AtomicBoolean(false);
        }

        SseEmitter emitter() {
            return emitter;
        }

        AtomicLong lastEventId() {
            return lastEventId;
        }

        boolean isClosed() {
            return closed.get();
        }

        boolean markClosed() {
            return closed.compareAndSet(false, true);
        }
    }
}
