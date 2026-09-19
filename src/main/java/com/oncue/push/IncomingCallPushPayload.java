package com.oncue.push;

/** Safe data required to render the native incoming-call screen. */
public record IncomingCallPushPayload(Long callSessionId, String displayName) {
}
