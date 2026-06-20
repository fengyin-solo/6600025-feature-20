package com.canbus.service;

import com.canbus.model.CanFrame;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CanService {

    private static final int[] MESSAGE_IDS = {0x7DF, 0x7E8, 0x7E9, 0x7EA, 0x7EB};
    private static final int TREND_MAX_POINTS = 60;
    private final Random random = new Random();
    private int frameCounter = 0;

    private final Deque<Map<String, Object>> trafficTrendHistory = new LinkedList<>();
    private int windowRxCount = 0;
    private int windowTxCount = 0;

    /**
     * Generate 20 mock OBD-II CAN frames with realistic values
     */
    public List<CanFrame> generateMockFrames() {
        List<CanFrame> frames = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            CanFrame frame = generateSingleFrame();
            if ("RX".equals(frame.getDirection())) {
                windowRxCount++;
            } else {
                windowTxCount++;
            }
            frames.add(frame);
        }
        return frames;
    }

    public void snapshotTrafficWindow() {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("time", System.currentTimeMillis());
        point.put("rx", windowRxCount);
        point.put("tx", windowTxCount);
        trafficTrendHistory.offerLast(point);
        while (trafficTrendHistory.size() > TREND_MAX_POINTS) {
            trafficTrendHistory.pollFirst();
        }
        windowRxCount = 0;
        windowTxCount = 0;
    }

    public List<Map<String, Object>> getTrafficTrend() {
        return new ArrayList<>(trafficTrendHistory);
    }

    private CanFrame generateSingleFrame() {
        int arbId = MESSAGE_IDS[random.nextInt(MESSAGE_IDS.length)];

        double rpm = 800 + random.nextDouble() * 5200;
        double speed = random.nextDouble() * 120;
        double temp = 70 + random.nextDouble() * 35;
        double throttle = random.nextDouble() * 100;
        double load = random.nextDouble() * 100;

        int rpmRaw = (int) Math.round(rpm / 0.25);
        int rpmLow = rpmRaw & 0xFF;
        int rpmHigh = (rpmRaw >> 8) & 0xFF;
        int speedByte = ((int) speed) & 0xFF;
        int tempByte = ((int) temp + 40) & 0xFF;
        int throttleByte = ((int) Math.round(throttle / 0.392)) & 0xFF;
        int loadByte = ((int) Math.round(load / 0.392)) & 0xFF;

        String data = String.format("%02X %02X %02X %02X %02X %02X 00 00",
                rpmLow, rpmHigh, speedByte, tempByte, throttleByte, loadByte);

        Map<String, Double> decoded = new LinkedHashMap<>();
        decoded.put("EngineRPM", Math.round(rpm * 100.0) / 100.0);
        decoded.put("VehicleSpeed", Math.round(speed * 100.0) / 100.0);
        decoded.put("CoolantTemp", Math.round(temp * 100.0) / 100.0);
        decoded.put("ThrottlePosition", Math.round(throttle * 100.0) / 100.0);
        decoded.put("EngineLoad", Math.round(load * 100.0) / 100.0);

        String direction = random.nextDouble() > 0.3 ? "RX" : "TX";

        return new CanFrame(
                "frame-" + (++frameCounter),
                System.currentTimeMillis(),
                arbId,
                8,
                data,
                decoded,
                direction
        );
    }

    /**
     * Parse DBC text and return message definitions
     */
    public Map<String, Object> parseDbc(String text) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> messages = new ArrayList<>();

        String[] lines = text.split("\n");
        Map<String, Object> currentMsg = null;
        List<Map<String, Object>> signals = null;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.matches("^BO_\\s+\\d+.*")) {
                String[] parts = trimmed.split("\\s+");
                if (parts.length >= 4) {
                    currentMsg = new LinkedHashMap<>();
                    currentMsg.put("id", Integer.parseInt(parts[1]));
                    String nameDlc = parts[2];
                    String name = nameDlc.endsWith(":") ? nameDlc.substring(0, nameDlc.length() - 1) : nameDlc;
                    currentMsg.put("name", name);
                    currentMsg.put("dlc", Integer.parseInt(parts[3]));
                    currentMsg.put("sender", parts.length > 4 ? parts[4] : "Unknown");
                    signals = new ArrayList<>();
                    currentMsg.put("signals", signals);
                    messages.add(currentMsg);
                }
            } else if (trimmed.matches("^SG_\\s+.*") && signals != null) {
                Map<String, Object> sig = new LinkedHashMap<>();
                String[] parts = trimmed.split("\\s+");
                if (parts.length >= 2) {
                    sig.put("name", parts[1]);
                    signals.add(sig);
                }
            } else if (trimmed.isEmpty()) {
                currentMsg = null;
                signals = null;
            }
        }

        result.put("messages", messages);
        result.put("messageCount", messages.size());
        return result;
    }

    /**
     * Decode a frame using signal definitions (simplified)
     */
    public Map<String, Double> decodeFrame(CanFrame frame) {
        return frame.getDecoded() != null ? frame.getDecoded() : new LinkedHashMap<>();
    }

    /**
     * Get bus statistics
     */
    public Map<String, Object> getStats(int totalFrames) {
        Map<String, Object> stats = new LinkedHashMap<>();
        int rxCount = (int) (totalFrames * 0.7);
        int txCount = totalFrames - rxCount;
        stats.put("totalFrames", totalFrames);
        stats.put("rxCount", rxCount);
        stats.put("txCount", txCount);
        stats.put("errorCount", 0);
        stats.put("busLoad", 15 + random.nextDouble() * 30);
        stats.put("lastUpdate", System.currentTimeMillis());

        double rxTxRatio;
        if (txCount == 0 && rxCount == 0) {
            rxTxRatio = 0;
        } else if (txCount == 0) {
            rxTxRatio = 9999.0;
        } else {
            rxTxRatio = Math.round((double) rxCount / txCount * 100.0) / 100.0;
        }
        stats.put("rxTxRatio", rxTxRatio);

        String imbalanceLevel;
        String imbalanceLabel;
        if (totalFrames < 20) {
            imbalanceLevel = "normal";
            imbalanceLabel = "数据不足";
        } else if (rxTxRatio > 5 || (rxTxRatio > 0 && rxTxRatio < 0.2)) {
            imbalanceLevel = "severe";
            imbalanceLabel = "严重失衡";
        } else if (rxTxRatio > 3 || (rxTxRatio > 0 && rxTxRatio < 0.33)) {
            imbalanceLevel = "warning";
            imbalanceLabel = "轻度失衡";
        } else {
            imbalanceLevel = "normal";
            imbalanceLabel = "正常";
        }
        stats.put("imbalanceLevel", imbalanceLevel);
        stats.put("imbalanceLabel", imbalanceLabel);

        stats.put("trafficTrend", getTrafficTrend());
        return stats;
    }
}
