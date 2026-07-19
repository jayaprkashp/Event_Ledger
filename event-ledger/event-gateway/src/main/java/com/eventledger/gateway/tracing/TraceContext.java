package com.eventledger.gateway.tracing;

/**
 * Simple ThreadLocal accessor so service/client classes can read the current
 * trace ID without threading it through every method signature.
 */
public class TraceContext {
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    public static void set(String traceId) { CURRENT.set(traceId); }
    public static String current() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
}
