package com.mpsesa.e2e;

import java.util.Map;

/** Control plane of the fake bank (rates, reconciliation total). */
final class BankAdmin {
    private BankAdmin() {
    }

    static void reset() {
        Api.post(Api.BANK, "/bank/admin/reset", null, null);
    }

    static void configure(Map<String, Object> changes) {
        Api.Resp r = Api.post(Api.BANK, "/bank/admin/config", null, changes);
        if (r.status() != 200) {
            throw new IllegalStateException("bank config failed " + r.status());
        }
    }

    static long totalPaid() {
        return Api.get(Api.BANK, "/bank/admin/total-paid").body().get("total_paid").asLong();
    }
}
