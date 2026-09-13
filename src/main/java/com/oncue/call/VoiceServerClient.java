package com.oncue.call;

public interface VoiceServerClient {

    VoiceSessionResponse createSession(CreateVoiceSessionRequest request);

    void terminateSession(String voiceSessionId);
}
