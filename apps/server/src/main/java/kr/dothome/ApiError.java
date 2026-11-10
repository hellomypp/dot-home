package kr.dothome;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.OptimisticLockingFailureException;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;

class ApiException extends RuntimeException {
    final HttpStatus status;
    final String code;
    ApiException(HttpStatus status,String code,String message) { super(message); this.status=status; this.code=code; }
    static ApiException forbidden() { return new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","이 집에 접근할 권한이 없어요."); }
    static ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND,"NOT_FOUND","찾을 수 없어요."); }
    static ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT,"CONFLICT",message); }
    static ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_INPUT",message); }
}
class InviteRateLimited extends ApiException {
    final long retryAfter;
    InviteRateLimited(long retryAfter){
        super(HttpStatus.TOO_MANY_REQUESTS,"INVITE_RATE_LIMITED",
            "짧은 시간에 초대 링크를 여러 번 만들었어요. 약 "+((retryAfter+59)/60)+"분 후 다시 만들어 주세요.");
        this.retryAfter=retryAfter;
    }
}
@RestControllerAdvice
class ApiError {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> domain(ApiException ex) {
        var response=ResponseEntity.status(ex.status);
        if(ex instanceof InviteRateLimited limit)response.header(HttpHeaders.RETRY_AFTER,Long.toString(limit.retryAfter));
        return response.body(Map.of("code",ex.code,"message",ex.getMessage()));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class,ConstraintViolationException.class,HttpMessageNotReadableException.class})
    ResponseEntity<?> validation(Exception ex) { return ResponseEntity.badRequest().body(Map.of("code","INVALID_INPUT","message","입력 값을 확인해주세요.")); }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<?> conflict(Exception ex) { return ResponseEntity.status(409).body(Map.of("code","CONFLICT","message","다른 구성원이 먼저 수정했어요. 새로고침 후 다시 기록해주세요.")); }
}
