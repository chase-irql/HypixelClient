package com.teeko.strata.client.combat;

import java.util.ArrayDeque;

public final class CpsTracker {

    private static final ArrayDeque<Long> ATTACK_TIMES = new ArrayDeque<>();

    private CpsTracker() {}

    public static void recordAttack() {
        long now = System.currentTimeMillis();
        ATTACK_TIMES.addLast(now);
        while (!ATTACK_TIMES.isEmpty() && now - ATTACK_TIMES.peekFirst() > 1000) {
            ATTACK_TIMES.pollFirst();
        }
    }

    public static int getCps() {
        long now = System.currentTimeMillis();
        ATTACK_TIMES.removeIf(time -> now - time > 1000);
        return ATTACK_TIMES.size();
    }
}
