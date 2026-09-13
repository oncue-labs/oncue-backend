package com.oncue.call;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

public record ConnectionTokenResponse(
        /** 모바일이 보이스 서버에 연결할 때 사용할 서명된 JWT */
        String connectionToken,
        /** WebSocket 시그널링을 시작할 보이스 서버 주소 */
        String signalingUrl,
        /** WebRTC가 연결 경로를 찾을 때 사용할 STUN/TURN 서버 목록 */
        List<IceServer> iceServers,
        /** 연결 토큰이 만료되는 시각 */
        Instant expiresAt,
        /** 연결 토큰을 발급한 시각 */
        Instant createdAt
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record IceServer(
            /** 이 ICE 서버에 연결할 때 사용할 주소 목록 */
            List<String> urls,
            /** TURN 서버 사용자 이름 */
            String username,
            /** TURN 서버 인증 비밀번호 또는 임시 자격 증명 */
            String credential) {
    }
}
