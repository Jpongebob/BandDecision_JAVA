import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.EnumMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/** 사용자의 작업 세 가지와 밴드 설정을 연결하는 사이드바. */
public final class WorkspaceView extends JPanel {
    private static final long serialVersionUID = 1L;
    public static final int SIDEBAR_WIDTH = 200;
    public enum Page { SCHEDULE, SONGS, PLANS, SETTINGS }
    private final CardLayout layout = new CardLayout();
    private final JPanel content = new JPanel(layout);
    private final EnumMap<Page, JScrollPane> pages = new EnumMap<>(Page.class);
    private final EnumMap<Page, JButton> links = new EnumMap<>(Page.class);
    private final BooleanSupplier beforeNavigation;
    private final Consumer<Page> navigated;
    private final JComboBox<String> memberSelector;
    private Page selected = Page.SCHEDULE;

    public WorkspaceView(BooleanSupplier beforeNavigation, Consumer<Page> navigated, JComboBox<String> memberSelector) {
        super(new BorderLayout(20, 0));
        this.beforeNavigation = beforeNavigation; this.navigated = navigated;
        this.memberSelector = memberSelector;
        setOpaque(false); content.setOpaque(false);
        JPanel sidebar = UiTheme.card(new BorderLayout(0, 24), Color.WHITE, 12);
        sidebar.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 600));
        JPanel brand = new JPanel(new java.awt.GridLayout(0, 1, 0, 3)); brand.setOpaque(false);
        JLabel title = new JLabel("BAND"); title.setFont(UiTheme.font(Font.BOLD, 26)); title.setForeground(UiTheme.ACCENT);
        JLabel subtitle = new JLabel("DECISION"); subtitle.setFont(UiTheme.font(Font.BOLD, 13)); subtitle.setForeground(UiTheme.MUTED);
        brand.add(title); brand.add(subtitle); brand.setBorder(BorderFactory.createEmptyBorder(12, 10, 18, 0));
        JPanel memberPanel = new JPanel(new BorderLayout(0, 7)); memberPanel.setOpaque(false);
        memberPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        JLabel memberLabel = new JLabel("입력할 멤버"); memberLabel.setLabelFor(memberSelector);
        memberLabel.setForeground(UiTheme.MUTED);
        memberPanel.add(memberLabel, BorderLayout.NORTH);
        memberPanel.add(memberSelector, BorderLayout.CENTER);
        JPanel sidebarTop = new JPanel(new BorderLayout(0, 14)); sidebarTop.setOpaque(false);
        sidebarTop.add(brand, BorderLayout.NORTH);
        sidebarTop.add(memberPanel, BorderLayout.CENTER);
        sidebar.add(sidebarTop, BorderLayout.NORTH);
        JPanel mainLinks = new JPanel(); mainLinks.setOpaque(false);
        mainLinks.setLayout(new javax.swing.BoxLayout(mainLinks, javax.swing.BoxLayout.Y_AXIS));
        addLink(mainLinks, Page.SCHEDULE, "일정");
        addLink(mainLinks, Page.SONGS, "곡·투표");
        addLink(mainLinks, Page.PLANS, "합주 계획");
        sidebar.add(mainLinks, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0, 12)); bottom.setOpaque(false);
        JLabel tip = new JLabel("<html>함께 맞추고<br>함께 연주해요.</html>"); tip.setForeground(UiTheme.MUTED);
        bottom.add(tip, BorderLayout.NORTH);
        addLink(bottom, Page.SETTINGS, "밴드 설정");
        sidebar.add(bottom, BorderLayout.SOUTH);
        add(sidebar, BorderLayout.WEST); add(content, BorderLayout.CENTER);
    }
    private void addLink(JPanel parent, Page page, String title) {
        JButton link = new JButton(title); UiTheme.styleButton(link, false);
        link.setHorizontalAlignment(JButton.LEFT); link.setAlignmentX(0);
        link.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        link.addActionListener(e -> show(page)); links.put(page, link);
        if (parent.getLayout() instanceof BorderLayout) parent.add(link, BorderLayout.SOUTH);
        else { parent.add(link); parent.add(javax.swing.Box.createVerticalStrut(10)); }
    }
    public void addPage(Page page, JPanel body) {
        JScrollPane scroll = UiTheme.scrollPage(body); pages.put(page, scroll); content.add(scroll, page.name());
    }
    public boolean show(Page page) {
        if (!pages.containsKey(page)) throw new IllegalArgumentException("존재하지 않는 화면입니다.");
        if (!beforeNavigation.getAsBoolean()) return false;
        selected = page; layout.show(content, page.name());
        for (Page key : links.keySet()) {
            JButton link = links.get(key);
            link.setForeground(key == page ? UiTheme.ACCENT : UiTheme.INK);
            link.setBorder(BorderFactory.createCompoundBorder(
                new UiTheme.RoundedBorder(key == page ? UiTheme.ACCENT : UiTheme.LINE),
                BorderFactory.createEmptyBorder(4, 0, 4, 0)));
        }
        navigated.accept(page); revalidate(); repaint(); return true;
    }
    public Page selected() { return selected; }
    public JComboBox<String> memberSelector() { return memberSelector; }
    public JScrollPane page(Page page) { return pages.get(page); }
}
