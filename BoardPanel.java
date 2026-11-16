import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.GlyphVector;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Vẽ bàn cờ và xử lý thao tác chuột (bấm-bấm hoặc kéo-thả). */
public class BoardPanel extends JPanel {

    private static final Color LIGHT = new Color(0xF0D9B5);
    private static final Color DARK = new Color(0xB58863);

    private static Font symbolFont;

    private final Game game;
    private boolean flipped;
    private int selected = -1;
    private Set<Integer> targets = new HashSet<>();
    private boolean dragging;
    private int dragX, dragY;
    private boolean pressedOnSelected;

    private BooleanSupplier inputEnabled = () -> true;
    private Runnable onMove = () -> {
    };

    public BoardPanel(Game game) {
        this.game = game;
        setPreferredSize(new Dimension(640, 640));
        setMinimumSize(new Dimension(320, 320));
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handlePress(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragging) {
                    dragX = e.getX();
                    dragY = e.getY();
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleRelease(e);
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    // ------------------------------------------------------------------ API

    public void setInputEnabled(BooleanSupplier supplier) {
        this.inputEnabled = supplier;
    }

    public void setOnMove(Runnable r) {
        this.onMove = r;
    }

    public boolean isFlipped() {
        return flipped;
    }

    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
        repaint();
    }

    public void clearSelection() {
        selected = -1;
        targets = new HashSet<>();
        dragging = false;
    }

    /** Tìm font có sẵn trong máy hiển thị được ký tự cờ vua Unicode. */
    static synchronized Font symbolFont() {
        if (symbolFont == null) {
            Set<String> families = new HashSet<>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
            String[] candidates = {"Segoe UI Symbol", "Apple Symbols", "DejaVu Sans", "Noto Sans Symbols2",
                    "Noto Sans Symbols", "FreeSerif", "Arial Unicode MS", "Symbola"};
            Font found = null;
            for (String name : candidates) {
                if (families.contains(name)) {
                    Font f = new Font(name, Font.PLAIN, 48);
                    if (f.canDisplay('\u265A')) {
                        found = f;
                        break;
                    }
                }
            }
            symbolFont = found != null ? found : new Font(Font.DIALOG, Font.PLAIN, 48);
        }
        return symbolFont;
    }

    // ------------------------------------------------------------------ hình học

    private int cell() {
        return Math.min(getWidth(), getHeight()) / 8;
    }

    private int originX() {
        return (getWidth() - 8 * cell()) / 2;
    }

    private int originY() {
        return (getHeight() - 8 * cell()) / 2;
    }

    private int squareAt(int x, int y) {
        int cs = cell();
        if (cs <= 0) return -1;
        int px = x - originX(), py = y - originY();
        if (px < 0 || py < 0) return -1;
        int col = px / cs, row = py / cs;
        if (col > 7 || row > 7) return -1;
        if (flipped) {
            row = 7 - row;
            col = 7 - col;
        }
        return row * 8 + col;
    }

    private Point center(int square) {
        int r = square / 8, c = square % 8;
        if (flipped) {
            r = 7 - r;
            c = 7 - c;
        }
        int cs = cell();
        return new Point(originX() + c * cs + cs / 2, originY() + r * cs + cs / 2);
    }

    // ------------------------------------------------------------------ chuột

    private boolean canInteract() {
        return inputEnabled.getAsBoolean() && game.status() == Game.Status.ONGOING;
    }

    private void handlePress(MouseEvent e) {
        if (e.getButton() != MouseEvent.BUTTON1 || !canInteract()) return;
        int s = squareAt(e.getX(), e.getY());
        if (s < 0) {
            clearSelection();
            repaint();
            return;
        }
        if (selected >= 0 && targets.contains(s)) {
            tryMove(selected, s);
            return;
        }
        Piece p = game.board().at(s);
        if (p != null && p.side() == game.board().turn()) {
            pressedOnSelected = (s == selected);
            selected = s;
            targets = new HashSet<>();
            for (Move m : game.legalMovesFrom(s)) targets.add(m.to());
            dragging = true;
            dragX = e.getX();
            dragY = e.getY();
        } else {
            clearSelection();
        }
        repaint();
    }

    private void handleRelease(MouseEvent e) {
        if (!dragging) return;
        dragging = false;
        int s = squareAt(e.getX(), e.getY());
        if (s >= 0 && s != selected && targets.contains(s)) {
            tryMove(selected, s);
            return;
        }
        if (s == selected && pressedOnSelected) clearSelection(); // bấm lại quân đang chọn để bỏ chọn
        repaint();
    }

    private boolean tryMove(int from, int to) {
        List<Move> candidates = game.legalMovesFrom(from).stream().filter(m -> m.to() == to).toList();
        if (candidates.isEmpty()) return false;
        Move chosen = candidates.get(0);
        if (candidates.size() > 1) { // phong cấp
            PieceType t = askPromotion();
            chosen = candidates.stream().filter(m -> m.promotion() == t).findFirst().orElse(candidates.get(0));
        }
        clearSelection();
        game.play(chosen);
        repaint();
        onMove.run();
        return true;
    }

    private PieceType askPromotion() {
        String[] options = {"Hậu", "Xe", "Tượng", "Mã"};
        PieceType[] types = {PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT};
        int choice = JOptionPane.showOptionDialog(this, "Chọn quân để phong cấp:", "Phong cấp",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        return choice < 0 ? PieceType.QUEEN : types[choice];
    }

    // ------------------------------------------------------------------ vẽ

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        int cs = cell();
        if (cs <= 0) {
            g.dispose();
            return;
        }
        int ox = originX(), oy = originY();
        Board board = game.board();
        Move last = game.lastMove();
        int kingInCheck = board.inCheck(board.turn()) ? board.kingSquare(board.turn()) : -1;
        Font labelFont = getFont().deriveFont(Font.BOLD, Math.max(10f, cs * 0.16f));

        for (int dr = 0; dr < 8; dr++) {
            for (int dc = 0; dc < 8; dc++) {
                int r = flipped ? 7 - dr : dr;
                int c = flipped ? 7 - dc : dc;
                int s = r * 8 + c;
                int x = ox + dc * cs, y = oy + dr * cs;
                boolean light = (dr + dc) % 2 == 0;

                g.setColor(light ? LIGHT : DARK);
                g.fillRect(x, y, cs, cs);

                if (last != null && (s == last.from() || s == last.to())) {
                    g.setColor(new Color(255, 230, 0, 90));
                    g.fillRect(x, y, cs, cs);
                }
                if (s == selected) {
                    g.setColor(new Color(20, 200, 60, 110));
                    g.fillRect(x, y, cs, cs);
                }
                if (s == kingInCheck) {
                    g.setPaint(new RadialGradientPaint(new Point(x + cs / 2, y + cs / 2), cs * 0.6f,
                            new float[]{0f, 1f}, new Color[]{new Color(255, 0, 0, 210), new Color(255, 0, 0, 0)}));
                    g.fillRect(x, y, cs, cs);
                }

                // tọa độ
                g.setFont(labelFont);
                g.setColor(light ? DARK : LIGHT);
                FontMetrics fm = g.getFontMetrics();
                if (dc == 0) g.drawString(String.valueOf(8 - r), x + 3, y + fm.getAscent() + 1);
                if (dr == 7) {
                    String file = String.valueOf((char) ('a' + c));
                    g.drawString(file, x + cs - fm.stringWidth(file) - 3, y + cs - 4);
                }
            }
        }

        // quân cờ
        for (int s = 0; s < 64; s++) {
            Piece p = board.at(s);
            if (p == null || (dragging && s == selected)) continue;
            Point c = center(s);
            drawPiece(g, p, c.x, c.y, cs);
        }

        // gợi ý nước đi
        for (int t : targets) {
            Point c = center(t);
            if (board.at(t) != null) {
                float w = cs * 0.09f;
                g.setStroke(new BasicStroke(w));
                g.setColor(new Color(0, 0, 0, 70));
                double d = cs - w;
                g.draw(new Ellipse2D.Double(c.x - d / 2, c.y - d / 2, d, d));
            } else {
                double d = cs * 0.30;
                g.setColor(new Color(0, 0, 0, 55));
                g.fill(new Ellipse2D.Double(c.x - d / 2, c.y - d / 2, d, d));
            }
        }

        // quân đang kéo
        if (dragging && selected >= 0 && board.at(selected) != null) {
            drawPiece(g, board.at(selected), dragX, dragY, (int) (cs * 1.1));
        }
        g.dispose();
    }

    private void drawPiece(Graphics2D g, Piece p, double cx, double cy, int cs) {
        Font f = symbolFont().deriveFont(cs * 0.80f);
        // luôn dùng bộ glyph đặc (♚..) rồi tự tô màu để nhìn rõ trên mọi ô
        String glyph = String.valueOf((char) (0x265A + p.type().ordinal()));
        GlyphVector gv = f.createGlyphVector(g.getFontRenderContext(), glyph);
        Rectangle2D b = gv.getVisualBounds();
        Shape shape = gv.getOutline((float) (cx - b.getCenterX()), (float) (cy - b.getCenterY()));
        if (p.side() == Side.WHITE) {
            g.setColor(new Color(0xFAFAFA));
            g.fill(shape);
            g.setStroke(new BasicStroke(Math.max(1.2f, cs / 38f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(0x1A1A1A));
            g.draw(shape);
        } else {
            g.setColor(new Color(0x202020));
            g.fill(shape);
            g.setStroke(new BasicStroke(Math.max(1f, cs / 70f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(0xE6E6E6));
            g.draw(shape);
        }
    }
}
