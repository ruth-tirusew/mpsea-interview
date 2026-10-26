package com.mpsesa.interview.bank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpsesa.interview.core.WalletProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@Component
public class BankClient {
    private static final Logger log = LoggerFactory.getLogger(BankClient.class);

    public enum SubmitOutcome { ACCEPTED, REJECTED, UNKNOWN }

    public enum BankStatus { IN_PROGRESS, SUCCEEDED, FAILED, REJECTED, NOT_FOUND, UNKNOWN }

    public record SubmitResult(SubmitOutcome outcome, String bankRef) {
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final ObjectMapper json;
    private final String baseUrl;
    private final Duration timeout;

    public BankClient(ObjectMapper json, WalletProperties props) {
        this.json = json;
        this.baseUrl = props.bank().baseUrl();
        this.timeout = props.bank().submitTimeout();
    }

    /** Idempotent at the bank on payout_id, so resubmitting after UNKNOWN can never pay twice. */
    public SubmitResult submit(String payoutId, String bankAccount, long amount) {
        try {
            String body = json.writeValueAsString(Map.of("payout_id", payoutId, "bank_account", bankAccount, "amount", amount));
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/bank/payouts")).timeout(timeout)
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                return new SubmitResult(SubmitOutcome.UNKNOWN, null);
            }
            JsonNode n = json.readTree(res.body());
            return switch (n.path("result").asText()) {
                case "ACCEPTED" -> new SubmitResult(SubmitOutcome.ACCEPTED, n.path("bank_ref").asText(null));
                case "REJECTED" -> new SubmitResult(SubmitOutcome.REJECTED, null);
                default -> new SubmitResult(SubmitOutcome.UNKNOWN, null);
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new SubmitResult(SubmitOutcome.UNKNOWN, null);
        } catch (Exception e) {
            log.debug("bank submit {} unknown: {}", payoutId, e.getClass().getSimpleName());
            return new SubmitResult(SubmitOutcome.UNKNOWN, null);
        }
    }

    public BankStatus status(String payoutId) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/bank/payouts/" + payoutId)).timeout(timeout).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                return BankStatus.UNKNOWN;
            }
            return BankStatus.valueOf(json.readTree(res.body()).path("status").asText());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return BankStatus.UNKNOWN;
        } catch (Exception e) {
            log.debug("bank status {} unknown: {}", payoutId, e.getClass().getSimpleName());
            return BankStatus.UNKNOWN;
        }
    }
}
