package com.eventledger.account.controller;

import com.eventledger.account.dto.request.ApplyTransactionRequest;
import com.eventledger.account.dto.response.AccountDetailResponse;
import com.eventledger.account.dto.response.BalanceResponse;
import com.eventledger.account.dto.response.TransactionResponse;
import com.eventledger.account.service.AccountTransactionService;
import com.eventledger.account.tracing.TraceContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountTransactionService transactionService;

    public AccountController(AccountTransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<TransactionResponse> applyTransaction(
            @PathVariable String accountId,
            @Valid @RequestBody ApplyTransactionRequest request) {

        boolean alreadyExisted = transactionService.transactionExists(request.eventId());
        TransactionResponse response = transactionService.applyTransaction(accountId, request);

        HttpStatus status = alreadyExisted ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .header("X-Trace-Id", TraceContext.current())
                .body(response);
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable String accountId) {
        return ResponseEntity.ok()
                .header("X-Trace-Id", TraceContext.current())
                .body(transactionService.getBalance(accountId));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountDetailResponse> getAccountDetail(@PathVariable String accountId) {
        return ResponseEntity.ok()
                .header("X-Trace-Id", TraceContext.current())
                .body(transactionService.getAccountDetail(accountId));
    }
}
