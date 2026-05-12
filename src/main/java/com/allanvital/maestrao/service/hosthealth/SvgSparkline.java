package com.allanvital.maestrao.service.hosthealth;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiny inline SVG sparkline generator (no external chart libs).
 */
public class SvgSparkline {

    public static String polyline(List<Double> values, int width, int height) {
        int w = Math.max(10, width);
        int h = Math.max(10, height);

        if (values == null || values.isEmpty()) {
            return emptySvg(w, h);
        }

        List<Double> clean = new ArrayList<>(values.size());
        for (Double v : values) {
            if (v == null || v.isNaN() || v.isInfinite()) {
                continue;
            }
            clean.add(v);
        }
        if (clean.isEmpty()) {
            return emptySvg(w, h);
        }

        double min = clean.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = clean.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        if (min == max) {
            // Give it a tiny range so we still render a line.
            min = min - 1;
            max = max + 1;
        }

        int n = clean.size();
        StringBuilder points = new StringBuilder();
        for (int i = 0; i < n; i++) {
            double v = clean.get(i);
            double x = (n == 1) ? 0 : (i * (w - 1.0) / (n - 1.0));
            double norm = (v - min) / (max - min);
            double y = (h - 1.0) - (norm * (h - 1.0));
            if (i > 0) {
                points.append(' ');
            }
            points.append(format(x)).append(',').append(format(y));
        }

        return "<svg viewBox=\"0 0 " + w + " " + h + "\" width=\"" + w + "\" height=\"" + h + "\" xmlns=\"http://www.w3.org/2000/svg\">"
                + "<polyline fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.5\" points=\"" + points + "\" />"
                + "</svg>";
    }

    private static String emptySvg(int w, int h) {
        return "<svg viewBox=\"0 0 " + w + " " + h + "\" width=\"" + w + "\" height=\"" + h + "\" xmlns=\"http://www.w3.org/2000/svg\"></svg>";
    }

    private static String format(double v) {
        // Keep output stable and short.
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
