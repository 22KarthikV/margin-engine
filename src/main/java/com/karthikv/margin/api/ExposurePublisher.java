package com.karthikv.margin.api;

import com.karthikv.margin.domain.AccountExposure;
import java.util.List;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Pushes exposure snapshots to every connected dashboard. */
@Component
public class ExposurePublisher {

    static final String DESTINATION = "/topic/exposure";

    private final SimpMessagingTemplate messaging;

    public ExposurePublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public void publish(List<AccountExposure> exposures) {
        for (AccountExposure exposure : exposures) {
            messaging.convertAndSend(DESTINATION, exposure);
        }
    }
}
