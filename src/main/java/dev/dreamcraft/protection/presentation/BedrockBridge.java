package dev.dreamcraft.protection.presentation;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Bridge to detect Minecraft Bedrock players via GeyserApi or FloodgateApi.
 * Uses reflection so the plugin does not take a compile-time dependency.
 */
public final class BedrockBridge {

    private static Method geyserApiMethod;
    private static Method geyserIsBedrockMethod;
    private static Method floodgateGetMethod;
    private static Method floodgateIsBedrockMethod;
    private static boolean initialized = false;

    private BedrockBridge() {}

    public static boolean isBedrockPlayer(UUID uuid) {
        if (uuid == null) return false;
        ensureInit();
        if (geyserApiMethod != null && geyserIsBedrockMethod != null) {
            try {
                Object api = geyserApiMethod.invoke(null);
                if (api != null) {
                    Object res = geyserIsBedrockMethod.invoke(api, uuid);
                    if (Boolean.TRUE.equals(res)) return true;
                }
            } catch (Throwable ignored) {}
        }
        if (floodgateGetMethod != null && floodgateIsBedrockMethod != null) {
            try {
                Object api = floodgateGetMethod.invoke(null);
                if (api != null) {
                    Object res = floodgateIsBedrockMethod.invoke(api, uuid);
                    if (Boolean.TRUE.equals(res)) return true;
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static synchronized void ensureInit() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> clazz = Class.forName("org.geysermc.geyser.api.GeyserApi");
            geyserApiMethod = clazz.getMethod("api");
            geyserIsBedrockMethod = clazz.getMethod("isBedrockPlayer", UUID.class);
        } catch (Throwable ignored) {}
        try {
            Class<?> clazz = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            floodgateGetMethod = clazz.getMethod("getInstance");
            floodgateIsBedrockMethod = clazz.getMethod("isFloodgatePlayer", UUID.class);
        } catch (Throwable ignored) {}
    }
}
