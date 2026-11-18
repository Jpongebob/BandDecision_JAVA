import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/** 달력과 응답 완료·추천·확정이 공유하는 기간. */
public final class ScheduleWindow {
    private ScheduleWindow() { }
    public static LocalDate end(LocalDate today) { return YearMonth.from(today).plusMonths(1).atEndOfMonth(); }
    public static boolean contains(LocalDate date, LocalDate today) {
        return date != null && !date.isBefore(today) && !date.isAfter(end(today));
    }
    public static String label(LocalDate today) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("M/d");
        return today.format(format) + "–" + end(today).format(format);
    }
}
