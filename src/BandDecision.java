import java.time.LocalDate;
import java.util.Arrays;

/** UI와 분리된 애플리케이션 상태 및 확정 검증. */
public final class BandDecision {
    private Member[] members = {
        new Member("남성 보컬", 4), new Member("여성 보컬", 3), new Member("기타", 2),
        new Member("베이스", 1), new Member("드럼", 2), new Member("건반", 3)
    };
    private final AttendanceService attendance = new AttendanceService();
    private final SongService songs = new SongService();
    public static final int MAX_PLANS = 5;
    private ConfirmedPlan[] confirmedPlans = new ConfirmedPlan[0];
    private int nextPlanId = 1;
    public Member[] getMembers() { return members.clone(); }
    public void addMember(String part, int skill) {
        addMember("", part, skill);
    }
    public void addMember(String name, String part, int skill) {
        if (members.length >= 64) throw new IllegalArgumentException("멤버는 최대 64명까지 추가할 수 있습니다.");
        Member added = new Member(name, part, skill);
        attendance.addMember();
        songs.addMember();
        members = Arrays.copyOf(members, members.length + 1);
        members[members.length - 1] = added;
    }
    public AttendanceService getAttendance() { return attendance; }
    public SongService getSongs() { return songs; }
    public ConfirmedPlan[] getConfirmedPlans() { return confirmedPlans.clone(); }
    public int getNextPlanId() { return nextPlanId; }
    public ConfirmedPlan findPlan(int id) {
        for (ConfirmedPlan entry : confirmedPlans) if (entry.id() == id) return entry;
        throw new IllegalArgumentException("존재하지 않는 계획 ID입니다.");
    }
    void restorePlan(int id, RehearsalPlan plan) {
        if (confirmedPlans.length >= MAX_PLANS) throw new IllegalArgumentException("확정 계획은 최대 5개까지 저장할 수 있습니다.");
        for (ConfirmedPlan entry : confirmedPlans) if (entry.id() == id) throw new IllegalArgumentException("중복 계획 ID입니다.");
        ConfirmedPlan entry = new ConfirmedPlan(id, plan);
        confirmedPlans = Arrays.copyOf(confirmedPlans, confirmedPlans.length + 1);
        confirmedPlans[confirmedPlans.length - 1] = entry; nextPlanId = Math.max(nextPlanId, id + 1);
    }
    void restoreNextPlanId(int value) {
        if (value < nextPlanId) throw new IllegalArgumentException("다음 계획 ID가 올바르지 않습니다.");
        nextPlanId = value;
    }
    public void deletePlan(int id) {
        findPlan(id);
        ConfirmedPlan[] next = new ConfirmedPlan[confirmedPlans.length - 1]; int index = 0;
        for (ConfirmedPlan entry : confirmedPlans) if (entry.id() != id) next[index++] = entry;
        confirmedPlans = next;
    }
    public RehearsalPlan preview(LocalDate date, int[] ids, LocalDate today) {
        return preview(date, ids, today, "");
    }
    public RehearsalPlan preview(LocalDate date, int[] ids, LocalDate today, String location) {
        if (!ScheduleWindow.contains(date, today))
            throw new IllegalArgumentException("확정 날짜는 오늘부터 다음 달 말일까지여야 합니다.");
        if (attendance.count(date, AttendanceService.UNANSWERED) > 0)
            throw new IllegalArgumentException("선택한 날짜에 전원 응답이 필요합니다.");
        if (DateRecommender.attendance(attendance, date) == 0) throw new IllegalArgumentException("참석 가능 인원이 없습니다.");
        if (ids == null || ids.length == 0) throw new IllegalArgumentException("곡을 1개 이상 선택하세요.");
        Song[] selected = new Song[ids.length];
        for (int i = 0; i < ids.length; i++) {
            for (int j = 0; j < i; j++) if (ids[i] == ids[j]) throw new IllegalArgumentException("중복 곡 ID입니다.");
            selected[i] = songs.find(ids[i]);
            if (!selected[i].eligible()) throw new IllegalArgumentException("추천 자격이 없는 곡입니다: " + ids[i]);
        }
        return new RehearsalPlan(date, members, attendance, selected, location);
    }
    public void confirm(LocalDate date, int[] ids, LocalDate today, RehearsalPlan approved, boolean partialApproved) {
        confirm(date, ids, today, approved == null ? "" : approved.getLocation(), approved, partialApproved);
    }
    public void confirm(LocalDate date, int[] ids, LocalDate today, String location, RehearsalPlan approved, boolean partialApproved) {
        if (confirmedPlans.length >= MAX_PLANS) throw new IllegalArgumentException("확정 계획은 최대 5개입니다. 기존 계획을 수정하거나 삭제하세요.");
        restorePlan(nextPlanId, validatedPlan(date, ids, today, location, approved, partialApproved));
    }
    public void updatePlan(int id, LocalDate date, int[] ids, LocalDate today, RehearsalPlan approved, boolean partialApproved) {
        updatePlan(id, date, ids, today, approved == null ? "" : approved.getLocation(), approved, partialApproved);
    }
    public void updatePlan(int id, LocalDate date, int[] ids, LocalDate today, String location, RehearsalPlan approved, boolean partialApproved) {
        findPlan(id);
        RehearsalPlan latest = validatedPlan(date, ids, today, location, approved, partialApproved);
        for (int i = 0; i < confirmedPlans.length; i++) if (confirmedPlans[i].id() == id) confirmedPlans[i] = new ConfirmedPlan(id, latest);
    }
    private RehearsalPlan validatedPlan(LocalDate date, int[] ids, LocalDate today, String location, RehearsalPlan approved, boolean partialApproved) {
        RehearsalPlan latest = preview(date, ids, today, location);
        if (approved == null || !latest.sameContent(approved))
            throw new IllegalArgumentException("확인 이후 정보가 변경되었습니다. 최신 내용을 다시 확인하세요.");
        if (latest.getAttendees().length < members.length && !partialApproved)
            throw new IllegalArgumentException("부분 참석 경고 동의가 필요합니다.");
        return latest;
    }
}
