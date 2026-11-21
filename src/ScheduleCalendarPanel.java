import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/** 달력의 모든 날짜에서 바로 응답을 편집한다. 날짜 기록은 첫 응답 시 생성한다. */
public final class ScheduleCalendarPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final AttendanceService attendance;
    private Member[] members;
    private final Runnable changed;
    private final Supplier<ConfirmedPlan[]> plans;
    private final JButton[] days = new JButton[42];
    private final JLabel monthTitle = new JLabel("", JLabel.CENTER);
    private final JLabel detailTitle = new JLabel();
    private final JLabel detailStatus = new JLabel();
    private final JLabel guidance = new JLabel();
    private final JLabel selectionInfo = new JLabel();
    private final JPanel dayGrid = new JPanel(new GridLayout(0, 7, 6, 6));
    private final JButton singleMode;
    private final JButton rangeMode;
    private final JButton wholeMonth;
    private final JButton previousMonth;
    private final JButton nextMonth;
    private final JButton undo;
    private AttendanceService.Snapshot undoState;
    private final JButton[] applyButtons = new JButton[3];
    private final JComboBox<String> bulkMember;
    private final JTextField bulkReason = new JTextField(8);
    private LocalDate[] selectedDays = new LocalDate[0];
    private LocalDate rangeStart;
    private boolean rangeSelection;
    private final JComboBox<LocalDate> recordedDatePicker = new JComboBox<>();
    private final JTable responses;
    private LocalDate selectedDate;
    private YearMonth month;
    private boolean refreshing;
    private Runnable memberSelectionListener = () -> { };

    public ScheduleCalendarPanel(AttendanceService attendance, Member[] members, Runnable changed) {
        this(attendance, members, changed, null, () -> new ConfirmedPlan[0]);
    }
    public ScheduleCalendarPanel(AttendanceService attendance, Member[] members, Runnable changed, JComboBox<String> memberSelector) {
        this(attendance, members, changed, memberSelector, () -> new ConfirmedPlan[0]);
    }
    public ScheduleCalendarPanel(AttendanceService attendance, Member[] members, Runnable changed,
            JComboBox<String> memberSelector, Supplier<ConfirmedPlan[]> plans) {
        super(new BorderLayout(0, 12));
        this.attendance = attendance; this.members = members; this.changed = changed; this.plans = plans;
        bulkMember = memberSelector == null ? new JComboBox<>() : memberSelector;
        selectedDate = LocalDate.now();
        for (LocalDate day : attendance.getDates()) if (inWindow(day)) { selectedDate = day; break; }
        month = boundedMonth(YearMonth.from(selectedDate));
        selectedDays = new LocalDate[] {selectedDate};
        setOpaque(false);
        JPanel calendar = UiTheme.card(new BorderLayout(0, 14), Color.WHITE, 14);
        calendar.setMinimumSize(new Dimension(450, 300));
        monthTitle.setFont(UiTheme.font(Font.BOLD, 23));
        JPanel navigation = new JPanel(new BorderLayout()); navigation.setOpaque(false);
        previousMonth = action("이전 달", () -> moveMonth(-1));
        nextMonth = action("다음 달", () -> moveMonth(1));
        navigation.add(previousMonth, BorderLayout.WEST);
        navigation.add(monthTitle, BorderLayout.CENTER);
        navigation.add(nextMonth, BorderLayout.EAST);
        JPanel heading = new JPanel(new BorderLayout(0, 10)); heading.setOpaque(false);
        singleMode = selectionButton("하루 선택", () -> setRangeSelection(false));
        rangeMode = selectionButton("기간으로", () -> setRangeSelection(true));
        JPanel modes = new JPanel(new BorderLayout(8, 0)); modes.setOpaque(false);
        JPanel toggles = new JPanel(new GridLayout(1, 2, 4, 0)); toggles.setOpaque(false);
        toggles.add(singleMode); toggles.add(rangeMode); modes.add(toggles, BorderLayout.WEST);
        wholeMonth = selectionButton("이 달 전체", this::selectWholeMonth);
        modes.add(wholeMonth, BorderLayout.EAST);
        heading.add(navigation, BorderLayout.NORTH); heading.add(modes, BorderLayout.CENTER);
        JPanel weekdays = new JPanel(new GridLayout(1, 7, 6, 0)); weekdays.setOpaque(false);
        String[] labels = {"일", "월", "화", "수", "목", "금", "토"};
        for (int i = 0; i < 7; i++) {
            JLabel label = new JLabel(labels[i], JLabel.CENTER);
            label.setForeground(Color.BLACK);
            weekdays.add(label);
        }
        heading.add(weekdays, BorderLayout.SOUTH); calendar.add(heading, BorderLayout.NORTH);
        dayGrid.setOpaque(false);
        for (int i = 0; i < days.length; i++) {
            final int cell = i;
            days[i] = action("", () -> pickDate((LocalDate) days[cell].getClientProperty("date")));
            UiTheme.styleCalendarDay(days[i]);
            days[i].setMargin(new java.awt.Insets(3, 2, 3, 2));
            days[i].setPreferredSize(new Dimension(76, 60));
            dayGrid.add(days[i]);
        }
        calendar.add(dayGrid, BorderLayout.CENTER);

        responses = responseTable();
        JPanel detail = UiTheme.card(new BorderLayout(0, 6), Color.WHITE, 8);
        detail.setMinimumSize(new Dimension(340, 300));
        detailTitle.setFont(UiTheme.font(Font.BOLD, 21));
        JPanel detailHeading = new JPanel(new BorderLayout(0, 4)); detailHeading.setOpaque(false);
        detailHeading.add(detailTitle, BorderLayout.NORTH); detailHeading.add(detailStatus, BorderLayout.SOUTH);
        JPanel detailTop = new JPanel(new BorderLayout(0, 6)); detailTop.setOpaque(false);
        detailTop.add(detailHeading, BorderLayout.NORTH);
        UiTheme.styleCombo(recordedDatePicker);
        recordedDatePicker.setToolTipText("달력 범위 밖의 기존 응답 기록을 선택합니다.");
        recordedDatePicker.addActionListener(e -> {
            if (!refreshing && recordedDatePicker.getSelectedItem() instanceof LocalDate)
                selectDate((LocalDate) recordedDatePicker.getSelectedItem());
        });
        detailTop.add(recordedDatePicker, BorderLayout.SOUTH);
        detail.add(detailTop, BorderLayout.NORTH);
        detail.add(UiTheme.scroll(responses), BorderLayout.CENTER);
        if (bulkMember.getItemCount() == 0) for (Member member : members) bulkMember.addItem(member.displayName());
        if (memberSelector == null) UiTheme.styleCombo(bulkMember);
        bulkMember.setToolTipText("일정을 입력하고 투표할 멤버");
        bulkMember.setPreferredSize(new Dimension(130, 36));
        bulkReason.setToolTipText("불가 사유: 비우면 기존 사유를 유지하고 새 불가는 기본 문구를 사용합니다.");
        JPanel bulkFields = new JPanel(new GridLayout(0, 1, 0, 4)); bulkFields.setOpaque(false);
        JPanel memberField = new JPanel(new BorderLayout(10, 0)); memberField.setOpaque(false);
        memberField.add(new JLabel("응답할 멤버 *"), BorderLayout.WEST);
        if (memberSelector == null) memberField.add(bulkMember, BorderLayout.CENTER);
        JPanel reasonField = new JPanel(new BorderLayout(10, 0)); reasonField.setOpaque(false);
        reasonField.add(new JLabel("불가 사유 (선택)"), BorderLayout.WEST); reasonField.add(bulkReason, BorderLayout.CENTER);
        if (memberSelector == null) bulkFields.add(memberField);
        bulkFields.add(reasonField);
        JPanel apply = new JPanel(new GridLayout(1, 3, 5, 0)); apply.setOpaque(false);
        String[] actions = {"미응답 적용", "가능 적용", "불가 적용"};
        for (int choice : new int[] {1, 2, 0}) {
            final int value = choice;
            applyButtons[choice] = action(actions[choice], () -> applySelected(bulkMember.getSelectedIndex(), value, bulkReason.getText()));
            UiTheme.styleButton(applyButtons[choice], choice == AttendanceService.AVAILABLE);
            apply.add(applyButtons[choice]);
        }
        JPanel bulkControls = new JPanel(new BorderLayout(0, 6)); bulkControls.setOpaque(false);
        JPanel applyArea = new JPanel(new BorderLayout(0, 4)); applyArea.setOpaque(false);
        guidance.setForeground(UiTheme.INK);
        applyArea.add(guidance, BorderLayout.NORTH);
        applyArea.add(apply, BorderLayout.CENTER);
        bulkControls.add(bulkFields, BorderLayout.NORTH); bulkControls.add(applyArea, BorderLayout.CENTER);
        undo = action("마지막 응답 되돌리기", this::undoLastResponse);
        undo.setToolTipText("이번 실행의 마지막 일정 응답·사유 변경을 한 번 복원하고 자동 저장합니다.");
        detail.add(bulkControls, BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, calendar, detail);
        split.setBorder(BorderFactory.createEmptyBorder()); split.setResizeWeight(0.58); split.setDividerSize(16);
        split.setOpaque(false);
        split.setDividerLocation(0.58); split.setContinuousLayout(true);
        add(split, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0, 6)); footer.setOpaque(false);
        footer.add(action("오늘", () -> selectDate(LocalDate.now())), BorderLayout.WEST);
        footer.add(action("선택 해제", this::clearSelection), BorderLayout.EAST);
        footer.add(selectionInfo, BorderLayout.CENTER);
        JPanel legend = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 9, 0)); legend.setOpaque(false);
        legend.add(legendItem("●", new Color(45, 97, 235), "가능"));
        legend.add(legendItem("●", new Color(190, 62, 54), "불가"));
        legend.add(legendItem("●", new Color(18, 112, 91), "전원 가능"));
        legend.add(legendItem("●", new Color(119, 73, 170), "합주 확정"));
        legend.add(legendItem("✓", UiTheme.ACCENT, "적용할 날짜"));
        JPanel footerBottom = new JPanel(new BorderLayout(0, 8)); footerBottom.setOpaque(false);
        footerBottom.add(legend, BorderLayout.CENTER);
        JPanel undoRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0)); undoRow.setOpaque(false);
        undoRow.add(undo); footerBottom.add(undoRow, BorderLayout.EAST);
        footer.add(footerBottom, BorderLayout.SOUTH);
        calendar.add(footer, BorderLayout.SOUTH);
        bulkMember.addActionListener(e -> {
            if (!refreshing && bulkMember.getSelectedIndex() >= 0) { refresh(); memberSelectionListener.run(); }
        });
        refresh();
    }

    private static JPanel legendItem(String icon, Color color, String label) {
        JPanel item = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 3, 0)); item.setOpaque(false);
        JLabel symbol = new JLabel(icon); symbol.setForeground(color);
        JLabel text = new JLabel(label); text.setForeground(UiTheme.MUTED);
        item.add(symbol); item.add(text); return item;
    }

    private JButton selectionButton(String title, Runnable task) {
        JButton button = action(title, task);
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
            @Override public void update(java.awt.Graphics graphics, javax.swing.JComponent component) {
                java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(button.getBackground()); g.fillRoundRect(0, 0, component.getWidth(), component.getHeight(), 14, 14);
                g.dispose(); super.paint(graphics, component);
            }
        });
        button.setContentAreaFilled(false); button.setOpaque(false);
        button.setBorder(new UiTheme.RoundedBorder(UiTheme.LINE));
        return button;
    }
    public void setRangeSelection(boolean value) {
        rangeSelection = value; rangeStart = null; selectedDays = new LocalDate[0]; refresh();
    }
    private boolean isSelected(LocalDate date) {
        for (LocalDate day : selectedDays) if (day.equals(date)) return true;
        return false;
    }
    public LocalDate[] getSelectedDates() { return selectedDays.clone(); }
    public int getSelectedMember() { return bulkMember.getSelectedIndex(); }
    public JComboBox<String> memberSelector() { return bulkMember; }
    public boolean finishEditing() {
        return !responses.isEditing() || responses.getCellEditor().stopCellEditing();
    }
    public void setMemberSelectionListener(Runnable listener) { memberSelectionListener = listener; }
    public void clearSelection() { selectedDays = new LocalDate[0]; rangeStart = null; refresh(); }
    public void pickDate(LocalDate date) {
        if (date == null) return;
        if (!inWindow(date)) throw new IllegalArgumentException("응답 기간 밖의 날짜는 선택할 수 없습니다.");
        if (responses.isEditing() && !responses.getCellEditor().stopCellEditing()) return;
        selectedDate = date; month = boundedMonth(YearMonth.from(date));
        if (rangeSelection) {
            if (rangeStart == null) { rangeStart = date; selectedDays = new LocalDate[] {date}; }
            else {
                LocalDate start = rangeStart.isBefore(date) ? rangeStart : date;
                LocalDate end = rangeStart.isBefore(date) ? date : rangeStart;
                selectedDays = new LocalDate[(int) java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1];
                for (int i = 0; i < selectedDays.length; i++) selectedDays[i] = start.plusDays(i);
                rangeStart = null;
            }
        } else {
            selectedDays = new LocalDate[] {date};
        }
        refresh();
    }
    public void selectWholeMonth() {
        rangeStart = null;
        LocalDate[] all = selectableMonthDates();
        if (Arrays.equals(selectedDays, all)) { clearSelection(); return; }
        selectedDays = all;
        if (selectedDays.length > 0 && (!YearMonth.from(selectedDate).equals(month) || selectedDate.isBefore(LocalDate.now()))) selectedDate = selectedDays[0];
        refresh();
    }
    private LocalDate[] selectableMonthDates() {
        LocalDate[] all = new LocalDate[month.lengthOfMonth()]; int size = 0;
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            if (!date.isBefore(LocalDate.now())) all[size++] = date;
        }
        return Arrays.copyOf(all, size);
    }
    public void applySelected(int member, int choice, String reason) {
        if (rangeStart != null) throw new IllegalArgumentException("기간의 종료일을 선택하세요.");
        for (LocalDate date : selectedDays) if (!inWindow(date)) throw new IllegalArgumentException("응답 기간 밖의 날짜는 변경할 수 없습니다.");
        AttendanceService.Snapshot previous = attendance.snapshot();
        attendance.respondMany(member, selectedDays, choice, reason);
        if (!previous.matches(attendance)) undoState = previous;
        changed.run();
    }
    public void undoLastResponse() {
        if (!finishEditing()) return;
        if (undoState == null) throw new IllegalArgumentException("되돌릴 일정 응답이 없습니다.");
        attendance.restore(undoState); undoState = null; changed.run();
    }

    private JButton action(String title, Runnable task) {
        JButton button = new JButton(title); UiTheme.styleButton(button, false);
        button.addActionListener(e -> {
            if (responses != null && responses.isEditing() && !responses.getCellEditor().stopCellEditing()) return;
            try { task.run(); }
            catch (IllegalArgumentException ex) { JOptionPane.showMessageDialog(this, ex.getMessage(), "확인해 주세요", JOptionPane.WARNING_MESSAGE); }
        });
        return button;
    }
    private JTable responseTable() {
        DefaultTableModel model = new DefaultTableModel(new String[] {"파트", "응답", "불가 사유 (선택)"}, 0) {
            private static final long serialVersionUID = 1L;
            @Override public boolean isCellEditable(int row, int column) {
                return inWindow(selectedDate) && (column == 1 || column == 2 && attendance.response(row, selectedDate) == AttendanceService.UNAVAILABLE);
            }
            @Override public void setValueAt(Object value, int row, int column) {
                if (refreshing) { super.setValueAt(value, row, column); return; }
                if (!inWindow(selectedDate)) return;
                AttendanceService.Snapshot previousState = attendance.snapshot();
                if (column == 1) {
                    int choice = "가능".equals(value) ? AttendanceService.AVAILABLE : "불가".equals(value) ? AttendanceService.UNAVAILABLE : AttendanceService.UNANSWERED;
                    String previous = attendance.response(row, selectedDate) == AttendanceService.UNAVAILABLE ? attendance.reason(row, selectedDate) : "";
                    attendance.respond(row, selectedDate, choice, previous);
                } else if (column == 2) attendance.respond(row, selectedDate, AttendanceService.UNAVAILABLE, value == null ? "" : value.toString());
                else return;
                if (!previousState.matches(attendance)) undoState = previousState;
                super.setValueAt(value, row, column);
                // JTable은 모델 저장 뒤 편집기를 제거한다. 갱신 콜백 전에 먼저 제거해
                // 행 재구성 중 입력 소실과 stopCellEditing 재진입을 방지한다.
                responses.removeEditor();
                changed.run();
            }
        };
        JTable table = new JTable(model); UiTheme.styleTable(table); table.setRowHeight(34);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        JComboBox<String> choices = new JComboBox<>(new String[] {"미응답", "가능", "불가"}); UiTheme.styleCombo(choices);
        table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(choices));
        table.getColumnModel().getColumn(0).setPreferredWidth(110);
        table.getColumnModel().getColumn(1).setPreferredWidth(75);
        table.getColumnModel().getColumn(2).setPreferredWidth(180);
        return table;
    }
    private boolean hasDate(LocalDate date) {
        for (LocalDate day : attendance.getDates()) if (day.equals(date)) return true;
        return false;
    }
    private boolean inWindow(LocalDate date) {
        return ScheduleWindow.contains(date, LocalDate.now());
    }
    public void selectDate(LocalDate date) {
        if (date == null) return;
        if (responses.isEditing() && !responses.getCellEditor().stopCellEditing()) return;
        selectedDate = date; selectedDays = inWindow(date) ? new LocalDate[] {date} : new LocalDate[0]; rangeStart = null;
        month = boundedMonth(YearMonth.from(date)); refresh();
    }
    private YearMonth boundedMonth(YearMonth value) {
        YearMonth current = YearMonth.now();
        return value.isBefore(current) ? current : value.isAfter(current.plusMonths(1)) ? current.plusMonths(1) : value;
    }
    public void showMonth(int offset) {
        if (offset < 0 || offset > 1) throw new IllegalArgumentException("이번 달과 다음 달만 표시할 수 있습니다.");
        month = YearMonth.now().plusMonths(offset); refresh();
    }
    private void moveMonth(int direction) {
        YearMonth target = month.plusMonths(direction);
        if (target.equals(boundedMonth(target))) { month = target; refresh(); }
    }
    public LocalDate getSelectedDate() { return selectedDate; }
    public YearMonth getVisibleMonth() { return month; }
    public String statusFor(LocalDate date) {
        if (!inWindow(date)) return date.isBefore(LocalDate.now()) ? "지난 날짜" : "추천 기간 밖";
        int pending = attendance.count(date, AttendanceService.UNANSWERED);
        if (pending > 0) return pending == members.length ? "미응답" : "대기 " + pending + "명";
        int available = attendance.count(date, AttendanceService.AVAILABLE);
        return available == members.length ? "합주 가능" : "가능 " + available + "/" + members.length + "명";
    }
    public void setMembers(Member[] members) {
        this.members = members;
        undoState = null;
    }
    private String planSummary(LocalDate date) {
        int count = 0;
        for (ConfirmedPlan entry : plans.get()) if (entry.plan().getDate().equals(date)) count++;
        return count == 0 ? "" : count + "건";
    }
    public void refresh() {
        if (!finishEditing()) return;
        refreshing = true;
        try {
            LocalDate[] remaining = new LocalDate[selectedDays.length]; int remainingCount = 0;
            for (LocalDate date : selectedDays) if (inWindow(date)) remaining[remainingCount++] = date;
            selectedDays = Arrays.copyOf(remaining, remainingCount);
            if (rangeStart != null && rangeStart.isBefore(LocalDate.now())) rangeStart = null;
            month = boundedMonth(month);
            monthTitle.setText(month.getYear() + "년 " + month.getMonthValue() + "월");
            previousMonth.setEnabled(month.isAfter(YearMonth.now()));
            nextMonth.setEnabled(month.isBefore(YearMonth.now().plusMonths(1)));
            previousMonth.setToolTipText(previousMonth.isEnabled() ? "이전 달 보기" : "표시 가능한 첫 달입니다.");
            nextMonth.setToolTipText(nextMonth.isEnabled() ? "다음 달 보기" : "표시 가능한 마지막 달입니다.");
            singleMode.setBackground(rangeSelection ? UiTheme.SOFT : UiTheme.ACCENT_SOFT);
            rangeMode.setBackground(rangeSelection ? UiTheme.ACCENT_SOFT : UiTheme.SOFT);
            wholeMonth.setBackground(Arrays.equals(selectedDays, selectableMonthDates()) ? UiTheme.ACCENT_SOFT : Color.WHITE);
            singleMode.setForeground(rangeSelection ? UiTheme.MUTED : UiTheme.ACCENT);
            rangeMode.setForeground(rangeSelection ? UiTheme.ACCENT : UiTheme.MUTED);
            wholeMonth.setForeground(UiTheme.ACCENT);
            wholeMonth.setToolTipText(month.getMonthValue() + "월의 오늘 이후 날짜 전체 선택 · 다시 누르면 해제");
            selectionInfo.setText("  적용할 날짜 " + selectedDays.length + "일 선택" + (rangeStart != null ? " · 종료일을 누르세요" : ""));
            for (JButton button : applyButtons) button.setEnabled(selectedDays.length > 0 && rangeStart == null);
            undo.setEnabled(undoState != null);
            boolean outside = false;
            recordedDatePicker.removeAllItems();
            for (LocalDate date : attendance.getDates()) {
                recordedDatePicker.addItem(date);
                if (!boundedMonth(YearMonth.from(date)).equals(YearMonth.from(date))) outside = true;
            }
            recordedDatePicker.setSelectedItem(hasDate(selectedDate) ? selectedDate : null);
            recordedDatePicker.setVisible(outside);
            int offset = month.atDay(1).getDayOfWeek().getValue() % 7;
            int cells = ((offset + month.lengthOfMonth() + 6) / 7) * 7;
            dayGrid.removeAll();
            for (int i = 0; i < cells; i++) dayGrid.add(days[i]);
            for (int i = 0; i < days.length; i++) {
                JButton button = days[i]; int day = i - offset + 1;
                boolean valid = day >= 1 && day <= month.lengthOfMonth(); button.setVisible(valid);
                if (!valid) { button.putClientProperty("date", null); continue; }
                LocalDate date = month.atDay(day);
                boolean past = date.isBefore(LocalDate.now()); button.setEnabled(!past);
                button.putClientProperty("date", date);
                String confirmed = planSummary(date);
                String label = !confirmed.isEmpty() ? "합주 확정"
                    : inWindow(date) ? statusFor(date) : date.equals(LocalDate.now()) ? "오늘" : "";
                button.setText(day + " " + label);
                button.putClientProperty("calendar.number", day);
                button.putClientProperty("calendar.status", label);
                button.putClientProperty("calendar.confirmed", !confirmed.isEmpty());
                button.putClientProperty("calendar.selected", isSelected(date));
                button.putClientProperty("calendar.focused", date.equals(selectedDate));
                int mine = attendance.response(bulkMember.getSelectedIndex(), date);
                Color background = mine == AttendanceService.AVAILABLE ? new Color(222, 234, 255)
                    : mine == AttendanceService.UNAVAILABLE ? new Color(255, 229, 225) : Color.WHITE;
                if (inWindow(date) && attendance.count(date, AttendanceService.AVAILABLE) == members.length)
                    background = new Color(219, 242, 232);
                if (mine == AttendanceService.UNANSWERED && isSelected(date)) background = new Color(240, 235, 255);
                if (!confirmed.isEmpty()) background = new Color(235, 225, 252);
                else if (past) background = UiTheme.SOFT;
                button.setBackground(background);
                String ownResponse = members[bulkMember.getSelectedIndex()].displayName() + ": " + AttendanceService.label(mine);
                String planText = confirmed.isEmpty() ? "" : " · 확정 계획 " + confirmed;
                button.setToolTipText(date + " · " + ownResponse + " · " + statusFor(date) + planText);
                button.getAccessibleContext().setAccessibleName(date + " " + ownResponse + " " + statusFor(date) + planText);
                button.setForeground(Color.BLACK);
            }
            detailTitle.setText(selectedDate.format(DateTimeFormatter.ofPattern("M월 d일 (E)", java.util.Locale.KOREAN)));
            responses.setEnabled(inWindow(selectedDate));
            String selectedPlans = planSummary(selectedDate);
            detailStatus.setText(statusFor(selectedDate) + (selectedPlans.isEmpty() ? "" : " · 확정 계획 " + selectedPlans));
            detailStatus.setForeground(UiTheme.ACCENT);
            String target = members[bulkMember.getSelectedIndex()].displayName();
            guidance.setText("<html>" + target + " · 선택한 " + selectedDays.length + "일에 적용"
                + (rangeStart != null ? " (종료일을 선택하세요)" : "")
                + "<br>위 응답표를 직접 편집하면 " + selectedDate.getMonthValue() + "/" + selectedDate.getDayOfMonth() + " 하루만 변경됩니다.</html>");
            DefaultTableModel model = (DefaultTableModel) responses.getModel(); model.setRowCount(0);
            for (int member = 0; member < members.length; member++) model.addRow(new Object[] {
                members[member].displayName(), AttendanceService.label(attendance.response(member, selectedDate)),
                attendance.response(member, selectedDate) == AttendanceService.UNAVAILABLE ? attendance.reason(member, selectedDate) : ""});
            revalidate(); repaint();
        } finally { refreshing = false; }
    }
}
