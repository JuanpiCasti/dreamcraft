package dev.dreamcraft.harness;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;

/** Writes results.json (machine) + report.txt (human) without extra dependencies. */
final class ResultsWriter {

    private ResultsWriter() {}

    static void write(File folder, List<ScenarioResult> results,
                      String serverVersion, String trigger, long startedAtMillis) {
        String startedAt = Instant.ofEpochMilli(startedAtMillis).toString();
        long pass = results.stream().filter(r -> "PASS".equals(r.status())).count();
        long fail = results.stream().filter(r -> "FAIL".equals(r.status())).count();
        long probe = results.stream().filter(r -> "PROBE".equals(r.status())).count();

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"startedAt\": \"").append(escape(startedAt)).append("\",\n");
        json.append("  \"finishedAt\": \"").append(escape(Instant.now().toString())).append("\",\n");
        json.append("  \"trigger\": \"").append(escape(trigger)).append("\",\n");
        json.append("  \"server\": \"").append(escape(serverVersion)).append("\",\n");
        json.append("  \"summary\": {\"total\": ").append(results.size())
                .append(", \"pass\": ").append(pass)
                .append(", \"fail\": ").append(fail)
                .append(", \"probe\": ").append(probe).append("},\n");
        json.append("  \"scenarios\": [\n");
        for (int i = 0; i < results.size(); i++) {
            ScenarioResult r = results.get(i);
            json.append("    {\"name\": \"").append(escape(r.scenario()))
                    .append("\", \"kind\": \"").append("PASS".equals(r.status()) || "FAIL".equals(r.status())
                            ? "ASSERTED" : "PROBE")
                    .append("\", \"category\": \"").append(escape(r.category()))
                    .append("\", \"status\": \"").append(r.status()).append('"');
            if (r.expected() != null) json.append(", \"expected\": \"").append(escape(r.expected())).append('"');
            if (r.actual() != null) json.append(", \"actual\": \"").append(escape(r.actual())).append('"');
            if (r.hint() != null) json.append(", \"hint\": \"").append(escape(r.hint())).append('"');
            json.append("}").append(i < results.size() - 1 ? "," : "").append('\n');
        }
        json.append("  ]\n}\n");

        try {
            Files.writeString(new File(folder, "results.json").toPath(),
                    json.toString(), StandardCharsets.UTF_8);
            Files.writeString(new File(folder, "report.txt").toPath(),
                    humanReport(results, startedAt, trigger, pass, fail, probe),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo escribir el reporte en " + folder, e);
        }
    }

    private static String humanReport(List<ScenarioResult> results, String startedAt,
                                      String trigger, long pass, long fail, long probe) {
        StringBuilder sb = new StringBuilder();
        sb.append("== DreamCraftTestHarness ==\n");
        sb.append("inicio: ").append(startedAt).append("   disparo: ").append(trigger).append('\n');
        sb.append(String.format("resultado: %d PASS / %d FAIL / %d PROBE de %d%n",
                pass, fail, probe, results.size()));
        sb.append('\n');
        for (ScenarioResult r : results) {
            sb.append(String.format("[%s] %s%n", r.status(), r.scenario()));
            if (r.expected() != null) sb.append("    esperado: ").append(r.expected()).append('\n');
            if (r.actual() != null && !r.actual().isBlank()) {
                String trimmed = r.actual();
                if (trimmed.length() > 600) trimmed = trimmed.substring(0, 600) + "…";
                for (String line : trimmed.split("\n")) sb.append("    | ").append(line).append('\n');
            }
            if (r.hint() != null) sb.append("    pista: ").append(r.hint()).append('\n');
        }
        sb.append('\n');
        List<ScenarioResult> probes = results.stream()
                .filter(r -> "PROBE".equals(r.status())).toList();
        if (!probes.isEmpty()) {
            sb.append("== GAPS CANDIDATOS (comportamiento observado, sin contrato definido) ==\n");
            for (ScenarioResult p : probes) {
                sb.append("- ").append(p.scenario()).append(": ");
                String a = p.actual() == null ? "" : p.actual().replace("\n", " ⏎ ");
                sb.append(a.isBlank() ? "(sin salida)" : a).append('\n');
            }
        }
        return sb.toString();
    }

    /** Minimal JSON string escaping (no external deps). */
    private static String escape(String raw) {
        StringBuilder out = new StringBuilder(raw.length() + 8);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(' ');
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }
}
