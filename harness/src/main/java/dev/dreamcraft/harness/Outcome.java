package dev.dreamcraft.harness;

/**
 * Outcome of one scenario execution.
 *
 * @param status   PASS | FAIL | PROBE
 * @param expected what the scenario required (null for probes)
 * @param actual   what really happened (captured response / state summary)
 * @param hint     likely cause / next debugging step on FAIL (null otherwise)
 */
public record Outcome(String status, String expected, String actual, String hint) {

    public static Outcome pass() {
        return new Outcome("PASS", null, null, null);
    }

    public static Outcome fail(String expected, String actual, String hint) {
        return new Outcome("FAIL", expected, actual, hint);
    }

    public static Outcome probe(String actual) {
        return new Outcome("PROBE", null, actual, null);
    }
}
