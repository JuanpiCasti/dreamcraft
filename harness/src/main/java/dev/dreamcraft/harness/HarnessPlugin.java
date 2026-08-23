package dev.dreamcraft.harness;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

/**
 * DreamCraftTestHarness — runs a scenario suite against the REAL plugins of
 * this server (black-box, only the Bukkit surface).
 *
 * <p>Trigger points:
 * <ul>
 *   <li>Autorun shortly after boot (default; disable with env HARNESS_AUTORUN=0)</li>
 *   <li>{@code /dctest run} from console or RCON</li>
 * </ul>
 *
 * <p>Reports land in {@code <server-root>/dreamcraft-test/results.json} plus a
 * human-readable {@code report.txt}, readable from the host volume.
 */
public final class HarnessPlugin extends JavaPlugin {

    private ScenarioRunner runner;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        runner = new ScenarioRunner(this);

        PluginCommand cmd = getCommand("dctest");
        if (cmd != null) {
            cmd.setExecutor((sender, command, label, args) -> {
                runSuite(sender.getName());
                return true;
            });
        }

        boolean autorun = !System.getenv().getOrDefault("HARNESS_AUTORUN", "1").equals("0")
                && getConfig().getBoolean("autorun", true);
        long delay = getConfig().getLong("delay-ticks", 300L);
        if (autorun) {
            Bukkit.getScheduler().runTaskLater(this,
                    () -> runSuite("autorun"), Math.max(100L, delay));
        }
        getLogger().info("Test harness listo. autorun=" + autorun + " delayTicks=" + delay);
    }

    /** Runs the whole suite and writes reports; safe to call repeatedly. */
    public void runSuite(String trigger) {
        getLogger().info("Ejecutando suite (disparo: " + trigger + ")...");
        long start = System.currentTimeMillis();
        List<ScenarioResult> results = runner.run();
        ResultsWriter.write(resultsFolder(), results,
                Bukkit.getBukkitVersion(), trigger, start);
        long pass = results.stream().filter(r -> "PASS".equals(r.status())).count();
        long fail = results.stream().filter(r -> "FAIL".equals(r.status())).count();
        long probe = results.stream().filter(r -> "PROBE".equals(r.status())).count();
        getLogger().info("DCTEST RESULT: total=" + results.size()
                + " pass=" + pass + " fail=" + fail + " probe=" + probe
                + " en " + (System.currentTimeMillis() - start) + "ms");
        if (fail > 0) {
            results.stream().filter(r -> "FAIL".equals(r.status()))
                    .forEach(r -> getLogger().warning("DCTEST FAIL [" + r.scenario() + "] "
                            + r.expected() + " | observado: " + r.actual()));
        }
    }

    private File resultsFolder() {
        // <plugins>/../dreamcraft-test → host ./data/dreamcraft-test
        File dir = new File(getDataFolder().getParentFile().getParentFile(), "dreamcraft-test");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("No se pudo crear " + dir);
        }
        return dir;
    }
}
