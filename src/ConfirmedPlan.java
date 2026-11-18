/** 목록 위치가 바뀌어도 수정·삭제 대상을 유지하는 확정 계획 ID와 불변 기록. */
public record ConfirmedPlan(int id, RehearsalPlan plan) {
    public ConfirmedPlan {
        if (id < 1 || id == Integer.MAX_VALUE || plan == null) throw new IllegalArgumentException("확정 계획 정보가 올바르지 않습니다.");
    }
}
