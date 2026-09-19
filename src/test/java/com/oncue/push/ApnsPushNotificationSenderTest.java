package com.oncue.push;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oncue.auth.model.User;
import java.net.http.HttpRequest;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

    @Test
    void includesApnsRejectionReasonInTheFailure() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(403);
        when(response.body()).thenReturn("{\"reason\":\"InvalidProviderToken\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        ApnsPushNotificationSender sender = new ApnsPushNotificationSender(
                httpClient,
                "key-id",
                "team-id",
                "com.oncue.oncueMobile",
                ecPrivateKeyPem(),
                "");
        PushDevice device = new PushDevice(
                new User(7L), "device-token", PushPlatform.IOS, PushEnvironment.SANDBOX);

        assertThatThrownBy(() -> sender.send(
                device, new IncomingCallPushPayload(321L, "Santa")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("InvalidProviderToken");
    }

    @Test
    void usesTheVoipTopicForPushKitTokens() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        when(httpClient.send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        ApnsPushNotificationSender sender = new ApnsPushNotificationSender(
                httpClient,
                "key-id",
                "team-id",
                "com.oncue.oncueMobile",
                ecPrivateKeyPem(),
                "");
        PushDevice device = new PushDevice(
                new User(7L), "device-token", PushPlatform.IOS, PushEnvironment.SANDBOX);

        sender.send(device, new IncomingCallPushPayload(321L, "Santa"));

        assertThat(requestCaptor.getValue().headers().firstValue("apns-topic"))
                .contains("com.oncue.oncueMobile.voip");
    }

    private static String ecPrivateKeyPem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        byte[] encoded = generator.generateKeyPair().getPrivate().getEncoded();
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded)
                + "\n-----END PRIVATE KEY-----";
    }
}
