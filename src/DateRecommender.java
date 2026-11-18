import java.time.LocalDate;
import java.util.Arrays;

public final class DateRecommender {
    private DateRecommender() { }
    public static int attendance(AttendanceService service, LocalDate date) {
        int count = 0;
        for (int i = 0; i < 6; i++) if (service.response(i, date) == AttendanceService.AVAILABLE) count++;
        return count;
    }
    public static DateRecommendation[] recommend(AttendanceService service, LocalDate today) {
        LocalDate[] candidates = service.activeDates(today);
        DateRecommendation[] results = new DateRecommendation[candidates.length];
        int count = 0;
        for (LocalDate date : candidates) {
            if (service.count(date, AttendanceService.UNANSWERED) > 0) continue;
            int people = attendance(service, date);
            if (people > 0) results[count++] = new DateRecommendation(date, people);
        }
        results = Arrays.copyOf(results, count);
        for (int i = 0; i < count - 1; i++) for (int j = 0; j < count - i - 1; j++) {
            if (after(results[j], results[j + 1])) {
                DateRecommendation temp = results[j]; results[j] = results[j + 1]; results[j + 1] = temp;
            }
        }
        return results;
    }
    private static boolean after(DateRecommendation a, DateRecommendation b) {
        return a.getAttendance() < b.getAttendance()
            || (a.getAttendance() == b.getAttendance() && a.getDate().isAfter(b.getDate()));
    }
}
