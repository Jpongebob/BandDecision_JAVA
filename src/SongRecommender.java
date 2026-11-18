import java.util.Arrays;

public final class SongRecommender {
    private SongRecommender() { }
    public static SongRecommendation[] recommend(SongService service, Member[] members) {
        Song[] songs = service.getSongs();
        SongRecommendation[] results = new SongRecommendation[songs.length];
        int n = 0;
        for (Song song : songs) if (song.eligible()) results[n++] = new SongRecommendation(song, members);
        results = Arrays.copyOf(results, n);
        for (int i = 0; i < n - 1; i++) for (int j = 0; j < n - i - 1; j++) if (after(results[j], results[j + 1])) {
            SongRecommendation temp = results[j]; results[j] = results[j + 1]; results[j + 1] = temp;
        }
        return results;
    }
    private static boolean after(SongRecommendation a, SongRecommendation b) {
        if (a.getScore() != b.getScore()) return a.getScore() < b.getScore();
        if (a.getApprovals() != b.getApprovals()) return a.getApprovals() < b.getApprovals();
        if (a.getTotalBurden() != b.getTotalBurden()) return a.getTotalBurden() > b.getTotalBurden();
        return a.getSong().getId() > b.getSong().getId();
    }
}
