import java.time.LocalDate;

public final class DateRecommendation {
    private final LocalDate date;
    private final int attendance;
    public DateRecommendation(LocalDate date, int attendance) { this.date = date; this.attendance = attendance; }
    public LocalDate getDate() { return date; }
    public int getAttendance() { return attendance; }
}
