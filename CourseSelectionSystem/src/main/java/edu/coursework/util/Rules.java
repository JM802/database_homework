package edu.coursework.util;

import java.time.*;
import java.time.temporal.ChronoUnit;

/** 与时区无关的选退课时间判定；学期开学日按北京自然日计算。 */
public final class Rules {
    public static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");
    private Rules() {}
    public static boolean regularWindow(LocalDate start, int term, Instant when) {
        LocalDateTime now = LocalDateTime.ofInstant(when, BEIJING);
        LocalDateTime begin = start.atStartOfDay();
        LocalDateTime end = begin.plusDays(term == 3 ? 3 : 21);
        return !now.isBefore(begin) && now.isBefore(end);
    }
    public static boolean midtermWindow(LocalDate start, int term, Instant when) {
        if (term == 3) return false;
        LocalDateTime now = LocalDateTime.ofInstant(when, BEIJING);
        LocalDateTime begin = start.plusWeeks(9).atStartOfDay();
        return !now.isBefore(begin) && now.isBefore(begin.plusDays(4));
    }
    public static boolean canRevise(Instant first, Instant now) {
        return !now.isBefore(first) && !now.isAfter(first.plus(168, ChronoUnit.HOURS));
    }
    public static int gpa(int score) {
        if (score < 0 || score > 100) throw new IllegalArgumentException("分数必须为 0 到 100 的整数");
        if (score < 60) return 0;
        if (score < 70) return 1;
        if (score < 80) return 2;
        if (score < 90) return 3;
        return 4;
    }
}
