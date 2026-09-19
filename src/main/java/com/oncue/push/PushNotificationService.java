package com.oncue.push;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    private final PushDeviceRepository pushDeviceRepository;
    private final PushNotificationSender pushNotificationSender;

    public PushNotificationService(
            PushDeviceRepository pushDeviceRepository,
            PushNotificationSender pushNotificationSender) {
        this.pushDeviceRepository = pushDeviceRepository;
        this.pushNotificationSender = pushNotificationSender;
    }

    public boolean sendIncomingCall(Long userId, IncomingCallPushPayload payload) {
        return pushDeviceRepository.findByUserId(userId)
                .map(device -> send(userId, device, payload))
                .orElse(false);
    }

    private boolean send(Long userId, PushDevice device, IncomingCallPushPayload payload) {
        try {
            pushNotificationSender.send(device, payload);
            log.info(
                    "APNs push accepted for userId={} environment={} callSessionId={}",
                    userId,
                    device.getEnvironment(),
                    payload.callSessionId());
            return true;
        } catch (RuntimeException exception) {
            log.warn(
                    "APNs push failed for userId={} environment={} reason={}",
                    userId,
                    device.getEnvironment(),
                    exception.getMessage());
            return false;
        }
    }
}
