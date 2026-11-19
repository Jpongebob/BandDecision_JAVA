public final class SongRecommendation {
    private final Song song;
    private final int[] burdens;
    private final int totalBurden;
    private final int approvals;
    public SongRecommendation(Song song, Member[] members) {
        this.song = song;
        burdens = new int[members.length];
        int total = 0;
        for (int i = 0; i < members.length; i++) { burdens[i] = Math.max(0, song.getDifficulty(i) - members[i].getSkill()); total += burdens[i]; }
        totalBurden = total;
        approvals = song.count(Song.YES);
    }
    public Song getSong() { return song; }
    public int[] getBurdens() { return burdens.clone(); }
    public int getTotalBurden() { return totalBurden; }
    public int getApprovals() { return approvals; }
    public int getScore() { return 3 * approvals - 2 * totalBurden; }
}
