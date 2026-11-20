package com.weivel.benchmarks;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The bounds the kit sets for this language, read from {@code benchmarks/java.txt} of the kit commit it pins, as the
 * kit's {@code RUNNER.md} lays the file out: each ratio gate's highest measured ratio per backend and platform, the
 * margin over it, each platform's quiet and worst noise, and the margin a same-language pair is judged by.
 */
public final class LanguageBudget {
    private static final List<String> PLATFORMS = Arrays.asList("linux", "windows", "macos");
    private static final List<String> BACKENDS = Arrays.asList("ffm", "jna");
    private static final String QUIET_NOISE = "quiet-noise";
    private static final String WORST_NOISE = "worst-noise";
    private static final String RATIO_MARGIN = "ratio-margin";
    private static final String NOISE_MARGIN = "noise-margin";

    private final Map<String, Double> perPlatform;
    private final double ratioMargin;

    private LanguageBudget(Map<String, Double> perPlatform, double ratioMargin) {
        this.perPlatform = perPlatform;
        this.ratioMargin = ratioMargin;
    }

    /**
     * Reads a budget file.
     *
     * @param path the file
     * @return the budget
     * @throws UncheckedIOException when the file cannot be read
     * @throws IllegalArgumentException as {@link #parse} throws it
     */
    public static LanguageBudget read(Path path) {
        try {
            return parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
        } catch (IOException unreadable) {
            throw new UncheckedIOException("the budget " + path + " cannot be read", unreadable);
        }
    }

    /**
     * Reads a budget's text.
     *
     * @param text the file's text
     * @return the budget
     * @throws IllegalArgumentException for a line the format does not define, a number that is not positive, a line
     *     given twice, or no ratio margin
     */
    public static LanguageBudget parse(String text) {
        Map<String, Double> perPlatform = new HashMap<>();
        Map<String, Double> margins = new HashMap<>();
        for (String raw : text.split("\r?\n")) {
            String line = raw.trim();
            if (!line.isEmpty()) {
                readLine(line, perPlatform, margins);
            }
        }
        return new LanguageBudget(perPlatform, margin(margins, RATIO_MARGIN));
    }

    private static void readLine(String line, Map<String, Double> perPlatform, Map<String, Double> margins) {
        String[] fields = line.split(" ");
        if (fields.length == 3 && isPerPlatformName(fields[0]) && PLATFORMS.contains(fields[1])) {
            putOnce(perPlatform, fields[0] + " " + fields[1], positiveNumber(fields[2], line), line);
        } else if (fields.length == 2 && fields[0].equals(RATIO_MARGIN)) {
            putOnce(margins, fields[0], positiveNumber(fields[1], line), line);
        } else {
            throw new IllegalArgumentException("the budget line \"" + line + "\" is neither a gate of a backend or a "
                    + "noise bound on a kit platform nor one of the kit's margins");
        }
    }

    private static boolean isPerPlatformName(String name) {
        if (name.equals(QUIET_NOISE) || name.equals(WORST_NOISE) || name.equals(NOISE_MARGIN)) {
            return true;
        }
        for (RatioGate gate : RatioGate.values()) {
            for (String backend : BACKENDS) {
                if (name.equals(gate.kitNameFor(backend))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void putOnce(Map<String, Double> numbers, String key, double number, String line) {
        if (numbers.put(key, number) != null) {
            throw new IllegalArgumentException("the budget gives " + key + " twice, the second time as \"" + line
                    + "\"");
        }
    }

    private static double positiveNumber(String text, String line) {
        double number;
        try {
            number = Double.parseDouble(text);
        } catch (NumberFormatException notANumber) {
            number = Double.NaN;
        }
        if (!(number > 0) || Double.isInfinite(number)) {
            throw new IllegalArgumentException("the budget line \"" + line + "\" carries " + text
                    + ", which is not a positive number");
        }
        return number;
    }

    private static double margin(Map<String, Double> margins, String name) {
        Double margin = margins.get(name);
        if (margin == null) {
            throw new IllegalArgumentException("the budget names no " + name);
        }
        return margin;
    }

    /**
     * The bound of a ratio gate on one backend and platform: its highest measured ratio times the ratio margin.
     *
     * @param gate the gate
     * @param backend the backend, {@code ffm} or {@code jna}
     * @param platform the kit's platform
     * @return the bound; empty for a backend and platform the budget gives no line, where the gate fails
     */
    public OptionalDouble ratioBound(RatioGate gate, String backend, String platform) {
        Double highest = perPlatform.get(gate.kitNameFor(backend) + " " + platform);
        return highest == null ? OptionalDouble.empty() : OptionalDouble.of(highest * ratioMargin);
    }

    /**
     * Whether the budget bounds a gate on any backend and platform. A gate it bounds nowhere is not yet measured; once
     * it bounds it anywhere, a backend and platform it gives no line fails as {@link #ratioBound} says.
     *
     * @param gate the gate
     * @return true when some line names the gate
     */
    public boolean isBoundAnywhere(RatioGate gate) {
        for (String backend : BACKENDS) {
            for (String platform : PLATFORMS) {
                if (perPlatform.containsKey(gate.kitNameFor(backend) + " " + platform)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The noise bounds on one platform.
     *
     * @param platform the kit's platform
     * @return the bounds; empty for a platform the budget gives no noise line, where the noise gate fails
     */
    public Optional<NoiseBounds> noiseBounds(String platform) {
        Double quiet = perPlatform.get(QUIET_NOISE + " " + platform);
        Double worst = perPlatform.get(WORST_NOISE + " " + platform);
        Double margin = perPlatform.get(NOISE_MARGIN + " " + platform);
        return quiet == null || worst == null || margin == null ? Optional.<NoiseBounds>empty()
                : Optional.of(new NoiseBounds(quiet, worst, margin));
    }

    /**
     * Answers {@code ratioMargin}.
     *
     * @return how far over its highest measured ratio a run may go
     */
    public double ratioMargin() {
        return ratioMargin;
    }
}
