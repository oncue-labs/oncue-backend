package com.oncue.push;

import org.springframework.stereotype.Service;

@Service
public class PushNotificationService {

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
                .map(device -> send(device, payload))
                .orElse(false);
    }

    private boolean send(PushDevice device, IncomingCallPushPayload payload) {
        try {
            pushNotificationSender.send(device, payload);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
