package com.codeforge.ai.application.service.repair;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class GeneratedArtifactRepairTransactionProbe {

    private final AtomicReference<ProbeState> lastState = new AtomicReference<>();

    public void record(boolean transactionActive, boolean synchronizationActive, boolean selectForUpdateActive) {
        lastState.set(new ProbeState(transactionActive, synchronizationActive, selectForUpdateActive));
    }

    public ProbeState lastState() {
        return lastState.get();
    }

    public void clear() {
        lastState.set(null);
    }

    public record ProbeState(
            boolean transactionActive,
            boolean synchronizationActive,
            boolean selectForUpdateActive) {
    }
}
