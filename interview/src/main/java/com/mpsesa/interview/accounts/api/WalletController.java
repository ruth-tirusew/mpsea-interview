package com.mpsesa.interview.accounts.api;

import com.mpsesa.interview.accounts.data.WalletAccountDTO;
import com.mpsesa.interview.accounts.data.WalletStatusRequest;
import com.mpsesa.interview.accounts.data.WalletStatusResponse;
import com.mpsesa.interview.accounts.services.WalletAccountReadService;
import com.mpsesa.interview.accounts.services.WalletAccountStatusService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mpesa/v1/wallets")
public class WalletController {
    private final WalletAccountReadService read;
    private final WalletAccountStatusService status;

    public WalletController(WalletAccountReadService read, WalletAccountStatusService status) {
        this.read = read;
        this.status = status;
    }

    @GetMapping("/{id}")
    public WalletAccountDTO get(@PathVariable String id) {
        return read.get(id);
    }

    @PutMapping("/{id}/status")
    public WalletStatusResponse setStatus(@PathVariable String id, @Valid @RequestBody WalletStatusRequest req) {
        return status.setStatus(id, req);
    }
}
