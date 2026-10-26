package com.mpsesa.interview.callbacks;

import com.mpsesa.interview.callbacks.data.CallbackRequest;
import com.mpsesa.interview.callbacks.repository.BankCallbackRepository;
import com.mpsesa.interview.core.ApiException;
import com.mpsesa.interview.core.DbLocks;
import com.mpsesa.interview.core.ErrorCode;
import com.mpsesa.interview.payouts.PayoutService;
import com.mpsesa.interview.payouts.domain.Payout;
import com.mpsesa.interview.payouts.domain.PayoutState;
import com.mpsesa.interview.payouts.repository.PayoutRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CallbackService {
    private final PayoutRepository payouts;
    private final BankCallbackRepository callbacks;
    private final PayoutService payoutService;
    private final DbLocks locks;

    public CallbackService(PayoutRepository payouts, BankCallbackRepository callbacks, PayoutService payoutService,
                           DbLocks locks) {
        this.payouts = payouts;
        this.callbacks = callbacks;
        this.payoutService = payoutService;
        this.locks = locks;
    }

    @Transactional
    public void handle(CallbackRequest req) {
        locks.boundWaits();
        Payout p = payouts.lockById(req.payoutId())
                .orElseThrow(() -> new ApiException(ErrorCode.PAYOUT_NOT_FOUND, "payout " + req.payoutId() + " not found"));
        if (callbacks.insertIfAbsent(req.eventId(), req.payoutId(), req.status()) == 0) {
            return;
        }
        payoutService.applyFinal(p, PayoutState.valueOf(req.status()), req.bankRef());
    }
}
