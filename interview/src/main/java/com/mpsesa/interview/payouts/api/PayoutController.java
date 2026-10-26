package com.mpsesa.interview.payouts.api;

import com.mpsesa.interview.payouts.PayoutService;
import com.mpsesa.interview.idempotency.IdempotencyService;
import com.mpsesa.interview.idempotency.IdempotencyService.Result;
import com.mpsesa.interview.payouts.data.PayoutRequest;
import com.mpsesa.interview.payouts.data.PayoutView;
import jakarta.validation.Valid;
import java.util.concurrent.Callable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 5.4 */
@RestController
@RequestMapping("/mpesa/v1/payouts")
public class PayoutController {
    private final PayoutService service;
    private final IdempotencyService idempotency;

    public PayoutController(PayoutService service, IdempotencyService idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    @PostMapping
    public Callable<ResponseEntity<String>> create(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                         @Valid @RequestBody PayoutRequest req) {
        return () -> idempotency.execute(key, "POST /payouts", req, () -> new Result(202, service.create(req))).toResponse();
    }

    @GetMapping("/{id}")
    public PayoutView get(@PathVariable String id) {
        return service.get(id);
    }
}
