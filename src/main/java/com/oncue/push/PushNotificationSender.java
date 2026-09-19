package com.oncue.push;

public interface PushNotificationSender {

    void send(PushDevice device, IncomingCallPushPayload payload);
}
