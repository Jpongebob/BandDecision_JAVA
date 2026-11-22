import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import javax.swing.JButton;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

/** Swing 화면의 표·선택·서비스 연결을 별도 임시 데이터로 검사한다. */
public final class GuiSmokeTest {
    private static int checks;
    private static void modelAnswer(JTable table, String value, int row, int column) { table.getModel().setValueAt(value, row, column); }
    private static void layoutAll(Container container) {
        container.doLayout();
        for (Component component : container.getComponents())
            if (component instanceof Container) layoutAll((Container) component);
    }
    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    private static <T extends Component> T find(Container root, Class<T> type, String label) {
        for (Component component : root.getComponents()) {
            if (type.isInstance(component)) {
                boolean match = label == null
                    || component instanceof JButton && ((JButton) component).getText().equals(label)
                    || component instanceof JTable && ((JTable) component).getColumnName(0).equals(label);
                if (match) return type.cast(component);
            }
            if (component instanceof Container) {
                T nested = find((Container) component, type, label);
                if (nested != null) return nested;
            }
        }
        return null;
    }
    private static JButton dayButton(Container root, LocalDate date) {
        for (Component component : root.getComponents()) {
            if (component instanceof JButton && date.equals(((JButton) component).getClientProperty("date"))) return (JButton) component;
            if (component instanceof Container) {
                JButton nested = dayButton((Container) component, date); if (nested != null) return nested;
            }
        }
        return null;
    }
    private static boolean hasLabel(Container root, String text) {
        for (Component component : root.getComponents()) {
            if (component instanceof javax.swing.JLabel && text.equals(((javax.swing.JLabel) component).getText())) return true;
            if (component instanceof Container && hasLabel((Container) component, text)) return true;
        }
        return false;
    }
    private static javax.swing.JComboBox<?> findMemberCombo(Container root) {
        if (root instanceof ScheduleCalendarPanel) return ((ScheduleCalendarPanel) root).memberSelector();
        for (Component component : root.getComponents()) {
            if (component instanceof javax.swing.JComboBox) {
                javax.swing.JComboBox<?> combo = (javax.swing.JComboBox<?>) component;
                if (combo.getItemCount() == 6 && "남성 보컬".equals(combo.getItemAt(0))) return combo;
            }
            if (component instanceof Container) {
                javax.swing.JComboBox<?> nested = findMemberCombo((Container) component); if (nested != null) return nested;
            }
        }
        return null;
    }
    private static void pendingEdits(Path directory) {
        Path file = directory.resolve("pending-edit.properties");
        BandDecision app = new BandDecision();
        LocalDate today = LocalDate.now();
        app.getAttendance().respond(0, today, AttendanceService.UNAVAILABLE, "기존 사유");
        DesktopUI frame = new DesktopUI(app, new DataStore(file), false);
        try {
            ScheduleCalendarPanel calendar = find(frame, ScheduleCalendarPanel.class, null);
            JTable table = find(calendar, JTable.class, "파트");
            check(table.editCellAt(0, 2), "지금 저장 전 사유 편집 시작");
            ((javax.swing.JTextField) table.getEditorComponent()).setText("지금 저장할 사유");
            find(frame, JButton.class, "저장 다시 시도").doClick();
            try {
                check("지금 저장할 사유".equals(new DataStore(file).load().getAttendance().reason(0, today))
                    && !table.isEditing(), "지금 저장은 포커스 이동 없이도 편집 확정 후 저장");
            } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            check(table.editCellAt(0, 2), "불가 사유 실제 셀 편집 시작");
            ((javax.swing.JTextField) table.getEditorComponent()).setText("멤버 전환 전 입력");
            findMemberCombo(calendar).setSelectedIndex(1);
            check("멤버 전환 전 입력".equals(app.getAttendance().reason(0, today)) && !table.isEditing(),
                "멤버 전환 전에 편집 중 사유 반영·편집 종료");
            check(table.editCellAt(0, 2), "날짜 이동 전 사유 편집 시작");
            ((javax.swing.JTextField) table.getEditorComponent()).setText("날짜 이동 전 입력");
            calendar.selectDate(today.plusDays(1));
            check("날짜 이동 전 입력".equals(app.getAttendance().reason(0, today))
                && app.getAttendance().response(0, today.plusDays(1)) == AttendanceService.UNANSWERED,
                "날짜 이동 전 사유는 원래 날짜에만 저장·재진입 없음");
            calendar.selectDate(today);
            WorkspaceView workspace = find(frame, WorkspaceView.class, null);
            workspace.show(WorkspaceView.Page.SCHEDULE);
            check(table.editCellAt(0, 2), "탭 전환 전 사유 편집 시작");
            ((javax.swing.JTextField) table.getEditorComponent()).setText("탭 전환 전 입력");
            workspace.show(WorkspaceView.Page.SONGS);
            check("탭 전환 전 입력".equals(app.getAttendance().reason(0, today)) && !table.isEditing(),
                "탭 전환 시 사유 확정·편집 종료");
            check(table.editCellAt(0, 2), "종료 전 불가 사유 셀 편집 시작");
            ((javax.swing.JTextField) table.getEditorComponent()).setText("창 닫기 전 입력");
            frame.dispatchEvent(new java.awt.event.WindowEvent(frame, java.awt.event.WindowEvent.WINDOW_CLOSING));
            try {
                check("창 닫기 전 입력".equals(new DataStore(file).load().getAttendance().reason(0, today)),
                    "창 종료 시 편집 중 사유까지 파일 저장 복원");
            } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        } finally {
            frame.dispose();
            try { Files.deleteIfExists(file); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        }
    }
    private static void completionAndUndo(Path directory) {
        Path file = directory.resolve("completion-undo.properties");
        BandDecision app = new BandDecision(); LocalDate today = LocalDate.now();
        DesktopUI frame = new DesktopUI(app, new DataStore(file), false);
        try {
            ScheduleCalendarPanel calendar = find(frame, ScheduleCalendarPanel.class, null);
            javax.swing.JComboBox<?> member = findMemberCombo(calendar);
            check(!find(calendar, JButton.class, "마지막 응답 되돌리기").isEnabled(), "시작 시 되돌리기 비활성");
            calendar.selectWholeMonth(); calendar.applySelected(0, AttendanceService.AVAILABLE, "");
            check(!app.getAttendance().isSubmitted(0), "이번 달만 응답하면 두 달 기간 완료 아님");
            calendar.showMonth(1); calendar.selectWholeMonth(); calendar.applySelected(0, AttendanceService.UNAVAILABLE, "여행");
            check(app.getAttendance().isSubmitted(0) && !app.getAttendance().isSubmitted(1)
                && hasLabel(frame, "응답 완료") && hasLabel(frame, "남성 보컬 · " + ScheduleWindow.label(today) + " 전체 날짜 기준"),
                "기간 전체 응답 완료·대상과 고정 범위 표시");
            byte[] saved;
            try { saved = Files.readAllBytes(file); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            member.setSelectedIndex(1);
            check(hasLabel(frame, "응답 미완료"), "선택 멤버 변경 시 미완료 표시");
            member.setSelectedIndex(0);
            try { check(java.util.Arrays.equals(saved, Files.readAllBytes(file)), "멤버 전환 자체는 저장하지 않음"); }
            catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            LocalDate nextMonth = java.time.YearMonth.now().plusMonths(1).atDay(1);
            calendar.selectDate(nextMonth);
            JTable responses = find(calendar, JTable.class, "파트");
            modelAnswer(responses, "가능", 1, 1);
            check(app.getAttendance().isSubmitted(0), "다른 멤버 응답은 개인 완료 기준을 바꾸지 않음");
            calendar.showMonth(1); calendar.selectWholeMonth();
            calendar.applySelected(0, AttendanceService.AVAILABLE, "");
            calendar.applySelected(0, AttendanceService.AVAILABLE, "");
            find(calendar, JButton.class, "마지막 응답 되돌리기").doClick();
            check(app.getAttendance().response(0, nextMonth) == AttendanceService.UNAVAILABLE
                && "여행".equals(app.getAttendance().reason(0, nextMonth)), "같은 응답 재적용 뒤에도 월 전체 오적용 되돌리기·사유 복원");
            check(app.getAttendance().response(1, nextMonth) == AttendanceService.AVAILABLE
                && !find(calendar, JButton.class, "마지막 응답 되돌리기").isEnabled(), "다른 멤버 상태 보존·한 단계만 되돌리기");
            modelAnswer(responses, "가능", 0, 1);
            calendar.clearSelection(); member.setSelectedIndex(1);
            find(calendar, JButton.class, "마지막 응답 되돌리기").doClick();
            check(app.getAttendance().response(0, nextMonth) == AttendanceService.UNAVAILABLE,
                "표 직접 수정도 선택 해제·멤버 전환 뒤 원래 멤버로 복원");
            try { check("여행".equals(new DataStore(file).load().getAttendance().reason(0, nextMonth)), "되돌린 사유 자동 저장 복원"); }
            catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            LocalDate fresh = today.plusDays(2);
            // 기존 기록 전체가 없는 상태로 복원되는 경우도 별도 빈 패널에서 확인한다.
            AttendanceService empty = new AttendanceService();
            ScheduleCalendarPanel[] panel = new ScheduleCalendarPanel[1];
            panel[0] = new ScheduleCalendarPanel(empty, app.getMembers(), () -> panel[0].refresh());
            panel[0].selectDate(fresh); panel[0].applySelected(2, AttendanceService.UNAVAILABLE, "일정");
            panel[0].undoLastResponse();
            check(empty.getDates().length == 0 && empty.response(2, fresh) == AttendanceService.UNANSWERED,
                "새 날짜 응답 되돌리기는 빈 기록도 제거");
            panel[0].selectDate(fresh);
            try { panel[0].applySelected(8, 1, ""); } catch (IllegalArgumentException expected) { /* 검증 실패 */ }
            check(!find(panel[0], JButton.class, "마지막 응답 되돌리기").isEnabled(), "잘못된 적용은 되돌리기 이력을 만들지 않음");
        } finally {
            frame.dispose();
            try { Files.deleteIfExists(file); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        }
    }
    private static JTable tableWithColumn(Container root, String column) {
        for (Component component : root.getComponents()) {
            if (component instanceof JTable) {
                JTable table = (JTable) component;
                for (int i = 0; i < table.getColumnCount(); i++) if (table.getColumnName(i).equals(column)) return table;
            }
            if (component instanceof Container) {
                JTable result = tableWithColumn((Container) component, column); if (result != null) return result;
            }
        }
        return null;
    }
    private static void workspaceFlows(Path directory) {
        Path file = directory.resolve("workspace-flow.properties");
        BandDecision app = DemoData.create(LocalDate.now());
        DesktopUI frame = new DesktopUI(app, new DataStore(file), false);
        try {
            WorkspaceView workspace = find(frame, WorkspaceView.class, null);
            ScheduleCalendarPanel calendar = find(frame, ScheduleCalendarPanel.class, null);
            javax.swing.JComboBox<?> profile = findMemberCombo(frame);
            frame.validate(); layoutAll(frame.getContentPane());
            JButton scheduleLink = find(workspace, JButton.class, "일정");
            java.awt.Point selectorBottom = SwingUtilities.convertPoint(profile.getParent(),
                profile.getX(), profile.getY() + profile.getHeight(), workspace);
            java.awt.Point scheduleTop = SwingUtilities.convertPoint(scheduleLink.getParent(),
                scheduleLink.getX(), scheduleLink.getY(), workspace);
            check(profile == calendar.memberSelector() && profile == workspace.memberSelector()
                && SwingUtilities.isDescendingFrom(profile, workspace) && selectorBottom.y <= scheduleTop.y,
                "입력 멤버 선택기를 BAND DECISION과 일정 버튼 사이에 배치·공유");
            find(workspace, JButton.class, "곡·투표").doClick();
            check(workspace.selected() == WorkspaceView.Page.SONGS, "왼쪽 곡·투표 버튼으로 한 화면 이동");
            JTable candidates = find(workspace.page(WorkspaceView.Page.SONGS), JTable.class, "제목");
            JTable votes = find(workspace.page(WorkspaceView.Page.SONGS), JTable.class, "파트");
            candidates.setRowSelectionInterval(1, 1);
            check(votes.getRowCount() == 6 && hasLabel(frame, "별빛 산책 · 샘플 밴드 B"), "목록 선택으로 곡 상세·투표 즉시 연동");
            profile.setSelectedIndex(3);
            check(calendar.getSelectedMember() == 3 && votes.getSelectedRow() == 3, "상단 멤버 전환이 일정·투표에 함께 반영");
            javax.swing.JComboBox<?> choice = find(workspace.page(WorkspaceView.Page.SONGS), javax.swing.JComboBox.class, null);
            javax.swing.JTextField reason = find(workspace.page(WorkspaceView.Page.SONGS), javax.swing.JTextField.class, null);
            choice.setSelectedIndex(1); reason.setText("합주 준비 부족");
            find(workspace.page(WorkspaceView.Page.SONGS), JButton.class, "내 투표 저장").doClick();
            check(app.getSongs().find(2).getVote(3) == Song.NO && "합주 준비 부족".equals(app.getSongs().find(2).getReason(3))
                && app.getSongs().find(1).getVote(3) == Song.YES, "선택 곡·상단 멤버에만 반대와 사유 저장");
            choice.setSelectedIndex(0);
            find(workspace.page(WorkspaceView.Page.SONGS), JButton.class, "내 투표 저장").doClick();
            check(app.getSongs().find(2).getVote(3) == Song.YES && app.getSongs().find(2).getReason(3) == null,
                "같은 화면에서 재투표·반대 사유 제거");
            JTable responses = find(calendar, JTable.class, "파트");
            calendar.selectDate(LocalDate.now()); modelAnswer(responses, "불가", 3, 1);
            check(responses.editCellAt(3, 2), "전체 갱신 전 일정 사유 편집 시작");
            ((javax.swing.JTextField) responses.getEditorComponent()).setText("갱신 중인 사유");
            frame.refresh();
            check("갱신 중인 사유".equals(app.getAttendance().reason(3, LocalDate.now())),
                "전체 갱신 시 편집 중인 일정 사유 확정");
            check(candidates.getSelectedRow() >= 0 && candidates.getValueAt(candidates.getSelectedRow(), 0).equals("별빛 산책")
                && votes.getRowCount() == 6 && votes.getSelectedRow() == 3,
                "일정 편집 중 전체 갱신해도 선택 곡·투표 멤버 보존");
            modelAnswer(responses, "가능", 3, 1);
            find(workspace, JButton.class, "일정").doClick(); calendar.selectDate(LocalDate.now());
            find(workspace, JButton.class, "합주 계획").doClick();
            find(workspace.page(WorkspaceView.Page.PLANS), JButton.class, "새 합주 만들기").doClick();
            JTable dates = find(workspace.page(WorkspaceView.Page.PLANS), JTable.class, "순위");
            JTable songs = tableWithColumn(workspace.page(WorkspaceView.Page.PLANS), "제목");
            dates.setRowSelectionInterval(0, 0); songs.setValueAt(true, 0, 0);
            check(frame.isBuildingPlan(), "같은 합주 계획 메뉴에서 만들기로 전환");
            byte[] saved;
            try { saved = Files.readAllBytes(file); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            find(workspace, JButton.class, "밴드 설정").doClick();
            check(workspace.selected() == WorkspaceView.Page.SETTINGS, "멤버 실력은 별도 밴드 설정에서 관리");
            find(workspace, JButton.class, "합주 계획").doClick();
            check(frame.isBuildingPlan() && dates.getSelectedRow() == 0 && Boolean.TRUE.equals(songs.getValueAt(0, 0)),
                "작업 메뉴를 왕복해도 계획 작성 선택 보존");
            find(workspace, JButton.class, "일정").doClick();
            check(calendar.getSelectedDate().equals(LocalDate.now()) && profile.getSelectedIndex() == 3,
                "작업 왕복 후 일정 날짜·입력 멤버 보존");
            try { check(java.util.Arrays.equals(saved, Files.readAllBytes(file)), "화면 이동만으로 저장 데이터 변경 없음"); }
            catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        } finally {
            frame.dispose();
            try { Files.deleteIfExists(file); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        }
    }
    private static void responsiveLayouts(DesktopUI frame, WorkspaceView workspace, boolean render) {
        int currentScale = 90;
        String[] actions = {"실력 저장", "가능 적용", "선택 곡 수정", "전체 곡 투표 결과",
            "선택한 날짜와 곡으로 계획 확정", "선택 계획 수정"};
        WorkspaceView.Page[] routes = {WorkspaceView.Page.SETTINGS, WorkspaceView.Page.SCHEDULE,
            WorkspaceView.Page.SONGS, WorkspaceView.Page.SONGS, WorkspaceView.Page.PLANS, WorkspaceView.Page.PLANS};
        for (int scale : new int[] {90, 100, 140}) {
            while (currentScale < scale) { find(frame, JButton.class, "A +").doClick(); currentScale += 10; }
            for (int[] size : new int[][] {{1080, 800}, {1280, 930}}) {
                frame.setSize(size[0], size[1]);
                frame.validate(); layoutAll(frame.getContentPane());
                javax.swing.JComboBox<?> profile = findMemberCombo(frame);
                check(profile.getVisibleRect().width == profile.getWidth() && profile.getVisibleRect().height == profile.getHeight()
                    && profile.getHeight() >= profile.getFontMetrics(profile.getFont()).getHeight() + profile.getInsets().top + profile.getInsets().bottom,
                    "고정 멤버 선택기 전체 접근·글자 높이 확보: " + scale + "% " + size[0]);
                for (int action = 0; action < actions.length; action++) {
                    workspace.show(routes[action]);
                    if (action == 4) frame.showPlanBuilder();
                    if (action == 5) frame.showPlanList();
                    frame.validate(); layoutAll(frame.getContentPane());
                    javax.swing.JScrollPane page = workspace.page(routes[action]);
                    JButton button = find(page, JButton.class, actions[action]);
                    button.scrollRectToVisible(new java.awt.Rectangle(0, 0, button.getWidth(), button.getHeight()));
                    layoutAll(frame.getContentPane());
                    java.awt.Rectangle bounds = SwingUtilities.convertRectangle(button.getParent(), button.getBounds(), page.getViewport().getView());
                    check(button.getWidth() > 0 && button.getHeight() > 0 && page.getViewport().getViewRect().contains(bounds),
                        "스크롤로 주요 동작 접근: " + scale + "% " + size[0] + "x" + size[1] + " " + actions[action]);
                    if (action == 3) {
                        JTable votes = find(page, JTable.class, "파트");
                        Component name = votes.getDefaultRenderer(Object.class).getTableCellRendererComponent(votes, "남성 보컬", false, false, 0, 0);
                        int nameWidth = name.getPreferredSize().width;
                        Component answer = votes.getDefaultRenderer(Object.class).getTableCellRendererComponent(votes, "찬성", false, false, 0, 1);
                        check(votes.getColumnModel().getColumn(0).getWidth() >= nameWidth
                            && votes.getColumnModel().getColumn(1).getWidth() >= answer.getPreferredSize().width,
                            "통합 투표표의 멤버 이름·찬반 배지를 줄임표 없이 표시 " + scale + "% " + size[0]);
                    }
                    if (action == 1) {
                        JButton undo = find(page, JButton.class, "마지막 응답 되돌리기");
                        undo.scrollRectToVisible(new java.awt.Rectangle(0, 0, undo.getWidth(), undo.getHeight()));
                        bounds = SwingUtilities.convertRectangle(undo.getParent(), undo.getBounds(), page.getViewport().getView());
                        check(page.getViewport().getViewRect().contains(bounds), "작은 창/확대에서도 되돌리기 접근 " + scale);
                        if (render && size[0] == 1080) render(frame, "calendar-" + scale + "-small");
                    }
                }
            }
        }
        while (currentScale > 90) { find(frame, JButton.class, "A -").doClick(); currentScale -= 10; }
        frame.setSize(1280, 930);
        for (WorkspaceView.Page page : WorkspaceView.Page.values()) workspace.page(page).getViewport().setViewPosition(new java.awt.Point());
    }
    private static void render(DesktopUI frame, String name) {
        java.awt.Container content = frame.getContentPane();
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(content.getWidth(), content.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics(); content.printAll(graphics); graphics.dispose();
        try {
            Path target = Path.of("out", "gui-preview", name + ".png"); Files.createDirectories(target.getParent());
            javax.imageio.ImageIO.write(image, "png", target.toFile());
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
    /** 삭제 회귀 검사의 끝 상태 대신 별도 가상 예시로 정상 작업 화면을 렌더링한다. */
    private static void renderWorkspace(Path directory) {
        LocalDate today = LocalDate.now();
        BandDecision demo = DemoData.create(today);
        demo.getSongs().find(1).setDifficulty(0, 0);
        LocalDate rehearsal = today.plusDays(4);
        RehearsalPlan approved = demo.preview(rehearsal, new int[] {1, 2}, today);
        demo.confirm(rehearsal, new int[] {1, 2}, today, approved, false);
        DesktopUI preview = new DesktopUI(demo, new DataStore(directory.resolve("preview.properties")), true);
        try {
            preview.addNotify(); preview.validate();
            WorkspaceView workspace = find(preview, WorkspaceView.class, null);
            ScheduleCalendarPanel calendar = find(preview, ScheduleCalendarPanel.class, null);
            calendar.setRangeSelection(true); calendar.pickDate(today); calendar.pickDate(rehearsal);
            for (WorkspaceView.Page page : WorkspaceView.Page.values()) {
                workspace.show(page); preview.validate(); layoutAll(preview.getContentPane());
                render(preview, "workspace-" + page.name().toLowerCase(java.util.Locale.ROOT));
            }
            preview.showPlanBuilder();
            JTable dates = find(workspace.page(WorkspaceView.Page.PLANS), JTable.class, "순위");
            JTable songs = tableWithColumn(workspace.page(WorkspaceView.Page.PLANS), "제목");
            dates.setRowSelectionInterval(0, 0); songs.setValueAt(true, 0, 0); songs.setValueAt(true, 1, 0);
            preview.validate(); layoutAll(preview.getContentPane()); render(preview, "workspace-builder");
        } finally { preview.dispose(); }
    }
    private static void dynamicMembersUi(Path directory) {
        BandDecision app = new BandDecision();
        app.getSongs().register("검사곡", "밴드", new int[] {2, 2, 2, 2, 2, 2});
        DesktopUI frame = new DesktopUI(app, new DataStore(directory.resolve("dynamic-ui.properties")), false);
        try {
            WorkspaceView workspace = find(frame, WorkspaceView.class, null);
            check(find(workspace.page(WorkspaceView.Page.SETTINGS), JButton.class, "멤버 추가") != null
                && find(workspace.page(WorkspaceView.Page.SETTINGS), JButton.class, "선택 멤버 수정") != null
                && find(workspace.page(WorkspaceView.Page.SETTINGS), JButton.class, "선택 멤버 삭제") != null,
                "멤버 추가·수정·삭제 화면 제공");
            frame.addMember("민수", "기타", 2);
            app.getSongs().find(1).setDifficulty(0, 0); frame.refresh();
            check(hasLabel(workspace.page(WorkspaceView.Page.SONGS), "남성 보컬  연주 안 함"), "0 난이도를 별 대신 연주 안 함으로 표시");
            app.getSongs().find(1).setDifficulty(0, 2); frame.refresh();
            check(hasLabel(workspace.page(WorkspaceView.Page.SONGS), "남성 보컬  ★★☆☆☆"), "다시 연주하면 난이도 별 표시 복원");
            ScheduleCalendarPanel calendar = find(frame, ScheduleCalendarPanel.class, null);
            JTable responses = find(calendar, JTable.class, "파트");
            JTable votes = find(workspace.page(WorkspaceView.Page.SONGS), JTable.class, "파트");
            check(app.getMembers().length == 7 && responses.getRowCount() == 7 && votes.getRowCount() == 7
                && findMemberCombo(frame).getItemCount() == 7, "멤버 추가 후 선택기·일정·투표 표 동시 확장");
            frame.editMember(app.getMembers()[6], "민수", "보조 기타", 4);
            check("민수 · 보조 기타".equals(responses.getValueAt(6, 0))
                && "민수 · 보조 기타".equals(findMemberCombo(frame).getItemAt(6))
                && app.getMembers()[6].getSkill() == 4, "멤버 이름·실력 수정 후 화면 동기화");
            try {
                frame.editMember(app.getMembers()[6], "잘못된 이름", " ", 2);
                throw new AssertionError("빈 파트 수정이 허용됨");
            } catch (IllegalArgumentException expected) {
                check(app.getMembers()[6].displayName().equals("민수 · 보조 기타")
                    && app.getMembers()[6].getSkill() == 4, "멤버 수정 검증 실패 시 원래 정보 보존");
            }
            findMemberCombo(frame).setSelectedIndex(4);
            frame.deleteMember(2);
            check(app.getMembers().length == 6 && responses.getRowCount() == 6 && votes.getRowCount() == 6
                && findMemberCombo(frame).getSelectedIndex() == 3
                && findMemberCombo(frame).getItemAt(3).equals("드럼"),
                "가운데 멤버 삭제 후 기존 선택 멤버와 일정·투표 행 유지");
            while (app.getMembers().length > 1) frame.deleteMember(app.getMembers().length - 1);
            check(responses.getRowCount() == 1 && votes.getRowCount() == 1
                && findMemberCombo(frame).getItemCount() == 1 && findMemberCombo(frame).getSelectedIndex() == 0,
                "1명 밴드에서도 사이드바·일정·투표 화면 동기화");
            app.restorePlan(1, new RehearsalPlan(LocalDate.now().minusDays(1),
                new String[] {"보컬"}, new String[] {"기타 | 일정"}, new String[] {"검사곡 — 밴드"}));
            app.restorePlan(2, new RehearsalPlan(LocalDate.now().plusDays(1),
                new String[] {"보컬"}, new String[] {"기타 | 일정"}, new String[] {"검사곡 — 밴드"}, "합주실 A"));
            frame.refresh();
            JTable plans = find(workspace.page(WorkspaceView.Page.PLANS), JTable.class, "날짜");
            check(plans.getRowCount() == 1 && plans.getModel().getValueAt(0, 0).equals(2)
                && "장소".equals(plans.getColumnName(1)) && "합주실 A".equals(plans.getModel().getValueAt(0, 2)),
                "예정된 계획과 합주 장소를 기본 목록에 표시");
            find(workspace.page(WorkspaceView.Page.PLANS), JButton.class, "이전 내역").doClick();
            check(plans.getRowCount() == 1 && plans.getModel().getValueAt(0, 0).equals(1), "이전 내역에는 지난 계획만 표시");
            find(workspace.page(WorkspaceView.Page.PLANS), JButton.class, "예정된 계획").doClick();
            check(plans.getRowCount() == 1 && plans.getModel().getValueAt(0, 0).equals(2), "예정된 계획으로 다시 전환");
            check(find(workspace.page(WorkspaceView.Page.PLANS), javax.swing.JTextField.class, null) != null
                && hasLabel(workspace.page(WorkspaceView.Page.PLANS), "합주 장소 (선택)"), "계획 작성 화면의 선택 장소 입력란");
            plans.setRowSelectionInterval(0, 0);
            find(workspace.page(WorkspaceView.Page.PLANS), JButton.class, "선택 계획 수정").doClick();
            check("합주실 A".equals(find(workspace.page(WorkspaceView.Page.PLANS), javax.swing.JTextField.class, null).getText()),
                "확정 계획 수정 시 기존 합주 장소를 입력란에 채움");
        } finally { frame.dispose(); }
    }
    private static void planOrderAndLimit(Path directory) {
        BandDecision app = new BandDecision();
        LocalDate today = LocalDate.now();
        Song song = app.getSongs().register("정렬 검사", "밴드", new int[] {1,1,1,1,1,1});
        for (int member = 0; member < 6; member++) song.vote(member, Song.YES, "");
        int[] ids = {song.getId()};
        for (int offset = 1; offset <= 11; offset++)
            for (int member = 0; member < 6; member++) app.getAttendance().respond(member, today.plusDays(offset), 1, "");
        for (int offset = 10; offset >= 1; offset--) {
            LocalDate day = today.plusDays(offset);
            app.confirm(day, ids, today, app.preview(day, ids, today), false);
        }
        check(app.getConfirmedPlans().length == 10, "확정 계획 10개 저장 허용");
        boolean refused = false;
        try { app.confirm(today.plusDays(11), ids, today, app.preview(today.plusDays(11), ids, today), false); }
        catch (IllegalArgumentException expected) { refused = true; }
        check(refused && app.getConfirmedPlans().length == 10 && app.getNextPlanId() == 11, "11번째 계획 거부·기존 10개와 다음 ID 보존");
        Path file = directory.resolve("order-limit.properties");
        try {
            DataStore store = new DataStore(file); store.save(app);
            check(store.load().getConfirmedPlans().length == 10, "10개 계획 파일 저장·복원");
            DesktopUI frame = new DesktopUI(app, store, false);
            try {
                WorkspaceView workspace = find(frame, WorkspaceView.class, null);
                JTable plans = find(workspace.page(WorkspaceView.Page.PLANS), JTable.class, "날짜");
                check(plans.getColumnCount() == 4 && "장소".equals(plans.getColumnName(1)), "계획 ID 열 숨김·날짜와 장소 표시");
                check(plans.getValueAt(0, 0).equals(today.plusDays(1)) && plans.getValueAt(9, 0).equals(today.plusDays(10)),
                    "역순 등록한 확정 계획을 날짜 오름차순으로 표시");
                check(!find(workspace.page(WorkspaceView.Page.PLANS), JButton.class, "선택한 날짜와 곡으로 계획 확정").isEnabled(),
                    "계획 10개에서 새 확정 비활성");
                plans.setRowSelectionInterval(9, 9);
                find(workspace.page(WorkspaceView.Page.PLANS), JButton.class, "선택 계획 수정").doClick();
                JTable dates = find(workspace.page(WorkspaceView.Page.PLANS), JTable.class, "순위");
                check(dates.getValueAt(dates.getSelectedRow(), 1).equals(today.plusDays(10))
                    && find(workspace.page(WorkspaceView.Page.PLANS), JButton.class, "선택 계획 수정 확정").isEnabled(),
                    "ID 숨김·정렬·10개 상태에서도 선택한 계획 날짜로 수정 진입");
                find(workspace.page(WorkspaceView.Page.PLANS), JButton.class, "수정 취소").doClick();
                plans.setRowSelectionInterval(4, 4);
                int chosenId = (Integer) plans.getModel().getValueAt(4, 0);
                LocalDate revised = today.plusDays(11);
                app.updatePlan(chosenId, revised, ids, today, app.preview(revised, ids, today), false); frame.refresh();
                check(plans.getSelectedRow() == 9 && plans.getModel().getValueAt(9, 0).equals(chosenId)
                    && plans.getValueAt(9, 0).equals(revised), "날짜 수정 재정렬 후 같은 내부 ID 선택 유지");
                app.deletePlan(chosenId); frame.refresh();
                check(plans.getRowCount() == 9 && find(workspace.page(WorkspaceView.Page.PLANS), JButton.class, "선택한 날짜와 곡으로 계획 확정").isEnabled(),
                    "10개에서 한 계획 삭제 시 새 확정 다시 허용");
            } finally { frame.dispose(); }
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        finally {
            try { Files.deleteIfExists(file); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        }
    }
    private static void fixedDemoUi(Path directory, boolean renderImages) {
        AppDate.configure(true);
        DesktopUI frame = null;
        try {
            BandDecision app = DemoData.createPresentation(AppDate.today());
            frame = new DesktopUI(app, new DataStore(directory.resolve("fixed-demo.properties")), true);
            WorkspaceView workspace = find(frame, WorkspaceView.class, null);
            ScheduleCalendarPanel calendar = find(frame, ScheduleCalendarPanel.class, null);
            check(calendar.getSelectedDate().equals(LocalDate.of(2026,12,9))
                && calendar.getVisibleMonth().equals(java.time.YearMonth.of(2026,12)), "Demo는 12월9일·12월 달력으로 시작");
            check(frame.getTitle().contains("2026-12-09"), "창 제목에 Demo 기준일 표시");
            check(!dayButton(calendar, LocalDate.of(2026,12,8)).isEnabled()
                && dayButton(calendar, LocalDate.of(2026,12,9)).isEnabled(), "Demo 지난 날짜 비활성 기준도 12월9일");
            check(!find(calendar,JButton.class,"이전 달").isEnabled() && find(calendar,JButton.class,"다음 달").isEnabled(),
                "고정 Demo 첫 달 이동 버튼 경계");
            find(calendar,JButton.class,"다음 달").doClick();
            check(calendar.getVisibleMonth().equals(java.time.YearMonth.of(2027,1))
                && !find(calendar,JButton.class,"다음 달").isEnabled(), "Demo 다음 달은 2027년1월·더 다음 달 불가");
            find(calendar,JButton.class,"오늘").doClick();
            check(calendar.getSelectedDate().equals(AppDate.DEMO_DATE)
                && calendar.getVisibleMonth().equals(java.time.YearMonth.of(2026,12)), "오늘 버튼은 고정 12월9일로 복귀");
            JTable plans = find(workspace.page(WorkspaceView.Page.PLANS), JTable.class, "날짜");
            check(plans.getRowCount()==2 && plans.getValueAt(0,0).equals(LocalDate.of(2026,12,10)),
                "Demo 예정 계획 분류도 고정 기준일 사용");
            if (renderImages) {
                frame.addNotify(); frame.validate(); layoutAll(frame.getContentPane()); render(frame,"demo-fixed-calendar");
                frame.showPlanBuilder(); frame.validate(); layoutAll(frame.getContentPane());
                JTable recommendedDates = find(workspace.page(WorkspaceView.Page.PLANS), JTable.class, "순위");
                recommendedDates.setRowSelectionInterval(0, 0);
                render(frame,"demo-fixed-builder");
            }
        } finally { if (frame != null) frame.dispose(); AppDate.configure(false); }
        check(AppDate.today().equals(LocalDate.now()), "고정 Demo UI 검사 후 일반 날짜 복귀");
    }
    public static void main(String[] args) throws Exception {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.println("SKIP: Swing requires a graphical environment"); return;
        }
        Path directory = Files.createTempDirectory("banddecision-gui-test-");
        Path file = directory.resolve("test.properties");
        try {
            String previousDirectory = System.getProperty("banddecision.dataDir");
            Path startupFile = directory.resolve("banddecision.properties");
            try {
                System.setProperty("banddecision.dataDir", directory.toString());
                Main.main(new String[0]);
                SwingUtilities.invokeAndWait(() -> {
                    boolean opened = false;
                    for (java.awt.Window window : java.awt.Window.getWindows()) {
                        if (window instanceof DesktopUI && window.isVisible()) {
                            opened = true;
                            window.dispatchEvent(new java.awt.event.WindowEvent(window, java.awt.event.WindowEvent.WINDOW_CLOSING));
                        }
                    }
                    check(opened, "Main 인자 없이 GUI 시작·종료");
                });
                check(new DataStore(startupFile).load().getSongs().getSongs().length == 0,
                    "GUI 기본 실행은 자동 저장 파일 생성·재로딩 가능");
            } finally {
                if (previousDirectory == null) System.clearProperty("banddecision.dataDir");
                else System.setProperty("banddecision.dataDir", previousDirectory);
                Files.deleteIfExists(startupFile);
            }
            SwingUtilities.invokeAndWait(() -> {
                DesktopUI.configureAppearance();
                pendingEdits(directory);
                completionAndUndo(directory);
                workspaceFlows(directory);
                dynamicMembersUi(directory);
                planOrderAndLimit(directory);
                fixedDemoUi(directory, args.length > 0 && args[0].equals("--render"));
                BandDecision app = DemoData.create(LocalDate.now());
                DesktopUI frame = new DesktopUI(app, new DataStore(file), true);
                try {
                    WorkspaceView workspace = find(frame, WorkspaceView.class, null);
                    check(workspace != null && workspace.selected() == WorkspaceView.Page.SCHEDULE, "세 작업 메뉴와 밴드 설정·일정부터 시작");
                    check(find(workspace, JButton.class, "일정") != null && find(workspace, JButton.class, "곡·투표") != null
                        && find(workspace, JButton.class, "합주 계획") != null && find(workspace, JButton.class, "밴드 설정") != null,
                        "왼쪽 작업 메뉴와 설정 제공");
                    JTable members = find(workspace.page(WorkspaceView.Page.SETTINGS), JTable.class, "멤버 · 파트");
                    check(find(workspace, JButton.class, "데이터 백업") == null && find(workspace, JButton.class, "백업에서 복원") == null,
                        "밴드 설정에서 수동 백업·복원 제거");
                    find(workspace, JButton.class, "저장 다시 시도").doClick();
                    check(Files.exists(file), "저장 다시 시도 버튼은 현재 파일 저장");
                    check(members.getRowCount() == 6 && members.getValueAt(3, 1).equals("★☆☆☆☆"), "초기 멤버 표시");
                    check(members.getValueAt(0, 2).equals("응답 미완료") && hasLabel(frame, "응답 미완료")
                        && hasLabel(frame, "남성 보컬 · " + ScheduleWindow.label(LocalDate.now()) + " 전체 날짜 기준"), "샘플 일부 날짜만 응답한 상태와 전체 기간 표시");
                    members.setRowSelectionInterval(3, 3);
                    JSpinner skill = find(workspace.page(WorkspaceView.Page.SETTINGS), JSpinner.class, null);
                    check(((Number) skill.getValue()).intValue() == 1, "선택 멤버 실력 입력칸 연동");
                    skill.setValue(4);
                    find(workspace.page(WorkspaceView.Page.SETTINGS), JButton.class, "실력 저장").doClick();
                    check(app.getMembers()[3].getSkill() == 4 && Files.exists(file), "실력 저장과 파일 자동 저장");
                    ScheduleCalendarPanel calendar = find(workspace.page(WorkspaceView.Page.SCHEDULE), ScheduleCalendarPanel.class, null);
                    JTable schedules = find(calendar, JTable.class, "파트");
                    LocalDate statusDay = LocalDate.now().plusDays(1);
                    app.getAttendance().respond(0, statusDay, AttendanceService.AVAILABLE, ""); frame.refresh();
                    check(!app.getAttendance().isSubmitted(0) && !app.getAttendance().isSubmitted(1)
                        && hasLabel(frame, "응답 미완료"), "일부 날짜 추가 응답만으로 전체 기간 완료가 되지 않음");
                    javax.swing.JComboBox<?> statusMember = findMemberCombo(calendar);
                    try {
                        byte[] beforeSwitch = Files.readAllBytes(file);
                        statusMember.setSelectedIndex(1);
                        check(hasLabel(frame, "응답 미완료") && hasLabel(frame, "여성 보컬 · " + ScheduleWindow.label(LocalDate.now()) + " 전체 날짜 기준"),
                            "멤버 전환 시 해당 멤버의 미완료 상태 즉시 갱신");
                        statusMember.setSelectedIndex(0);
                        check(hasLabel(frame, "응답 미완료") && hasLabel(frame, "남성 보컬 · " + ScheduleWindow.label(LocalDate.now()) + " 전체 날짜 기준"),
                            "원래 멤버로 돌아오면 해당 멤버 상태 복원");
                        check(java.util.Arrays.equals(beforeSwitch, Files.readAllBytes(file)), "멤버 선택 변경은 저장 데이터를 수정하지 않음");
                    } catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
                    app.getAttendance().removeDate(statusDay); frame.refresh();
                    LocalDate firstDay = app.getAttendance().getDates()[2];
                    calendar.selectDate(firstDay);
                    check(schedules.getRowCount() == 6 && schedules.getColumnCount() == 3, "선택한 날짜의 6명 응답·사유 표시");
                    check(calendar.statusFor(firstDay).equals("합주 가능"), "전원 가능 후보의 합주 가능 표시");
                    JTable songs = find(workspace.page(WorkspaceView.Page.SONGS), JTable.class, "제목");
                    check(songs.getRowCount() == 4 && songs.getColumnCount() == 3
                        && "제목".equals(songs.getColumnName(0))
                        && songs.getValueAt(0, 2).toString().contains("찬 ")
                        && hasLabel(workspace.page(WorkspaceView.Page.SONGS), "남성 보컬  ★★★☆☆"), "간결한 곡 목록과 선택곡의 6파트 난이도");
                    JTable votes = find(workspace.page(WorkspaceView.Page.SONGS), JTable.class, "파트");
                    check(votes.getRowCount() == 6 && votes.getValueAt(5, 1).equals("반대"), "곡별 투표와 사유 표시");
                    JTable dates = find(workspace.page(WorkspaceView.Page.PLANS), JTable.class, "순위");
                    JTable recommendedSongs = tableWithColumn(workspace.page(WorkspaceView.Page.PLANS), "제목");
                    check(recommendedSongs.getColumnCount() == 7 && "제목".equals(recommendedSongs.getColumnName(2)),
                        "추천 곡 표에는 내부 ID 대신 곡 제목 표시");
                    check(recommendedSongs.getColumnClass(0) == Boolean.class && recommendedSongs.isCellEditable(0, 0)
                        && !recommendedSongs.isCellEditable(0, 2), "곡 선택 칸만 체크박스로 편집");
                    check(frame.selectedPlanSongIds().length == 0, "처음에는 선택한 곡 없음");
                    check(recommendedSongs.editCellAt(0, 0), "첫 곡 체크 편집 시작");
                    ((javax.swing.JCheckBox) recommendedSongs.getEditorComponent()).doClick();
                    check(recommendedSongs.editCellAt(1, 0), "두 번째 곡 체크 편집 시작");
                    ((javax.swing.JCheckBox) recommendedSongs.getEditorComponent()).doClick();
                    int[] checkedIds = frame.selectedPlanSongIds();
                    check(checkedIds.length == 2 && checkedIds[0] != checkedIds[1] && hasLabel(workspace, "선택한 곡 2개"),
                        "Ctrl 없이 두 체크박스로 별도 곡 ID와 선택 개수 반영");
                    recommendedSongs.setRowSelectionInterval(0, 0);
                    check(java.util.Arrays.equals(checkedIds, frame.selectedPlanSongIds()), "일반 행 클릭은 체크 선택을 바꾸지 않음");
                    recommendedSongs.setValueAt(false, 0, 0);
                    check(frame.selectedPlanSongIds().length == 1 && hasLabel(workspace, "선택한 곡 1개"), "한 곡 해제 시 다른 체크 유지");
                    check(dates.getRowCount() == 7, "등록한 후보 7개만 추천");
                    dates.setRowSelectionInterval(0, 0);
                    check(find(workspace.page(WorkspaceView.Page.PLANS), JButton.class, "선택한 날짜와 곡으로 계획 확정").isEnabled(), "추천 자격이 있으면 확정 가능");
                    find(workspace.page(WorkspaceView.Page.PLANS), JButton.class, "새 합주 만들기").doClick();
                    check(workspace.selected() == WorkspaceView.Page.PLANS && frame.isBuildingPlan() && dates.getSelectedRow() == -1, "새 추천에서 이전 선택 해제");
                    check(frame.selectedPlanSongIds().length == 0 && hasLabel(workspace, "선택한 곡 0개"), "새 합주는 이전 곡 체크 초기화");
                    for (WorkspaceView.Page page : WorkspaceView.Page.values()) {
                        workspace.show(page); frame.validate();
                        check(workspace.selected() == page, "작업 메뉴 이동 " + page);
                    }
                    float baseFontSize = members.getFont().getSize2D();
                    int baseRowHeight = members.getRowHeight();
                    find(frame, JButton.class, "A +").doClick();
                    check(members.getFont().getSize2D() > baseFontSize && members.getRowHeight() > baseRowHeight,
                        "글자 확대 시 표 글자와 행 높이 함께 확대");
                    find(frame, JButton.class, "A -").doClick();
                    check(Math.abs(members.getFont().getSize2D() - baseFontSize) < 0.01
                        && members.getRowHeight() == baseRowHeight, "글자 크기 원래 배율 복귀");
                    frame.addNotify(); frame.setSize(1500, 980);
                    workspace.show(WorkspaceView.Page.SONGS); frame.validate(); layoutAll(frame.getContentPane());
                    check(songs.getWidth() == ((javax.swing.JViewport) songs.getParent()).getExtentSize().width,
                        "넓은 창에서 후보곡 표가 가로 여백 없이 viewport를 채움");
                    frame.setSize(1280, 930);
                    workspace.show(WorkspaceView.Page.SONGS); frame.validate(); layoutAll(frame.getContentPane());
                    check(((javax.swing.JViewport) votes.getParent()).getExtentSize().height >= votes.getRowCount() * votes.getRowHeight(),
                        "기본 창 크기에서 6명 투표 행을 동시에 표시: viewport=" + ((javax.swing.JViewport) votes.getParent()).getExtentSize()
                            + ", rowHeight=" + votes.getRowHeight());
                    check(votes.getColumnModel().getColumn(2).getWidth() > votes.getColumnModel().getColumn(0).getWidth() * 2,
                        "반대 사유 열에 넓은 공간 배정");
                    workspace.show(WorkspaceView.Page.SCHEDULE); frame.validate(); layoutAll(frame.getContentPane());
                    check(((javax.swing.JViewport) schedules.getParent()).getExtentSize().height >= 6 * schedules.getRowHeight(),
                        "기본 90%에서 달력 옆 멤버 6명 응답 동시 표시: " + ((javax.swing.JViewport) schedules.getParent()).getExtentSize());
                    check(find(workspace.page(WorkspaceView.Page.SONGS), JButton.class, "전체 곡 투표 결과") != null,
                        "전체 곡 집계 조회 버튼 유지");
                    check(schedules.isCellEditable(0, 1) && !schedules.isCellEditable(0, 0)
                        && !schedules.isCellEditable(0, 2), "응답 편집 가능·파트와 가능한 멤버의 사유 편집 불가");
                    modelAnswer(schedules, "미응답", 0, 1); frame.refresh();
                    check(app.getAttendance().response(0, firstDay) == 0 && dates.getRowCount() == 6
                        && calendar.statusFor(firstDay).equals("대기 1명"), "미응답 변경은 해당 날짜만 추천 제외·달력 대기 표시");
                    check(members.getValueAt(0, 2).equals("응답 미완료") && hasLabel(frame, "응답 미완료"),
                        "기록 날짜 중 미응답이 생기면 완료 표시 해제·다른 완료 날짜 추천 유지");
                    modelAnswer(schedules, "가능", 0, 1); frame.refresh();
                    check(dates.getRowCount() == 7, "응답 완료 후 GUI 추천 복원");
                    modelAnswer(schedules, "불가", 0, 1);
                    check(calendar.statusFor(firstDay).equals("참석 가능 5/6명") && schedules.isCellEditable(0, 2), "전체 참석 가능 인원 표시와 불가 사유 편집 활성화");
                    modelAnswer(schedules, "개인 일정", 0, 2);
                    check(app.getAttendance().reason(0, firstDay).equals("개인 일정"), "불가 사유 직접 수정");
                    modelAnswer(schedules, "", 0, 2);
                    check(app.getAttendance().reason(0, firstDay).equals("불가 응답"), "빈 사유는 기본 불가 사유로 저장");
                    modelAnswer(schedules, "가능", 0, 1);
                    check(calendar.statusFor(firstDay).equals("합주 가능"), "다시 전원 가능이면 달력 상태 복원");
                    LocalDate secondDay = app.getAttendance().getDates()[1];
                    JButton secondButton = dayButton(calendar, secondDay);
                    if (secondButton == null) {
                        calendar.selectDate(secondDay); secondButton = dayButton(calendar, secondDay);
                    }
                    secondButton.doClick();
                    check(calendar.getSelectedDate().equals(secondDay)
                        && schedules.getValueAt(3, 1).equals(AttendanceService.label(app.getAttendance().response(3, secondDay))), "날짜 클릭 시 해당 멤버 응답으로 전환");
                    find(calendar, JButton.class, "다음 달").doClick();
                    check(calendar.getVisibleMonth().equals(java.time.YearMonth.now().plusMonths(1)) && calendar.getSelectedDate().equals(secondDay)
                        && !find(calendar, JButton.class, "다음 달").isEnabled() && find(calendar, JButton.class, "이전 달").isEnabled(),
                        "다음 달 이동 후 다음 버튼 비활성·이전 버튼 활성·선택 날짜 유지");
                    find(calendar, JButton.class, "다음 달").doClick();
                    check(calendar.getVisibleMonth().equals(java.time.YearMonth.now().plusMonths(1)), "다음 달을 반복 눌러도 두 달 뒤로 넘어가지 않음");
                    find(calendar, JButton.class, "이전 달").doClick();
                    check(calendar.getVisibleMonth().equals(java.time.YearMonth.now()) && !find(calendar, JButton.class, "이전 달").isEnabled()
                        && find(calendar, JButton.class, "다음 달").isEnabled(), "첫 달 복귀 후 이전 버튼 비활성·다음 버튼 활성");
                    find(calendar, JButton.class, "이전 달").doClick();
                    check(calendar.getVisibleMonth().equals(java.time.YearMonth.now()), "비활성 이전 달을 눌러도 지난달로 넘어가지 않음");
                    calendar.selectDate(LocalDate.of(2028, 2, 29));
                    check(calendar.getVisibleMonth().equals(java.time.YearMonth.now().plusMonths(1)), "범위 밖 날짜를 선택해도 달력 표시 범위 유지");
                    calendar.selectDate(LocalDate.now().minusDays(1));
                    check(schedules.getRowCount() == 6 && !schedules.isCellEditable(0, 1) && !schedules.isEnabled()
                        && calendar.statusFor(calendar.getSelectedDate()).equals("지난 날짜"), "지난 날짜 기록은 읽기 전용·응답 편집 비활성");
                    modelAnswer(schedules, "가능", 0, 1);
                    check(app.getAttendance().response(0, LocalDate.now().minusDays(1)) == 0, "지난 날짜 모델 편집도 무시·기록 변경 없음");
                    calendar.selectDate(firstDay);
                    JButton yesterday = dayButton(calendar, LocalDate.now().minusDays(1));
                    if (yesterday != null) {
                        yesterday.doClick();
                        check(!yesterday.isEnabled() && calendar.getSelectedDate().equals(firstDay), "지난 날짜 클릭은 선택을 바꾸지 않음");
                    }
                    calendar.selectDate(LocalDate.now().plusDays(60));
                    check(schedules.getRowCount() == 6 && calendar.statusFor(calendar.getSelectedDate()).equals("추천 기간 밖"), "기간 밖 날짜 응답표와 추천 기간 구분");
                    LocalDate outsideCandidate = java.time.YearMonth.now().plusMonths(2).atDay(1);
                    app.getAttendance().respond(0, outsideCandidate, AttendanceService.UNANSWERED, ""); frame.refresh();
                    javax.swing.JComboBox<?> candidatePicker = find(calendar, javax.swing.JComboBox.class, null);
                    candidatePicker.setSelectedItem(outsideCandidate);
                    check(candidatePicker.isVisible() && calendar.getSelectedDate().equals(outsideCandidate) && schedules.getRowCount() == 6,
                        "표시 범위 밖 기존 후보도 선택 목록으로 응답 조회 가능");
                    modelAnswer(schedules, "가능", 0, 1);
                    check(app.getAttendance().response(0, outsideCandidate) == AttendanceService.UNANSWERED && !schedules.isEnabled(),
                        "기간 밖 기존 기록은 조회만 가능·응답 편집 비활성");
                    app.getAttendance().removeDate(outsideCandidate); frame.refresh();
                    LocalDate added = LocalDate.now();
                    while (java.util.Arrays.asList(app.getAttendance().getDates()).contains(added)) added = added.plusDays(1);
                    calendar.selectDate(added);
                    check(schedules.getRowCount() == 6 && calendar.statusFor(added).equals("미응답")
                        && schedules.isCellEditable(0, 1), "미기록 날짜도 즉시 응답표 표시·편집 가능");
                    check(find(calendar, JButton.class, "선택 날짜 후보 추가") == null
                        && find(calendar, JButton.class, "선택 후보 삭제") == null
                        && find(calendar, JButton.class, "후보 추가") == null, "후보 추가·삭제 버튼 제거");
                    modelAnswer(schedules, "가능", 0, 1);
                    check(calendar.statusFor(added).equals("대기 5명") && dates.getRowCount() == 7, "새 날짜 첫 응답 자동 저장·기존 완료 날짜 추천 유지");
                    try {
                        check(new DataStore(file).load().getAttendance().response(0, added) == AttendanceService.AVAILABLE,
                            "후보 등록 없이 새 날짜 응답 저장 복원");
                    } catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
                    for (int member = 1; member < 6; member++) modelAnswer(schedules, "가능", member, 1);
                    check(calendar.statusFor(added).equals("합주 가능") && dates.getRowCount() == 8,
                        "사전 등록 없이 새 날짜 전원 응답 시 합주 가능·추천에 추가");
                    calendar.selectDate(firstDay); modelAnswer(schedules, "불가", 0, 1);
                    check(app.getAttendance().response(0, added) == AttendanceService.AVAILABLE
                        && app.getAttendance().response(0, firstDay) == AttendanceService.UNAVAILABLE, "선택 전환 후 수정이 정확한 날짜에 반영");
                    modelAnswer(schedules, "가능", 0, 1);
                    app.getAttendance().removeDate(added); frame.refresh();
                    modelAnswer(schedules, "가능", 0, 1);
                    check(dates.getRowCount() == 7 && calendar.getSelectedDate().equals(firstDay), "응답 후 갱신해도 날짜 선택 유지");
                    find(calendar, JButton.class, "하루 선택").doClick();
                    dayButton(calendar, firstDay).doClick(); dayButton(calendar, firstDay.plusDays(1)).doClick();
                    check(calendar.getSelectedDates().length == 1 && calendar.getSelectedDates()[0].equals(firstDay.plusDays(1)), "일반 클릭은 마지막 날짜 하나만 선택·누적하지 않음");
                    dayButton(calendar, firstDay).doClick();
                    dayButton(calendar, firstDay).doClick();
                    check(calendar.getSelectedDates().length == 1 && calendar.getSelectedDates()[0].equals(firstDay), "같은 날짜 재클릭도 한 날짜 선택 유지");
                    find(calendar, JButton.class, "선택 해제").doClick();
                    check(calendar.getSelectedDates().length == 0 && !find(calendar, JButton.class, "가능 적용").isEnabled(), "빈 선택이면 적용 버튼 비활성");
                    find(calendar, JButton.class, "기간으로").doClick();
                    dayButton(calendar, firstDay.plusDays(4)).doClick();
                    check(!find(calendar, JButton.class, "불가 적용").isEnabled(), "기간 시작만 선택한 동안 적용 차단");
                    dayButton(calendar, firstDay).doClick();
                    check(calendar.getSelectedDates().length == 5 && calendar.getSelectedDates()[0].equals(firstDay), "종료일을 먼저 눌러도 양끝 포함 기간 선택");
                    javax.swing.JComboBox<?> bulkMember = findMemberCombo(calendar);
                    bulkMember.setSelectedIndex(2);
                    check(hasLabel(calendar, "<html>기타 · 선택한 5일에 적용<br>위 응답표를 직접 편집하면 "
                        + firstDay.getMonthValue() + "/" + firstDay.getDayOfMonth() + " 하루만 변경됩니다.</html>"),
                        "적용 버튼 위에 대상 멤버·선택 일수와 직접 편집 날짜 표시");
                    find(calendar, JButton.class, "불가 적용").doClick();
                    check(app.getAttendance().response(2, firstDay) == 2 && app.getAttendance().response(2, firstDay.plusDays(4)) == 2
                        && app.getAttendance().response(0, firstDay) == 1, "버튼으로 기간 전체 지정 멤버만 불가 적용");
                    java.awt.Color unavailableColor = dayButton(calendar, firstDay).getBackground();
                    calendar.clearSelection();
                    check(dayButton(calendar, firstDay).getBackground().equals(unavailableColor)
                        && unavailableColor.equals(new java.awt.Color(255, 229, 225)), "선택 해제 후에도 저장된 내 불가 날짜 색상 유지");
                    calendar.showMonth(1); calendar.showMonth(0);
                    check(dayButton(calendar, firstDay).getBackground().equals(unavailableColor), "월 이동 후에도 내 응답 색상 유지");
                    bulkMember.setSelectedIndex(0);
                    check(dayButton(calendar, firstDay).getBackground().equals(new java.awt.Color(222, 234, 255)),
                        "멤버 전환 시 해당 멤버의 가능 날짜 색상 표시");
                    bulkMember.setSelectedIndex(2);
                    try {
                        BandDecision saved = new DataStore(file).load();
                        check(saved.getAttendance().response(2, firstDay.plusDays(3)) == 2,
                            "다중 응답 파일 저장 복원");
                        ScheduleCalendarPanel reopened = new ScheduleCalendarPanel(saved.getAttendance(), saved.getMembers(), () -> { });
                        findMemberCombo(reopened).setSelectedIndex(2); reopened.selectDate(firstDay); reopened.clearSelection();
                        check(dayButton(reopened, firstDay).getBackground().equals(unavailableColor),
                            "재실행 복원한 내 응답도 같은 색상으로 표시");
                    } catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
                    calendar.setRangeSelection(true); calendar.pickDate(firstDay); calendar.pickDate(firstDay.plusDays(4));
                    find(calendar, JButton.class, "가능 적용").doClick();
                    check(app.getAttendance().response(2, firstDay.plusDays(2)) == 1
                        && app.getAttendance().reason(2, firstDay.plusDays(2)) == null, "다중 가능 적용과 불가 사유 제거");
                    find(calendar, JButton.class, "미응답 적용").doClick();
                    check(app.getAttendance().response(2, firstDay) == 0 && calendar.getSelectedDates().length == 5,
                        "다중 미응답 적용 후 선택 유지");
                    check(dayButton(calendar, firstDay).getBackground().equals(new java.awt.Color(240, 235, 255)),
                        "미응답으로 되돌리면 개인 응답 색상 제거·현재 선택은 연보라로 구분");
                    calendar.showMonth(0);
                    LocalDate endOfMonth = java.time.YearMonth.now().atEndOfMonth();
                    find(calendar, JButton.class, "기간으로").doClick(); dayButton(calendar, endOfMonth).doClick();
                    find(calendar, JButton.class, "다음 달").doClick();
                    dayButton(calendar, endOfMonth.plusDays(2)).doClick();
                    check(calendar.getSelectedDates().length == 3, "월을 넘는 기간 선택 유지");
                    find(calendar, JButton.class, "이 달 전체").doClick();
                    check(calendar.getSelectedDates().length == java.time.YearMonth.now().plusMonths(1).lengthOfMonth()
                        && calendar.getSelectedDates()[0].getDayOfMonth() == 1, "전체 선택은 표시 중인 달의 모든 날짜");
                    find(calendar, JButton.class, "이 달 전체").doClick();
                    check(calendar.getSelectedDates().length == 0, "전체 선택 버튼을 다시 누르면 선택 해제");
                    find(calendar, JButton.class, "이전 달").doClick(); find(calendar, JButton.class, "이 달 전체").doClick();
                    check(calendar.getSelectedDates()[0].equals(LocalDate.now())
                        && calendar.getSelectedDates().length == java.time.YearMonth.now().lengthOfMonth() - LocalDate.now().getDayOfMonth() + 1,
                        "현재 달 전체 선택은 오늘부터 월말까지만");
                    find(calendar, JButton.class, "하루 선택").doClick();
                    check(calendar.getSelectedDates().length == 0, "선택 모드 변경 시 이전 선택·기간 시작 초기화");
                    for (LocalDate day : app.getAttendance().getDates()) app.getAttendance().removeDate(day);
                    AttendanceService original = DemoData.create(LocalDate.now()).getAttendance();
                    for (LocalDate day : original.getDates()) for (int member = 0; member < 6; member++)
                        app.getAttendance().respond(member, day, original.response(member, day), original.reason(member, day));
                    calendar.selectDate(firstDay); frame.refresh(); modelAnswer(schedules, "가능", 0, 1);
                    for (int i = 0; i < 5; i++) {
                        LocalDate day = original.getDates()[i];
                        RehearsalPlan approved = app.preview(day, new int[] {1, 2}, LocalDate.now());
                        app.confirm(day, new int[] {1, 2}, LocalDate.now(), approved, true);
                    }
                    frame.refresh();
                    check(!app.getConfirmedPlans()[0].plan().describe().contains("[ID "),
                        "확정 계획 설명에도 내부 곡 ID를 노출하지 않음");
                    check("합주 확정".equals(dayButton(calendar, firstDay).getClientProperty("calendar.status"))
                        && dayButton(calendar, firstDay).getToolTipText().contains("확정 계획")
                        && hasLabel(calendar, calendar.statusFor(firstDay) + " · 확정 계획 1건"),
                        "확정 계획 날짜의 달력 표시·상세·툴팁");
                    check("합주 확정".equals(dayButton(calendar, original.getDates()[1]).getClientProperty("calendar.status"))
                        && dayButton(calendar, original.getDates()[1]).getBackground().equals(new java.awt.Color(235, 225, 252))
                        && dayButton(calendar, firstDay).getBackground().equals(new java.awt.Color(235, 225, 252)),
                        "확정 날짜는 개인 응답·전원 가능 여부와 관계없이 보라색으로 구분");
                    calendar.selectDate(original.getDates()[5]);
                    check(!Boolean.TRUE.equals(dayButton(calendar, original.getDates()[5]).getClientProperty("calendar.confirmed")),
                        "다음 달의 미확정 날짜는 해당 달로 이동해 표시 확인");
                    calendar.selectDate(firstDay);
                    JTable plans = find(workspace.page(WorkspaceView.Page.PLANS), JTable.class, "날짜");
                    check(plans.getRowCount() == 5 && find(workspace.page(WorkspaceView.Page.PLANS), JButton.class, "선택한 날짜와 곡으로 계획 확정").isEnabled(),
                        "계획 5개 저장 후에도 10개 한도까지 새 합주 가능");
                    for (int row = 0; row < dates.getRowCount(); row++) {
                        LocalDate availableDate = (LocalDate) dates.getValueAt(row, 1);
                        check(java.util.Arrays.stream(app.getConfirmedPlans()).noneMatch(p -> p.plan().getDate().equals(availableDate)),
                            "새 합주 추천은 이미 확정한 날짜 제외 " + availableDate);
                    }
                    plans.setRowSelectionInterval(2, 2);
                    find(workspace.page(WorkspaceView.Page.PLANS), JButton.class, "선택 계획 수정").doClick();
                    check(workspace.selected() == WorkspaceView.Page.PLANS && frame.isBuildingPlan() && find(workspace.page(WorkspaceView.Page.PLANS), JButton.class, "선택 계획 수정 확정").isEnabled()
                        && dates.getSelectedRow() >= 0, "5개 상태에서도 선택 계획 수정 가능·기존 날짜 선택 복원");
                    check(dates.getValueAt(dates.getSelectedRow(), 1).equals(app.findPlan(3).plan().getDate())
                        && frame.selectedPlanSongIds().length == app.findPlan(3).plan().getSongs().length,
                        "수정 중인 계획 날짜와 곡 체크 복원");
                    check(dates.getRowCount() == 3, "수정 시 자기 날짜만 다시 포함하고 다른 네 계획 날짜 제외");
                    find(workspace.page(WorkspaceView.Page.PLANS), JButton.class, "수정 취소").doClick();
                    check(workspace.selected() == WorkspaceView.Page.PLANS && !frame.isBuildingPlan() && plans.getRowCount() == 5, "계획 수정 취소는 기존 목록 보존");
                    LocalDate revisedDate = original.getDates()[3];
                    RehearsalPlan revised = app.preview(revisedDate, new int[] {2}, LocalDate.now());
                    app.updatePlan(3, revisedDate, new int[] {2}, LocalDate.now(), revised, true); frame.refresh();
                    check(dayButton(calendar, revisedDate).getToolTipText().contains("확정 계획")
                        && !dayButton(calendar, original.getDates()[2]).getToolTipText().contains("확정 계획"),
                        "계획 날짜 수정 후 달력 확정 표시 이동");
                    check(plans.getModel().getValueAt(2, 0).equals(3) && plans.getModel().getValueAt(2, 1).equals(revisedDate), "계획 수정 후 ID·표 갱신");
                    app.deletePlan(2); frame.refresh();
                    boolean deletedDateAvailable = false;
                    for (int row = 0; row < dates.getRowCount(); row++)
                        if (dates.getValueAt(row, 1).equals(original.getDates()[1])) deletedDateAvailable = true;
                    check(deletedDateAvailable, "계획 삭제 후 해당 날짜가 새 합주 추천에 복귀");
                    check(!Boolean.TRUE.equals(dayButton(calendar, original.getDates()[1]).getClientProperty("calendar.confirmed"))
                        && !dayButton(calendar, original.getDates()[1]).getToolTipText().contains("확정 계획")
                        && !dayButton(calendar, original.getDates()[1]).getBackground().equals(new java.awt.Color(235, 225, 252)),
                        "계획 삭제 후 해당 날짜 확정 표시 제거");
                    check(plans.getRowCount() == 4 && plans.getModel().getValueAt(plans.getSelectedRow(), 0).equals(3), "중간 계획 삭제 후 선택 대상 ID 유지");
                    check(find(workspace.page(WorkspaceView.Page.PLANS), JButton.class, "선택 계획 삭제") != null, "확정 계획 삭제 버튼 제공");
                    find(workspace.page(WorkspaceView.Page.PLANS), JButton.class, "새 합주 만들기").doClick();
                    check(find(workspace.page(WorkspaceView.Page.PLANS), JButton.class, "선택한 날짜와 곡으로 계획 확정").isEnabled(), "계획 삭제로 자리 생기면 신규 확정 가능");
                    modelAnswer(schedules, "가능", 0, 1);
                    check(find(workspace.page(WorkspaceView.Page.SONGS), JButton.class, "선택 곡 수정") != null
                        && find(workspace.page(WorkspaceView.Page.SONGS), JButton.class, "선택 곡 삭제") != null, "후보곡 수정·삭제 버튼 제공");
                    songs.setRowSelectionInterval(1, 1);
                    app.getSongs().update(2, "수정한 후보곡", "수정한 아티스트", new int[] {2, 2, 2, 1, 2, 2});
                    frame.refresh();
                    check(songs.getValueAt(1, 0).equals("수정한 후보곡") && hasLabel(workspace.page(WorkspaceView.Page.SONGS), "수정한 후보곡 · 수정한 아티스트"),
                        "서비스 수정 후 후보곡 표·투표 선택지 갱신");
                    app.getSongs().delete(1); frame.refresh();
                    check(songs.getRowCount() == 3 && songs.getValueAt(0, 0).equals("수정한 후보곡")
                        && songs.getSelectedRow() == 0 && votes.getRowCount() == 6,
                        "앞 곡 삭제 후에도 선택한 투표 곡은 ID로 유지");
                    app.getSongs().delete(2); frame.refresh();
                    check(songs.getSelectedRow() == -1 && votes.getRowCount() == 0
                        && !find(workspace.page(WorkspaceView.Page.SONGS), JButton.class, "내 투표 저장").isEnabled(), "선택 곡 삭제 시 투표 대상 자동 변경 금지·저장 비활성");
                    responsiveLayouts(frame, workspace, args.length > 0 && args[0].equals("--render"));
                    if (args.length > 0 && args[0].equals("--render")) {
                        renderWorkspace(directory);
                    }
                } finally { frame.dispose(); }
            });
            BandDecision restored = new DataStore(file).load();
            check(restored.getMembers()[3].getSkill() == 4, "GUI 저장값 재실행 복원");
            check(restored.getConfirmedPlans().length == 4 && restored.findPlan(3).plan().getSongs().length == 1,
                "GUI 상태 저장 시 여러 계획과 수정 내용 복원");
            check(restored.getAttendance().getDates().length == 7
                && restored.getAttendance().response(0, restored.getAttendance().getDates()[0]) == AttendanceService.AVAILABLE,
                "달력 응답 변경과 후보 상태의 파일 저장 복원");
            System.out.println("PASS: " + checks + " Swing checks");
        } finally {
            Files.deleteIfExists(file); Files.deleteIfExists(directory.resolve("dynamic-ui.properties")); Files.deleteIfExists(directory);
        }
    }
}
