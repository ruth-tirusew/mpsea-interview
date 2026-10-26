package com.mpsesa.interview.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mpesa/internal")
public class ResetController {
    private final ResetService service;

    public ResetController(ResetService service) {
        this.service = service;
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        service.reset();
        return ResponseEntity.ok().build();
    }
}
