public final class Main {
    private Main() { }
    public static void main(String[] args) {
        boolean demo = java.util.Arrays.asList(args).contains("--demo");
        AppDate.configure(demo);
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.err.println("GUI를 표시할 수 없는 환경입니다. 화면이 있는 환경에서 실행하세요.");
            return;
        }
        java.nio.file.Path directory = java.nio.file.Path.of(System.getProperty("banddecision.dataDir", "data"));
        DataStore store = new DataStore(directory.resolve(demo ? AppDate.DEMO_FILE : "banddecision.properties"));
        BandDecision app;
        try {
            if (store.exists()) {
                boolean legacy = store.isLegacy();
                app = store.load();
                if (legacy) {
                    if (demo) DemoData.populateSchedule(app, AppDate.today());
                    store.save(app);
                    System.out.println("날짜별 응답 방식으로 전환했습니다. 이전 일정 파일은 .v1.bak에 보관했습니다.");
                }
                System.out.println("저장된 데이터를 불러왔습니다: " + store.getFile());
            } else {
                app = demo ? DemoData.createPresentation(AppDate.today()) : new BandDecision();
                store.save(app);
                if (demo) System.out.println("[샘플 모드] 날짜 " + app.getAttendance().getDates().length
                    + "개, 곡 " + app.getSongs().getSongs().length + "개, 확정 계획 " + app.getConfirmedPlans().length + "개를 준비했습니다.");
            }
        } catch (java.io.IOException | IllegalArgumentException e) {
            System.err.println("데이터를 열 수 없어 실행을 중단합니다. 기존 파일은 유지됩니다: " + store.getFile());
            System.err.println(e.getMessage());
            javax.swing.JOptionPane.showMessageDialog(null,
                "저장 파일을 열 수 없습니다. 기존 파일은 유지됩니다.\n" + store.getFile() + "\n" + e.getMessage(),
                "BandDecision 실행 오류", javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }
        BandDecision loaded = app;
        javax.swing.SwingUtilities.invokeLater(() -> {
            DesktopUI.configureAppearance();
            new DesktopUI(loaded, store, demo).setVisible(true);
        });
    }
}
