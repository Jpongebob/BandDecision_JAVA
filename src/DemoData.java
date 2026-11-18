import java.time.LocalDate;

/** 실행할 때마다 기준일에 맞춰 생성하는 가상 밴드의 연습용 데이터. */
public final class DemoData {
    private DemoData() { }
    public static BandDecision create(LocalDate today) {
        BandDecision app = new BandDecision();
        populateSchedule(app, today);
        String[] titles = {"여름의 끝", "별빛 산책", "도시의 밤", "다시 시작"};
        String[] artists = {"샘플 밴드 A", "샘플 밴드 B", "샘플 밴드 C", "샘플 밴드 D"};
        int[][] difficulties = {{3, 3, 3, 2, 2, 3}, {2, 2, 2, 1, 2, 2}, {5, 4, 5, 4, 5, 4}, {3, 3, 4, 3, 3, 4}};
        int[][] votes = {{1, 1, 1, 1, 1, 2}, {1, 1, 1, 1, 2, 2}, {1, 1, 1, 2, 2, 2}, {1, 1, 1, 1, 1, 0}};
        String[] objections = {"후렴 음역이 부담됩니다.", "연습 시간이 부족합니다.", "템포가 너무 빠릅니다.", "다른 곡을 먼저 연습하고 싶습니다."};
        for (int index = 0; index < titles.length; index++) {
            Song song = app.getSongs().register(titles[index], artists[index], difficulties[index]);
            for (int member = 0; member < 6; member++) {
                int vote = votes[index][member];
                if (vote != Song.UNANSWERED) song.vote(member, vote, vote == Song.NO ? objections[index] : "");
            }
        }
        return app;
    }
    public static void populateSchedule(BandDecision app, LocalDate today) {
        AttendanceService attendance = app.getAttendance();
        LocalDate[] dates = new LocalDate[7];
        int[] possible = {4, 5, 6, 3, 6, 2, 5};
        for (int i = 0; i < dates.length; i++) dates[i] = today.plusDays(i * 2);
        for (int i = 0; i < dates.length; i++) for (int member = 0; member < 6; member++)
            attendance.respond(member, dates[i], member < possible[i] ? AttendanceService.AVAILABLE : AttendanceService.UNAVAILABLE, "개인 일정");
    }
}
