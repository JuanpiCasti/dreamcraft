package dev.dreamcraft.harness;

import java.util.function.Supplier;

/**
 * A single test scenario. ASSERTED scenarios must satisfy their expectation to
 * PASS; PROBE scenarios only RECORD observed behaviour (gap discovery).
 *
 * @param name     stable id used in reports
 * @param kind     ASSERTED or PROBE
 * @param category grouping for the report ("arranque", "comandos", …)
 * @param action   runs the scenario and returns its outcome
 */
public record Scenario(String name, Kind kind, String category,
                       Supplier<Outcome> action) {

    public enum Kind { ASSERTED, PROBE }

    static Scenario asserted(String name, String category, Supplier<Outcome> action) {
        return new Scenario(name, Kind.ASSERTED, category, action);
    }

    static Scenario probe(String name, String category, Supplier<Outcome> action) {
        return new Scenario(name, Kind.PROBE, category, action);
    }

    /** Convenience for dispatch-based checks. */
    public interface Dispatcher {
        /** Dispatches a command line as the given persona, returns captured output. */
        String dispatchAs(Persona persona, String commandLine);
    }
}
