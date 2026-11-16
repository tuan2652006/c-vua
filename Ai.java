import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** AI đơn giản: negamax + cắt tỉa alpha-beta + tìm kiếm yên lặng (chỉ các nước ăn quân). */
public final class Ai {

    private static final int INF = 1_000_000;
    private static final int MATE = 100_000;
    private static final int QUIESCE_DEPTH = 3;

    private final int depth;
    private final int noise;
    private final Random rnd = new Random();

    /**
     * @param depth độ sâu tìm kiếm (số nước nửa)
     * @param noise độ nhiễu cộng vào điểm đánh giá để máy "đi sai" ở mức dễ
     */
    public Ai(int depth, int noise) {
        this.depth = depth;
        this.noise = noise;
    }

    public Move bestMove(Board b) {
        List<Move> moves = ordered(b, b.legalMoves());
        if (moves.isEmpty()) return null;
        Move best = moves.get(0);
        int alpha = -INF;
        for (Move m : moves) {
            Board n = b.copy();
            n.apply(m);
            int score = -search(n, depth - 1, -INF, -alpha, 1);
            if (score > alpha) {
                alpha = score;
                best = m;
            }
        }
        return best;
    }

    private int search(Board b, int depth, int alpha, int beta, int ply) {
        if (depth <= 0) return quiesce(b, alpha, beta, QUIESCE_DEPTH);
        List<Move> moves = b.legalMoves();
        if (moves.isEmpty()) return b.inCheck(b.turn()) ? -MATE + ply : 0;
        for (Move m : ordered(b, moves)) {
            Board n = b.copy();
            n.apply(m);
            int score = -search(n, depth - 1, -beta, -alpha, ply + 1);
            if (score >= beta) return beta;
            if (score > alpha) alpha = score;
        }
        return alpha;
    }

    private int quiesce(Board b, int alpha, int beta, int qd) {
        int stand = eval(b);
        if (stand >= beta) return beta;
        if (stand > alpha) alpha = stand;
        if (qd == 0) return alpha;
        for (Move m : ordered(b, b.legalMoves())) {
            if (b.captured(m) == null) continue;
            Board n = b.copy();
            n.apply(m);
            int score = -quiesce(n, -beta, -alpha, qd - 1);
            if (score >= beta) return beta;
            if (score > alpha) alpha = score;
        }
        return alpha;
    }

    /** Sắp xếp: nước ăn quân giá trị cao bằng quân yếu và phong cấp được thử trước. */
    private List<Move> ordered(Board b, List<Move> moves) {
        List<Move> sorted = new ArrayList<>(moves);
        sorted.sort((x, y) -> Integer.compare(orderScore(b, y), orderScore(b, x)));
        return sorted;
    }

    private int orderScore(Board b, Move m) {
        int s = 0;
        Piece cap = b.captured(m);
        if (cap != null) s += 10 * cap.type().value - b.at(m.from()).type().value / 10 + 1000;
        if (m.promotion() != null) s += m.promotion().value;
        return s;
    }

    /** Điểm đánh giá theo góc nhìn của bên đang đến lượt. */
    private int eval(Board b) {
        int score = 0;
        for (int i = 0; i < 64; i++) {
            Piece p = b.at(i);
            if (p == null) continue;
            int r = i / 8, c = i % 8;
            int v = p.type().value;
            int dist = Math.max(Math.abs(2 * r - 7), Math.abs(2 * c - 7)); // 1,3,5,7
            int central = 7 - dist; // 6,4,2,0
            switch (p.type()) {
                case KNIGHT, BISHOP -> v += central * 5;
                case QUEEN -> v += central * 2;
                case PAWN -> {
                    int advance = p.side() == Side.WHITE ? 6 - r : r - 1;
                    v += advance * 6;
                    if (c >= 2 && c <= 5) v += central * 2;
                }
                case KING -> v -= central * 4;
                default -> {
                }
            }
            score += p.side() == Side.WHITE ? v : -v;
        }
        int result = b.turn() == Side.WHITE ? score : -score;
        if (noise > 0) result += rnd.nextInt(2 * noise + 1) - noise;
        return result;
    }
}
