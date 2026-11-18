import java.util.Arrays;

public final class SongService {
    private Song[] songs = new Song[0];
    private int nextId = 1;
    public Song register(String title, String artist, int[] difficulties) {
        return restore(nextId, title, artist, difficulties);
    }
    Song restore(int id, String title, String artist, int[] difficulties) {
        for (Song existing : songs) if (existing.getId() == id) throw new IllegalArgumentException("중복 곡 ID입니다.");
        Song song = new Song(id, title, artist, difficulties);
        songs = Arrays.copyOf(songs, songs.length + 1);
        songs[songs.length - 1] = song;
        nextId = Math.max(nextId, id + 1);
        return song;
    }
    public int getNextId() { return nextId; }
    void restoreNextId(int value) {
        if (value < nextId) throw new IllegalArgumentException("다음 곡 ID가 올바르지 않습니다.");
        nextId = value;
    }
    public void update(int id, String title, String artist, int[] difficulties) {
        find(id).updateDetails(title, artist, difficulties);
    }
    public void delete(int id) {
        find(id);
        Song[] next = new Song[songs.length - 1];
        int index = 0;
        for (Song song : songs) if (song.getId() != id) next[index++] = song;
        songs = next;
    }
    public Song[] getSongs() { return songs.clone(); }
    public Song find(int id) {
        for (Song song : songs) if (song.getId() == id) return song;
        throw new IllegalArgumentException("존재하지 않는 곡 ID입니다.");
    }
    public Song[] voteResults() {
        Song[] sorted = getSongs();
        for (int i = 0; i < sorted.length - 1; i++) for (int j = 0; j < sorted.length - i - 1; j++) {
            Song a = sorted[j], b = sorted[j + 1];
            if (a.count(Song.YES) < b.count(Song.YES)
                || (a.count(Song.YES) == b.count(Song.YES) && a.count(Song.NO) > b.count(Song.NO))
                || (a.count(Song.YES) == b.count(Song.YES) && a.count(Song.NO) == b.count(Song.NO) && a.getId() > b.getId())) {
                sorted[j] = b; sorted[j + 1] = a;
            }
        }
        return sorted;
    }
}
