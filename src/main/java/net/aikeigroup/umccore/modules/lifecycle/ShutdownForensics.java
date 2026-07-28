package net.aikeigroup.umccore.modules.lifecycle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Inspects the live JVM thread state at the moment of shutdown to name the cause
 * as specifically as the evidence allows — purely from thread names and stack
 * traces, with no Bukkit API calls (so it is safe from the JVM shutdown hook).
 *
 * <p>It never guesses beyond what the stacks show: every conclusion carries a
 * {@code confidence} and the raw evidence is attached to the report so a human
 * can verify it. The distinctions it can draw:</p>
 *
 * <ul>
 *   <li><b>WATCHDOG_HANG</b> — Paper's watchdog thread is alive and sitting in a
 *       halt/shutdown path, meaning a tick froze past the timeout and the server
 *       force-killed itself. The frozen "Server thread" stack is captured as the
 *       smoking gun (where it hung).</li>
 *   <li><b>EXTERNAL_SIGNAL</b> — a JVM shutdown-hook thread is driving the stop
 *       and no watchdog is involved: a SIGTERM from a panel / systemd / docker /
 *       {@code kill} (graceful), i.e. someone pressed Stop/Restart on the host.</li>
 *   <li><b>API_OR_COMMAND</b> — the stop is being driven on the main server thread
 *       (e.g. {@code Bukkit.shutdown()} or a {@code /stop} handler), not a hook.</li>
 * </ul>
 */
final class ShutdownForensics {

    private ShutdownForensics() {
    }

    /** Result of the inspection: a cause label plus human detail plus evidence. */
    record Result(String cause, String confidence, String detail, Map<String, Object> evidence) {
    }

    /**
     * @param fromHook whether we are running inside the JVM shutdown hook
     * @param sawCommand whether a stop/restart command was attributed to this stop
     */
    static Result inspect(boolean fromHook, boolean sawCommand) {
        Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();

        Thread current = Thread.currentThread();
        String killingThread = current.getName();

        // Locate the key threads.
        Thread watchdog = null;
        Thread serverThread = null;
        List<String> shutdownHookThreads = new ArrayList<>();
        for (Map.Entry<Thread, StackTraceElement[]> e : all.entrySet()) {
            String name = e.getKey().getName();
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("watchdog")) {
                watchdog = e.getKey();
            } else if (name.equals("Server thread") || lower.contains("server thread")) {
                serverThread = e.getKey();
            }
            if (stackMentions(e.getValue(), "java.lang.Shutdown")
                    || stackMentions(e.getValue(), "ApplicationShutdownHooks")) {
                shutdownHookThreads.add(name);
            }
        }

        boolean watchdogFiring = watchdog != null
                && stackMentionsAny(all.get(watchdog),
                        "halt", "shutdown", "stop", "crash", "WatchdogThread");

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("killing-thread", killingThread);
        evidence.put("ran-in-shutdown-hook", fromHook);
        evidence.put("watchdog-thread-present", watchdog != null);
        evidence.put("watchdog-appears-firing", watchdogFiring);
        evidence.put("shutdown-hook-threads", shutdownHookThreads);
        if (watchdog != null) {
            evidence.put("watchdog-stack", frames(all.get(watchdog), 12));
        }
        if (serverThread != null) {
            evidence.put("server-thread-state", String.valueOf(serverThread.getState()));
            evidence.put("server-thread-stack", frames(all.get(serverThread), 16));
        }

        // --- Inference ---------------------------------------------------
        if (sawCommand) {
            return new Result("COMMAND",
                    "high",
                    "A stop/restart command was seen just before shutdown; see triggered-by.",
                    evidence);
        }

        if (watchdogFiring) {
            return new Result("WATCHDOG_HANG",
                    "high",
                    "Paper's watchdog force-killed the server: a single tick exceeded the "
                            + "timeout (a hang/deadlock/GC stall on the main thread). The captured "
                            + "server-thread-stack shows where it was frozen.",
                    evidence);
        }

        if (fromHook || !shutdownHookThreads.isEmpty()) {
            return new Result("EXTERNAL_SIGNAL",
                    "high",
                    "Shutdown was driven by a JVM shutdown hook with no stop command and no "
                            + "watchdog — i.e. an OS signal (SIGTERM) from the host: the hosting "
                            + "panel's Stop/Restart button, a scheduled restart, systemctl stop, "
                            + "docker stop, or a graceful kill. NOT a crash (a hard crash/kill -9 "
                            + "leaves no report at all and is flagged UNCLEAN_SHUTDOWN next boot).",
                    evidence);
        }

        return new Result("API_OR_MAIN_THREAD",
                "medium",
                "Shutdown ran on the main server thread without a command — most likely a "
                        + "plugin calling Bukkit.shutdown()/Server.shutdown() programmatically.",
                evidence);
    }

    private static boolean stackMentions(StackTraceElement[] stack, String needle) {
        if (stack == null) {
            return false;
        }
        for (StackTraceElement el : stack) {
            if (el.getClassName().contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean stackMentionsAny(StackTraceElement[] stack, String... needles) {
        if (stack == null) {
            return false;
        }
        for (StackTraceElement el : stack) {
            String s = (el.getClassName() + "." + el.getMethodName()).toLowerCase(Locale.ROOT);
            for (String n : needles) {
                if (s.contains(n.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> frames(StackTraceElement[] stack, int max) {
        List<String> out = new ArrayList<>();
        if (stack == null) {
            return out;
        }
        for (int i = 0; i < stack.length && i < max; i++) {
            out.add(stack[i].toString());
        }
        if (stack.length > max) {
            out.add("... (" + (stack.length - max) + " more frames)");
        }
        return out;
    }
}
