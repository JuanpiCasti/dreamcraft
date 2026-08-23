package dev.dreamcraft.harness;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds fake {@link CommandSender}s backed by a {@link Persona}.
 *
 * <p>Implementation: a {@link Proxy} over the CommandSender interface that
 * intercepts permission checks, name, op state and message sending; every
 * other call delegates to the real console sender (or no-ops for Adventure
 * default methods). This gives full control of the permission layer without
 * mocking the whole Bukkit hierarchy.
 */
final class CapturingSenderFactory {

    private final CommandSender console = Bukkit.getConsoleSender();

    /**
     * Builds a fresh capturing sender for the persona. Deliberately NOT
     * cached: reusing buffers across scenarios made outputs bleed into each
     * other (a probe would report the previous scenario's lines too).
     */
    CapturedSender forPersona(Persona persona) {
        return build(persona);
    }

    private CapturedSender build(Persona persona) {
        List<String> captured = new ArrayList<>();
        CommandSender sender = (CommandSender) Proxy.newProxyInstance(
                CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class},
                handlerFor(persona, captured));
        return new CapturedSender(sender, captured);
    }

    /** Sender + everything it was sent during the run. */
    record CapturedSender(CommandSender sender, List<String> lines) {
        String output() {
            return String.join("\n", lines);
        }
    }

    private InvocationHandler handlerFor(Persona persona, List<String> captured) {
        return (Object proxy, Method method, Object[] args) -> {
            switch (method.getName()) {
                case "getName" -> {
                    return persona.name();
                }
                case "isOp" -> {
                    return persona.op();
                }
                case "setOp" -> {
                    return null; // personas are immutable during a run
                }
                case "hasPermission" -> {
                    if (args != null && args.length == 1) {
                        if (args[0] instanceof Permission perm) return persona.grants(perm);
                        return persona.grants(String.valueOf(args[0]));
                    }
                    return false;
                }
                case "isPermissionSet" -> {
                    // Deterministic: treat exactly the granted set as "set"
                    if (args != null && args.length == 1) {
                        if (args[0] instanceof Permission perm) return persona.grants(perm);
                        return persona.grants(String.valueOf(args[0]));
                    }
                    return false;
                }
                case "sendMessage" -> {
                    capture(captured, args);
                    return null;
                }
                case "toString" -> {
                    return "Persona[" + persona.name() + "]";
                }
                default -> {
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    try {
                        return method.invoke(console, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                }
            }
        };
    }

    /** Captures every message-shaped argument (Components, Strings, arrays). */
    private static void capture(List<String> captured, Object[] args) {
        if (args == null) return;
        for (Object arg : args) {
            if (arg instanceof Component || arg instanceof String
                    || arg instanceof String[] || arg instanceof Iterable<?>) {
                appendMessage(captured, arg);
            }
        }
    }

    private static void appendMessage(List<String> captured, Object message) {
        String text = toText(message);
        if (!text.isEmpty()) captured.add(text);
    }

    private static String toText(Object message) {
        if (message == null) return "";
        if (message instanceof Component component) {
            return PlainTextComponentSerializer.plainText().serialize(component);
        }
        if (message instanceof String s) {
            return ChatColor.stripColor(s);
        }
        if (message instanceof String[] arr) {
            StringBuilder sb = new StringBuilder();
            for (String s : arr) sb.append(toText(s)).append('\n');
            return sb.toString();
        }
        if (message instanceof Iterable<?> it) {
            StringBuilder sb = new StringBuilder();
            for (Object o : it) sb.append(toText(o)).append('\n');
            return sb.toString();
        }
        return String.valueOf(message);
    }
}
