public final class Song {
    public static final int UNANSWERED = 0, YES = 1, NO = 2;
    private final int id;
    private String title;
    private String artist;
    private final int[] difficulties;
    private final int[] votes = new int[6];
    private final String[] reasons = new String[6];
    public Song(int id, String title, String artist, int[] difficulties) {
        if (id < 1 || id == Integer.MAX_VALUE) throw new IllegalArgumentException("곡 ID가 올바르지 않습니다.");
        validateDetails(title, artist, difficulties);
        this.id = id; this.title = title.trim(); this.artist = artist.trim();
        this.difficulties = difficulties.clone();
    }
    private static void validateDetails(String title, String artist, int[] difficulties) {
        if (title == null || title.isBlank() || artist == null || artist.isBlank())
            throw new IllegalArgumentException("제목과 아티스트는 필수입니다.");
        if (difficulties == null || difficulties.length != 6) throw new IllegalArgumentException("6파트 난이도가 필요합니다.");
        for (int value : difficulties) Member.validateRating(value);
    }
    public void updateDetails(String title, String artist, int[] difficulties) {
        validateDetails(title, artist, difficulties);
        this.title = title.trim(); this.artist = artist.trim();
        System.arraycopy(difficulties, 0, this.difficulties, 0, 6);
    }
    private void check(int part) {
        if (part < 0 || part >= 6) throw new IllegalArgumentException("파트 번호가 잘못되었습니다.");
    }
    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public int getDifficulty(int part) { check(part); return difficulties[part]; }
    public void setDifficulty(int part, int value) { check(part); Member.validateRating(value); difficulties[part] = value; }
    public int getVote(int part) { check(part); return votes[part]; }
    public String getReason(int part) { check(part); return reasons[part]; }
    public void vote(int part, int choice, String reason) {
        check(part);
        if (choice != YES && choice != NO) throw new IllegalArgumentException("찬성 또는 반대를 선택하세요.");
        if (choice == NO && (reason == null || reason.isBlank())) throw new IllegalArgumentException("반대 사유는 필수입니다.");
        votes[part] = choice;
        reasons[part] = choice == NO ? reason.trim() : null;
    }
    public int count(int choice) { int n = 0; for (int vote : votes) if (vote == choice) n++; return n; }
    public boolean eligible() {
        for (int value : difficulties) if (value < 1 || value > 5) return false;
        return count(UNANSWERED) == 0 && count(YES) >= 4;
    }
    public String label() { return "[ID " + id + "] " + title + " — " + artist; }
}
