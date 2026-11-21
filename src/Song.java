public final class Song {
    public static final int UNANSWERED = 0, YES = 1, NO = 2;
    private final int id;
    private String title;
    private String artist;
    private int[] difficulties;
    private int[] votes;
    private String[] reasons;
    public Song(int id, String title, String artist, int[] difficulties) {
        if (id < 1 || id == Integer.MAX_VALUE) throw new IllegalArgumentException("곡 ID가 올바르지 않습니다.");
        validateDetails(title, artist, difficulties);
        this.id = id; this.title = title.trim(); this.artist = artist.trim();
        this.difficulties = difficulties.clone(); votes = new int[difficulties.length]; reasons = new String[difficulties.length];
    }
    private static void validateDetails(String title, String artist, int[] difficulties) {
        if (title == null || title.isBlank() || artist == null || artist.isBlank())
            throw new IllegalArgumentException("제목과 아티스트는 필수입니다.");
        if (difficulties == null || difficulties.length == 0) throw new IllegalArgumentException("멤버별 난이도가 필요합니다.");
        for (int value : difficulties) validateDifficulty(value);
    }
    public static void validateDifficulty(int value) {
        if (value < 0 || value > 5) throw new IllegalArgumentException("곡 난이도는 0~5 정수여야 합니다. 0은 연주 안 함입니다.");
    }
    public static String difficultyLabel(int value) {
        validateDifficulty(value);
        return value == 0 ? "연주 안 함" : Member.stars(value);
    }
    public void updateDetails(String title, String artist, int[] difficulties) {
        validateDetails(title, artist, difficulties);
        if (difficulties.length != this.difficulties.length) throw new IllegalArgumentException("현재 멤버 수만큼 난이도를 입력하세요.");
        this.title = title.trim(); this.artist = artist.trim();
        System.arraycopy(difficulties, 0, this.difficulties, 0, difficulties.length);
    }
    private void check(int part) {
        if (part < 0 || part >= difficulties.length) throw new IllegalArgumentException("파트 번호가 잘못되었습니다.");
    }
    void addMember() {
        difficulties = java.util.Arrays.copyOf(difficulties, difficulties.length + 1);
        difficulties[difficulties.length - 1] = 3;
        votes = java.util.Arrays.copyOf(votes, votes.length + 1);
        reasons = java.util.Arrays.copyOf(reasons, reasons.length + 1);
    }
    void removeMember(int member) {
        check(member);
        int[] nextDifficulties = new int[difficulties.length - 1];
        int[] nextVotes = new int[votes.length - 1];
        String[] nextReasons = new String[reasons.length - 1];
        System.arraycopy(difficulties, 0, nextDifficulties, 0, member);
        System.arraycopy(difficulties, member + 1, nextDifficulties, member, nextDifficulties.length - member);
        System.arraycopy(votes, 0, nextVotes, 0, member);
        System.arraycopy(votes, member + 1, nextVotes, member, nextVotes.length - member);
        System.arraycopy(reasons, 0, nextReasons, 0, member);
        System.arraycopy(reasons, member + 1, nextReasons, member, nextReasons.length - member);
        difficulties = nextDifficulties;
        votes = nextVotes;
        reasons = nextReasons;
    }
    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public int getDifficulty(int part) { check(part); return difficulties[part]; }
    public void setDifficulty(int part, int value) { check(part); validateDifficulty(value); difficulties[part] = value; }
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
        boolean hasPerformer = false;
        for (int value : difficulties) {
            if (value < 0 || value > 5) return false;
            if (value > 0) hasPerformer = true;
        }
        return hasPerformer && count(UNANSWERED) == 0 && count(YES) >= (votes.length * 2 + 2) / 3;
    }
    public String label() { return "[ID " + id + "] " + title + " — " + artist; }
}
