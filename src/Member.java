public final class Member {
    private String name = "";
    private String part;
    private int skill;

    public Member(String part, int skill) {
        setPart(part);
        setSkill(skill);
    }
    public Member(String name, String part, int skill) {
        this(part, skill);
        setName(name);
    }
    public String getName() { return name; }
    public void setName(String name) { this.name = name == null ? "" : name.trim(); }
    public String displayName() { return name.isEmpty() ? part : name + " · " + part; }
    public String getPart() { return part; }
    public void setPart(String part) {
        if (part == null || part.isBlank()) throw new IllegalArgumentException("멤버 이름 또는 파트를 입력하세요.");
        this.part = part.trim();
    }
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
