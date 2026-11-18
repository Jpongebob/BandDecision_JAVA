import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Properties;

/** UTF-8 파일 저장. 불러오기는 새 객체에서 검증하고 저장은 임시 파일 작성 후 교체한다. */
public final class DataStore {
    private final Path file;
    public DataStore(Path file) { this.file = file.toAbsolutePath().normalize(); }
    public Path getFile() { return file; }
    public boolean exists() { return Files.exists(file); }
    public boolean isLegacy() throws IOException {
        if (!exists()) return false;
        Properties p = readProperties();
        return "1".equals(p.getProperty("version"));
    }
    public BandDecision load() throws IOException {
        Properties p = readProperties();
        try {
            String version = required(p, "version");
            if (!version.equals("1") && !version.equals("2") && !version.equals("3")) throw new IllegalArgumentException("지원하지 않는 저장 파일 버전입니다.");
            BandDecision app = new BandDecision();
            for (int i = 0; i < 6; i++) {
                String key = "member." + i + ".";
                app.getMembers()[i].setSkill(integer(p, key + "skill"));
            }
            if (!version.equals("1")) {
                int dateCount = count(p, "candidates");
                for (int i = 0; i < dateCount; i++) {
                    String key = "candidate." + i + ".";
                    LocalDate date = LocalDate.parse(required(p, key + "date"));
                    if (java.util.Arrays.asList(app.getAttendance().getDates()).contains(date))
                        throw new IllegalArgumentException("중복 후보 날짜");
                    for (int member = 0; member < 6; member++) {
                        int answer = integer(p, key + "answer." + member);
                        app.getAttendance().respond(member, date, answer,
                            answer == AttendanceService.UNAVAILABLE ? required(p, key + "reason." + member) : "");
                    }
                }
            }
            int songCount = count(p, "songs");
            for (int i = 0; i < songCount; i++) {
                String key = "song." + i + ".";
                int[] difficulty = new int[6];
                for (int member = 0; member < 6; member++) difficulty[member] = integer(p, key + "difficulty." + member);
                Song song = app.getSongs().restore(integer(p, key + "id"), required(p, key + "title"), required(p, key + "artist"), difficulty);
                for (int member = 0; member < 6; member++) {
                    int vote = integer(p, key + "vote." + member);
                    if (vote != Song.UNANSWERED) song.vote(member, vote, vote == Song.NO ? required(p, key + "reason." + member) : "");
                }
            }
            if (p.getProperty("songs.nextId") != null) app.getSongs().restoreNextId(integer(p, "songs.nextId"));
            if (version.equals("3")) {
                int planCount = count(p, "plans");
                if (planCount > BandDecision.MAX_PLANS) throw new IllegalArgumentException("확정 계획 최대 개수 초과");
                for (int i = 0; i < planCount; i++) {
                    String key = "plan." + i;
                    app.restorePlan(integer(p, key + ".id"), readPlan(p, key));
                }
                app.restoreNextPlanId(integer(p, "plans.nextId"));
            } else {
                String hasPlan = required(p, "plan.present");
                if (hasPlan.equals("true")) app.restorePlan(1, readPlan(p, "plan"));
                else if (!hasPlan.equals("false")) throw new IllegalArgumentException("확정 계획 상태 오류");
            }
            return app;
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            throw new IOException("저장 데이터 형식이 올바르지 않습니다: " + e.getMessage(), e);
        }
    }
    public void save(BandDecision app) throws IOException {
        Properties p = new Properties();
        p.setProperty("version", "3");
        for (int i = 0; i < 6; i++) {
            String key = "member." + i + ".";
            p.setProperty(key + "skill", "" + app.getMembers()[i].getSkill());
        }
        LocalDate[] dates = app.getAttendance().getDates();
        p.setProperty("candidates", "" + dates.length);
        for (int i = 0; i < dates.length; i++) {
            String key = "candidate." + i + ".";
            p.setProperty(key + "date", dates[i].toString());
            for (int member = 0; member < 6; member++) {
                int answer = app.getAttendance().response(member, dates[i]);
                p.setProperty(key + "answer." + member, "" + answer);
                if (answer == AttendanceService.UNAVAILABLE) p.setProperty(key + "reason." + member, app.getAttendance().reason(member, dates[i]));
            }
        }
        Song[] songs = app.getSongs().getSongs();
        p.setProperty("songs", "" + songs.length);
        p.setProperty("songs.nextId", "" + app.getSongs().getNextId());
        for (int i = 0; i < songs.length; i++) {
            String key = "song." + i + ".";
            Song song = songs[i];
            p.setProperty(key + "id", "" + song.getId());
            p.setProperty(key + "title", song.getTitle());
            p.setProperty(key + "artist", song.getArtist());
            for (int member = 0; member < 6; member++) {
                p.setProperty(key + "difficulty." + member, "" + song.getDifficulty(member));
                p.setProperty(key + "vote." + member, "" + song.getVote(member));
                if (song.getVote(member) == Song.NO) p.setProperty(key + "reason." + member, song.getReason(member));
            }
        }
        ConfirmedPlan[] plans = app.getConfirmedPlans();
        p.setProperty("plans", "" + plans.length); p.setProperty("plans.nextId", "" + app.getNextPlanId());
        for (int i = 0; i < plans.length; i++) {
            String key = "plan." + i;
            p.setProperty(key + ".id", "" + plans[i].id());
            RehearsalPlan plan = plans[i].plan();
            p.setProperty(key + ".date", plan.getDate().toString());
            writeArray(p, key + ".attendees", plan.getAttendees());
            writeArray(p, key + ".absentees", plan.getAbsentees());
            writeArray(p, key + ".songs", plan.getSongs());
        }
        Files.createDirectories(file.getParent());
        if (exists()) {
            Properties previous = readProperties();
            String oldVersion = previous.getProperty("version");
            if ("1".equals(oldVersion) || "2".equals(oldVersion)) {
                Path backup = file.resolveSibling(file.getFileName() + ".v" + oldVersion + ".bak");
                if (!Files.exists(backup)) Files.copy(file, backup);
            }
        }
        Path temp = Files.createTempFile(file.getParent(), "banddecision-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) { p.store(writer, "BandDecision data v3 - up to five confirmed plans"); }
            try { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    private Properties readProperties() throws IOException {
        Properties result = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            result.load(reader);
        } catch (IllegalArgumentException e) {
            throw new IOException("저장 데이터 문자 형식이 올바르지 않습니다: " + e.getMessage(), e);
        }
        return result;
    }
    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null) throw new IllegalArgumentException("누락 항목: " + key);
        return value;
    }
    private static RehearsalPlan readPlan(Properties p, String key) {
        return new RehearsalPlan(LocalDate.parse(required(p, key + ".date")), readArray(p, key + ".attendees"),
            readArray(p, key + ".absentees"), readArray(p, key + ".songs"));
    }
    private static int integer(Properties p, String key) { return Integer.parseInt(required(p, key)); }
    private static int count(Properties p, String key) {
        int value = integer(p, key);
        if (value < 0 || value > 100000) throw new IllegalArgumentException("개수 범위 오류: " + key);
        return value;
    }
    private static void writeArray(Properties p, String key, String[] values) {
        p.setProperty(key + ".count", "" + values.length);
        for (int i = 0; i < values.length; i++) p.setProperty(key + "." + i, values[i]);
    }
    private static String[] readArray(Properties p, String key) {
        String[] values = new String[count(p, key + ".count")];
        for (int i = 0; i < values.length; i++) values[i] = required(p, key + "." + i);
        return values;
    }
}
