import java.time.LocalDate;

/** 일반 실행은 실제 날짜, 발표 Demo는 고정 기준일을 공유한다. */
public final class AppDate {
    public static final LocalDate DEMO_DATE = LocalDate.of(2026, 12, 9);
    public static final String DEMO_FILE = "demo-2026-12-09.properties";
    private static boolean demoMode;
    private AppDate() { }
    public static void configure(boolean demo) { demoMode = demo; }
    public static LocalDate today() { return demoMode ? DEMO_DATE : LocalDate.now(); }
}
