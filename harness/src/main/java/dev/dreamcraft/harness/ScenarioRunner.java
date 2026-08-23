package dev.dreamcraft.harness;

import java.util.ArrayList;
import java.util.List;

/** Executes every scenario, isolating failures so one crash never aborts the run. */
final class ScenarioRunner {

    private final CapturingSenderFactory senders = new CapturingSenderFactory();
    private final HarnessPlugin plugin;

    ScenarioRunner(HarnessPlugin plugin) {
        this.plugin = plugin;
    }

    List<ScenarioResult> run() {
        List<Scenario> catalog = Scenarios.catalog(plugin, senders);
        List<ScenarioResult> results = new ArrayList<>(catalog.size());
        for (Scenario scenario : catalog) {
            results.add(execute(scenario));
        }
        return results;
    }

    private ScenarioResult execute(Scenario scenario) {
        try {
            Outcome outcome = scenario.action().get();
            if ("PROBE".equals(outcome.status())) {
                return new ScenarioResult(scenario.name(), "PROBE", scenario.category(),
                        null, outcome.actual(), null);
            }
            return new ScenarioResult(scenario.name(), outcome.status(), scenario.category(),
                    outcome.expected(), outcome.actual(), outcome.hint());
        } catch (Throwable t) {
            return new ScenarioResult(scenario.name(), "FAIL", scenario.category(),
                    "ejecutar sin excepciones",
                    t.getClass().getSimpleName() + ": " + t.getMessage(),
                    "Escenario lanzó una excepción — probable bug del plugin bajo test o del harness");
        }
    }
}
