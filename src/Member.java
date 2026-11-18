public final class Member {
    private final String part;
    private int skill;

    public Member(String part, int skill) {
        this.part = part;
        setSkill(skill);
    }
    public String getPart() { return part; }
    public int getSkill() { return skill; }
    public void setSkill(int skill) { validateRating(skill); this.skill = skill; }
    public static void validateRating(int value) {
        if (value < 1 || value > 5) throw new IllegalArgumentException("등급은 1~5 정수여야 합니다.");
    }
    public static String stars(int value) {
        validateRating(value);
        return "★".repeat(value) + "☆".repeat(5 - value);
    }
}
