package com.allanvital.maestrao.service.hosthealth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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

    public static String areaLine(List<Double> values, int width, int height, String strokeColor, String fillColor) {
        return areaLine(values, width, height, strokeColor, fillColor, 0.0, 100.0, true, "", "", "", "%", true);
    }

    public static String areaLine(
            List<Double> values,
            int width,
            int height,
            String strokeColor,
            String fillColor,
            double yMin,
            double yMax,
            boolean fixedY,
            String xStart,
            String xMid,
            String xEnd,
            String yUnit,
            boolean drawBorder
    ) {
        int w = Math.max(10, width);
        int h = Math.max(10, height);

        List<Double> clean = sanitize(values);
        if (clean.isEmpty()) {
            return emptySvg(w, h);
        }

        Bounds bounds = fixedY ? safeBounds(yMin, yMax) : bounds(clean);
        int leftPad = 42;
        int rightPad = 8;
        int topPad = 8;
        int bottomPad = 24;

        PlotArea plot = plotArea(w, h, leftPad, rightPad, topPad, bottomPad);
        List<Point> points = points(clean, plot, bounds.min, bounds.max);

        String polylinePoints = asSvgPoints(points);
        StringBuilder area = new StringBuilder(polylinePoints);
        Point last = points.get(points.size() - 1);
        Point first = points.get(0);
        area.append(' ').append(format(last.x)).append(',').append(format(plot.bottom));
        area.append(' ').append(format(first.x)).append(',').append(format(plot.bottom));

        StringBuilder svg = new StringBuilder();
        svg.append("<svg viewBox=\"0 0 ").append(w).append(' ').append(h).append("\" width=\"100%\" height=\"").append(h).append("\" xmlns=\"http://www.w3.org/2000/svg\">");
        appendAxes(svg, plot, bounds, yUnit, xStart, xMid, xEnd);
        svg.append("<polygon fill=\"").append(fillColor).append("\" points=\"").append(area).append("\" />");
        svg.append("<polyline fill=\"none\" stroke=\"").append(strokeColor).append("\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" points=\"").append(polylinePoints).append("\" />");
        svg.append("<circle cx=\"").append(format(last.x)).append("\" cy=\"").append(format(last.y)).append("\" r=\"2.5\" fill=\"").append(strokeColor).append("\" />");
        if (drawBorder) {
            svg.append("<rect x=\"").append(format(plot.left)).append("\" y=\"").append(format(plot.top)).append("\" width=\"").append(format(plot.width())).append("\" height=\"").append(format(plot.height())).append("\" fill=\"none\" stroke=\"rgba(120,120,120,0.30)\" stroke-width=\"1\" />");
        }
        svg.append("</svg>");
        return svg.toString();
    }

    public static String multiLine(List<Double> a, List<Double> b, List<Double> c, int width, int height) {
        return multiLine(a, b, c, width, height, "", "", "", true, true);
    }

    public static String multiLine(
            List<Double> a,
            List<Double> b,
            List<Double> c,
            int width,
            int height,
            String xStart,
            String xMid,
            String xEnd,
            boolean showLegend,
            boolean drawBorder
    ) {
        int w = Math.max(10, width);
        int h = Math.max(10, height);
        List<Double> aa = sanitize(a);
        List<Double> bb = sanitize(b);
        List<Double> cc = sanitize(c);
        if (aa.isEmpty() || bb.isEmpty() || cc.isEmpty()) {
            return emptySvg(w, h);
        }

        int n = Math.min(aa.size(), Math.min(bb.size(), cc.size()));
        aa = aa.subList(0, n);
        bb = bb.subList(0, n);
        cc = cc.subList(0, n);

        List<Double> all = new ArrayList<>(n * 3);
        all.addAll(aa);
        all.addAll(bb);
        all.addAll(cc);
        Bounds bounds = bounds(all);

        int leftPad = 42;
        int rightPad = 8;
        int topPad = showLegend ? 22 : 8;
        int bottomPad = 24;
        PlotArea plot = plotArea(w, h, leftPad, rightPad, topPad, bottomPad);

        List<Point> p1 = points(aa, plot, bounds.min, bounds.max);
        List<Point> p2 = points(bb, plot, bounds.min, bounds.max);
        List<Point> p3 = points(cc, plot, bounds.min, bounds.max);

        StringBuilder svg = new StringBuilder();
        svg.append("<svg viewBox=\"0 0 ").append(w).append(' ').append(h).append("\" width=\"100%\" height=\"").append(h).append("\" xmlns=\"http://www.w3.org/2000/svg\">");
        appendAxes(svg, plot, bounds, "", xStart, xMid, xEnd);
        if (showLegend) {
            appendLegend(svg, 46, 12);
        }
        svg.append("<polyline fill=\"none\" stroke=\"#0ea5e9\" stroke-width=\"2\" stroke-linecap=\"round\" points=\"").append(asSvgPoints(p1)).append("\" />");
        svg.append("<polyline fill=\"none\" stroke=\"#22c55e\" stroke-width=\"1.8\" stroke-linecap=\"round\" points=\"").append(asSvgPoints(p2)).append("\" />");
        svg.append("<polyline fill=\"none\" stroke=\"#f59e0b\" stroke-width=\"1.8\" stroke-linecap=\"round\" points=\"").append(asSvgPoints(p3)).append("\" />");
        if (drawBorder) {
            svg.append("<rect x=\"").append(format(plot.left)).append("\" y=\"").append(format(plot.top)).append("\" width=\"").append(format(plot.width())).append("\" height=\"").append(format(plot.height())).append("\" fill=\"none\" stroke=\"rgba(120,120,120,0.30)\" stroke-width=\"1\" />");
        }
        svg.append("</svg>");
        return svg.toString();
    }

    private static List<Double> sanitize(List<Double> values) {
        if (values == null) {
            return List.of();
        }
        List<Double> clean = new ArrayList<>(values.size());
        for (Double v : values) {
            if (v == null || v.isNaN() || v.isInfinite()) {
                continue;
            }
            clean.add(v);
        }
        return clean;
    }

    private static Bounds bounds(List<Double> values) {
        double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        if (min == max) {
            min = min - 1;
            max = max + 1;
        }
        return new Bounds(min, max);
    }

    private static List<Point> points(List<Double> values, PlotArea plot, double min, double max) {
        int n = values.size();
        List<Point> points = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double v = values.get(i);
            double x = (n == 1) ? plot.left : (plot.left + (i * plot.width() / (n - 1.0)));
            double norm = (v - min) / (max - min);
            double y = plot.bottom - (norm * plot.height());
            points.add(new Point(x, y));
        }
        return points;
    }

    private static PlotArea plotArea(int w, int h, int leftPad, int rightPad, int topPad, int bottomPad) {
        double left = leftPad;
        double top = topPad;
        double right = Math.max(left + 1, w - rightPad - 1.0);
        double bottom = Math.max(top + 1, h - bottomPad - 1.0);
        return new PlotArea(left, top, right, bottom);
    }

    private static Bounds safeBounds(double min, double max) {
        if (Double.isNaN(min) || Double.isNaN(max) || Double.isInfinite(min) || Double.isInfinite(max) || min >= max) {
            return new Bounds(0, 100);
        }
        return new Bounds(min, max);
    }

    private static void appendAxes(StringBuilder svg, PlotArea plot, Bounds bounds, String yUnit, String xStart, String xMid, String xEnd) {
        double[] ticks = new double[] {bounds.min, bounds.min + ((bounds.max - bounds.min) / 2.0), bounds.max};
        for (double tick : ticks) {
            double y = plot.bottom - (((tick - bounds.min) / (bounds.max - bounds.min)) * plot.height());
            svg.append("<line x1=\"").append(format(plot.left)).append("\" y1=\"").append(format(y)).append("\" x2=\"").append(format(plot.right)).append("\" y2=\"").append(format(y)).append("\" stroke=\"rgba(120,120,120,0.18)\" stroke-width=\"1\"/>");
            svg.append("<text x=\"").append(format(plot.left - 4)).append("\" y=\"").append(format(y + 3)).append("\" text-anchor=\"end\" font-size=\"10\" fill=\"rgba(120,120,120,0.95)\">")
                    .append(escapeXml(formatTick(tick, yUnit)))
                    .append("</text>");
        }
        svg.append("<line x1=\"").append(format(plot.left)).append("\" y1=\"").append(format(plot.top)).append("\" x2=\"").append(format(plot.left)).append("\" y2=\"").append(format(plot.bottom)).append("\" stroke=\"rgba(120,120,120,0.35)\" stroke-width=\"1\"/>");
        svg.append("<line x1=\"").append(format(plot.left)).append("\" y1=\"").append(format(plot.bottom)).append("\" x2=\"").append(format(plot.right)).append("\" y2=\"").append(format(plot.bottom)).append("\" stroke=\"rgba(120,120,120,0.35)\" stroke-width=\"1\"/>");
        appendXLabel(svg, plot.left, plot.bottom + 15, "start", xStart);
        appendXLabel(svg, plot.left + (plot.width() / 2.0), plot.bottom + 15, "middle", xMid);
        appendXLabel(svg, plot.right, plot.bottom + 15, "end", xEnd);
    }

    private static void appendXLabel(StringBuilder svg, double x, double y, String position, String label) {
        String anchor = switch (position) {
            case "start" -> "start";
            case "end" -> "end";
            default -> "middle";
        };
        svg.append("<text x=\"").append(format(x)).append("\" y=\"").append(format(y)).append("\" text-anchor=\"").append(anchor)
                .append("\" font-size=\"10\" fill=\"rgba(120,120,120,0.95)\">")
                .append(escapeXml(label == null ? "" : label))
                .append("</text>");
    }

    private static void appendLegend(StringBuilder svg, int x, int y) {
        appendLegendItem(svg, x, y, "#0ea5e9", "Load1");
        appendLegendItem(svg, x + 74, y, "#22c55e", "Load5");
        appendLegendItem(svg, x + 148, y, "#f59e0b", "Load15");
    }

    private static void appendLegendItem(StringBuilder svg, int x, int y, String color, String label) {
        svg.append("<line x1=\"").append(x).append("\" y1=\"").append(y).append("\" x2=\"").append(x + 14).append("\" y2=\"").append(y)
                .append("\" stroke=\"").append(color).append("\" stroke-width=\"2\"/>");
        svg.append("<text x=\"").append(x + 18).append("\" y=\"").append(y + 3).append("\" font-size=\"10\" fill=\"rgba(120,120,120,0.95)\">")
                .append(label)
                .append("</text>");
    }

    private static String formatTick(double v, String unit) {
        String suffix = unit == null ? "" : unit;
        return String.format(Locale.ROOT, "%.0f%s", v, suffix);
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String asSvgPoints(List<Point> points) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < points.size(); i++) {
            Point p = points.get(i);
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(format(p.x)).append(',').append(format(p.y));
        }
        return sb.toString();
    }

    private static String emptySvg(int w, int h) {
        return "<svg viewBox=\"0 0 " + w + " " + h + "\" width=\"100%\" height=\"" + h + "\" xmlns=\"http://www.w3.org/2000/svg\"></svg>";
    }

    private static String format(double v) {
        // Keep output stable and short.
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    private record Bounds(double min, double max) {
    }

    private record Point(double x, double y) {
    }

    private record PlotArea(double left, double top, double right, double bottom) {
        double width() {
            return right - left;
        }

        double height() {
            return bottom - top;
        }
    }
}
