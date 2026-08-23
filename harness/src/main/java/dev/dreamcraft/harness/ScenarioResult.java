package dev.dreamcraft.harness;

/**
 * Flat result row written to the report.
 *
 * @param status   PASS | FAIL | PROBE
 */
record ScenarioResult(String scenario, String status, String category,
                      String expected, String actual, String hint) {
}
