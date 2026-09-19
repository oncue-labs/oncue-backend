package com.oncue.push;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oncue.auth.model.User;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;

class ApnsPushNotificationSenderTest {

    @Test
    void failsClearlyWhenApnsIsNotConfigured() {
        ApnsPushNotificationSender sender = new ApnsPushNotificationSender(
                HttpClient.newHttpClient(), "", "", "", "", "");

        PushDevice device = new PushDevice(
                new User(7L), "device-token", PushPlatform.IOS, PushEnvironment.SANDBOX);

        assertThatThrownBy(() -> sender.send(
                device, new IncomingCallPushPayload(321L, "Santa")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APNs is not configured");
    }
}
