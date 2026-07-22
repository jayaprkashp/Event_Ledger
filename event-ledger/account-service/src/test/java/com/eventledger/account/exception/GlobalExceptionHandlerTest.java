package com.eventledger.account.exception;

import com.eventledger.account.dto.response.ErrorResponse;
import com.eventledger.account.domain.Account;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises GlobalExceptionHandler's methods directly rather than through
 * MockMvc/a full request. This is deliberate: InvalidTransactionException
 * and the ObjectOptimisticLockingFailureException/generic-Exception
 * handlers are not currently thrown anywhere in production code (see their
 * class-level comments), so no end-to-end request could ever reach them --
 * calling the handler methods directly is the only way to cover that logic
 * at all, and is also simpler/faster than standing up a Spring context to
 * cover the ones that ARE reachable end-to-end.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleValidation_usesFirstFieldError_andReturns400() throws NoSuchMethodException {
        Method dummyMethod = DummyTarget.class.getDeclaredMethod("dummyMethod", String.class);
        MethodParameter methodParameter = new MethodParameter(dummyMethod, 0);

        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "amount", "amount must be greater than 0"));
        bindingResult.addError(new FieldError("request", "currency", "currency is required"));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("VALIDATION_ERROR");
        // Only the FIRST field error should be used, not both.
        assertThat(response.getBody().error().message()).isEqualTo("amount: amount must be greater than 0");
    }

    @Test
    void handleValidation_noFieldErrors_fallsBackToGenericMessage() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        // Deliberately no field errors added -- covers the .orElse("Validation failed") branch.
        MethodParameter methodParameter = getDummyMethodParameter();
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().message()).isEqualTo("Validation failed");
    }

    @Test
    void handleNotFound_returns404_withAccountNotFoundCode() {
        AccountNotFoundException ex = new AccountNotFoundException("acct-missing");

        ResponseEntity<ErrorResponse> response = handler.handleNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("ACCOUNT_NOT_FOUND");
        assertThat(response.getBody().error().message()).contains("acct-missing");
    }

    @Test
    void handleInvalid_returns400_withInvalidTransactionCode() {
        InvalidTransactionException ex = new InvalidTransactionException("currency mismatch on existing account");

        ResponseEntity<ErrorResponse> response = handler.handleInvalid(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("INVALID_TRANSACTION");
        assertThat(response.getBody().error().message()).isEqualTo("currency mismatch on existing account");
    }

    @Test
    void handleConcurrentUpdate_returns409_withConcurrentUpdateCode() {
        ObjectOptimisticLockingFailureException ex =
                new ObjectOptimisticLockingFailureException(Account.class, "acct-1");

        ResponseEntity<ErrorResponse> response = handler.handleConcurrentUpdate(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("CONCURRENT_UPDATE");
        assertThat(response.getBody().error().message()).contains("concurrently");
    }

    @Test
    void handleUnexpected_returns500_withInternalErrorCode() {
        RuntimeException ex = new RuntimeException("something truly unexpected");

        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
        // Message is deliberately generic -- the real exception detail goes to
        // the log, not the client response.
        assertThat(response.getBody().error().message()).isEqualTo("An unexpected error occurred");
    }

    private MethodParameter getDummyMethodParameter() {
        try {
            Method dummyMethod = DummyTarget.class.getDeclaredMethod("dummyMethod", String.class);
            return new MethodParameter(dummyMethod, 0);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    /** Just a method to point a real MethodParameter at -- MethodArgumentNotValidException requires one. */
    static class DummyTarget {
        void dummyMethod(String arg) {
        }
    }
}
