import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.time.LocalDate;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;

/** 기존 서비스와 저장 파일을 공유하는 Swing 화면. 모든 UI 변경은 EDT에서 처리한다. */
public final class DesktopUI extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final Color NAVY = UiTheme.INK;
    private final BandDecision app;
    private final DataStore store;
    private final Member[] members;
    private final AttendanceService attendance;
    private final SongService songs;
    private final JLabel status = new JLabel(" ");
    private final JLabel submittedValue = new JLabel();
    private final JLabel scheduleOwner = new JLabel();
    private final JLabel screenTitle = new JLabel();
    private final JLabel screenHint = new JLabel();
    private final JComboBox<String> activeMember = new JComboBox<>();
    private final JLabel recommendationNotice = new JLabel();
    private final WorkspaceView workspace;
    private final JPanel planModes = new JPanel(new CardLayout());
    private JPanel planBuilder;
    private JPanel planList;
    private final JTable memberTable = table("파트", "현재 실력", "응답 상태");
    private ScheduleCalendarPanel scheduleCalendar;
    private final JTable songTable = table("ID", "제목", "아티스트");
    private final JLabel selectedSongTitle = new JLabel("후보곡을 선택하세요");
    private final JLabel[] difficultyLabels = new JLabel[6];
    private final JTable voteTable = table("파트", "찬반", "반대 사유");
    private final JTable dateTable = table("순위", "날짜", "참석 인원");
    private final JTable recommendationTable = table("순위", "곡 ID", "제목", "아티스트", "찬성", "총 부담", "추천 점수");
    private final JTextArea dateDetail = textArea(5);
    private final JTextArea planDetail = textArea(14);
    private final JTable plansTable = table("계획 ID", "날짜", "참석", "곡 수");
    private int editingPlanId = -1;
    private final JLabel editingNotice = new JLabel();
    private final JButton cancelPlanEdit = new JButton("수정 취소");
    
    private int selectedSongId = -1;
    private boolean firstSongSelection = true;
    private boolean updatingVotes;
    private JButton voteSaveButton;
    private final JComboBox<String> voteChoice = new JComboBox<>(new String[] {"찬성", "반대"});
    private final JTextField voteReason = new JTextField(20);
    private final JLabel voteSummary = new JLabel();
    private final JButton confirmButton = new JButton("선택한 날짜와 곡으로 계획 확정");
    private boolean refreshing;
    private int textPercent = 90;
    private final JLabel textSize = new JLabel("90%");

    public static void configureAppearance() {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception e) { /* 기본 Look & Feel 사용 */ }
        Font font = UiTheme.font(Font.PLAIN, 18);
        for (Object key : UIManager.getDefaults().keySet().toArray())
            if (UIManager.get(key) instanceof Font) UIManager.put(key, font);
        UIManager.put("Table.selectionBackground", UiTheme.ACCENT_SOFT);
        UIManager.put("Table.selectionForeground", NAVY);
        UIManager.put("Panel.background", Color.WHITE);
        UIManager.put("Label.foreground", NAVY);
        UIManager.put("TextField.border", new UiTheme.RoundedBorder(UiTheme.LINE));
        UIManager.put("TextField.background", Color.WHITE);
        UIManager.put("ComboBox.background", Color.WHITE);
        UIManager.put("ComboBox.selectionBackground", UiTheme.ACCENT_SOFT);
        UIManager.put("ComboBox.selectionForeground", NAVY);
        UIManager.put("SplitPane.background", Color.WHITE);
        UIManager.put("ScrollBar.width", 12);
    }

    public DesktopUI(BandDecision app, DataStore store, boolean demo) {
        super("BandDecision" + (demo ? " · 샘플 데이터" : ""));
        this.app = app; this.store = store;
        members = app.getMembers(); attendance = app.getAttendance(); songs = app.getSongs();
        UiTheme.styleCombo(activeMember); UiTheme.styleCombo(voteChoice);
        for (Member member : members) activeMember.addItem(member.getPart());
        workspace = new WorkspaceView(() -> scheduleCalendar == null || scheduleCalendar.finishEditing(), this::updateHeader);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1080, 800));
        setSize(1280, 930);
        setLocationRelativeTo(null);
        JPanel root = UiTheme.surface(new BorderLayout(0, 14));
        root.setBackground(UiTheme.BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 10, 16));
        JPanel main = new JPanel(new BorderLayout(0, 16)); main.setOpaque(false);
        JPanel header = new JPanel(new BorderLayout(0, 8)); header.setOpaque(false);
        screenTitle.setFont(UiTheme.font(Font.BOLD, 27));
        screenHint.setForeground(UiTheme.MUTED); screenHint.setFont(UiTheme.font(Font.PLAIN, 14));
        JPanel heading = new JPanel(new BorderLayout(0, 4)); heading.setOpaque(false);
        heading.add(screenTitle, BorderLayout.NORTH); heading.add(screenHint, BorderLayout.SOUTH);
        header.add(heading, BorderLayout.WEST);
        activeMember.setPreferredSize(new Dimension(155, 38));
        JLabel memberLabel = new JLabel("입력할 멤버"); memberLabel.setLabelFor(activeMember);
        header.add(row(memberLabel, activeMember), BorderLayout.EAST);
        JPanel context = new JPanel(new BorderLayout()); context.setOpaque(false);
        submittedValue.setForeground(UiTheme.ACCENT);
        context.add(row(submittedValue, scheduleOwner), BorderLayout.WEST);
        context.add(row(button("A -", () -> resizeText(-10)), textSize, button("A +", () -> resizeText(10))), BorderLayout.EAST);
        header.add(context, BorderLayout.SOUTH);
        workspace.addPage(WorkspaceView.Page.SETTINGS, membersPanel());
        workspace.addPage(WorkspaceView.Page.SCHEDULE, schedulesPanel());
        workspace.addPage(WorkspaceView.Page.SONGS, songsWorkspace());
        workspace.addPage(WorkspaceView.Page.PLANS, plansWorkspace());
        main.add(header, BorderLayout.NORTH); main.add(workspace, BorderLayout.CENTER);
        root.add(main, BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(6, 4, 0, 0));
        status.setForeground(UiTheme.MUTED); status.setFont(status.getFont().deriveFont(14f));
        root.add(status, BorderLayout.SOUTH);
        setContentPane(root);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                if (!scheduleCalendar.finishEditing()) return;
                if (save() || ask("저장하지 못했습니다. 현재 변경을 저장하지 않고 종료할까요?")) dispose();
            }
        });
        refresh();
        status.setText("변경한 내용은 자동으로 저장됩니다."); status.setToolTipText("저장 위치: " + store.getFile());
        resizeText(0);
        workspace.show(WorkspaceView.Page.SCHEDULE);
    }

    private void resizeText(int step) {
        textPercent = Math.max(90, Math.min(140, textPercent + step));
        textSize.setText(textPercent + "%");
        UiTheme.resizeFonts(getContentPane(), textPercent / 100f);
        int memberHeight = activeMember.getFontMetrics(activeMember.getFont()).getHeight()
            + activeMember.getInsets().top + activeMember.getInsets().bottom + 2;
        activeMember.setPreferredSize(new Dimension(Math.round(155 * textPercent / 100f),
            Math.max(memberHeight, Math.round(42 * textPercent / 100f))));
        voteTable.getColumnModel().getColumn(0).setMinWidth(Math.round(120 * textPercent / 100f));
        voteTable.getColumnModel().getColumn(1).setMinWidth(Math.round(95 * textPercent / 100f));
        scheduleCalendar.refresh();
        getContentPane().revalidate(); getContentPane().repaint();
    }

    private void updateHeader(WorkspaceView.Page page) {
        String[] titles = {"우리 밴드의 일정", "함께 고르는 합주곡", "다음 합주를 계획해요", "밴드 설정"};
        String[] hints = {"날짜를 고르고, 가능한 날을 알려주세요.", "곡을 선택해 난이도를 확인하고 투표하세요.",
            "추천을 비교하고 합주 계획을 확정하세요.", "파트별 실력을 관리하면 곡 추천에 반영돼요."};
        screenTitle.setText(titles[page.ordinal()]); screenHint.setText(hints[page.ordinal()]);
    }

    private static JTable table(String... columns) {
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            private static final long serialVersionUID = 1L;
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable table = new JTable(model);
        table.setRowHeight(34); table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowVerticalLines(false);
        table.setGridColor(new Color(231, 235, 239));
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(100, 36));
        UiTheme.styleTable(table);
        return table;
    }
    private static DefaultTableModel model(JTable table) { return (DefaultTableModel) table.getModel(); }
    private static JTextArea textArea(int rows) {
        JTextArea area = new JTextArea(rows, 30);
        area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true);
        area.setMargin(new java.awt.Insets(18, 20, 18, 20));
        area.setForeground(NAVY); area.setBackground(new Color(250, 252, 253));
        return area;
    }
    private JPanel page(String heading, String help) {
        JPanel panel = UiTheme.card(new BorderLayout(0, 12), UiTheme.BACKGROUND, 16);
        JPanel top = new JPanel(new BorderLayout(0, 7)); top.setOpaque(false);
        JLabel title = new JLabel(heading); title.setFont(title.getFont().deriveFont(Font.BOLD, 23)); title.setForeground(NAVY);
        JLabel description = new JLabel("<html>" + help + "</html>"); description.setForeground(UiTheme.MUTED); description.setFont(description.getFont().deriveFont(16f));
        top.add(title, BorderLayout.NORTH); top.add(description, BorderLayout.CENTER); panel.add(top, BorderLayout.NORTH);
        return panel;
    }
    private static JPanel row(java.awt.Component... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        panel.setOpaque(false);
        for (java.awt.Component component : components) panel.add(component);
        return panel;
    }
    private static JPanel stack(java.awt.Component... components) {
        JPanel panel = UiTheme.card(null, UiTheme.SOFT, 10);
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        for (java.awt.Component component : components) {
            if (component instanceof javax.swing.JComponent) {
                ((javax.swing.JComponent) component).setAlignmentX(0);
                component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
            }
            panel.add(component);
        }
        return panel;
    }
    private JButton button(String label, Runnable action) {
        JButton button = new JButton(label);
        UiTheme.styleButton(button, label.contains("저장") || label.equals("새 후보곡 등록"));
        button.addActionListener(e -> perform(action));
        return button;
    }
    private void perform(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException e) { error(e.getMessage()); }
    }
    private void error(String message) { JOptionPane.showMessageDialog(this, message, "확인해 주세요", JOptionPane.WARNING_MESSAGE); }
    private boolean ask(String message) {
        return JOptionPane.showConfirmDialog(this, message, "내용 확인", JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }
    private static JSpinner rating() {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(3, 1, 5, 1));
        spinner.setPreferredSize(new Dimension(88, 44));
        spinner.setBorder(new UiTheme.RoundedBorder(UiTheme.LINE));
        return spinner;
    }
    private static int ratingValue(JSpinner spinner) {
        try { spinner.commitEdit(); }
        catch (java.text.ParseException e) { throw new IllegalArgumentException("등급은 1~5 정수로 입력하세요."); }
        int value = ((Number) spinner.getValue()).intValue(); Member.validateRating(value); return value;
    }
    private int selectedRow(JTable table, String message) {
        int selected = table.getSelectedRow();
        if (selected < 0) throw new IllegalArgumentException(message);
        return selected;
    }
    private boolean save() {
        try {
            store.save(app); status.setForeground(UiTheme.ACCENT);
            status.setText("저장됨 · " + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
            status.setToolTipText("저장 위치: " + store.getFile());
            return true;
        }
        catch (IOException e) {
            status.setForeground(new Color(185, 50, 45));
            status.setText("저장 실패 · 현재 변경은 메모리에만 남아 있습니다.");
            error("저장하지 못했습니다.\n" + e.getMessage()); return false;
        }
    }
    private void changed() { refresh(); save(); }

    private JPanel membersPanel() {
        JPanel panel = page("파트별 현재 실력", "실력은 추천곡의 연주 부담 계산에 사용됩니다. 파트를 선택하고 새 실력을 저장하세요.");
        panel.add(UiTheme.scroll(memberTable), BorderLayout.CENTER);
        JSpinner skill = rating();
        memberTable.getSelectionModel().addListSelectionListener(e -> {
            int index = memberTable.getSelectedRow();
            if (!refreshing && index >= 0) skill.setValue(members[index].getSkill());
        });
        panel.add(stack(row(new JLabel("새 실력 (1~5)"), skill, button("실력 저장", () -> {
            int index = selectedRow(memberTable, "실력을 수정할 파트를 선택하세요.");
            members[index].setSkill(ratingValue(skill)); changed();
        })), row(
            button("지금 저장", () -> { if (scheduleCalendar.finishEditing()) save(); }))), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel schedulesPanel() {
        JPanel panel = new JPanel(new BorderLayout()); panel.setOpaque(false);
        scheduleCalendar = new ScheduleCalendarPanel(attendance, members, this::changed, activeMember);
        scheduleCalendar.setMemberSelectionListener(() -> { refreshScheduleStatus(); selectVoteMember(); });
        panel.add(scheduleCalendar, BorderLayout.CENTER);
        return panel;
    }
    private JPanel songsPanel() {
        JPanel panel = page("후보곡", "곡을 누르면 오른쪽에서 난이도와 투표를 확인해요.");
        songTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        for (int i = 0; i < songTable.getColumnCount(); i++) {
            songTable.getColumnModel().getColumn(i).setPreferredWidth(i == 1 || i == 2 ? 180 : i == 0 ? 55 : 110);
            songTable.getColumnModel().getColumn(i).setMinWidth(i == 0 ? 42 : 100);
        }
        panel.add(UiTheme.scroll(songTable), BorderLayout.CENTER);
        JPanel tools = new JPanel(new BorderLayout(0, 6)); tools.setOpaque(false);
        tools.add(button("새 후보곡 등록", this::addSong), BorderLayout.NORTH);
        tools.add(row(button("선택 곡 수정", () -> songForm(selectedCandidate())), button("선택 곡 삭제", () -> {
                Song song = selectedCandidate();
                if (!ask(song.label() + "\n이 곡과 해당 곡의 투표·반대 사유를 삭제할까요?\n이미 확정된 계획의 기록은 유지됩니다.")) return;
                songs.delete(song.getId()); changed();
            })), BorderLayout.SOUTH);
        panel.add(tools, BorderLayout.SOUTH);
        songTable.getSelectionModel().addListSelectionListener(e -> {
            if (refreshing || e.getValueIsAdjusting()) return;
            selectedSongId = songTable.getSelectedRow() < 0 ? -1 : (Integer) songTable.getValueAt(songTable.getSelectedRow(), 0);
            refreshVotes();
        });
        return panel;
    }
    private JPanel songsWorkspace() {
        JPanel body = new JPanel(new BorderLayout()); body.setOpaque(false);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, songsPanel(), votesPanel());
        split.setBorder(null); split.setDividerSize(14); split.setResizeWeight(0.40);
        split.getLeftComponent().setMinimumSize(new Dimension(370, 400));
        split.getRightComponent().setMinimumSize(new Dimension(470, 400));
        body.add(split); return body;
    }
    private JPanel plansWorkspace() {
        JPanel body = new JPanel(new BorderLayout(0, 12)); body.setOpaque(false);
        body.add(row(button("확정된 계획", this::showPlanList), button("새 합주 만들기", () -> {
            if (app.getConfirmedPlans().length >= BandDecision.MAX_PLANS)
                throw new IllegalArgumentException("최대 5개입니다. 기존 계획을 수정하거나 삭제하세요.");
            editingPlanId = -1; refresh(); showPlanBuilder();
        })), BorderLayout.NORTH);
        planList = planPanel(); planBuilder = recommendationsPanel();
        planModes.add(planList, "list"); planModes.add(planBuilder, "builder");
        body.add(planModes); return body;
    }
    public void showPlanList() {
        if (workspace.show(WorkspaceView.Page.PLANS)) ((CardLayout) planModes.getLayout()).show(planModes, "list");
    }
    public void showPlanBuilder() {
        if (workspace.show(WorkspaceView.Page.PLANS)) ((CardLayout) planModes.getLayout()).show(planModes, "builder");
    }
    public boolean isBuildingPlan() { return planBuilder.isVisible(); }
    private Song selectedCandidate() {
        int index = selectedRow(songTable, "수정하거나 삭제할 후보곡을 표에서 선택하세요.");
        return songs.find((Integer) songTable.getValueAt(index, 0));
    }
    private void addSong() { songForm(null); }
    private void songForm(Song editing) {
        JTextField title = new JTextField(20), artist = new JTextField(20);
        if (editing != null) { title.setText(editing.getTitle()); artist.setText(editing.getArtist()); }
        JSpinner[] difficulties = new JSpinner[6];
        JPanel form = new JPanel(new GridLayout(0, 2, 10, 10));
        form.add(new JLabel("제목")); form.add(title); form.add(new JLabel("아티스트")); form.add(artist);
        for (int i = 0; i < 6; i++) {
            difficulties[i] = rating(); if (editing != null) difficulties[i].setValue(editing.getDifficulty(i));
            form.add(new JLabel(members[i].getPart() + " 난이도")); form.add(difficulties[i]);
        }
        while (JOptionPane.showConfirmDialog(this, form, editing == null ? "새 후보곡 등록" : "후보곡 수정 · 기존 투표 유지",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            try {
                int[] values = new int[6]; for (int i = 0; i < 6; i++) values[i] = ratingValue(difficulties[i]);
                if (editing == null) selectedSongId = songs.register(title.getText(), artist.getText(), values).getId();
                else songs.update(editing.getId(), title.getText(), artist.getText(), values);
                changed(); return;
            } catch (IllegalArgumentException e) { error(e.getMessage()); }
        }
    }

    private JPanel votesPanel() {
        JPanel panel = page("난이도와 투표", "상단에서 선택한 멤버로 투표해요. 반대에는 사유를 적어주세요.");
        ((BorderLayout) panel.getLayout()).setVgap(8);
        JPanel details = new JPanel(new BorderLayout(0, 8)); details.setOpaque(false);
        JPanel songInfo = new JPanel(new BorderLayout(0, 8)); songInfo.setOpaque(false);
        selectedSongTitle.setFont(UiTheme.font(Font.BOLD, 20));
        songInfo.add(selectedSongTitle, BorderLayout.NORTH);
        JPanel levels = new JPanel(new GridLayout(3, 2, 8, 6)); levels.setOpaque(false);
        for (int i = 0; i < 6; i++) {
            difficultyLabels[i] = new JLabel(); difficultyLabels[i].setFont(UiTheme.font(Font.PLAIN, 14));
            levels.add(difficultyLabels[i]);
        }
        songInfo.add(levels, BorderLayout.CENTER);
        songInfo.add(voteSummary, BorderLayout.SOUTH); details.add(songInfo, BorderLayout.NORTH);
        voteTable.getColumnModel().getColumn(0).setPreferredWidth(140);
        voteTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        voteTable.getColumnModel().getColumn(2).setPreferredWidth(750);
        voteTable.setRowHeight(42);
        details.add(UiTheme.scroll(voteTable), BorderLayout.CENTER);
        panel.add(details, BorderLayout.CENTER);
        voteTable.getSelectionModel().addListSelectionListener(e -> {
            if (refreshing || updatingVotes || e.getValueIsAdjusting() || voteTable.getSelectedRow() < 0 || selectedSongId < 0) return;
            if (activeMember.getSelectedIndex() != voteTable.getSelectedRow()) activeMember.setSelectedIndex(voteTable.getSelectedRow());
            loadVoteEditor();
        });
        voteChoice.addActionListener(e -> voteReason.setEnabled(voteChoice.getSelectedIndex() == 1)); voteReason.setEnabled(false);
        voteSaveButton = button("내 투표 저장", () -> {
            Song song = selectedVoteSong();
            int member = activeMember.getSelectedIndex();
            song.vote(member, voteChoice.getSelectedIndex() == 0 ? Song.YES : Song.NO, voteReason.getText()); changed();
        });
        JPanel controls = new JPanel(new BorderLayout(0, 6)); controls.setOpaque(false);
        controls.add(row(voteChoice, new JLabel("반대 사유")), BorderLayout.NORTH);
        controls.add(voteReason, BorderLayout.CENTER);
        controls.add(row(voteSaveButton, button("전체 곡 투표 결과", this::showVoteResults)), BorderLayout.SOUTH);
        panel.add(controls, BorderLayout.SOUTH);
        return panel;
    }
    private void showVoteResults() {
        javax.swing.JDialog dialog = new javax.swing.JDialog(this, "전체 곡 투표 결과", true);
        dialog.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        JPanel content = page("전체 곡 투표 결과", "찬성·반대·미응답 수와 추천 자격을 비교할 수 있습니다.");
        JTable resultTable = table("ID", "제목", "찬성", "반대", "미응답", "추천 상태");
        for (Song song : songs.voteResults()) model(resultTable).addRow(new Object[] {song.getId(), song.getTitle(),
            song.count(Song.YES), song.count(Song.NO), song.count(Song.UNANSWERED),
            song.eligible() ? "추천 가능" : song.count(Song.UNANSWERED) > 0 ? "투표 미완료" : "찬성 부족"});
        resultTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        resultTable.getColumnModel().getColumn(1).setPreferredWidth(260);
        resultTable.getColumnModel().getColumn(5).setPreferredWidth(150);
        content.add(UiTheme.scroll(resultTable), BorderLayout.CENTER);
        content.add(row(button("닫기", dialog::dispose)), BorderLayout.SOUTH);
        UiTheme.resizeFonts(content, textPercent / 100f);
        dialog.setContentPane(content);
        java.awt.Rectangle screen = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        dialog.setSize(Math.min(1000, screen.width - 80), Math.min(560, screen.height - 100));
        dialog.setLocationRelativeTo(this); dialog.setVisible(true);
    }
    private Song selectedVoteSong() {
        if (selectedSongId < 0) throw new IllegalArgumentException("왼쪽 목록에서 후보곡을 선택하세요.");
        return songs.find(selectedSongId);
    }
    private void refreshVotes() {
        updatingVotes = true;
        try {
            model(voteTable).setRowCount(0); voteReason.setText("");
            voteSaveButton.setEnabled(selectedSongId > 0); voteChoice.setEnabled(selectedSongId > 0);
            if (selectedSongId < 0) {
                selectedSongTitle.setText("후보곡을 선택하세요");
                selectedSongTitle.setToolTipText(null); voteReason.setEnabled(false);
                for (JLabel label : difficultyLabels) label.setText("");
                voteSummary.setText(songs.getSongs().length == 0 ? "새 후보곡을 등록하면 투표할 수 있어요." : "왼쪽 목록에서 곡을 선택하세요."); return;
            }
            Song song = selectedVoteSong(); selectedSongTitle.setText(song.getTitle() + " · " + song.getArtist());
            selectedSongTitle.setToolTipText(song.label());
            for (int i = 0; i < 6; i++) difficultyLabels[i].setText(members[i].getPart() + "  " + Member.stars(song.getDifficulty(i)));
            voteSummary.setText("찬성 " + song.count(Song.YES) + " · 반대 " + song.count(Song.NO) + " · 미응답 " + song.count(Song.UNANSWERED));
            for (int i = 0; i < 6; i++) model(voteTable).addRow(new Object[] {members[i].getPart(),
                song.getVote(i) == Song.YES ? "찬성" : song.getVote(i) == Song.NO ? "반대" : "미응답", song.getReason(i)});
            voteTable.setRowSelectionInterval(activeMember.getSelectedIndex(), activeMember.getSelectedIndex());
        } finally { updatingVotes = false; }
        loadVoteEditor();
    }
    private void selectVoteMember() {
        if (voteTable.getRowCount() == 6) voteTable.setRowSelectionInterval(activeMember.getSelectedIndex(), activeMember.getSelectedIndex());
        loadVoteEditor();
    }
    private void loadVoteEditor() {
        if (selectedSongId < 0 || voteSaveButton == null) return;
        Song song = selectedVoteSong(); int member = activeMember.getSelectedIndex();
        voteChoice.setSelectedIndex(song.getVote(member) == Song.NO ? 1 : 0);
        voteReason.setText(song.getReason(member) == null ? "" : song.getReason(member));
    }

    private JPanel recommendationsPanel() {
        JPanel panel = page("추천을 확인하고 직접 확정하세요", "왼쪽에서 날짜 1개, 오른쪽에서 곡 1개 이상을 선택하세요. 여러 곡은 Ctrl 또는 Shift를 누르고 선택합니다.");
        JPanel dates = new JPanel(new BorderLayout(0, 8));
        dateDetail.setRows(2); dateDetail.setMargin(new java.awt.Insets(9, 14, 9, 14));
        dateTable.setRowHeight(44);
        dates.add(new JLabel("날짜 추천 · 전원 응답한 날짜 중 가능 인원순, 동률은 빠른 날짜순"), BorderLayout.NORTH);
        dates.add(UiTheme.scroll(dateTable), BorderLayout.CENTER);
        dates.add(UiTheme.scroll(dateDetail), BorderLayout.SOUTH);
        JPanel recommended = new JPanel(new BorderLayout(0, 8));
        int[] widths = {46, 54, 130, 130, 60, 70, 80};
        for (int i = 0; i < widths.length; i++) recommendationTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        dateTable.getColumnModel().getColumn(0).setPreferredWidth(55);
        dateTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        dateTable.getColumnModel().getColumn(2).setPreferredWidth(110);
        recommended.add(new JLabel("곡 추천 · 전원 투표 + 찬성 4명 이상"), BorderLayout.NORTH);
        recommendationTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        recommended.add(UiTheme.scroll(recommendationTable), BorderLayout.CENTER);
        recommended.add(new JLabel("점수 = 찬성 × 3 - 총 부담 × 2"), BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, dates, recommended);
        split.setResizeWeight(0.42); split.setBorder(null); split.setDividerSize(18); panel.add(split, BorderLayout.CENTER);
        dateTable.getSelectionModel().addListSelectionListener(e -> { if (!refreshing) showDateDetails(); });
        UiTheme.styleButton(confirmButton, true);
        confirmButton.addActionListener(e -> perform(this::confirmPlan));
        UiTheme.styleButton(cancelPlanEdit, false);
        cancelPlanEdit.addActionListener(e -> { editingPlanId = -1; refresh(); showPlanList(); });
        panel.add(stack(row(recommendationNotice), row(editingNotice),
            row(button("최신 데이터로 다시 추천", this::refresh), confirmButton, cancelPlanEdit)), BorderLayout.SOUTH);
        return panel;
    }
    private void showDateDetails() {
        int index = dateTable.getSelectedRow();
        if (index < 0) { dateDetail.setText("날짜를 선택하면 참석자와 불참 사유가 표시됩니다."); return; }
        LocalDate day = LocalDate.parse(dateTable.getValueAt(index, 1).toString());
        StringBuilder text = new StringBuilder(day + " · 20:00–22:00\n");
        for (int i = 0; i < 6; i++) {
            String reason = attendance.reason(i, day);
            text.append(members[i].getPart()).append(reason == null ? " · 참석 가능" : " · 불참: " + reason).append('\n');
        }
        dateDetail.setText(text.toString()); dateDetail.setCaretPosition(0);
    }
    private void confirmPlan() {
        if (editingPlanId < 0 && app.getConfirmedPlans().length >= BandDecision.MAX_PLANS)
            throw new IllegalArgumentException("확정 계획은 최대 5개입니다. 기존 계획을 수정하거나 삭제하세요.");
        int index = selectedRow(dateTable, "추천 날짜를 선택하세요.");
        int[] rows = recommendationTable.getSelectedRows();
        if (rows.length == 0) throw new IllegalArgumentException("추천곡을 하나 이상 선택하세요.");
        LocalDate date = LocalDate.parse(dateTable.getValueAt(index, 1).toString());
        int[] ids = new int[rows.length]; for (int i = 0; i < rows.length; i++) ids[i] = (Integer) recommendationTable.getValueAt(rows[i], 1);
        RehearsalPlan preview = app.preview(date, ids, LocalDate.now());
        boolean partial = preview.getAttendees().length < 6;
        if (partial && !ask("주의: 전체 6인 합주가 아닙니다. 빠지는 파트와 사유:\n"
            + String.join("\n", preview.getAbsentees()) + "\n이 날짜로 진행할까요?")) return;
        JTextArea detail = textArea(17); detail.setText(preview.describe()); detail.setCaretPosition(0);
        if (JOptionPane.showConfirmDialog(this, UiTheme.scroll(detail), editingPlanId < 0 ? "이 계획을 추가할까요?" : "선택한 계획을 수정할까요?",
            JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.YES_OPTION) return;
        int changedId = editingPlanId < 0 ? app.getNextPlanId() : editingPlanId;
        if (editingPlanId < 0) app.confirm(date, ids, LocalDate.now(), preview, partial);
        else app.updatePlan(editingPlanId, date, ids, LocalDate.now(), preview, partial);
        editingPlanId = -1;
        changed(); showPlanList();
        for (int row = 0; row < plansTable.getRowCount(); row++)
            if ((Integer) plansTable.getValueAt(row, 0) == changedId) plansTable.setRowSelectionInterval(row, row);
    }
    private JPanel planPanel() {
        JPanel panel = page("확정 합주 계획", "최대 5개까지 보관합니다. 목록에서 선택하여 조회·수정·삭제하세요. 기존 기록은 명시적으로 수정할 때만 바뀝니다.");
        plansTable.getColumnModel().getColumn(0).setPreferredWidth(65);
        plansTable.getColumnModel().getColumn(1).setPreferredWidth(140);
        plansTable.getColumnModel().getColumn(2).setPreferredWidth(65);
        plansTable.getColumnModel().getColumn(3).setPreferredWidth(55);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, UiTheme.scroll(plansTable), UiTheme.scroll(planDetail));
        split.setResizeWeight(0.36); split.setBorder(null); split.setDividerSize(12);
        panel.add(split, BorderLayout.CENTER);
        plansTable.getSelectionModel().addListSelectionListener(e -> { if (!refreshing) showSelectedPlan(); });
        panel.add(row(button("선택 계획 수정", this::startPlanEdit), button("선택 계획 삭제", () -> {
            int id = selectedPlanId();
            if (!ask("계획 #" + id + "을 삭제할까요? 날짜 응답과 후보곡은 유지됩니다.")) return;
            app.deletePlan(id); if (editingPlanId == id) editingPlanId = -1; changed();
        })), BorderLayout.SOUTH);
        return panel;
    }
    private int selectedPlanId() {
        return (Integer) plansTable.getValueAt(selectedRow(plansTable, "수정하거나 삭제할 계획을 선택하세요."), 0);
    }
    private void showSelectedPlan() {
        int row = plansTable.getSelectedRow();
        planDetail.setText(row < 0 ? app.getConfirmedPlans().length == 0
            ? "아직 확정한 합주가 없어요.\n위의 '새 합주 만들기'에서 날짜와 곡을 선택해보세요." : "확정 계획을 목록에서 선택하세요."
            : app.findPlan((Integer) plansTable.getValueAt(row, 0)).plan().describe() + "\n상태: 확정 완료");
        planDetail.setCaretPosition(0);
    }
    private void startPlanEdit() {
        int id = selectedPlanId(); RehearsalPlan plan = app.findPlan(id).plan();
        editingPlanId = id; refresh();
        for (int row = 0; row < dateTable.getRowCount(); row++)
            if (plan.getDate().toString().equals(dateTable.getValueAt(row, 1).toString())) dateTable.setRowSelectionInterval(row, row);
        for (int row = 0; row < recommendationTable.getRowCount(); row++) {
            String prefix = "[ID " + recommendationTable.getValueAt(row, 1) + "] ";
            for (String label : plan.getSongs()) if (label.startsWith(prefix)) recommendationTable.addRowSelectionInterval(row, row);
        }
        showPlanBuilder();
    }

    private void refreshScheduleStatus() {
        int selected = scheduleCalendar.getSelectedMember();
        boolean completed = attendance.isSubmitted(selected);
        submittedValue.setText(completed ? "응답 완료" : "응답 미완료");
        scheduleOwner.setText(members[selected].getPart() + " · " + ScheduleWindow.label(LocalDate.now()));
        submittedValue.setToolTipText("오늘부터 다음 달 말일까지 모든 날짜에 가능/불가 응답하면 완료입니다.");
    }
    /** 갱신 시 선택을 해제해 이전 행 번호로 다른 항목을 수정하는 일을 방지한다. */
    public void refresh() {
        // 편집 저장은 changed()를 통해 refresh()를 다시 호출할 수 있다.
        // 갱신 플래그를 켜기 전에 확정해 중첩 호출이 선택 보호를 해제하지 않게 한다.
        if (!scheduleCalendar.finishEditing()) return;
        refreshing = true;
        try {
            model(memberTable).setRowCount(0);
            for (int i = 0; i < 6; i++) {
                model(memberTable).addRow(new Object[] {members[i].getPart(), Member.stars(members[i].getSkill()),
                    attendance.isSubmitted(i) ? "응답 완료" : "미응답"});
            }
            refreshScheduleStatus();
            scheduleCalendar.refresh();
            model(songTable).setRowCount(0);
            int previousId = selectedSongId;
            Song[] currentSongs = songs.getSongs();
            if (firstSongSelection && currentSongs.length > 0) previousId = currentSongs[0].getId();
            firstSongSelection = false;
            int selectedIndex = -1;
            for (int songIndex = 0; songIndex < currentSongs.length; songIndex++) {
                Song song = currentSongs[songIndex];
                if (song.getId() == previousId) selectedIndex = songIndex;
                model(songTable).addRow(new Object[] {song.getId(), song.getTitle(), song.getArtist()});
            }
            selectedSongId = selectedIndex < 0 ? -1 : previousId;
            if (selectedIndex >= 0) songTable.setRowSelectionInterval(selectedIndex, selectedIndex);
            refreshVotes();
            model(dateTable).setRowCount(0); dateDetail.setText("날짜를 선택하면 참석자와 불참 사유가 표시됩니다.");
            DateRecommendation[] dates = DateRecommender.recommend(attendance, LocalDate.now());
            for (int i = 0; i < dates.length; i++) model(dateTable).addRow(new Object[] {i + 1, dates[i].getDate(), dates[i].getAttendance() + "/6"});
            recommendationNotice.setText(dates.length == 0 ? "전원 응답이 모인 합주 가능 날짜가 없습니다." : "날짜별 전원 응답 완료 · 추천 " + dates.length + "개 · 미응답 날짜는 제외합니다.");
            model(recommendationTable).setRowCount(0);
            SongRecommendation[] recommended = SongRecommender.recommend(songs, members);
            for (int i = 0; i < recommended.length; i++) {
                SongRecommendation r = recommended[i]; Song song = r.getSong();
                model(recommendationTable).addRow(new Object[] {i + 1, song.getId(), song.getTitle(), song.getArtist(), r.getApprovals(), r.getTotalBurden(), r.getScore()});
            }
            confirmButton.setText(editingPlanId < 0 ? "선택한 날짜와 곡으로 계획 확정" : "선택 계획 수정 확정");
            cancelPlanEdit.setVisible(editingPlanId >= 0);
            editingNotice.setText(editingPlanId < 0 ? "확정 계획 " + app.getConfirmedPlans().length + "/5개 · 새 계획 추가"
                : "계획 #" + editingPlanId + " 수정 중 · 현재 추천에서 날짜와 곡을 선택하세요.");
            confirmButton.setEnabled(dateTable.getRowCount() > 0 && recommended.length > 0
                && (editingPlanId >= 0 || app.getConfirmedPlans().length < BandDecision.MAX_PLANS));
            if (recommended.length == 0) recommendationNotice.setText(recommendationNotice.getText() + " / 추천 자격을 충족하는 곡이 없습니다.");
            int oldRow = plansTable.getSelectedRow();
            int selectedId = oldRow < 0 ? -1 : (Integer) plansTable.getValueAt(oldRow, 0);
            model(plansTable).setRowCount(0); int selectedPlanRow = -1;
            for (ConfirmedPlan entry : app.getConfirmedPlans()) {
                RehearsalPlan plan = entry.plan();
                if (entry.id() == selectedId) selectedPlanRow = plansTable.getRowCount();
                model(plansTable).addRow(new Object[] {entry.id(), plan.getDate(), plan.getAttendees().length + "/6", plan.getSongs().length});
            }
            if (selectedPlanRow < 0 && selectedId < 0 && plansTable.getRowCount() > 0) selectedPlanRow = 0;
            if (selectedPlanRow >= 0) plansTable.setRowSelectionInterval(selectedPlanRow, selectedPlanRow);
            showSelectedPlan();
        } finally { refreshing = false; }
    }
}
