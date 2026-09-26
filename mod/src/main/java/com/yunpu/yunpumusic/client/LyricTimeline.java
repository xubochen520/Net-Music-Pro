package com.yunpu.yunpumusic.client;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LRC 歌词时间轴。
 *
 * <p>三个平台返回的歌词格式一致（LRC），这里把它整理成「tick → 文本」的有序表。
 * tick 的换算与 Net Music 保持一致：{@code tick = 毫秒 / 50}，
 * 这样界面显示的当前行与游戏中的 3D 歌词渲染完全同步。
 *
 * <p>同时支持一行多时间戳（{@code [00:12.00][01:20.00]词}）与翻译行合并。
 */
public final class LyricTimeline {
    private static final Pattern TAG = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");

    private final Map<Integer, String> lines;
    private final List<Integer> ticks;
    private final int lastTick;

    private LyricTimeline(Map<Integer, String> lines) {
        this.lines = lines;
        this.ticks = new ArrayList<>(lines.keySet());
        this.ticks.sort(Integer::compareTo);
        this.lastTick = ticks.isEmpty() ? 0 : ticks.get(ticks.size() - 1);
    }

    public static LyricTimeline parse(String lrc) {
        Map<Integer, String> lines = new LinkedHashMap<>();
        if (lrc == null || lrc.isBlank()) {
            return new LyricTimeline(lines);
        }
        for (String rawLine : lrc.split("\\R")) {
            String line = rawLine.trim();
            Matcher matcher = TAG.matcher(line);
            List<Integer> stamps = new ArrayList<>();
            int lastEnd = 0;
            while (matcher.find()) {
                int minutes = Integer.parseInt(matcher.group(1));
                int seconds = Integer.parseInt(matcher.group(2));
                String fraction = matcher.group(3);
                int millis = 0;
                if (fraction != null) {
                    millis = switch (fraction.length()) {
                        case 1 -> Integer.parseInt(fraction) * 100;
                        case 2 -> Integer.parseInt(fraction) * 10;
                        default -> Integer.parseInt(fraction.substring(0, 3));
                    };
                }
                stamps.add(((minutes * 60 + seconds) * 1000 + millis) / 50);
                lastEnd = matcher.end();
            }
            if (stamps.isEmpty()) {
                continue;
            }
            String text = line.substring(lastEnd).trim();
            for (int stamp : stamps) {
                String existing = lines.get(stamp);
                if (existing == null || existing.isBlank()) {
                    lines.put(stamp, text);
                } else if (!text.isBlank() && !existing.contains(text)) {
                    // 同一时间点既有原文又有翻译时，合并成两行显示
                    lines.put(stamp, existing + "\n" + text);
                }
            }
        }
        return new LyricTimeline(lines);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public int lineCount() {
        return lines.size();
    }

    /** 当前播放位置对应的歌词行。 */
    @Nullable
    public String lineAt(int tick) {
        if (lines.isEmpty()) {
            return null;
        }
        int best = -1;
        for (int candidate : ticks) {
            if (candidate <= tick) {
                best = candidate;
            } else {
                break;
            }
        }
        return best < 0 ? null : lines.get(best);
    }

    /** 下一行歌词，用于界面上提前提示。 */
    @Nullable
    public String nextLineAt(int tick) {
        for (int candidate : ticks) {
            if (candidate > tick) {
                return lines.get(candidate);
            }
        }
        return null;
    }

    /** 整首歌词的最后时间点（tick），用于计算进度。 */
    public int lastTick() {
        return lastTick;
    }

    /** 按时间顺序的全部歌词行，供界面做滚动列表。 */
    public List<String> allLines() {
        List<String> result = new ArrayList<>(ticks.size());
        for (int tick : ticks) {
            result.add(lines.get(tick));
        }
        return result;
    }

    /** 返回当前行在 {@link #allLines()} 中的下标，找不到返回 -1。 */
    public int indexAt(int tick) {
        int index = -1;
        for (int candidate : ticks) {
            if (candidate <= tick) {
                index++;
            } else {
                break;
            }
        }
        return index;
    }

    /** 第 {@code index} 行对应的时间点（tick）；越界时返回 0。 */
    public int tickAt(int index) {
        if (index < 0 || index >= ticks.size()) {
            return 0;
        }
        return ticks.get(index);
    }
}
