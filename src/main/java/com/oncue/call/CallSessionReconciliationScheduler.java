package com.oncue.call;

import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CallSessionReconciliationScheduler {

    private final CallSessionService callSessionService;
    private final Clock clock;

    @Autowired
    public CallSessionReconciliationScheduler(CallSessionService callSessionService) {
        this(callSessionService, Clock.systemUTC());
    }

    CallSessionReconciliationScheduler(CallSessionService callSessionService, Clock clock) {
        this.callSessionService = callSessionService;
        this.clock = clock;
    }

    @Scheduled(cron = "${oncue.call.reconciliation-cron:0 0 0 * * *}", zone = "UTC")
    public void reconcileExpiredCallSessions() {
        callSessionService.reconcileExpiredCallSessions(Instant.now(clock));
    }
}
