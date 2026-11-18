import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.DefaultTableCellRenderer;

/** 외부 라이브러리 없이 사용하는 공통 색상·카드·버튼·표 스타일. */
public final class UiTheme {
    public static final Color BACKGROUND = new Color(246, 248, 252);
    public static final Color INK = new Color(26, 39, 65);
    public static final Color MUTED = new Color(91, 105, 131);
    public static final Color ACCENT = new Color(43, 96, 235);
    public static final Color ACCENT_SOFT = new Color(234, 240, 255);
    public static final Color SOFT = new Color(244, 246, 250);
    public static final Color LINE = new Color(225, 231, 241);
    private static final String FONT_FAMILY = selectFont();
    private UiTheme() { }
    private static String selectFont() {
        String[] installed = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String preferred : new String[] {"Noto Sans KR", "Noto Sans CJK KR", "Malgun Gothic", "맑은 고딕"})
            for (String family : installed) if (family.equalsIgnoreCase(preferred)) return family;
        return Font.SANS_SERIF;
    }
    public static Font font(int style, float size) { return new Font(FONT_FAMILY, style, Math.round(size)).deriveFont(size); }
    /** 작은 창/큰 글자에서는 페이지 전체를 스크롤해 하단 조작부를 보존한다. */
    public static JScrollPane scrollPage(JPanel page) {
        JScrollPane scroll = scroll(new Page(page));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        return scroll;
    }
    private static final class Page extends JPanel implements javax.swing.Scrollable {
        private static final long serialVersionUID = 1L;
        Page(JPanel content) { super(new java.awt.BorderLayout()); setOpaque(false); add(content); }
        @Override public java.awt.Dimension getPreferredSize() {
            float scale = getFont().getSize2D() / 18f;
            return new java.awt.Dimension(Math.round(1100 * scale), Math.round(640 * scale));
        }
        @Override public java.awt.Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 24; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) {
            return Math.max(24, (o == SwingConstants.VERTICAL ? r.height : r.width) - 24);
        }
        @Override public boolean getScrollableTracksViewportWidth() {
            return getParent() != null && getParent().getWidth() >= getPreferredSize().width;
        }
        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() != null && getParent().getHeight() >= getPreferredSize().height;
        }
    }
    public static void resizeFonts(Component component, float scale) {
        if (component instanceof JComponent) {
            JComponent control = (JComponent) component;
            Font base = (Font) control.getClientProperty("banddecision.baseFont");
            if (base == null && control.getFont() != null) {
                base = control.getFont(); control.putClientProperty("banddecision.baseFont", base);
            }
            if (base != null) control.setFont(base.deriveFont(base.getSize2D() * scale));
            if (control instanceof JTable) {
                JTable table = (JTable) control;
                Integer height = (Integer) table.getClientProperty("banddecision.baseRowHeight");
                if (height == null) { height = table.getRowHeight(); table.putClientProperty("banddecision.baseRowHeight", height); }
                table.setRowHeight(Math.round(height * scale));
                resizeFonts(table.getTableHeader(), scale);
                if (table.getTableHeader().getDefaultRenderer() instanceof JComponent)
                    resizeFonts((JComponent) table.getTableHeader().getDefaultRenderer(), scale);
                table.getTableHeader().setPreferredSize(new java.awt.Dimension(100, Math.round(46 * scale)));
            }
        }
        if (component instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) component).getComponents()) resizeFonts(child, scale);
        }
        if (component.getParent() != null && component.getParent().getLayout() instanceof javax.swing.BoxLayout)
            component.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        if (component instanceof javax.swing.JSpinner)
            component.setPreferredSize(new java.awt.Dimension(Math.round(88 * scale), Math.round(44 * scale)));
    }
    /** 자식 컴포넌트에도 일관된 글자 안티앨리어싱을 전달한다. */
    public static JPanel surface(LayoutManager layout) {
        return new JPanel(layout) {
            private static final long serialVersionUID = 1L;
            @Override public void paint(Graphics graphics) {
                Graphics2D g = smooth(graphics);
                super.paint(g); g.dispose();
            }
        };
    }
    private static Graphics2D smooth(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }
    public static JPanel card(LayoutManager layout, Color color, int padding) {
        JPanel panel = new JPanel(layout) {
            private static final long serialVersionUID = 1L;
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = smooth(graphics);
                g.setColor(color); g.fillRoundRect(0, 0, getWidth(), getHeight(), 26, 26);
                g.setColor(LINE); g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 26, 26); g.dispose();
                super.paintComponent(graphics);
            }
        };
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));
        return panel;
    }
    public static final class RoundedBorder extends AbstractBorder {
        private static final long serialVersionUID = 1L;
        private final Color color;
        public RoundedBorder(Color color) { this.color = color; }
        @Override public Insets getBorderInsets(Component c) { return new Insets(8, 12, 8, 12); }
        @Override public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(8, 12, 8, 12); return insets;
        }
        @Override public void paintBorder(Component c, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = smooth(graphics); g.setColor(c.hasFocus() ? ACCENT : color);
            g.setStroke(new BasicStroke(c.hasFocus() ? 1.5f : 1f));
            g.drawRoundRect(x, y, width - 1, height - 1, 12, 12); g.dispose();
        }
    }
    public static void styleButton(JButton button, boolean primary) {
        button.setFont(button.getFont().deriveFont(Font.BOLD, 16));
        button.setForeground(primary ? Color.WHITE : INK);
        button.setBorder(BorderFactory.createEmptyBorder(11, 17, 11, 17));
        button.setContentAreaFilled(false); button.setFocusPainted(false); button.setOpaque(false);
        button.setRolloverEnabled(true); button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setUI(new BasicButtonUI() {
            @Override public void update(Graphics graphics, JComponent component) {
                Graphics2D g = smooth(graphics);
                Color fill = primary ? ACCENT : Color.WHITE;
                if (!button.isEnabled()) fill = SOFT;
                else if (button.getModel().isPressed()) fill = primary ? new Color(31, 72, 183) : ACCENT_SOFT;
                else if (button.getModel().isRollover()) fill = primary ? new Color(35, 82, 207) : new Color(246, 249, 255);
                g.setColor(fill); g.fillRoundRect(0, 0, component.getWidth(), component.getHeight(), 16, 16);
                if (!primary) { g.setColor(LINE); g.drawRoundRect(0, 0, component.getWidth() - 1, component.getHeight() - 1, 16, 16); }
                if (button.isFocusOwner()) { g.setColor(primary ? new Color(172, 195, 255) : ACCENT); g.drawRoundRect(2, 2, component.getWidth() - 5, component.getHeight() - 5, 12, 12); }
                g.dispose(); super.paint(graphics, component);
            }
        });
    }
    /** 날짜 숫자·응답·선택 체크를 둥근 카드 안에 직접 그린다. */
    public static void styleCalendarDay(JButton button) {
        button.setOpaque(false); button.setContentAreaFilled(false); button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        button.setUI(new BasicButtonUI() {
            @Override public void update(Graphics graphics, JComponent component) {
                Graphics2D g = smooth(graphics);
                int w = component.getWidth(), h = component.getHeight();
                boolean selected = Boolean.TRUE.equals(button.getClientProperty("calendar.selected"));
                boolean focused = Boolean.TRUE.equals(button.getClientProperty("calendar.focused"));
                g.setColor(button.getBackground()); g.fillRoundRect(1, 1, w - 2, h - 2, 20, 20);
                g.setColor(selected || focused || button.isFocusOwner() ? ACCENT : LINE);
                g.setStroke(new BasicStroke(selected || focused ? 1.6f : 1f));
                g.drawRoundRect(1, 1, w - 3, h - 3, 20, 20);
                if (button.isEnabled() && button.getModel().isRollover()) {
                    g.setColor(new Color(43, 96, 235, 16)); g.fillRoundRect(1, 1, w - 2, h - 2, 20, 20);
                }
                String number = String.valueOf(button.getClientProperty("calendar.number"));
                String status = String.valueOf(button.getClientProperty("calendar.status"));
                Font numberFont = button.getFont().deriveFont(Font.BOLD);
                Font statusFont = button.getFont().deriveFont(Font.PLAIN, Math.max(11f, button.getFont().getSize2D() * 0.72f));
                int totalHeight = g.getFontMetrics(numberFont).getHeight() + (status.isEmpty() ? 0 : g.getFontMetrics(statusFont).getHeight());
                int y = Math.max(3, (h - totalHeight) / 2);
                g.setFont(numberFont); g.setColor(button.isEnabled() ? selected ? ACCENT : button.getForeground() : new Color(164, 174, 192));
                y += g.getFontMetrics().getAscent();
                g.drawString(number, (w - g.getFontMetrics().stringWidth(number)) / 2, y);
                if (!status.isEmpty()) {
                    y += g.getFontMetrics().getDescent(); g.setFont(statusFont); y += g.getFontMetrics().getAscent();
                    g.drawString(status, (w - g.getFontMetrics().stringWidth(status)) / 2, y);
                }
                if (selected) {
                    int size = Math.max(12, Math.round(button.getFont().getSize2D() * 0.75f));
                    g.setColor(ACCENT); g.fillOval(w - size - 5, 4, size, size);
                    g.setFont(button.getFont().deriveFont(Font.BOLD, size * 0.78f)); g.setColor(Color.WHITE);
                    g.drawString("✓", w - size - 4, size + 1);
                }
                g.dispose();
            }
        });
    }
    public static JScrollPane scroll(Component content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createLineBorder(LINE));
        scroll.getViewport().setBackground(Color.WHITE);
        scroll.setBackground(Color.WHITE);
        for (JScrollBar bar : new JScrollBar[] {scroll.getVerticalScrollBar(), scroll.getHorizontalScrollBar()}) {
            bar.setUnitIncrement(20);
            bar.setUI(new BasicScrollBarUI() {
                @Override protected void configureScrollBarColors() { thumbColor = new Color(188, 203, 210); trackColor = SOFT; }
                @Override protected JButton createDecreaseButton(int orientation) { return zeroButton(); }
                @Override protected JButton createIncreaseButton(int orientation) { return zeroButton(); }
                private JButton zeroButton() {
                    JButton button = new JButton(); button.setPreferredSize(new java.awt.Dimension(0, 0)); return button;
                }
                @Override protected void paintThumb(Graphics graphics, JComponent c, java.awt.Rectangle bounds) {
                    Graphics2D g = smooth(graphics); g.setColor(thumbColor);
                    g.fillRoundRect(bounds.x + 3, bounds.y + 3, Math.max(0, bounds.width - 6), Math.max(0, bounds.height - 6), 8, 8); g.dispose();
                }
            });
        }
        return scroll;
    }
    public static void styleCombo(JComboBox<?> combo) {
        combo.setBackground(Color.WHITE); combo.setForeground(INK);
        combo.setBorder(new RoundedBorder(LINE));
        combo.setUI(new BasicComboBoxUI() {
            @Override protected JButton createArrowButton() {
                JButton button = new JButton(new javax.swing.Icon() {
                    @Override public int getIconWidth() { return 10; }
                    @Override public int getIconHeight() { return 7; }
                    @Override public void paintIcon(Component c, Graphics graphics, int x, int y) {
                        Graphics2D g = smooth(graphics); g.setColor(MUTED);
                        g.fillPolygon(new int[] {x, x + 8, x + 4}, new int[] {y + 1, y + 1, y + 5}, 3); g.dispose();
                    }
                });
                button.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 3));
                button.setContentAreaFilled(false); button.setFocusPainted(false);
                return button;
            }
        });
    }
    public static void styleTable(JTable table) {
        table.setRowHeight(48); table.setIntercellSpacing(new java.awt.Dimension(0, 0));
        table.setShowGrid(false); table.setBackground(Color.WHITE);
        table.setSelectionBackground(ACCENT_SOFT); table.setSelectionForeground(INK);
        table.setDefaultRenderer(Object.class, new Cells());
        DefaultTableCellRenderer header = new DefaultTableCellRenderer();
        header.setBackground(SOFT); header.setForeground(MUTED);
        header.setFont(table.getFont().deriveFont(Font.BOLD, 16));
        header.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 10));
        table.getTableHeader().setDefaultRenderer(header);
        table.getTableHeader().setPreferredSize(new java.awt.Dimension(100, 46));
    }
    private static final class Cells extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;
        private Color badge;
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            setOpaque(false); badge = null;
            String text = value == null ? "" : value.toString();
            setBackground(selected ? table.getSelectionBackground() : row % 2 == 0 ? Color.WHITE : new Color(249, 251, 252));
            setForeground(INK); setFont(table.getFont()); setHorizontalAlignment(SwingConstants.LEFT);
            if (text.contains("★")) { setForeground(new Color(177, 128, 38)); setFont(getFont().deriveFont(Font.BOLD)); }
            if (text.equals("응답 완료") || text.equals("가능") || text.equals("찬성") || text.equals("추천 가능") || text.equals("제출 완료")) {
                badge = new Color(224, 242, 235); setForeground(new Color(24, 116, 86));
            } else if (text.equals("불가") || text.equals("반대") || text.equals("찬성 부족")) {
                badge = new Color(251, 234, 231); setForeground(new Color(169, 79, 66));
            } else if (text.equals("미제출") || text.equals("미응답") || text.equals("투표 미완료")) {
                badge = SOFT; setForeground(MUTED);
            }
            if (badge != null) setFont(getFont().deriveFont(Font.BOLD, Math.max(16f, table.getFont().getSize2D() - 1)));
            setBorder(BorderFactory.createEmptyBorder(0, badge == null ? 14 : 22, 0, 8));
            setToolTipText(text.isEmpty() ? null : text);
            return this;
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = smooth(graphics); g.setColor(getBackground()); g.fillRect(0, 0, getWidth(), getHeight());
            if (badge != null) {
                g.setColor(badge); int width = getFontMetrics(getFont()).stringWidth(getText()) + 20;
                int height = getFontMetrics(getFont()).getHeight() + 6;
                g.fillRoundRect(12, (getHeight() - height) / 2, Math.min(width, getWidth() - 16), height, 12, 12);
            }
            g.dispose(); super.paintComponent(graphics);
        }
    }
}
