package com.mpsesa.fakebank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Section IV, as unkind as specified:
 *  - POST /bank/payouts takes 2-6 s; ~80% accepted, ~8% rejected, ~12% no response (hangs 60 s then 504;
 *    half of those were processed, with a callback to follow, half never received).
 *  - idempotent on payout_id; same id with another amount is rejected.
 *  - the outcome is decided on arrival, so the callback (1-10 s later) can beat the submit response.
 *  - ~30% of callbacks are sent 2-3 times concurrently with one event_id; no 2xx within 2 s is retried every 3 s, 5 times.
 *  - GET /bank/payouts/{id} takes 1-3 s.
 */
@RestController
@RequestMapping("/bank")
public class BankController {
    private static final Logger log = LoggerFactory.getLogger(BankController.class);

    private record Payout(String id, long amount, String bankRef, String outcome, long finalAtMillis) {
        /** outcome: SUCCEEDED | FAILED | REJECTED. Accepted payouts become visible as final at finalAt. */
        String statusAt(long now) {
            return outcome.equals("REJECTED") || now >= finalAtMillis ? outcome : "IN_PROGRESS";
        }
    }

    private final Map<String, Payout> payouts = new ConcurrentHashMap<>();
    private final BankProperties defaults;
    private volatile BankProperties cfg;
    private final ObjectMapper json;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(64);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    private final String callbackSecret;

    public BankController(BankProperties props, ObjectMapper json,
                          @org.springframework.beans.factory.annotation.Value("${bank.callback-secret:}") String callbackSecret) {
        this.callbackSecret = callbackSecret;
        this.defaults = props.copy();
        this.cfg = props;
        this.json = json;
    }

    @PostMapping("/payouts")
    public ResponseEntity<Map<String, Object>> submit(@RequestBody JsonNode req) throws InterruptedException {
        BankProperties c = cfg;
        String id = req.path("payout_id").asText();
        long amount = req.path("amount").asLong();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        Payout known = payouts.get(id);
        if (known != null) {
            pause(c.getSubmitLatencyMin(), c.getSubmitLatencyMax());
            return answer(known, amount);
        }

        double roll = rnd.nextDouble();
        if (roll < c.getNoResponseRate()) {
            // Hang. Half the time the bank did process it (and a callback follows); half it never saw it.
            if (rnd.nextDouble() < c.getNoResponseProcessedRate()) {
                accept(c, id, amount);
            }
            Thread.sleep(c.getHang().toMillis());
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).build();
        }
        Payout p;
        if (roll < c.getNoResponseRate() + c.getRejectRate()) {
            p = payouts.computeIfAbsent(id, k -> new Payout(id, amount, null, "REJECTED", 0));
        } else {
            p = accept(c, id, amount);
        }
        pause(c.getSubmitLatencyMin(), c.getSubmitLatencyMax());
        return answer(p, amount);
    }

    @GetMapping("/payouts/{id}")
    public Map<String, Object> status(@PathVariable String id) throws InterruptedException {
        BankProperties c = cfg;
        pause(c.getStatusLatencyMin(), c.getStatusLatencyMax());
        Payout p = payouts.get(id);
        return Map.of("status", p == null ? "NOT_FOUND" : p.statusAt(System.currentTimeMillis()));
    }

    /** B3: what the bank has actually paid, i.e. SUCCEEDED payouts that have reached their final time. */
    @GetMapping("/admin/total-paid")
    public Map<String, Object> totalPaid() {
        long now = System.currentTimeMillis();
        long total = 0;
        long count = 0;
        for (Payout p : payouts.values()) {
            if (p.statusAt(now).equals("SUCCEEDED")) {
                total += p.amount();
                count++;
            }
        }
        return Map.of("total_paid", total, "paid_count", count, "known_payouts", payouts.size());
    }

    @PostMapping("/admin/config")
    public BankProperties configure(@RequestBody Map<String, Object> changes) {
        BankProperties c = cfg;
        changes.forEach((k, v) -> apply(c, k, v));
        return c;
    }

    /** Forget every payout and restore the configured defaults. */
    @PostMapping("/admin/reset")
    public Map<String, Object> reset() {
        payouts.clear();
        cfg = defaults.copy();
        return Map.of("ok", true);
    }

    // --- internals ---

    private Payout accept(BankProperties c, String id, long amount) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        long delay = between(c.getCallbackDelayMin(), c.getCallbackDelayMax());
        boolean fails = rnd.nextDouble() < c.getCallbackFailRate();
        Payout fresh = new Payout(id, amount, "BR" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(),
                fails ? "FAILED" : "SUCCEEDED", System.currentTimeMillis() + delay);
        Payout p = payouts.putIfAbsent(id, fresh);
        if (p != null) {
            return p;     // lost a race with a concurrent submit of the same id: it owns the callback
        }
        scheduler.schedule(() -> sendCallbacks(c, fresh), delay, TimeUnit.MILLISECONDS);
        return fresh;
    }

    private ResponseEntity<Map<String, Object>> answer(Payout p, long requestedAmount) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (p.amount() != requestedAmount || p.outcome().equals("REJECTED")) {
            body.put("result", "REJECTED");
            body.put("reason", p.amount() != requestedAmount ? "AMOUNT_MISMATCH" : "BENEFICIARY_ACCOUNT_INVALID");
        } else {
            body.put("result", "ACCEPTED");
            body.put("bank_ref", p.bankRef());
        }
        return ResponseEntity.ok(body);
    }

    private void sendCallbacks(BankProperties c, Payout p) {
        String eventId = UUID.randomUUID().toString();
        int copies = ThreadLocalRandom.current().nextDouble() < c.getDuplicateCallbackRate()
                ? 2 + ThreadLocalRandom.current().nextInt(2) : 1;
        for (int i = 0; i < copies; i++) {
            scheduler.execute(() -> deliver(c.getCallbackUrls(), eventId, p));   // concurrent copies
        }
    }

    /** No 2xx within 2 s: retry every 3 s, up to 5 times. */
    private void deliver(List<String> urls, String eventId, Payout p) {
        try {
            String body = json.writeValueAsString(Map.of("event_id", eventId, "payout_id", p.id(),
                    "bank_ref", p.bankRef(), "status", p.outcome()));
            for (int attempt = 0; attempt <= 5; attempt++) {
                String url = urls.get(ThreadLocalRandom.current().nextInt(urls.size()));
                try {
                    HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(2))
                            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
                    sign(req, body);   // fresh timestamp per attempt, so retries stay inside the receiver's window
                    HttpResponse<Void> r = http.send(req.build(), HttpResponse.BodyHandlers.discarding());
                    if (r.statusCode() / 100 == 2) {
                        return;
                    }
                } catch (java.io.IOException e) {
                    log.debug("callback {} attempt {} failed: {}", eventId, attempt, e.toString());
                }
                Thread.sleep(3000);
            }
            log.warn("callback {} for {} gave up", eventId, p.id());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("callback delivery error", e);
        }
    }

    /** HMAC-SHA256(secret, timestamp + "." + body), the contract the wallet service verifies. */
    private void sign(HttpRequest.Builder req, String body) throws Exception {
        if (callbackSecret.isBlank()) {
            return;
        }
        String ts = String.valueOf(java.time.Instant.now().getEpochSecond());
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(callbackSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((ts + ".").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        req.header("X-Bank-Timestamp", ts)
                .header("X-Bank-Signature", java.util.HexFormat.of().formatHex(mac.doFinal(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }

    private static void pause(Duration min, Duration max) throws InterruptedException {
        Thread.sleep(between(min, max));
    }

    private static long between(Duration min, Duration max) {
        long lo = min.toMillis();
        long hi = max.toMillis();
        return hi <= lo ? lo : ThreadLocalRandom.current().nextLong(lo, hi + 1);
    }

    private static void apply(BankProperties c, String key, Object v) {
        String s = String.valueOf(v);
        switch (key) {
            case "acceptRate" -> c.setAcceptRate(Double.parseDouble(s));
            case "rejectRate" -> c.setRejectRate(Double.parseDouble(s));
            case "noResponseRate" -> c.setNoResponseRate(Double.parseDouble(s));
            case "noResponseProcessedRate" -> c.setNoResponseProcessedRate(Double.parseDouble(s));
            case "callbackFailRate" -> c.setCallbackFailRate(Double.parseDouble(s));
            case "duplicateCallbackRate" -> c.setDuplicateCallbackRate(Double.parseDouble(s));
            case "hangSeconds" -> c.setHang(Duration.ofSeconds(Long.parseLong(s)));
            default -> throw new IllegalArgumentException("unknown setting " + key);
        }
    }
}
