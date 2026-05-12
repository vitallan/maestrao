package com.allanvital.maestrao.service.hosthealth;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SvgSparklineTest {

    @Test
    void polylineRendersSvgAndPolyline() {
        String svg = SvgSparkline.polyline(List.of(1.0, 2.0, 3.0), 100, 20);
        assertTrue(svg.startsWith("<svg"));
        assertTrue(svg.contains("<polyline"));
        assertTrue(svg.contains("points=\""));
    }

    @Test
    void polylineHandlesNaNValues() {
        String svg = SvgSparkline.polyline(List.of(Double.NaN, Double.NaN), 100, 20);
        assertTrue(svg.startsWith("<svg"));
        assertFalse(svg.contains("<polyline"));
    }
}
