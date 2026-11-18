import java.time.LocalDate;

/** 실행할 때마다 기준일에 맞춰 생성하는 가상 밴드의 연습용 데이터. */
public final class DemoData {
    private DemoData() { }
    /** 발표 전용 예시. 기본 회귀검사용 샘플과 분리한다. */
    public static BandDecision createPresentation(LocalDate today) {
        BandDecision app = create(today);
        String[] reasons = {"수업", "아르바이트", "가족 일정", "시험 준비", "개인 연습", "출장"};
        int day = 0;
        for (LocalDate date = today; !date.isAfter(ScheduleWindow.end(today)); date = date.plusDays(1), day++) {
            for (int member = 0; member < 6; member++) {
                int choice = day % 7 == 6 && member > 2 ? AttendanceService.UNANSWERED
                    : day % 7 == 4 || (day % 7 == 1 && member == 5)
                    || (day % 7 == 3 && member >= 3) ? AttendanceService.UNAVAILABLE : AttendanceService.AVAILABLE;
                // 첫 멤버는 전 기간 응답 완료, 나머지는 미완료도 비교할 수 있다.
                app.getAttendance().respond(member, date, choice, reasons[member]);
            }
        }
        String[] titles = {"파란 새벽", "함께 걷는 길", "빠른 심장", "첫 합주"};
        int[][] ratings = {{2, 2, 2, 1, 2, 2}, {3, 2, 3, 2, 3, 2}, {5, 5, 5, 5, 5, 5}, {1, 1, 1, 1, 1, 1}};
        int[][] votes = {{1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 2, 2}, {1, 2, 2, 1, 2, 2}, {1, 1, 0, 0, 0, 0}};
        for (int i = 0; i < titles.length; i++) {
            Song song = app.getSongs().register(titles[i], "발표용 가상 밴드", ratings[i]);
            for (int member = 0; member < 6; member++) if (votes[i][member] != Song.UNANSWERED)
                song.vote(member, votes[i][member], votes[i][member] == Song.NO ? "현재 실력보다 난이도가 높아 추가 연습이 필요합니다." : "");
        }
        LocalDate full = today.plusDays(2), partial = today.plusDays(1);
        RehearsalPlan first = app.preview(full, new int[] {1, 5}, today);
        app.confirm(full, new int[] {1, 5}, today, first, false);
        RehearsalPlan second = app.preview(partial, new int[] {2, 6}, today);
        app.confirm(partial, new int[] {2, 6}, today, second, true);
        return app;
    }
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
