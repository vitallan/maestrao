package com.allanvital.maestrao.service.hosthealth;

import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class HostHealthMetricsParser {

    public ParsedMetrics parse(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            throw new IllegalArgumentException("Empty metrics output");
        }

        String[] lines = stdout.split("\\r?\\n");
        if (lines.length < 4) {
            throw new IllegalArgumentException("Unexpected metrics output");
        }

        // 1) /proc/loadavg
        double load1;
        double load5;
        double load15;
        {
            String[] parts = lines[0].trim().split("\\s+");
            if (parts.length < 3) {
                throw new IllegalArgumentException("Invalid loadavg line");
            }
            load1 = Double.parseDouble(parts[0]);
            load5 = Double.parseDouble(parts[1]);
            load15 = Double.parseDouble(parts[2]);
        }

        // 2) /proc/stat first line (cpu)
        long cpuUser;
        long cpuNice;
        long cpuSystem;
        long cpuIdle;
        long cpuIowait;
        long cpuIrq;
        long cpuSoftirq;
        long cpuSteal;
        {
            String line = lines[1].trim();
            String[] parts = line.split("\\s+");
            if (parts.length < 9 || !parts[0].equalsIgnoreCase("cpu")) {
                throw new IllegalArgumentException("Invalid /proc/stat cpu line");
            }
            cpuUser = parseLong(parts[1]);
            cpuNice = parseLong(parts[2]);
            cpuSystem = parseLong(parts[3]);
            cpuIdle = parseLong(parts[4]);
            cpuIowait = parseLong(parts[5]);
            cpuIrq = parseLong(parts[6]);
            cpuSoftirq = parseLong(parts[7]);
            cpuSteal = parseLong(parts[8]);
        }

        // 3) meminfo (MemTotal, MemAvailable) in kB
        long memTotalBytes = -1;
        long memAvailableBytes = -1;
        int idx = 2;
        for (; idx < lines.length; idx++) {
            String l = lines[idx];
            String lower = l.toLowerCase(Locale.ROOT);
            if (lower.startsWith("filesystem")) {
                break;
            }
            if (lower.startsWith("memtotal:")) {
                memTotalBytes = parseMeminfoBytes(l);
            } else if (lower.startsWith("memavailable:")) {
                memAvailableBytes = parseMeminfoBytes(l);
            }
        }
        if (memTotalBytes < 0 || memAvailableBytes < 0) {
            throw new IllegalArgumentException("Invalid meminfo output");
        }

        // 4) df -P -k /
        long diskTotalBytes;
        long diskAvailBytes;
        {
            // Find the line whose mountpoint is '/'
            String dfLine = null;
            for (int i = idx; i < lines.length; i++) {
                String l = lines[i].trim();
                if (l.isEmpty() || l.toLowerCase(Locale.ROOT).startsWith("filesystem")) {
                    continue;
                }
                String[] parts = l.split("\\s+");
                if (parts.length >= 6 && parts[5].equals("/")) {
                    dfLine = l;
                    break;
                }
            }
            if (dfLine == null) {
                throw new IllegalArgumentException("Invalid df output");
            }
            String[] parts = dfLine.split("\\s+");
            // df -k: columns are 1K-blocks; convert to bytes.
            diskTotalBytes = parseLong(parts[1]) * 1024L;
            diskAvailBytes = parseLong(parts[3]) * 1024L;
        }

        return new ParsedMetrics(
                load1, load5, load15,
                memTotalBytes, memAvailableBytes,
                diskTotalBytes, diskAvailBytes,
                cpuUser, cpuNice, cpuSystem, cpuIdle, cpuIowait, cpuIrq, cpuSoftirq, cpuSteal
        );
    }

    private long parseMeminfoBytes(String line) {
        // Example: "MemTotal:       16367456 kB"
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid meminfo line");
        }
        long kb = parseLong(parts[1]);
        return kb * 1024L;
    }

    private long parseLong(String v) {
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number: " + v);
        }
    }

    public record ParsedMetrics(
            double load1,
            double load5,
            double load15,
            long memTotalBytes,
            long memAvailableBytes,
            long diskRootTotalBytes,
            long diskRootAvailableBytes,
            long cpuUser,
            long cpuNice,
            long cpuSystem,
            long cpuIdle,
            long cpuIowait,
            long cpuIrq,
            long cpuSoftirq,
            long cpuSteal
    ) {
    }
}
