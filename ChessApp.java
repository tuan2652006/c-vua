import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Cửa sổ chính của game cờ vua. */
public class ChessApp {

    /** Các mức độ khó của máy. */
    private enum Level {
        EASY("Dễ", 1, 150),
        MEDIUM("Trung bình", 2, 12),
        HARD("Khó", 3, 0);

        final String label;
        final int depth;
        final int noise;

        Level(String label, int depth, int noise) {
            this.label = label;
            this.depth = depth;
            this.noise = noise;
        }
    }

    private final Game game = new Game();
    private final JFrame frame = new JFrame("Cờ vua - Java Swing");
    private final BoardPanel boardPanel = new BoardPanel(game);
    private final JLabel statusLabel = new JLabel(" ");
    private final JTextArea moveArea = new JTextArea();
    private final JLabel capturedByWhite = new JLabel(" ");
    private final JLabel capturedByBlack = new JLabel(" ");
    private final JButton undoButton = new JButton("Đi lại");

    /** Chỉ số 0: chơi với máy (bạn Trắng), 1: chơi với máy (bạn Đen), 2: hai người chơi. */
    private static final String[] MODE_NAMES = {
            "Chơi với máy (bạn cầm Trắng)", "Chơi với máy (bạn cầm Đen)", "Hai người chơi"};
    private final JComboBox<String> modeCombo = new JComboBox<>(MODE_NAMES);
    private final List<JRadioButtonMenuItem> modeItems = new ArrayList<>();
    private boolean syncingMode;

    private Side aiSide = Side.BLACK; // null = hai người chơi
    private Level level = Level.MEDIUM;
    private boolean thinking;
    private int generation;
    private boolean announced;

    // ------------------------------------------------------------------ dựng giao diện

    private void build() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setJMenuBar(buildMenuBar());

        boardPanel.setInputEnabled(() -> !thinking && game.status() == Game.Status.ONGOING
                && (aiSide == null || game.turn() != aiSide));
        boardPanel.setOnMove(this::onHumanMoved);

        frame.add(boardPanel, BorderLayout.CENTER);
        frame.add(buildSidePanel(), BorderLayout.EAST);

        frame.pack();
        frame.setMinimumSize(new Dimension(700, 520));
        frame.setLocationRelativeTo(null);

        // hỏi chế độ chơi ngay khi mở game
        int choice = JOptionPane.showOptionDialog(frame, "Bạn muốn chơi theo chế độ nào?", "Chọn chế độ chơi",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, MODE_NAMES, MODE_NAMES[2]);
        applyMode(choice < 0 ? 2 : choice);
        frame.setVisible(true);

        refreshUi();
        maybeAiMove();
    }

    private JMenuBar buildMenuBar() {
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        JMenu gameMenu = new JMenu("Ván cờ");
        JMenuItem newItem = new JMenuItem("Ván mới");
        newItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, mask));
        newItem.addActionListener(e -> newGame());
        JMenuItem undoItem = new JMenuItem("Đi lại");
        undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, mask));
        undoItem.addActionListener(e -> undo());
        JMenuItem flipItem = new JMenuItem("Lật bàn cờ");
        flipItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, mask));
        flipItem.addActionListener(e -> boardPanel.setFlipped(!boardPanel.isFlipped()));
        JMenuItem exitItem = new JMenuItem("Thoát");
        exitItem.addActionListener(e -> System.exit(0));
        gameMenu.add(newItem);
        gameMenu.add(undoItem);
        gameMenu.add(flipItem);
        gameMenu.addSeparator();
        gameMenu.add(exitItem);

        JMenu modeMenu = new JMenu("Chế độ");
        ButtonGroup modeGroup = new ButtonGroup();
        for (int i = 0; i < MODE_NAMES.length; i++) {
            final int index = i;
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(MODE_NAMES[i], i == 0);
            item.addActionListener(e -> setMode(index));
            modeGroup.add(item);
            modeMenu.add(item);
            modeItems.add(item);
        }

        JMenu levelMenu = new JMenu("Độ khó của máy");
        ButtonGroup levelGroup = new ButtonGroup();
        for (Level lv : Level.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(lv.label, lv == level);
            item.addActionListener(e -> level = lv);
            levelGroup.add(item);
            levelMenu.add(item);
        }

        JMenuBar bar = new JMenuBar();
        bar.add(gameMenu);
        bar.add(modeMenu);
        bar.add(levelMenu);
        return bar;
    }

    /** Áp dụng chế độ chơi (không bắt đầu ván mới) và đồng bộ menu + ô chọn. */
    private void applyMode(int index) {
        aiSide = switch (index) {
            case 0 -> Side.BLACK;
            case 1 -> Side.WHITE;
            default -> null; // hai người chơi
        };
        boardPanel.setFlipped(index == 1); // người cầm Đen thì lật bàn cờ
        syncingMode = true;
        modeCombo.setSelectedIndex(index);
        modeItems.get(index).setSelected(true);
        syncingMode = false;
    }

    private void setMode(int index) {
        if (syncingMode) return;
        applyMode(index);
        newGame();
    }

    private JPanel buildSidePanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.setPreferredSize(new Dimension(260, 640));

        panel.add(sectionLabel("Chế độ chơi:"));
        modeCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        modeCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        modeCombo.addActionListener(e -> setMode(modeCombo.getSelectedIndex()));
        panel.add(modeCombo);
        panel.add(Box.createVerticalStrut(12));

        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 16f));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(statusLabel);
        panel.add(Box.createVerticalStrut(12));

        Font symbols = BoardPanel.symbolFont().deriveFont(22f);
        panel.add(sectionLabel("Quân Trắng đã ăn:"));
        capturedByWhite.setFont(symbols);
        capturedByWhite.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(capturedByWhite);
        panel.add(Box.createVerticalStrut(6));
        panel.add(sectionLabel("Quân Đen đã ăn:"));
        capturedByBlack.setFont(symbols);
        capturedByBlack.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(capturedByBlack);
        panel.add(Box.createVerticalStrut(12));

        panel.add(sectionLabel("Các nước đi:"));
        moveArea.setEditable(false);
        moveArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        moveArea.setMargin(new java.awt.Insets(4, 6, 4, 6));
        JScrollPane scroll = new JScrollPane(moveArea);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(scroll);
        panel.add(Box.createVerticalStrut(12));

        JPanel buttons = new JPanel(new java.awt.GridLayout(1, 3, 6, 0));
        JButton newButton = new JButton("Ván mới");
        newButton.addActionListener(e -> newGame());
        undoButton.addActionListener(e -> undo());
        JButton flipButton = new JButton("Lật bàn");
        flipButton.addActionListener(e -> boardPanel.setFlipped(!boardPanel.isFlipped()));
        buttons.add(newButton);
        buttons.add(undoButton);
        buttons.add(flipButton);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        panel.add(buttons);
        return panel;
    }

    private JLabel sectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 3, 0));
        return l;
    }

    // ------------------------------------------------------------------ điều khiển ván cờ

    private void newGame() {
        generation++;
        thinking = false;
        announced = false;
        game.reset();
        boardPanel.clearSelection();
        boardPanel.repaint();
        refreshUi();
        maybeAiMove();
    }

    private void undo() {
        if (thinking || !game.canUndo()) return;
        game.undo();
        if (aiSide != null) {
            while (game.turn() == aiSide && game.canUndo()) game.undo();
        }
        announced = false;
        boardPanel.clearSelection();
        boardPanel.repaint();
        refreshUi();
        maybeAiMove();
    }

    private void onHumanMoved() {
        refreshUi();
        maybeAiMove();
    }

    private void maybeAiMove() {
        if (aiSide == null || thinking || game.status() != Game.Status.ONGOING || game.turn() != aiSide) return;
        thinking = true;
        final int gen = ++generation;
        final Board snapshot = game.board().copy();
        final Ai ai = new Ai(level.depth, level.noise);
        refreshUi();
        new SwingWorker<Move, Void>() {
            @Override
            protected Move doInBackground() {
                return ai.bestMove(snapshot);
            }

            @Override
            protected void done() {
                if (gen != generation) return; // ván đã bị reset trong lúc máy nghĩ
                thinking = false;
                try {
                    Move m = get();
                    if (m != null) game.play(m);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                boardPanel.repaint();
                refreshUi();
            }
        }.execute();
    }

    // ------------------------------------------------------------------ cập nhật giao diện

    private void refreshUi() {
        statusLabel.setText(statusText());
        undoButton.setEnabled(!thinking && game.canUndo());

        StringBuilder sb = new StringBuilder();
        List<String> san = game.sanList();
        for (int i = 0; i < san.size(); i += 2) {
            sb.append(i / 2 + 1).append(". ").append(san.get(i));
            if (i + 1 < san.size()) sb.append("   ").append(san.get(i + 1));
            sb.append('\n');
        }
        moveArea.setText(sb.toString());
        moveArea.setCaretPosition(moveArea.getDocument().getLength());

        List<Piece> byWhite = new ArrayList<>(), byBlack = new ArrayList<>();
        for (Piece p : game.capturedPieces()) {
            (p.side() == Side.BLACK ? byWhite : byBlack).add(p);
        }
        capturedByWhite.setText(glyphs(byWhite));
        capturedByBlack.setText(glyphs(byBlack));

        if (game.status() != Game.Status.ONGOING && !announced) {
            announced = true;
            String text = statusText();
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(frame, text, "Kết thúc ván cờ", JOptionPane.INFORMATION_MESSAGE));
        }
    }

    private String glyphs(List<Piece> pieces) {
        pieces.sort(Comparator.comparingInt((Piece p) -> p.type().ordinal()));
        StringBuilder sb = new StringBuilder();
        for (Piece p : pieces) sb.append(p.glyph()).append('\u2009');
        return sb.length() == 0 ? " " : sb.toString();
    }

    private String statusText() {
        if (thinking) return "Máy đang suy nghĩ...";
        Side turn = game.turn();
        return switch (game.status()) {
            case ONGOING -> "Lượt: " + turn.vi + (game.board().inCheck(turn) ? " — CHIẾU!" : "");
            case CHECKMATE -> "Chiếu hết! " + turn.opposite().vi + " thắng";
            case STALEMATE -> "Hòa: hết nước đi (bí)";
            case FIFTY_MOVE -> "Hòa: luật 50 nước";
            case INSUFFICIENT -> "Hòa: không đủ quân chiếu hết";
            case REPETITION -> "Hòa: lặp lại thế cờ 3 lần";
        };
    }

    // ------------------------------------------------------------------ main

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // dùng giao diện mặc định
        }
        SwingUtilities.invokeLater(() -> new ChessApp().build());
    }
}
