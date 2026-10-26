package com.mpsesa.interview.callbacks;

import com.mpsesa.interview.callbacks.data.CallbackRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mpesa/v1/callbacks")
public class CallbackController {
    private final CallbackService service;

    public CallbackController(CallbackService service) {
        this.service = service;
    }

    @PostMapping("/bank")
    public ResponseEntity<Void> bank(@Valid @RequestBody CallbackRequest req) {
        service.handle(req);
        return ResponseEntity.ok().build();
    }
}
