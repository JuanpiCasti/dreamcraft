package dev.dreamcraft.protection.integration.chunky;

/**
 * Integration adapter contract for Chunky.
 *
 * <p>Chunky handles world pre-generation. DreamCraft uses it to trigger
 * pre-generation of the area around a newly established Ward or City.
 */
public interface ChunkyAdapter {

    /**
     * Schedules a circular pre-generation task centred on the given world coordinates
     * with the specified radius (in blocks). The task is enqueued asynchronously;
     * this method returns immediately.
     *
     * @param worldName target world
     * @param centerX   centre X block coordinate
     * @param centerZ   centre Z block coordinate
     * @param radius    radius in blocks
     * @param taskLabel a label used to identify this Chunky task (e.g. "ward-<id>")
     */
    void pregenerateRadius(String worldName, int centerX, int centerZ, int radius, String taskLabel);

    /**
     * Cancels a previously scheduled pre-generation task by its label.
     */
    void cancelTask(String taskLabel);

    boolean isAvailable();
}
