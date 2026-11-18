import java.time.LocalDate;
import java.util.Arrays;

/** 날짜별 응답을 저장한다. 미기록 날짜는 미응답이며 첫 응답 시 저장 행을 만든다. */
public final class AttendanceService {
    public static final int UNANSWERED = 0, AVAILABLE = 1, UNAVAILABLE = 2;
    private LocalDate[] dates = new LocalDate[0];
    private int[][] answers = new int[0][6];
    private String[][] reasons = new String[0][6];
    private void check(int member) {
        if (member < 0 || member >= 6) throw new IllegalArgumentException("멤버 번호가 잘못되었습니다.");
    }
    private int index(LocalDate date) {
        for (int i = 0; i < dates.length; i++) if (dates[i].equals(date)) return i;
        throw new IllegalArgumentException("응답 기록이 없는 날짜입니다.");
    }
    public LocalDate[] getDates() { return dates.clone(); }
    public LocalDate[] activeDates(LocalDate today) {
        LocalDate[] result = new LocalDate[dates.length]; int size = 0;
        for (LocalDate date : dates) if (ScheduleWindow.contains(date, today)) result[size++] = date;
        return Arrays.copyOf(result, size);
    }
    private void ensureDate(LocalDate date) {
        if (date == null) throw new IllegalArgumentException("날짜가 필요합니다.");
        for (LocalDate old : dates) if (old.equals(date)) return;
        int size = dates.length;
        dates = Arrays.copyOf(dates, size + 1); answers = Arrays.copyOf(answers, size + 1); reasons = Arrays.copyOf(reasons, size + 1);
        dates[size] = date; answers[size] = new int[6]; reasons[size] = new String[6];
        for (int i = size; i > 0 && dates[i].isBefore(dates[i - 1]); i--) {
            LocalDate d = dates[i]; dates[i] = dates[i - 1]; dates[i - 1] = d;
            int[] a = answers[i]; answers[i] = answers[i - 1]; answers[i - 1] = a;
            String[] r = reasons[i]; reasons[i] = reasons[i - 1]; reasons[i - 1] = r;
        }
    }
    /** 임시 검사 데이터 정리용. 사용자 화면에는 날짜 기록 삭제 기능을 노출하지 않는다. */
    void removeDate(LocalDate date) {
        int removed = index(date);
        LocalDate[] nextDates = new LocalDate[dates.length - 1];
        int[][] nextAnswers = new int[nextDates.length][]; String[][] nextReasons = new String[nextDates.length][];
        for (int i = 0, j = 0; i < dates.length; i++) if (i != removed) {
            nextDates[j] = dates[i]; nextAnswers[j] = answers[i]; nextReasons[j++] = reasons[i];
        }
        dates = nextDates; answers = nextAnswers; reasons = nextReasons;
    }
    public int response(int member, LocalDate date) {
        check(member);
        if (date == null) throw new IllegalArgumentException("날짜가 필요합니다.");
        for (int i = 0; i < dates.length; i++) if (dates[i].equals(date)) return answers[i][member];
        return UNANSWERED;
    }
    public void respond(int member, LocalDate date, int choice, String reason) {
        check(member);
        if (choice < 0 || choice > 2) throw new IllegalArgumentException("미응답/가능/불가 중 선택하세요.");
        ensureDate(date); int i = index(date);
        answers[i][member] = choice;
        reasons[i][member] = choice == UNAVAILABLE ? (reason == null || reason.isBlank() ? "불가 응답" : reason.trim()) : null;
    }
    public int count(LocalDate date, int choice) {
        int n = 0; for (int member = 0; member < 6; member++) if (response(member, date) == choice) n++; return n;
    }
    public void respondMany(int member, LocalDate[] selected, int choice, String reason) {
        check(member);
        if (selected == null || selected.length == 0) throw new IllegalArgumentException("응답할 날짜를 선택하세요.");
        if (choice < 0 || choice > 2) throw new IllegalArgumentException("미응답/가능/불가 중 선택하세요.");
        for (LocalDate date : selected) if (date == null) throw new IllegalArgumentException("날짜가 필요합니다.");
        for (LocalDate date : selected) {
            String value = reason;
            if (choice == UNAVAILABLE && (value == null || value.isBlank()) && response(member, date) == UNAVAILABLE)
                value = reason(member, date);
            respond(member, date, choice, value);
        }
    }
    public static String label(int choice) { return choice == AVAILABLE ? "가능" : choice == UNAVAILABLE ? "불가" : "미응답"; }
    public String reason(int member, LocalDate date) {
        int value = response(member, date);
        return value == AVAILABLE ? null : value == UNANSWERED ? "미응답" : reasons[index(date)][member];
    }
    public void changeReason(int member, LocalDate date, String reason) {
        if (response(member, date) != UNAVAILABLE) throw new IllegalArgumentException("불가 응답의 사유만 수정할 수 있습니다.");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("사유를 입력하세요.");
        respond(member, date, UNAVAILABLE, reason);
    }
    public boolean isSubmitted(int member) { return isSubmitted(member, LocalDate.now()); }
    public boolean isSubmitted(int member, LocalDate today) {
        check(member);
        for (LocalDate date = today; !date.isAfter(ScheduleWindow.end(today)); date = date.plusDays(1))
            if (response(member, date) == UNANSWERED) return false;
        return true;
    }
    /** GUI의 한 단계 되돌리기. 외부에 내부 배열을 노출하지 않는다. */
    static final class Snapshot {
        private final LocalDate[] dates;
        private final int[][] answers;
        private final String[][] reasons;
        private Snapshot(AttendanceService source) {
            dates = source.dates.clone(); answers = new int[dates.length][]; reasons = new String[dates.length][];
            for (int i = 0; i < dates.length; i++) { answers[i] = source.answers[i].clone(); reasons[i] = source.reasons[i].clone(); }
        }
        boolean matches(AttendanceService other) {
            return Arrays.equals(dates, other.dates) && Arrays.deepEquals(answers, other.answers)
                && Arrays.deepEquals(reasons, other.reasons);
        }
    }
    Snapshot snapshot() { return new Snapshot(this); }
    void restore(Snapshot saved) {
        dates = saved.dates.clone(); answers = new int[dates.length][]; reasons = new String[dates.length][];
        for (int i = 0; i < dates.length; i++) { answers[i] = saved.answers[i].clone(); reasons[i] = saved.reasons[i].clone(); }
    }
    public boolean allSubmitted() { return allSubmitted(LocalDate.now()); }
    public boolean allSubmitted(LocalDate today) {
        for (int i = 0; i < 6; i++) if (!isSubmitted(i, today)) return false;
        return true;
    }
}
