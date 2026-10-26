package com.mpsesa.interview.core;

/** All amounts are integers in santim. 1 ETB = 100 santim is a fixed rule, not configuration. */
public final class Money {
    public static final long SANTIM_PER_ETB = 100;

    public static final long MIN_AMOUNT = 1;
    public static final long MAX_AMOUNT = 5_000_000;

    public static final long SEED_WALLET_BALANCE = 1_000_000;
    public static final long SEED_MERCHANT_BALANCE = 50_000_000;

    private Money() {
    }

    public static long etb(long etb) {
        return Math.multiplyExact(etb, SANTIM_PER_ETB);
    }

    public static boolean isValidAmount(long santim) {
        return santim >= MIN_AMOUNT && santim <= MAX_AMOUNT;
    }

    public static String format(long santim) {
        long abs = Math.abs(santim);
        return (santim < 0 ? "-" : "") + abs / SANTIM_PER_ETB + "." + String.format("%02d", abs % SANTIM_PER_ETB) + " ETB";
    }
}
