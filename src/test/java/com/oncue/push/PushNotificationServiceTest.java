package com.oncue.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushNotificationServiceTest {

    @Mock
    private PushDeviceRepository pushDeviceRepository;

    @Mock
    private PushNotificationSender pushNotificationSender;

    @Test
    void sendsOnlySafeIncomingCallDisplayDataToTheRegisteredDevice() {
        PushDevice device = new PushDevice(
                new com.oncue.auth.model.User(7L),
                "device-token",
                PushPlatform.IOS,
                PushEnvironment.SANDBOX);
        when(pushDeviceRepository.findByUserId(7L)).thenReturn(Optional.of(device));

        boolean sent = new PushNotificationService(
                pushDeviceRepository, pushNotificationSender)
                .sendIncomingCall(7L, new IncomingCallPushPayload(321L, "Santa"));

        assertThat(sent).isTrue();
        verify(pushNotificationSender).send(device, new IncomingCallPushPayload(321L, "Santa"));
    }

    @Test
    void reportsFailureWhenTheUserHasNoRegisteredDevice() {
        when(pushDeviceRepository.findByUserId(7L)).thenReturn(Optional.empty());

        boolean sent = new PushNotificationService(
                pushDeviceRepository, pushNotificationSender)
                .sendIncomingCall(7L, new IncomingCallPushPayload(321L, "Santa"));

        assertThat(sent).isFalse();
    }

    @Test
    void reportsFailureWhenApnsRejectsThePush() {
        PushDevice device = new PushDevice(
                new com.oncue.auth.model.User(7L),
                "device-token",
                PushPlatform.IOS,
                PushEnvironment.PRODUCTION);
        when(pushDeviceRepository.findByUserId(7L)).thenReturn(Optional.of(device));
        doThrow(new IllegalStateException("APNs rejected the push"))
                .when(pushNotificationSender)
                .send(device, new IncomingCallPushPayload(321L, "Santa"));

        boolean sent = new PushNotificationService(
                pushDeviceRepository, pushNotificationSender)
                .sendIncomingCall(7L, new IncomingCallPushPayload(321L, "Santa"));

        assertThat(sent).isFalse();
    }
}
