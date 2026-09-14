package com.oncue.reservation.exception;

import com.oncue.common.web.ErrorResponse;
import com.oncue.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ReservationExceptionHandler {

    @ExceptionHandler(ReservationException.class)
    public ResponseEntity<ErrorResponse> handle(
            ReservationException exception,
            HttpServletRequest request) {
        String requestId = (String) request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return ResponseEntity.status(exception.getStatus())
                .body(new ErrorResponse(exception.getMessage(), requestId, Instant.now()));
    }
}
