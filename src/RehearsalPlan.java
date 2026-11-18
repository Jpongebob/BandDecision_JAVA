import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.Locale;

/** 가변 Member/Song 객체를 보관하지 않는 확정 당시의 독립 스냅샷. */
public final class RehearsalPlan {
    private final LocalDate date;
    private final String[] attendees;
    private final String[] absentees;
    private final String[] songs;
    public RehearsalPlan(LocalDate date, Member[] members, AttendanceService attendance, Song[] selected) {
        this.date = date;
        String[] present = new String[6], absent = new String[6];
        int p = 0, a = 0;
        for (int i = 0; i < 6; i++) {
            String reason = attendance.reason(i, date);
            if (reason == null) present[p++] = members[i].getPart();
            else absent[a++] = members[i].getPart() + " | " + reason;
        }
        attendees = Arrays.copyOf(present, p);
        absentees = Arrays.copyOf(absent, a);
        songs = new String[selected.length];
        for (int i = 0; i < selected.length; i++) songs[i] = selected[i].label();
    }
    public LocalDate getDate() { return date; }
    RehearsalPlan(LocalDate date, String[] attendees, String[] absentees, String[] songs) {
        if (date == null || attendees.length < 1 || attendees.length + absentees.length != 6 || songs.length < 1)
            throw new IllegalArgumentException("확정 계획 정보가 올바르지 않습니다.");
        for (String[] group : new String[][] {attendees, absentees, songs})
            for (String value : group) if (value == null || value.isBlank()) throw new IllegalArgumentException("빈 확정 정보");
        this.date = date;
        this.attendees = attendees.clone(); this.absentees = absentees.clone(); this.songs = songs.clone();
    }
    public String[] getAttendees() { return attendees.clone(); }
    public String[] getAbsentees() { return absentees.clone(); }
    public String[] getSongs() { return songs.clone(); }
    public String describe() {
        String text = "날짜: " + date + " (" + date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN)
            + ")\n시간: 20:00 ~ 22:00\n\n[참석 예정: " + attendees.length + "/6]\n"
            + String.join(" / ", attendees) + "\n\n[불참 멤버]\n";
        text += absentees.length == 0 ? "없음\n" : String.join("\n", absentees) + "\n";
        text += "\n[확정 합주곡]\n";
        for (int i = 0; i < songs.length; i++) text += (i + 1) + ". " + songs[i] + "\n";
        return text;
    }
    /** 재확인 이후 참석자, 사유, 곡 정보가 달라졌는지도 확인한다. */
    public boolean sameContent(RehearsalPlan other) {
        return date.equals(other.date) && Arrays.equals(attendees, other.attendees)
            && Arrays.equals(absentees, other.absentees) && Arrays.equals(songs, other.songs);
    }
}
