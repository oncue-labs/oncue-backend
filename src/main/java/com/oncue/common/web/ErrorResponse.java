package com.oncue.common.web;

import java.time.Instant;

/** 클라이언트가 오류를 추적할 때 사용하는 공통 오류 응답. */
public record ErrorResponse(
        /** 사용자에게 표시할 수 있는 오류 설명. */
        String message,
        /** 서버 로그에서 같은 요청을 찾을 때 사용할 값. */
        String requestId,
        /** 이 오류 응답이 만들어진 시각. */
        Instant createdAt
) {
}
