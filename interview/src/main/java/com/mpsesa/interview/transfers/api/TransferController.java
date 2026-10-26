package com.mpsesa.interview.transfers.api;

import com.mpsesa.interview.transfers.TransferService;
import com.mpsesa.interview.idempotency.IdempotencyService;
import com.mpsesa.interview.idempotency.IdempotencyService.Result;
import com.mpsesa.interview.transfers.data.ReversalRequest;
import com.mpsesa.interview.transfers.data.ReversalResponse;
import com.mpsesa.interview.transfers.data.TransferRequest;
import jakarta.validation.Valid;
import java.util.concurrent.Callable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 5.1 and 5.2 */
@RestController
@RequestMapping("/mpesa/v1/p2p/transfers")
public class TransferController {
    private final TransferService service;
    private final IdempotencyService idempotency;

    public TransferController(TransferService service, IdempotencyService idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    @PostMapping
    public Callable<ResponseEntity<String>> transfer(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                           @Valid @RequestBody TransferRequest req) {
        return () -> idempotency.execute(key, "POST /p2p/transfers", req,
                () -> new Result(201, service.transfer(req))).toResponse();
    }

    @PostMapping("/{id}/reversal")
    public Callable<ReversalResponse> reverse(@PathVariable String id, @Valid @RequestBody(required = false) ReversalRequest req) {
        String reason = req == null ? null : req.reason();
        return () -> service.reverse(id, reason);
    }
}
