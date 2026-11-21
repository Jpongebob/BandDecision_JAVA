import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Properties;

public final class AcceptanceTest {
    private static int checks;
    private static final LocalDate BASE = LocalDate.of(2026, 10, 28);
    private static void check(boolean condition, String label) { checks++; if (!condition) throw new AssertionError(label); }
    private static void rejects(Runnable action, String label) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, label);
    }
    private static int[] low() { return new int[] {1, 1, 1, 1, 1, 1}; }
    private static Song song(BandDecision app, int[] difficulty, int approvals) {
        Song song = app.getSongs().register("곡" + app.getSongs().getSongs().length, "아티스트", difficulty);
        for (int i = 0; i < 6; i++) song.vote(i, i < approvals ? Song.YES : Song.NO, "연습 시간 부족");
        return song;
    }
    private static void answerAll(AttendanceService attendance, LocalDate date, int available) {
        for (int i = 0; i < 6; i++) attendance.respond(i, date, i < available ? 1 : 2, "개인 일정");
    }
    private static void songs() {
        BandDecision app = new BandDecision();
        Member[] members = app.getMembers();
        int[] initial = {4, 3, 2, 1, 2, 3};
        for (int i = 0; i < 6; i++) check(members[i].getSkill() == initial[i] && Member.stars(initial[i]).length() == 5, "AC01 초깃값");
        rejects(() -> members[3].setSkill(0), "AC03 실력 0");
        rejects(() -> members[3].setSkill(6), "AC03 실력 6");
        members[3].setSkill(2);
        check(Member.stars(members[3].getSkill()).equals("★★☆☆☆"), "AC02 별점 변경");
        members[3].setSkill(1);
        rejects(() -> app.getSongs().register(" ", "가수", low()), "빈 제목");
        rejects(() -> app.getSongs().register("곡", " ", low()), "빈 아티스트");
        int[] bad = low(); bad[2] = 6;
        rejects(() -> app.getSongs().register("곡", "가수", bad), "AC27 난이도 오류");
        Song a = song(app, new int[] {4, 3, 4, 2, 2, 3}, 5);
        check(a.getId() == 1 && a.count(Song.YES) == 5 && a.count(Song.NO) == 1, "AC26/29 등록 집계");
        check(new SongRecommendation(a, members).getScore() == 9, "AC35 공식");
        a.setDifficulty(2, 3);
        check(a.getVote(5) == Song.NO && a.getDifficulty(3) == 2 && new SongRecommendation(a, members).getScore() == 11, "AC28/36 난이도 수정");
        a.vote(5, Song.YES, "");
        check(a.count(Song.NO) == 0 && a.getReason(5) == null, "AC30 반대 사유 삭제");
        rejects(() -> a.vote(5, Song.NO, " "), "AC31 빈 반대 사유");
        check(a.getVote(5) == Song.YES, "실패한 투표 유지");
        Song b = song(app, low(), 4);
        check(b.eligible(), "AC32 찬성 4명");
        Song waiting = app.getSongs().register("보류", "가수", low());
        for (int i = 0; i < 5; i++) waiting.vote(i, Song.YES, "");
        check(!waiting.eligible(), "AC33 미응답 보류");
        Song no = song(app, low(), 3);
        check(!no.eligible(), "AC34 찬성 부족");
        Song negative = song(app, new int[] {5, 5, 5, 5, 5, 5}, 4);
        check(Arrays.stream(SongRecommender.recommend(app.getSongs(), members)).anyMatch(r -> r.getSong() == negative && r.getScore() < 0), "AC38 음수 허용");
        Song tie = song(app, low(), 4);
        SongRecommendation[] ranked = SongRecommender.recommend(app.getSongs(), members);
        check(ranked[1].getSong() == b && ranked[2].getSong() == tie, "AC37 동점 ID 순");
        check(app.getSongs().voteResults().length == 6 && app.getSongs().voteResults()[0] == a, "모든 곡 투표 결과 정렬");
        BandDecision tieApp = new BandDecision();
        Song four = song(tieApp, low(), 4);
        Song six = song(tieApp, new int[] {4, 3, 4, 2, 2, 3}, 6);
        SongRecommendation[] tied = SongRecommender.recommend(tieApp.getSongs(), tieApp.getMembers());
        check(tied[0].getScore() == tied[1].getScore() && tied[0].getSong() == six && tied[1].getSong() == four,
            "AC37 동점 시 찬성 우선");
        members[2].setSkill(5);
        check(new SongRecommendation(a, members).getTotalBurden() == 1, "실력 변경 즉시 반영");
    }
    private static void attendance() {
        for (LocalDate day : new LocalDate[] {LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30),
                LocalDate.of(2026, 12, 31), LocalDate.of(2028, 1, 31)}) {
            AttendanceService fixed = new AttendanceService();
            LocalDate end = ScheduleWindow.end(day);
            check(ScheduleWindow.contains(day, day) && ScheduleWindow.contains(end, day)
                && !ScheduleWindow.contains(end.plusDays(1), day) && !ScheduleWindow.contains(day.minusDays(1), day),
                "일정 공통 범위의 시작·종료·바깥 경계 " + day);
            for (LocalDate date = day; !date.isAfter(end); date = date.plusDays(1)) fixed.respond(0, date, 1, "");
            check(fixed.isSubmitted(0, day) && !fixed.isSubmitted(1, day), "고정 기간 전체 응답 완료 " + day);
            fixed.respond(1, end, 2, "일정");
            check(fixed.isSubmitted(0, day), "다른 멤버 변경에 따른 완료 기준 불변 " + day);
            fixed.respond(0, end, 0, "");
            check(!fixed.isSubmitted(0, day), "마지막 하루 미응답이면 미완료 " + day);
            BandDecision boundary = DemoData.create(day);
            answerAll(boundary.getAttendance(), end, 6);
            check(boundary.preview(end, new int[] {1}, day).getDate().equals(end), "달력 마지막 날 최종 확정 허용 " + day);
            rejects(() -> boundary.preview(end.plusDays(1), new int[] {1}, day), "달력 범위 밖 확정 거절 " + day);
        }
        check(ScheduleWindow.end(LocalDate.of(2028, 1, 31)).equals(LocalDate.of(2028, 2, 29)), "윤년 다음 달 말일");
        AttendanceService service = new AttendanceService();
        check(DateRecommender.recommend(service, BASE).length == 0, "응답 없으면 빈 추천");
        check(service.response(0, BASE) == 0 && service.count(BASE, 0) == 6 && service.getDates().length == 0,
            "미기록 날짜는 전원 미응답이며 조회로 데이터 생성하지 않음");
        for (LocalDate date : new LocalDate[] {BASE.plusDays(2), BASE, BASE.plusDays(1), BASE}) service.respond(0, date, 0, "");
        check(service.getDates().length == 3 && service.getDates()[0].equals(BASE), "후보 중복 제거·날짜 정렬");
        check(service.response(0, BASE) == 0 && DateRecommender.attendance(service, BASE) == 0, "미응답은 가능이 아님");
        check(DateRecommender.recommend(service, BASE).length == 0, "전원 미응답 날짜 추천 제외");
        answerAll(service, BASE, 4); answerAll(service, BASE.plusDays(1), 6); answerAll(service, BASE.plusDays(2), 6);
        DateRecommendation[] ranked = DateRecommender.recommend(service, BASE);
        check(ranked.length == 3 && ranked[0].getDate().equals(BASE.plusDays(1)) && ranked[1].getDate().equals(BASE.plusDays(2))
            && ranked[2].getAttendance() == 4, "후보만 추천·전원 참석 우선·날짜 동률 정렬");
        service.respond(0, BASE, 0, "");
        check(!service.allSubmitted(BASE) && !service.isSubmitted(0, BASE), "미응답으로 돌리면 응답 미완료");
        service.respond(0, BASE, 1, "");
        service.respond(0, BASE, 1, "");
        check(service.response(0, BASE) == 1 && service.getDates().length == 3, "같은 날짜 재응답은 중복 기록을 만들지 않음");
        service.respond(0, BASE.plusDays(3), 0, "");
        check(DateRecommender.recommend(service, BASE).length == 3, "다른 날짜 미응답은 완료 날짜 추천을 차단하지 않음");
        service.removeDate(BASE.plusDays(3));
        check(!service.allSubmitted(BASE), "몇 개 기록만 응답해도 전체 기간 완료로 간주하지 않음");
        answerAll(service, BASE, 0);
        check(DateRecommender.recommend(service, BASE).length == 2, "전원 불가 후보 제외");
        service.respond(0, BASE, 1, "");
        check(DateRecommender.recommend(service, BASE)[2].getAttendance() == 1, "1명 가능 후보도 추천");
        service.respond(0, BASE, 2, "시험"); service.respond(0, BASE, 1, "");
        check(service.reason(0, BASE) == null, "가능 전환 시 불가 사유 제거");
        rejects(() -> service.respond(0, BASE, 3, ""), "잘못된 응답 거절");
        service.respond(0, BASE.plusDays(20), 1, "");
        check(service.response(0, BASE.plusDays(20)) == 1 && service.count(BASE.plusDays(20), 0) == 5,
            "별도 후보 등록 없이 날짜 응답 가능");
        int beforeInvalid = service.getDates().length;
        rejects(() -> service.respond(0, BASE.plusDays(21), 8, ""), "잘못된 최초 응답 거절");
        check(service.getDates().length == beforeInvalid, "잘못된 최초 응답은 기록을 만들지 않음");
        service.respond(0, BASE, 0, "");
        check(DateRecommender.recommend(service, BASE.plusDays(1)).length == 2, "다른 날짜·과거 미응답은 완료 날짜 추천에 영향 없음");
        check(DateRecommender.recommend(service, BASE.plusDays(1)).length == 2, "과거 후보 추천 제외");
        AttendanceService open = new AttendanceService();
        answerAll(open, ScheduleWindow.end(BASE), 6);
        check(DateRecommender.recommend(open, BASE)[0].getDate().equals(ScheduleWindow.end(BASE)),
            "사전 날짜 등록 없이 전원 응답하면 다음 달 말일 추천");
        answerAll(open, ScheduleWindow.end(BASE).plusDays(1), 6); answerAll(open, BASE.minusDays(1), 6);
        check(DateRecommender.recommend(open, BASE).length == 1,
            "보존된 기록 중 과거·다음 달 말일 이후는 추천 제외");
        LocalDate[] selected = {BASE, BASE.plusDays(1), BASE.plusDays(2)};
        open.respondMany(2, selected, AttendanceService.UNAVAILABLE, "연습 일정");
        check(open.response(2, selected[2]) == 2 && open.reason(2, selected[0]).equals("연습 일정")
            && open.response(1, selected[0]) == 0, "여러 날짜의 지정 멤버만 응답·사유 적용");
        open.changeReason(2, selected[0], "기존 사유");
        open.respondMany(2, selected, AttendanceService.UNAVAILABLE, "");
        check(open.reason(2, selected[0]).equals("기존 사유") && open.reason(2, selected[1]).equals("연습 일정"),
            "여러 날짜 불가 적용에서 사유 공란은 기존 사유 유지");
        rejects(() -> open.respondMany(2, new LocalDate[] {BASE.plusDays(10), null}, 1, ""), "다중 응답의 잘못된 날짜 거절");
        check(open.response(2, BASE.plusDays(10)) == 0, "다중 응답 전체 검증 후 반영");
        rejects(() -> open.respondMany(6, selected, 1, ""), "다중 응답 잘못된 멤버 거절");
        rejects(() -> open.respondMany(0, new LocalDate[0], 1, ""), "선택 날짜 없는 다중 응답 거절");
        open.respondMany(2, selected, AttendanceService.AVAILABLE, "");
        check(open.reason(2, selected[0]) == null && open.response(2, selected[2]) == 1, "다중 가능 적용 시 불가 사유 제거");
        open.respondMany(2, selected, AttendanceService.UNANSWERED, "");
        check(open.count(selected[0], AttendanceService.UNANSWERED) == 6, "다중 응답 미응답 복귀");
    }
    private static void plans() {
        BandDecision app = DemoData.create(BASE);
        int[] ids = {1, 2};
        RehearsalPlan preview = app.preview(BASE, ids, BASE);
        rejects(() -> app.confirm(BASE, ids, BASE, preview, false), "부분 참석 동의 필요");
        app.confirm(BASE, ids, BASE, preview, true);
        RehearsalPlan first = app.findPlan(1).plan();
        check(first.getAttendees().length == 4 && first.getAbsentees().length == 2, "가능/불가에 따른 확정 스냅샷");
        app.getAttendance().respond(0, BASE, 2, "시험");
        check(first.getAttendees().length == 4, "응답 수정 후 기존 확정 보존");
        rejects(() -> app.confirm(BASE, ids, BASE, preview, true), "최종 확인 후 응답 변경 재검증");
        rejects(() -> app.preview(BASE.plusDays(1), ids, BASE), "후보가 아닌 날짜 확정 거절");
        rejects(() -> app.preview(BASE, new int[0], BASE), "곡 0개 거절");
        rejects(() -> app.preview(BASE, new int[] {1, 1}, BASE), "중복 곡 거절");
        rejects(() -> app.preview(BASE, new int[] {3}, BASE), "미추천 곡 거절");
        app.getAttendance().respond(0, BASE, 0, "");
        rejects(() -> app.preview(BASE, ids, BASE), "선택 날짜 미응답은 최종 확정 차단");
        check(app.preview(BASE.plusDays(4), ids, BASE).getAttendees().length == 6, "다른 날짜 미응답이 있어도 완료 날짜 확정 가능");
        check(app.findPlan(1).plan() == first, "실패 시 확정 계획 유지");
        app.getAttendance().respond(0, BASE, 1, "");
        RehearsalPlan next = app.preview(BASE.plusDays(4), ids, BASE);
        app.confirm(BASE.plusDays(4), ids, BASE, next, false);
        check(app.findPlan(2).plan().getAttendees().length == 6, "전원 참석 후보 재확정");
        app.getAttendance().removeDate(BASE.plusDays(4));
        check(app.findPlan(2).plan().sameContent(next), "후보 삭제 후 확정 기록 유지");
    }
    private static void persistence() throws Exception {
        Path directory = Files.createTempDirectory("banddecision-candidate-test-");
        Path file = directory.resolve("data.properties"); DataStore store = new DataStore(file);
        try {
            BandDecision app = DemoData.create(BASE);
            RehearsalPlan preview = app.preview(BASE, new int[] {1, 2}, BASE);
            app.confirm(BASE, new int[] {1, 2}, BASE, preview, true);
            String fixedPlan = app.findPlan(1).plan().describe();
            app.getAttendance().respond(0, BASE, 0, "");
            app.getAttendance().changeReason(5, BASE, "한글 = 사유\n일정");
            app.getMembers()[3].setSkill(4); store.save(app);
            BandDecision restored = store.load();
            check(restored.getAttendance().getDates().length == 7 && restored.getAttendance().response(0, BASE) == 0,
                "후보와 미응답 복원");
            check(restored.getAttendance().reason(5, BASE).equals("한글 = 사유\n일정"), "한글 사유 복원");
            check(restored.getMembers()[3].getSkill() == 4 && restored.getSongs().getSongs().length == 4, "실력과 곡 복원");
            check(restored.findPlan(1).plan().describe().equals(fixedPlan), "독립 확정 기록 복원");
            check(restored.getSongs().find(4).count(Song.UNANSWERED) == 1, "곡 투표 미응답 복원");
            Properties p = new Properties();
            try (java.io.Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { p.load(reader); }
            p.setProperty("version", "1");
            legacyPlanKeys(p);
            try (java.io.Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) { p.store(writer, "legacy fixture"); }
            byte[] legacy = Files.readAllBytes(file);
            BandDecision migrated = store.load();
            check(migrated.getAttendance().getDates().length == 0 && migrated.getSongs().getSongs().length == 4
                && migrated.findPlan(1).plan().describe().equals(fixedPlan), "구버전 곡·투표·확정 유지, 가능 여부 추측 금지");
            store.save(migrated);
            check(Arrays.equals(legacy, Files.readAllBytes(directory.resolve("data.properties.v1.bak"))), "기존 파일 원본 백업");
            check(!store.isLegacy() && Files.readString(file).contains("version=3"), "v3 저장 전환");
            Files.writeString(file, "version=2\nmember.0.skill=wrong");
            boolean failed = false;
            try { store.load(); } catch (java.io.IOException e) { failed = true; }
            check(failed && Files.readString(file).contains("wrong"), "손상 파일을 덮어쓰지 않음");
            String malformed = "version=3\ninvalid=" + '\\' + "uZZZZ\n";
            Files.writeString(file, malformed);
            boolean saveRejected = false;
            try { store.save(app); } catch (java.io.IOException e) { saveRejected = true; }
            check(saveRejected && malformed.equals(Files.readString(file)),
                "잘못된 문자 이스케이프 저장 실패를 IOException으로 안내·원본 보존");
            rejectsLoad(store, "잘못된 문자 이스케이프 복원 실패도 IOException");
            boolean legacyRejected = false;
            try { store.isLegacy(); } catch (java.io.IOException e) { legacyRejected = true; }
            check(legacyRejected, "잘못된 문자 이스케이프 버전 조회 실패도 IOException");
        } finally {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) { for (Path entry : files) Files.delete(entry); }
            Files.delete(directory);
        }
    }
    private static void songManagement() throws Exception {
        BandDecision app = DemoData.create(BASE);
        SongService service = app.getSongs();
        RehearsalPlan approved = app.preview(BASE, new int[] {1, 2}, BASE);
        app.confirm(BASE, new int[] {1, 2}, BASE, approved, true);
        String fixedPlan = app.findPlan(1).plan().describe();
        int[] difficulty = low();
        service.update(1, "  수정한 제목  ", "수정한 가수", difficulty);
        Song first = service.find(1);
        check(first.getTitle().equals("수정한 제목") && first.getArtist().equals("수정한 가수") && first.getId() == 1,
            "제목·아티스트 수정, ID 유지");
        check(first.count(Song.YES) == 5 && first.getReason(5).equals("후렴 음역이 부담됩니다."), "곡 수정 후 투표와 반대 사유 유지");
        difficulty[0] = 5;
        check(first.getDifficulty(0) == 1 && new SongRecommendation(first, app.getMembers()).getScore() == 15,
            "난이도 복사와 수정된 값으로 재추천");
        rejects(() -> service.update(1, "다른 제목", " ", low()), "빈 아티스트 수정 거절");
        rejects(() -> service.update(1, " ", "다른 가수", low()), "빈 제목 수정 거절");
        int[] invalid = low(); invalid[5] = 6;
        rejects(() -> service.update(1, "다른 제목", "다른 가수", invalid), "잘못된 난이도 수정 거절");
        check(first.getTitle().equals("수정한 제목") && first.getArtist().equals("수정한 가수") && first.getDifficulty(5) == 1,
            "수정 실패 시 기존 모든 정보 유지");
        check(app.findPlan(1).plan().describe().equals(fixedPlan), "곡 제목 수정 후 확정 스냅샷 보존");
        service.delete(2);
        rejects(() -> service.find(2), "삭제한 곡 조회 거절");
        check(Arrays.equals(Arrays.stream(service.getSongs()).mapToInt(Song::getId).toArray(), new int[] {1, 3, 4}), "삭제 후 다른 곡 ID 유지");
        check(Arrays.stream(SongRecommender.recommend(service, app.getMembers())).noneMatch(r -> r.getSong().getId() == 2), "삭제 곡 추천 제외");
        rejects(() -> service.delete(99), "존재하지 않는 곡 삭제 거절");
        rejects(() -> service.update(99, "제목", "가수", low()), "존재하지 않는 곡 수정 거절");
        service.delete(4);
        Song next = service.register("새 곡", "가수", low());
        check(next.getId() == 5, "마지막 곡 삭제 후에도 ID 재사용 금지");
        service.delete(5);
        Path directory = Files.createTempDirectory("banddecision-song-test-");
        Path file = directory.resolve("songs.properties"); DataStore store = new DataStore(file);
        try {
            store.save(app);
            BandDecision restored = store.load();
            check(restored.getSongs().find(3).getId() == 3 && restored.getSongs().find(1).getTitle().equals("수정한 제목"), "ID가 비연속인 곡과 수정 정보 복원");
            check(restored.getSongs().getNextId() == 6 && restored.getSongs().register("복원 후 곡", "가수", low()).getId() == 6,
                "재실행 후에도 마지막 삭제 ID 재사용 금지");
            service.delete(1); store.save(app);
            check(store.load().findPlan(1).plan().describe().equals(fixedPlan), "곡 삭제 후 재실행해도 확정 당시 곡 보존");
            rejects(() -> app.preview(BASE, new int[] {1}, BASE), "삭제 곡 신규 확정 거절");
            service.delete(3); store.save(app);
            BandDecision empty = store.load();
            check(empty.getSongs().getSongs().length == 0 && empty.getSongs().register("다시 등록", "가수", low()).getId() == 6,
                "전부 삭제·재실행 후에도 다음 ID 보존");
        } finally { Files.deleteIfExists(file); Files.delete(directory); }

    }
    private static void legacyPlanKeys(Properties p) {
        boolean present = Integer.parseInt(p.getProperty("plans", "0")) > 0;
        p.setProperty("plan.present", "" + present);
        for (String key : p.stringPropertyNames().toArray(new String[0]))
            if (key.startsWith("plan.0.")) p.setProperty("plan." + key.substring("plan.0.".length()), p.getProperty(key));
    }
    private static void rejectsLoad(DataStore store, String label) {
        boolean rejected = false; try { store.load(); } catch (java.io.IOException ex) { rejected = true; }
        check(rejected, label);
    }
    private static void planManagement() throws Exception {
        BandDecision app = DemoData.create(BASE); int[] ids = {1, 2};
        for (int i = 0; i < 5; i++) {
            LocalDate date = BASE.plusDays(i * 2);
            RehearsalPlan approved = app.preview(date, ids, BASE);
            app.confirm(date, ids, BASE, approved, true);
        }
        check(app.getConfirmedPlans().length == 5 && app.getConfirmedPlans()[4].id() == 5, "확정 계획 5개 추가·ID 유지");
        String first = app.findPlan(1).plan().describe();
        RehearsalPlan extra = app.preview(BASE.plusDays(10), ids, BASE);
        check(app.getConfirmedPlans().length == 5 && app.findPlan(1).plan().describe().equals(first), "기존 계획 내용 보존");
        RehearsalPlan before = app.findPlan(3).plan();
        rejects(() -> app.updatePlan(3, BASE.plusDays(10), ids, BASE, extra, false), "5개 상태의 수정도 부분 참석 동의 필요");
        check(app.findPlan(3).plan() == before, "실패한 수정은 기존 스냅샷 유지");
        app.getAttendance().respond(0, BASE.plusDays(10), 0, "");
        rejects(() -> app.updatePlan(3, BASE.plusDays(10), ids, BASE, extra, true), "계획 수정도 최신 응답 재검증");
        app.getAttendance().respond(0, BASE.plusDays(10), 1, "");
        RehearsalPlan changed = app.preview(BASE.plusDays(10), new int[] {2}, BASE);
        app.updatePlan(3, BASE.plusDays(10), new int[] {2}, BASE, changed, true);
        check(app.getConfirmedPlans().length == 5 && app.findPlan(3).plan().sameContent(changed)
            && app.findPlan(1).plan().describe().equals(first), "선택 계획만 ID 유지하며 수정");
        rejects(() -> app.updatePlan(99, BASE, ids, BASE, extra, true), "없는 계획 수정 거절");
        rejects(() -> app.deletePlan(99), "없는 계획 삭제 거절");
        app.deletePlan(2);
        rejects(() -> app.findPlan(2), "삭제 계획 조회 거절");
        RehearsalPlan next = app.preview(BASE.plusDays(12), ids, BASE);
        app.confirm(BASE.plusDays(12), ids, BASE, next, true);
        check(Arrays.equals(Arrays.stream(app.getConfirmedPlans()).mapToInt(ConfirmedPlan::id).toArray(), new int[] {1, 3, 4, 5, 6}),
            "중간 계획 삭제 후 나머지 ID·순서 유지·새 계획은 새 ID");
        ConfirmedPlan[] copy = app.getConfirmedPlans(); copy[0] = null;
        check(app.findPlan(1).plan().describe().equals(first), "계획 배열 외부 변경 방어");
        rejects(() -> app.restorePlan(1, next), "최대 개수·중복 ID 복원 거절");
        Path directory = Files.createTempDirectory("banddecision-plans-");
        Path file = directory.resolve("plans.properties"); DataStore store = new DataStore(file);
        try {
            store.save(app); BandDecision restored = store.load();
            check(restored.getConfirmedPlans().length == 5 && restored.findPlan(3).plan().sameContent(changed)
                && restored.getNextPlanId() == 7, "5개 계획·수정 기록·다음 ID 저장 복원");
            app.getSongs().delete(1);
            check(app.findPlan(1).plan().describe().equals(first), "곡 삭제 후에도 여러 계획의 과거 스냅샷 유지");
            for (ConfirmedPlan entry : app.getConfirmedPlans()) app.deletePlan(entry.id());
            store.save(app); BandDecision empty = store.load();
        check(empty.getConfirmedPlans().length == 0 && empty.getNextPlanId() == 7,
                "전체 계획 삭제와 다음 ID 복원");
            RehearsalPlan again = empty.preview(BASE.plusDays(4), new int[] {2}, BASE);
            empty.confirm(BASE.plusDays(4), new int[] {2}, BASE, again, false);
            check(empty.getConfirmedPlans()[0].id() == 7, "전체 삭제 후에도 계획 ID 재사용 금지");
            store.save(empty);
            Properties old = new Properties();
            try (java.io.Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { old.load(reader); }
            legacyPlanKeys(old); old.setProperty("version", "2");
            try (java.io.Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) { old.store(writer, "v2 fixture"); }
            byte[] original = Files.readAllBytes(file);
            BandDecision migrated = store.load();
            check(migrated.getConfirmedPlans().length == 1 && migrated.findPlan(1).plan().sameContent(again)
                && migrated.getAttendance().getDates().length == 7, "v2 단일 계획을 목록 1개로 변환·응답 유지");
            store.save(migrated);
            check(Arrays.equals(original, Files.readAllBytes(directory.resolve("plans.properties.v2.bak"))), "v2 파일 v3 저장 전 원본 백업");
            Properties invalid = new Properties();
            try (java.io.Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { invalid.load(reader); }
            invalid.setProperty("plans", "11");
            try (java.io.Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) { invalid.store(writer, "invalid"); }
            rejectsLoad(store, "10개 한도 초과 계획 파일 거부");
            invalid.setProperty("plans", "1"); invalid.setProperty("plans.nextId", "1");
            try (java.io.Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) { invalid.store(writer, "invalid"); }
            rejectsLoad(store, "잘못된 다음 계획 ID 파일 거부");
        } finally {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) { for (Path entry : files) Files.delete(entry); }
            Files.delete(directory);
        }

    }
    private static void dynamicMembers() throws Exception {
        BandDecision app = new BandDecision();
        LocalDate day = LocalDate.now().plusDays(1);
        for (int i = 0; i < 6; i++) app.getAttendance().respond(i, day, AttendanceService.AVAILABLE, "");
        Song song = app.getSongs().register("추가 멤버 검사", "밴드", new int[] {2, 2, 2, 2, 2, 2});
        for (int i = 0; i < 6; i++) song.vote(i, Song.YES, "");
        check(DateRecommender.recommend(app.getAttendance(), LocalDate.now()).length == 1 && song.eligible(),
            "기존 6명 응답·투표 완료");
        app.addMember("민수", "기타", 3);
        check(app.getMembers().length == 7 && app.getAttendance().getMemberCount() == 7
            && app.getMembers()[6].displayName().equals("민수 · 기타") && song.getDifficulty(6) == 3,
            "같은 파트 7번째 멤버 추가와 기본 난이도");
        check(DateRecommender.recommend(app.getAttendance(), LocalDate.now()).length == 0
            && !song.eligible() && song.getVote(6) == Song.UNANSWERED,
            "새 멤버의 미응답은 날짜·곡 추천에서 제외");
        app.getAttendance().respond(6, day, AttendanceService.AVAILABLE, ""); song.vote(6, Song.YES, "");
        check(DateRecommender.recommend(app.getAttendance(), LocalDate.now()).length == 1
            && SongRecommender.recommend(app.getSongs(), app.getMembers()).length == 1,
            "7명 전원 응답 후 날짜·곡 추천 복귀");
        Song threshold = app.getSongs().register("찬성 기준 검사", "밴드", new int[] {2, 2, 2, 2, 2, 2, 2});
        for (int i = 0; i < 7; i++) threshold.vote(i, i < 4 ? Song.YES : Song.NO, i < 4 ? "" : "의견 다름");
        check(!threshold.eligible(), "7명일 때 찬성 4명은 2/3 미만이라 추천 제외");
        threshold.vote(4, Song.YES, "");
        check(threshold.eligible(), "7명일 때 찬성 5명은 2/3 이상이라 추천");
        RehearsalPlan sevenMemberPlan = app.preview(day, new int[] {song.getId()}, LocalDate.now(), "합주실");
        app.confirm(day, new int[] {song.getId()}, LocalDate.now(), "합주실", sevenMemberPlan, false);
        check(sevenMemberPlan.getAttendees().length == 7
            && java.util.Arrays.asList(sevenMemberPlan.getAttendees()).contains("민수 · 기타"),
            "7명 확정 계획에 같은 파트 멤버 이름 보존");
        app.getMembers()[0].setPart("보컬");
        Path directory = Files.createTempDirectory("banddecision-members-");
        try {
            DataStore store = new DataStore(directory.resolve("members.properties")); store.save(app);
            BandDecision restored = store.load();
            check(restored.getMembers().length == 7 && restored.getMembers()[0].getPart().equals("보컬")
                && restored.getMembers()[6].displayName().equals("민수 · 기타") && restored.getSongs().find(song.getId()).getVote(6) == Song.YES
                && restored.getAttendance().response(6, day) == AttendanceService.AVAILABLE
                && restored.findPlan(1).plan().getAttendees().length == 7,
                "7명 파트·투표·일정 저장 후 복원");
        } finally { Files.deleteIfExists(directory.resolve("members.properties")); Files.delete(directory); }
    }
    private static void memberLimit() throws Exception {
        BandDecision app = new BandDecision();
        LocalDate day = LocalDate.now().plusDays(1);
        Song song = app.getSongs().register("12명 검사", "밴드", low());
        while (app.getMembers().length < 12) app.addMember("추가 멤버", "기타", 3);
        check(app.getMembers().length == 12 && app.getAttendance().getMemberCount() == 12
            && song.getDifficulty(11) == 3 && song.count(Song.UNANSWERED) == 12, "12명 허용·응답과 난이도 배열 동기화");
        rejects(() -> app.addMember("13번째", "기타", 2), "13번째 멤버 추가 거부");
        check(app.getMembers().length == 12 && app.getAttendance().getMemberCount() == 12
            && song.getDifficulty(11) == 3 && song.count(Song.UNANSWERED) == 12, "상한 초과 실패 시 기존 배열 보존");
        for (int i = 0; i < 12; i++) {
            app.getAttendance().respond(i, day, AttendanceService.AVAILABLE, "");
            song.vote(i, i < 7 ? Song.YES : Song.NO, "연습 부족");
        }
        check(DateRecommender.recommend(app.getAttendance(), LocalDate.now())[0].getAttendance() == 12,
            "12명 전원 응답 날짜 추천");
        check(!song.eligible(), "12명 중 찬성 7명은 추천 제외");
        song.vote(7, Song.YES, "");
        check(song.eligible(), "12명 중 찬성 8명은 추천 허용");
        Path directory = Files.createTempDirectory("banddecision-limit-");
        Path file = directory.resolve("members.properties");
        try {
            DataStore store = new DataStore(file); store.save(app);
            BandDecision restored = store.load();
            check(restored.getMembers().length == 12 && restored.getAttendance().response(11, day) == AttendanceService.AVAILABLE
                && restored.getSongs().find(song.getId()).count(Song.YES) == 8, "12명 일정·곡·투표 저장 복원");
            Properties invalid = new Properties();
            try (java.io.Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { invalid.load(reader); }
            invalid.setProperty("members", "13");
            try (java.io.Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) { invalid.store(writer, "invalid member count"); }
            byte[] original = Files.readAllBytes(file);
            rejectsLoad(store, "13명 저장 파일 읽기 거부");
            check(Arrays.equals(original, Files.readAllBytes(file)), "인원 초과 파일 거부 시 원본 보존");
        } finally { Files.deleteIfExists(file); Files.delete(directory); }
    }
    private static void smallBands() throws Exception {
        BandDecision app = new BandDecision();
        LocalDate day = LocalDate.now().plusDays(1);
        Song song = app.getSongs().register("소규모 합주", "밴드", new int[] {1, 2, 3, 4, 5, 1});
        for (int i = 0; i < 6; i++) {
            app.getAttendance().respond(i, day, i == 2 ? AttendanceService.UNAVAILABLE : AttendanceService.AVAILABLE, "수업");
            song.vote(i, i == 2 || i == 4 ? Song.NO : Song.YES, i == 2 ? "어려움" : i == 4 ? "장비" : "");
        }
        app.getAttendance().respond(3, day.plusDays(1), AttendanceService.UNAVAILABLE, "아르바이트");
        RehearsalPlan approved = app.preview(day, new int[] {song.getId()}, LocalDate.now());
        app.confirm(day, new int[] {song.getId()}, LocalDate.now(), approved, true);
        app.removeMember(2);
        check(app.getMembers().length == 5 && app.getAttendance().getMemberCount() == 5
            && app.getMembers()[2].getPart().equals("베이스") && song.getDifficulty(2) == 4
            && song.getVote(2) == Song.YES && app.getAttendance().response(2, day) == AttendanceService.AVAILABLE,
            "가운데 멤버 삭제 후 일정·난이도·투표가 같은 사람에게 연결됨");
        check(app.getAttendance().reason(2, day.plusDays(1)).equals("아르바이트")
            && song.getVote(3) == Song.NO && song.getReason(3).equals("장비"),
            "삭제 뒤 남은 멤버의 불가 사유·반대 사유 보존");
        check(app.findPlan(1).plan().getAttendees().length == 5
            && app.findPlan(1).plan().getAbsentees().length == 1
            && DateRecommender.recommend(app.getAttendance(), LocalDate.now())[0].getAttendance() == 5,
            "기존 확정 기록은 보존하고 날짜 추천은 현재 5명으로 재계산");
        app.removeMember(0);
        check(app.getMembers()[0].getPart().equals("여성 보컬") && song.getDifficulty(0) == 2,
            "첫 멤버 삭제 후 순서 재정렬");
        while (app.getMembers().length > 1) app.removeMember(app.getMembers().length - 1);
        check(app.getMembers().length == 1 && song.eligible()
            && DateRecommender.recommend(app.getAttendance(), LocalDate.now())[0].getAttendance() == 1,
            "1명 밴드의 곡·날짜 추천");
        check(SongRecommender.recommend(app.getSongs(), app.getMembers()).length == 1
            && app.preview(day, new int[] {song.getId()}, LocalDate.now()).getAttendees().length == 1,
            "1명 밴드의 곡 추천과 합주 계획 미리보기");
        rejects(() -> app.removeMember(0), "마지막 멤버 삭제 거부");
        rejects(() -> app.removeMember(1), "범위 밖 멤버 삭제 거부");
        Path directory = Files.createTempDirectory("banddecision-small-band-");
        try {
            DataStore store = new DataStore(directory.resolve("small.properties"));
            store.save(app);
            BandDecision restored = store.load();
            check(restored.getMembers().length == 1 && restored.getMembers()[0].getPart().equals("여성 보컬")
                && restored.getAttendance().response(0, day) == AttendanceService.AVAILABLE
                && restored.getSongs().find(song.getId()).getDifficulty(0) == 2
                && restored.findPlan(1).plan().getAbsentees().length == 1,
                "1명 밴드 저장·복원과 과거 계획 보존");
            restored.addMember("새 멤버", "여성 보컬", 2);
            Song restoredSong = restored.getSongs().find(song.getId());
            check(restored.getMembers().length == 2 && restoredSong.getDifficulty(1) == 3
                && restoredSong.getVote(1) == Song.UNANSWERED
                && restored.getAttendance().response(1, day) == AttendanceService.UNANSWERED
                && !restoredSong.eligible(), "1명에서 같은 파트 2명으로 늘리면 새 멤버는 미응답");
            store.save(restored);
            check(store.load().getMembers().length == 2 && store.load().getSongs().find(song.getId()).getDifficulty(1) == 3,
                "2명 밴드 저장·복원");
        } finally { Files.deleteIfExists(directory.resolve("small.properties")); Files.delete(directory); }
    }
    private static void planLocation() throws Exception {
        BandDecision app = new BandDecision();
        LocalDate day = LocalDate.now().plusDays(1);
        for (int i = 0; i < 6; i++) app.getAttendance().respond(i, day, AttendanceService.AVAILABLE, "");
        Song song = app.getSongs().register("장소 검사곡", "밴드", new int[] {2, 2, 2, 2, 2, 2});
        for (int i = 0; i < 6; i++) song.vote(i, Song.YES, "");
        int[] ids = {song.getId()};
        RehearsalPlan approved = app.preview(day, ids, LocalDate.now(), "  연습실 A  ");
        check(approved.getLocation().equals("연습실 A") && approved.describe().contains("장소: 연습실 A"),
            "합주 장소 공백 정리·상세 표시");
        rejects(() -> app.confirm(day, ids, LocalDate.now(), "연습실 B", approved, false),
            "확인 후 장소가 바뀌면 확정 거부");
        app.confirm(day, ids, LocalDate.now(), "연습실 A", approved, false);
        Path directory = Files.createTempDirectory("banddecision-location-");
        try {
            DataStore store = new DataStore(directory.resolve("location.properties")); store.save(app);
            BandDecision restored = store.load();
            check(restored.findPlan(1).plan().getLocation().equals("연습실 A"), "합주 장소 저장·복원");
            Properties previousFormat = new Properties();
            try (java.io.Reader reader = Files.newBufferedReader(store.getFile(), StandardCharsets.UTF_8)) {
                previousFormat.load(reader);
            }
            previousFormat.remove("members"); previousFormat.remove("plan.0.location");
            for (int i = 0; i < 6; i++) {
                previousFormat.remove("member." + i + ".name");
                previousFormat.remove("member." + i + ".part");
            }
            Path oldFile = directory.resolve("old-v3.properties");
            try (java.io.Writer writer = Files.newBufferedWriter(oldFile, StandardCharsets.UTF_8)) {
                previousFormat.store(writer, "old v3 without member and location properties");
            }
            BandDecision old = new DataStore(oldFile).load();
            check(old.getMembers().length == 6 && old.findPlan(1).plan().getLocation().isEmpty(),
                "이전 v3 파일에 멤버 수·장소 속성이 없어도 6명·장소 미정으로 복원");
            RehearsalPlan changed = restored.preview(day, ids, LocalDate.now(), "연습실 B");
            restored.updatePlan(1, day, ids, LocalDate.now(), "연습실 B", changed, false);
            check(restored.findPlan(1).plan().getLocation().equals("연습실 B"), "기존 계획 장소 수정");
        } finally { Files.deleteIfExists(directory.resolve("location.properties"));
            Files.deleteIfExists(directory.resolve("old-v3.properties")); Files.delete(directory); }
    }
    private static void nonPerformingPart() throws Exception {
        BandDecision app = new BandDecision();
        Song song = app.getSongs().register("여성 보컬 곡", "밴드", new int[] {0,3,2,1,2,3});
        check(song.getDifficulty(0) == 0 && Song.difficultyLabel(0).equals("연주 안 함"), "난이도 0 등록·연주 안 함 표시");
        for (int i=0;i<5;i++) song.vote(i, Song.YES, "");
        check(!song.eligible(), "연주 안 함이어도 전원 투표 조건 유지");
        song.vote(5, Song.YES, "");
        SongRecommendation score = new SongRecommendation(song, app.getMembers());
        check(song.eligible() && score.getBurdens()[0] == 0 && score.getTotalBurden() == 0 && score.getScore() == 18,
            "0 난이도 부담은 0·추천 점수 정상 계산");
        song.setDifficulty(0, 2); song.setDifficulty(0, 0);
        check(song.getDifficulty(0) == 0 && song.count(Song.YES) == 6, "연주 참여·미참여 전환 후 투표 유지");
        rejects(() -> song.setDifficulty(0, -1), "음수 곡 난이도 거부");
        rejects(() -> song.setDifficulty(0, 6), "6 이상 곡 난이도 거부");
        check(song.getDifficulty(0) == 0, "잘못된 난이도 입력 후 기존 0 유지");
        rejects(() -> app.getSongs().update(song.getId(), "변경 제목", "변경 가수", new int[] {-1,1,1,1,1,1}),
            "잘못된 전체 수정 거부");
        check(song.getTitle().equals("여성 보컬 곡") && song.getDifficulty(0) == 0, "실패한 전체 수정에서 제목과 난이도 보존");
        Song empty = app.getSongs().register("연주자 없음", "밴드", new int[] {0,0,0,0,0,0});
        for (int i=0;i<6;i++) empty.vote(i, Song.YES, "");
        check(!empty.eligible(), "전원이 연주 안 함이면 추천 제외");
        empty.setDifficulty(1, 1);
        check(empty.eligible(), "한 명 이상 연주하면 기존 투표 조건으로 추천");
        Path folder = Files.createTempDirectory("banddecision-zero-difficulty-");
        Path file = folder.resolve("songs.properties");
        try {
            DataStore store = new DataStore(file); store.save(app); BandDecision loaded = store.load();
            check(loaded.getSongs().find(song.getId()).getDifficulty(0) == 0
                && loaded.getSongs().find(song.getId()).count(Song.YES) == 6,
                "연주 안 함과 투표 저장 복원");
        } finally { Files.deleteIfExists(file); Files.delete(folder); }
    }
    private static void fixedDemoDate() {
        AppDate.configure(true);
        try {
            check(AppDate.today().equals(LocalDate.of(2026,12,9)), "Demo 오늘은 2026-12-09로 고정");
            check(ScheduleWindow.end(AppDate.today()).equals(LocalDate.of(2027,1,31)), "고정 Demo 추천 범위는 2027-01-31까지");
            BandDecision app = DemoData.createPresentation(AppDate.today());
            check(app.getAttendance().isSubmitted(0) && !app.getAttendance().isSubmitted(5), "응답 완료 판단도 고정 기준일 사용");
            check(app.getAttendance().getDates()[0].equals(AppDate.DEMO_DATE)
                && app.getConfirmedPlans().length == 2, "고정 기준일의 일정·확정 계획 생성");
        } finally { AppDate.configure(false); }
        check(AppDate.today().equals(LocalDate.now()), "일반 모드는 실제 날짜로 복귀");
    }
    public static void main(String[] args) throws Exception {
        fixedDemoDate();
        for (LocalDate today : new LocalDate[] {LocalDate.now(), LocalDate.of(2026, 12, 31), LocalDate.of(2028, 2, 29)}) {
            BandDecision demo = DemoData.createPresentation(today);
            check(demo.getSongs().getSongs().length == 8 && demo.getConfirmedPlans().length == 2,
                "발표 샘플 8곡·2계획 생성: " + today);
            check(demo.getAttendance().isSubmitted(0, today) && !demo.getAttendance().isSubmitted(5, today),
                "발표 샘플 완료·미완료 비교: " + today);
            check(demo.getAttendance().count(today.plusDays(2), AttendanceService.AVAILABLE) == 6
                && demo.getAttendance().count(today.plusDays(4), AttendanceService.UNAVAILABLE) == 6
                && demo.getAttendance().count(today.plusDays(6), AttendanceService.UNANSWERED) == 3,
                "발표 샘플 전원가능·전원불가·미응답: " + today);
            check(SongRecommender.recommend(demo.getSongs(), demo.getMembers()).length == 4,
                "발표 샘플 추천 자격 곡 4개: " + today);
        }
        songs(); attendance(); plans(); persistence();
        songManagement(); planManagement(); dynamicMembers(); memberLimit(); smallBands(); planLocation(); nonPerformingPart();
        System.out.println("PASS: " + checks + " checks (scheduling + songs + persistence)");
    }
}
