package com.mpsesa.fakebank;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Section IV rates and timings. Bound from application.properties / env (B2) and mutable at runtime via /bank/admin/config. */
@ConfigurationProperties(prefix = "bank")
public class BankProperties {
    private volatile double acceptRate = 0.80;
    private volatile double rejectRate = 0.08;
    private volatile double noResponseRate = 0.12;
    private volatile double noResponseProcessedRate = 0.5;
    private volatile double callbackFailRate = 0.05;
    private volatile double duplicateCallbackRate = 0.30;
    private volatile Duration submitLatencyMin = Duration.ofSeconds(2);
    private volatile Duration submitLatencyMax = Duration.ofSeconds(6);
    private volatile Duration statusLatencyMin = Duration.ofSeconds(1);
    private volatile Duration statusLatencyMax = Duration.ofSeconds(3);
    private volatile Duration hang = Duration.ofSeconds(60);
    private volatile Duration callbackDelayMin = Duration.ofSeconds(1);
    private volatile Duration callbackDelayMax = Duration.ofSeconds(10);
    private volatile List<String> callbackUrls = List.of(
            "http://localhost:8081/mpesa/v1/callbacks/bank", "http://localhost:8082/mpesa/v1/callbacks/bank");

    public double getAcceptRate() { return acceptRate; }
    public void setAcceptRate(double v) { this.acceptRate = v; }
    public double getRejectRate() { return rejectRate; }
    public void setRejectRate(double v) { this.rejectRate = v; }
    public double getNoResponseRate() { return noResponseRate; }
    public void setNoResponseRate(double v) { this.noResponseRate = v; }
    public double getNoResponseProcessedRate() { return noResponseProcessedRate; }
    public void setNoResponseProcessedRate(double v) { this.noResponseProcessedRate = v; }
    public double getCallbackFailRate() { return callbackFailRate; }
    public void setCallbackFailRate(double v) { this.callbackFailRate = v; }
    public double getDuplicateCallbackRate() { return duplicateCallbackRate; }
    public void setDuplicateCallbackRate(double v) { this.duplicateCallbackRate = v; }
    public Duration getSubmitLatencyMin() { return submitLatencyMin; }
    public void setSubmitLatencyMin(Duration v) { this.submitLatencyMin = v; }
    public Duration getSubmitLatencyMax() { return submitLatencyMax; }
    public void setSubmitLatencyMax(Duration v) { this.submitLatencyMax = v; }
    public Duration getStatusLatencyMin() { return statusLatencyMin; }
    public void setStatusLatencyMin(Duration v) { this.statusLatencyMin = v; }
    public Duration getStatusLatencyMax() { return statusLatencyMax; }
    public void setStatusLatencyMax(Duration v) { this.statusLatencyMax = v; }
    public Duration getHang() { return hang; }
    public void setHang(Duration v) { this.hang = v; }
    public Duration getCallbackDelayMin() { return callbackDelayMin; }
    public void setCallbackDelayMin(Duration v) { this.callbackDelayMin = v; }
    public Duration getCallbackDelayMax() { return callbackDelayMax; }
    public void setCallbackDelayMax(Duration v) { this.callbackDelayMax = v; }
    public List<String> getCallbackUrls() { return callbackUrls; }
    public void setCallbackUrls(List<String> v) { this.callbackUrls = v; }

    public BankProperties copy() {
        BankProperties c = new BankProperties();
        c.acceptRate = acceptRate; c.rejectRate = rejectRate; c.noResponseRate = noResponseRate;
        c.noResponseProcessedRate = noResponseProcessedRate; c.callbackFailRate = callbackFailRate;
        c.duplicateCallbackRate = duplicateCallbackRate; c.submitLatencyMin = submitLatencyMin;
        c.submitLatencyMax = submitLatencyMax; c.statusLatencyMin = statusLatencyMin; c.statusLatencyMax = statusLatencyMax;
        c.hang = hang; c.callbackDelayMin = callbackDelayMin; c.callbackDelayMax = callbackDelayMax;
        c.callbackUrls = callbackUrls;
        return c;
    }
}
