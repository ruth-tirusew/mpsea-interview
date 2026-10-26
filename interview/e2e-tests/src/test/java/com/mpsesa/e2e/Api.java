package com.mpsesa.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

/** Tiny HTTP helper. Alternates across both instances so every scenario hits both ports. */
final class Api {
    static final ObjectMapper JSON = new ObjectMapper();
    static final List<String> INSTANCES = List.of(System.getProperty("wallet.urls").split(","));
    static final String BANK = System.getProperty("bank.url");
    static final String USER_KEY = System.getProperty("wallet.api-key");
    static final String ADMIN_KEY = System.getProperty("wallet.admin-api-key");
    static final String CALLBACK_SECRET = System.getProperty("bank.callback-secret");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final AtomicInteger NEXT = new AtomicInteger();

    record Resp(int status, JsonNode body, long millis) {
        String error() {
            return body == null ? null : body.path("error").asText(null);
        }
    }

    static String instance(int i) {
        return INSTANCES.get(Math.floorMod(i, INSTANCES.size()));
    }

    static String anyInstance() {
        return instance(NEXT.getAndIncrement());
    }

    static String key() {
        return UUID.randomUUID().toString();
    }

    static Resp post(String base, String path, String idemKey, Object body) {
        return send(base, "POST", path, idemKey, body);
    }

    static Resp get(String base, String path) {
        return send(base, "GET", path, null, null);
    }

    static Resp put(String base, String path, Object body) {
        return send(base, "PUT", path, null, body);
    }

    private static Resp send(String base, String method, String path, String idemKey, Object body) {
        try {
            Map<String, String> headers = new java.util.LinkedHashMap<>();
            if (idemKey != null) {
                headers.put("Idempotency-Key", idemKey);
            }
            // admin-only surface: internal endpoints and wallet freeze; everything else is a regular user call
            boolean admin = path.startsWith("/mpesa/internal") || path.endsWith("/status");
            headers.put("X-API-Key", admin ? ADMIN_KEY : USER_KEY);
            return raw(base, method, path, headers, body == null ? null : JSON.writeValueAsString(body));
        } catch (Exception e) {
            throw new IllegalStateException(method + " " + base + path, e);
        }
    }

    /** Request with a caller-chosen key (or none), to exercise authentication itself. */
    static Resp withKey(String base, String method, String path, String apiKey, Object body) {
        try {
            Map<String, String> headers = new java.util.LinkedHashMap<>();
            if (apiKey != null) {
                headers.put("X-API-Key", apiKey);
            }
            return raw(base, method, path, headers, body == null ? null : JSON.writeValueAsString(body));
        } catch (Exception e) {
            throw new IllegalStateException(method + " " + base + path, e);
        }
    }

    /** A callback as the bank would send it: HMAC-SHA256(secret, timestamp + "." + body). */
    static Resp callback(String base, Map<String, Object> body) {
        return signedCallback(base, body, CALLBACK_SECRET, System.currentTimeMillis() / 1000);
    }

    static Resp signedCallback(String base, Map<String, Object> body, String secret, long timestampSeconds) {
        try {
            String json = JSON.writeValueAsString(body);
            String ts = String.valueOf(timestampSeconds);
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((ts + ".").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String sig = java.util.HexFormat.of().formatHex(mac.doFinal(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return raw(base, "POST", "/mpesa/v1/callbacks/bank",
                    Map.of("X-Bank-Timestamp", ts, "X-Bank-Signature", sig), json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static Resp rawCallback(String base, Map<String, String> headers, String json) {
        return raw(base, "POST", "/mpesa/v1/callbacks/bank", headers, json);
    }

    private static Resp raw(String base, String method, String path, Map<String, String> headers, String json) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(130))
                    .header("Content-Type", "application/json");
            headers.forEach(b::header);
            b.method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
            long t0 = System.nanoTime();
            HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
            long ms = (System.nanoTime() - t0) / 1_000_000;
            JsonNode node = r.body() == null || r.body().isBlank() ? null : JSON.readTree(r.body());
            return new Resp(r.statusCode(), node, ms);
        } catch (Exception e) {
            throw new IllegalStateException(method + " " + base + path, e);
        }
    }

    /** Runs n calls at the same moment (start gate), each on its own thread. */
    static List<Resp> concurrently(int n, IntFunction<Resp> call) {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch gate = new CountDownLatch(1);
        try {
            List<Future<Resp>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int idx = i;
                futures.add(pool.submit(() -> {
                    gate.await();
                    return call.apply(idx);
                }));
            }
            gate.countDown();
            List<Resp> out = new ArrayList<>();
            for (Future<Resp> f : futures) {
                out.add(f.get());
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            pool.shutdownNow();
        }
    }

    /** Same, but at most `parallelism` in flight at once (for latency measurements). */
    static List<Resp> withParallelism(int total, int parallelism, IntFunction<Resp> call) {
        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        try {
            List<Future<Resp>> futures = new ArrayList<>();
            for (int i = 0; i < total; i++) {
                int idx = i;
                futures.add(pool.submit(() -> call.apply(idx)));
            }
            List<Resp> out = new ArrayList<>();
            for (Future<Resp> f : futures) {
                out.add(f.get());
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            pool.shutdownNow();
        }
    }

    static long p95(List<Resp> rs) {
        List<Long> ms = rs.stream().map(Resp::millis).sorted().toList();
        return ms.get((int) Math.ceil(ms.size() * 0.95) - 1);
    }

    static long count(List<Resp> rs, int status) {
        return rs.stream().filter(r -> r.status() == status).count();
    }

    static long fiveXx(List<Resp> rs) {
        return rs.stream().filter(r -> r.status() >= 500).count();
    }

    static void resetAll() {
        Resp r = post(INSTANCES.get(0), "/mpesa/internal/reset", null, null);
        if (r.status() != 200) {
            throw new IllegalStateException("reset failed: " + r.status());
        }
        BankAdmin.reset();
    }

    // ---- domain helpers ----

    static Resp transfer(String base, String from, String to, long amount) {
        return post(base, "/mpesa/v1/p2p/transfers", key(),
                Map.of("from_wallet", from, "to_wallet", to, "amount", amount, "reference", "e2e"));
    }

    static JsonNode wallet(String id) {
        return get(INSTANCES.get(0), "/mpesa/v1/wallets/" + id).body();
    }

    static long balance(String id) {
        return wallet(id).get("balance").asLong();
    }

    static String wid(int n) {
        return String.format("W%03d", n);
    }

    /** Sum of every seeded wallet's balance, read through alternating instances. */
    static long sumAllBalances() {
        long sum = balance("M001");
        for (int i = 1; i <= 105; i++) {
            sum += balance(wid(i));
        }
        return sum;
    }

    static final long SEED_TOTAL = 105L * 1_000_000 + 50_000_000;
}
