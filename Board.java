import java.util.ArrayList;
import java.util.List;

/**
 * Trạng thái bàn cờ và toàn bộ luật di chuyển.
 * Ô 0..63: hàng 0 là hàng 8 (phía quân Đen), hàng 7 là hàng 1 (phía quân Trắng).
 */
public final class Board {

    private static final int[][] KNIGHT_OFFSETS = {{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}};
    private static final int[][] KING_OFFSETS = {{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};
    private static final int[][] ROOK_DIRS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private static final int[][] BISHOP_DIRS = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};

    private final Piece[] sq = new Piece[64];
    private Side turn = Side.WHITE;
    private boolean whiteKingside = true, whiteQueenside = true, blackKingside = true, blackQueenside = true;
    private int enPassant = -1; // ô mà tốt có thể bắt qua đường đi tới, -1 nếu không có
    private int halfmoveClock = 0;
    private int fullmoveNumber = 1;

    private Board() {
    }

    // ------------------------------------------------------------------ khởi tạo

    public static Board initial() {
        Board b = new Board();
        PieceType[] back = {PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
                PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK};
        for (int c = 0; c < 8; c++) {
            b.sq[c] = new Piece(back[c], Side.BLACK);
            b.sq[8 + c] = new Piece(PieceType.PAWN, Side.BLACK);
            b.sq[48 + c] = new Piece(PieceType.PAWN, Side.WHITE);
            b.sq[56 + c] = new Piece(back[c], Side.WHITE);
        }
        return b;
    }

    /** Tạo bàn cờ từ chuỗi FEN (dùng cho kiểm thử). */
    public static Board fromFen(String fen) {
        String[] parts = fen.trim().split("\\s+");
        Board b = new Board();
        b.whiteKingside = b.whiteQueenside = b.blackKingside = b.blackQueenside = false;
        int r = 0, c = 0;
        for (char ch : parts[0].toCharArray()) {
            if (ch == '/') {
                r++;
                c = 0;
            } else if (Character.isDigit(ch)) {
                c += ch - '0';
            } else {
                Side side = Character.isUpperCase(ch) ? Side.WHITE : Side.BLACK;
                PieceType type = switch (Character.toLowerCase(ch)) {
                    case 'k' -> PieceType.KING;
                    case 'q' -> PieceType.QUEEN;
                    case 'r' -> PieceType.ROOK;
                    case 'b' -> PieceType.BISHOP;
                    case 'n' -> PieceType.KNIGHT;
                    case 'p' -> PieceType.PAWN;
                    default -> throw new IllegalArgumentException("FEN không hợp lệ: " + fen);
                };
                b.sq[r * 8 + c++] = new Piece(type, side);
            }
        }
        b.turn = parts.length > 1 && parts[1].equals("b") ? Side.BLACK : Side.WHITE;
        if (parts.length > 2) {
            b.whiteKingside = parts[2].contains("K");
            b.whiteQueenside = parts[2].contains("Q");
            b.blackKingside = parts[2].contains("k");
            b.blackQueenside = parts[2].contains("q");
        }
        if (parts.length > 3 && !parts[3].equals("-")) {
            b.enPassant = (8 - (parts[3].charAt(1) - '0')) * 8 + (parts[3].charAt(0) - 'a');
        }
        if (parts.length > 4) b.halfmoveClock = Integer.parseInt(parts[4]);
        if (parts.length > 5) b.fullmoveNumber = Integer.parseInt(parts[5]);
        return b;
    }

    public Board copy() {
        Board b = new Board();
        System.arraycopy(sq, 0, b.sq, 0, 64);
        b.turn = turn;
        b.whiteKingside = whiteKingside;
        b.whiteQueenside = whiteQueenside;
        b.blackKingside = blackKingside;
        b.blackQueenside = blackQueenside;
        b.enPassant = enPassant;
        b.halfmoveClock = halfmoveClock;
        b.fullmoveNumber = fullmoveNumber;
        return b;
    }

    // ------------------------------------------------------------------ truy vấn

    public Piece at(int square) {
        return sq[square];
    }

    public Side turn() {
        return turn;
    }

    public int halfmoveClock() {
        return halfmoveClock;
    }

    public int fullmoveNumber() {
        return fullmoveNumber;
    }

    public int kingSquare(Side side) {
        for (int i = 0; i < 64; i++) {
            Piece p = sq[i];
            if (p != null && p.type() == PieceType.KING && p.side() == side) return i;
        }
        return -1;
    }

    public boolean inCheck(Side side) {
        int k = kingSquare(side);
        return k >= 0 && isAttacked(k, side.opposite());
    }

    /** Quân bị ăn nếu đi nước này (kể cả bắt tốt qua đường), hoặc null. */
    public Piece captured(Move m) {
        if (sq[m.to()] != null) return sq[m.to()];
        Piece p = sq[m.from()];
        if (p != null && p.type() == PieceType.PAWN && m.to() == enPassant && m.from() % 8 != m.to() % 8) {
            return sq[(m.from() / 8) * 8 + m.to() % 8];
        }
        return null;
    }

    /** Khóa nhận diện thế cờ, dùng để phát hiện lặp lại nước đi. */
    public String key() {
        StringBuilder sb = new StringBuilder(80);
        for (int i = 0; i < 64; i++) {
            Piece p = sq[i];
            sb.append(p == null ? '.' : (char) ((p.side() == Side.WHITE ? 'a' : 'n') + p.type().ordinal()));
        }
        sb.append(turn == Side.WHITE ? 'w' : 'b');
        sb.append(whiteKingside ? 'K' : '-').append(whiteQueenside ? 'Q' : '-');
        sb.append(blackKingside ? 'k' : '-').append(blackQueenside ? 'q' : '-');
        sb.append(enPassant);
        return sb.toString();
    }

    /** Hòa do không đủ quân để chiếu hết. */
    public boolean insufficientMaterial() {
        int minors = 0, lightBishops = 0, darkBishops = 0;
        boolean bishopsOnly = true;
        for (int i = 0; i < 64; i++) {
            Piece p = sq[i];
            if (p == null || p.type() == PieceType.KING) continue;
            switch (p.type()) {
                case PAWN, ROOK, QUEEN -> {
                    return false;
                }
                case KNIGHT -> {
                    minors++;
                    bishopsOnly = false;
                }
                case BISHOP -> {
                    minors++;
                    if ((i / 8 + i % 8) % 2 == 0) lightBishops++;
                    else darkBishops++;
                }
                default -> {
                }
            }
        }
        if (minors <= 1) return true;
        return bishopsOnly && (lightBishops == 0 || darkBishops == 0);
    }

    // ------------------------------------------------------------------ sinh nước đi

    /** Tất cả nước đi hợp lệ của bên đang đến lượt. */
    public List<Move> legalMoves() {
        List<Move> pseudo = new ArrayList<>(64);
        for (int i = 0; i < 64; i++) {
            Piece p = sq[i];
            if (p != null && p.side() == turn) generate(i, pseudo);
        }
        List<Move> legal = new ArrayList<>(pseudo.size());
        for (Move m : pseudo) {
            Board n = copy();
            n.apply(m);
            if (!n.inCheck(turn)) legal.add(m);
        }
        return legal;
    }

    private static boolean in(int r, int c) {
        return r >= 0 && r < 8 && c >= 0 && c < 8;
    }

    private void generate(int from, List<Move> out) {
        Piece p = sq[from];
        int r = from / 8, c = from % 8;
        Side me = p.side();
        switch (p.type()) {
            case PAWN -> {
                int dir = me == Side.WHITE ? -1 : 1;
                int startRow = me == Side.WHITE ? 6 : 1;
                int promoRow = me == Side.WHITE ? 0 : 7;
                int r1 = r + dir;
                if (in(r1, c) && sq[r1 * 8 + c] == null) {
                    addPawnMove(from, r1 * 8 + c, r1 == promoRow, out);
                    if (r == startRow && sq[(r + 2 * dir) * 8 + c] == null) {
                        out.add(new Move(from, (r + 2 * dir) * 8 + c, null));
                    }
                }
                for (int dc = -1; dc <= 1; dc += 2) {
                    int nc = c + dc;
                    if (!in(r1, nc)) continue;
                    int t = r1 * 8 + nc;
                    Piece q = sq[t];
                    if (q != null && q.side() != me) addPawnMove(from, t, r1 == promoRow, out);
                    else if (q == null && t == enPassant) out.add(new Move(from, t, null));
                }
            }
            case KNIGHT -> stepMoves(from, r, c, me, KNIGHT_OFFSETS, out);
            case KING -> {
                stepMoves(from, r, c, me, KING_OFFSETS, out);
                generateCastling(from, r, c, me, out);
            }
            case BISHOP -> slideMoves(from, r, c, me, BISHOP_DIRS, out);
            case ROOK -> slideMoves(from, r, c, me, ROOK_DIRS, out);
            case QUEEN -> {
                slideMoves(from, r, c, me, BISHOP_DIRS, out);
                slideMoves(from, r, c, me, ROOK_DIRS, out);
            }
        }
    }

    private void addPawnMove(int from, int to, boolean promotes, List<Move> out) {
        if (promotes) {
            out.add(new Move(from, to, PieceType.QUEEN));
            out.add(new Move(from, to, PieceType.ROOK));
            out.add(new Move(from, to, PieceType.BISHOP));
            out.add(new Move(from, to, PieceType.KNIGHT));
        } else {
            out.add(new Move(from, to, null));
        }
    }

    private void stepMoves(int from, int r, int c, Side me, int[][] offsets, List<Move> out) {
        for (int[] o : offsets) {
            int nr = r + o[0], nc = c + o[1];
            if (!in(nr, nc)) continue;
            Piece q = sq[nr * 8 + nc];
            if (q == null || q.side() != me) out.add(new Move(from, nr * 8 + nc, null));
        }
    }

    private void slideMoves(int from, int r, int c, Side me, int[][] dirs, List<Move> out) {
        for (int[] d : dirs) {
            int nr = r + d[0], nc = c + d[1];
            while (in(nr, nc)) {
                Piece q = sq[nr * 8 + nc];
                if (q == null) {
                    out.add(new Move(from, nr * 8 + nc, null));
                } else {
                    if (q.side() != me) out.add(new Move(from, nr * 8 + nc, null));
                    break;
                }
                nr += d[0];
                nc += d[1];
            }
        }
    }

    private void generateCastling(int from, int r, int c, Side me, List<Move> out) {
        int home = me == Side.WHITE ? 7 : 0;
        if (r != home || c != 4) return;
        Side opp = me.opposite();
        boolean kingside = me == Side.WHITE ? whiteKingside : blackKingside;
        boolean queenside = me == Side.WHITE ? whiteQueenside : blackQueenside;
        if (!kingside && !queenside) return;
        if (isAttacked(from, opp)) return; // không được nhập thành khi đang bị chiếu
        Piece rook = new Piece(PieceType.ROOK, me);
        int base = home * 8;
        if (kingside && rook.equals(sq[base + 7]) && sq[base + 5] == null && sq[base + 6] == null
                && !isAttacked(base + 5, opp) && !isAttacked(base + 6, opp)) {
            out.add(new Move(from, base + 6, null));
        }
        if (queenside && rook.equals(sq[base]) && sq[base + 1] == null && sq[base + 2] == null && sq[base + 3] == null
                && !isAttacked(base + 3, opp) && !isAttacked(base + 2, opp)) {
            out.add(new Move(from, base + 2, null));
        }
    }

    /** Ô {@code s} có đang bị bên {@code by} tấn công không. */
    public boolean isAttacked(int s, Side by) {
        int r = s / 8, c = s % 8;
        int pr = by == Side.WHITE ? r + 1 : r - 1; // tốt của bên 'by' đứng ở hàng này
        for (int dc = -1; dc <= 1; dc += 2) {
            int pc = c + dc;
            if (in(pr, pc)) {
                Piece p = sq[pr * 8 + pc];
                if (p != null && p.side() == by && p.type() == PieceType.PAWN) return true;
            }
        }
        for (int[] o : KNIGHT_OFFSETS) {
            int nr = r + o[0], nc = c + o[1];
            if (in(nr, nc)) {
                Piece p = sq[nr * 8 + nc];
                if (p != null && p.side() == by && p.type() == PieceType.KNIGHT) return true;
            }
        }
        for (int[] o : KING_OFFSETS) {
            int nr = r + o[0], nc = c + o[1];
            if (in(nr, nc)) {
                Piece p = sq[nr * 8 + nc];
                if (p != null && p.side() == by && p.type() == PieceType.KING) return true;
            }
        }
        for (int[] d : ROOK_DIRS) {
            if (rayHits(r, c, d, by, PieceType.ROOK)) return true;
        }
        for (int[] d : BISHOP_DIRS) {
            if (rayHits(r, c, d, by, PieceType.BISHOP)) return true;
        }
        return false;
    }

    private boolean rayHits(int r, int c, int[] d, Side by, PieceType slider) {
        int nr = r + d[0], nc = c + d[1];
        while (in(nr, nc)) {
            Piece p = sq[nr * 8 + nc];
            if (p != null) {
                return p.side() == by && (p.type() == slider || p.type() == PieceType.QUEEN);
            }
            nr += d[0];
            nc += d[1];
        }
        return false;
    }

    // ------------------------------------------------------------------ thực hiện nước đi

    /** Thực hiện nước đi (giả định đã hợp lệ). */
    public void apply(Move m) {
        int from = m.from(), to = m.to();
        Piece p = sq[from];
        int fr = from / 8, fc = from % 8, tr = to / 8, tc = to % 8;
        boolean capture = sq[to] != null;

        // bắt tốt qua đường
        if (p.type() == PieceType.PAWN && to == enPassant && fc != tc && sq[to] == null) {
            sq[fr * 8 + tc] = null;
            capture = true;
        }

        sq[to] = p;
        sq[from] = null;

        // phong cấp
        if (p.type() == PieceType.PAWN && (tr == 0 || tr == 7)) {
            PieceType t = m.promotion() == null ? PieceType.QUEEN : m.promotion();
            sq[to] = new Piece(t, p.side());
        }

        // nhập thành: di chuyển xe
        if (p.type() == PieceType.KING && Math.abs(tc - fc) == 2) {
            if (tc > fc) {
                sq[fr * 8 + 5] = sq[fr * 8 + 7];
                sq[fr * 8 + 7] = null;
            } else {
                sq[fr * 8 + 3] = sq[fr * 8];
                sq[fr * 8] = null;
            }
        }

        // quyền nhập thành
        if (p.type() == PieceType.KING) {
            if (p.side() == Side.WHITE) whiteKingside = whiteQueenside = false;
            else blackKingside = blackQueenside = false;
        }
        revokeCastling(from);
        revokeCastling(to);

        enPassant = (p.type() == PieceType.PAWN && Math.abs(tr - fr) == 2) ? ((fr + tr) / 2) * 8 + fc : -1;
        halfmoveClock = (p.type() == PieceType.PAWN || capture) ? 0 : halfmoveClock + 1;
        if (turn == Side.BLACK) fullmoveNumber++;
        turn = turn.opposite();
    }

    private void revokeCastling(int square) {
        switch (square) {
            case 63 -> whiteKingside = false;
            case 56 -> whiteQueenside = false;
            case 7 -> blackKingside = false;
            case 0 -> blackQueenside = false;
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ ký hiệu đại số (SAN)

    /** Ký hiệu đại số của nước đi (gọi TRƯỚC khi apply), ví dụ "Nf3", "exd5", "O-O", "e8=Q#". */
    public String toSan(Move m) {
        Piece p = sq[m.from()];
        int fc = m.from() % 8, fr = m.from() / 8, tc = m.to() % 8;
        boolean cap = captured(m) != null;
        StringBuilder s = new StringBuilder();

        if (p.type() == PieceType.KING && Math.abs(tc - fc) == 2) {
            s.append(tc > fc ? "O-O" : "O-O-O");
        } else if (p.type() == PieceType.PAWN) {
            if (cap) s.append((char) ('a' + fc)).append('x');
            s.append(Move.name(m.to()));
            if (m.promotion() != null) s.append('=').append(m.promotion().letter);
        } else {
            s.append(p.type().letter);
            boolean ambiguous = false, sameFile = false, sameRank = false;
            for (Move o : legalMoves()) {
                if (o.to() == m.to() && o.from() != m.from() && sq[o.from()].type() == p.type()) {
                    ambiguous = true;
                    if (o.from() % 8 == fc) sameFile = true;
                    if (o.from() / 8 == fr) sameRank = true;
                }
            }
            if (ambiguous) {
                if (!sameFile) s.append((char) ('a' + fc));
                else if (!sameRank) s.append((char) ('0' + 8 - fr));
                else s.append(Move.name(m.from()));
            }
            if (cap) s.append('x');
            s.append(Move.name(m.to()));
        }

        Board n = copy();
        n.apply(m);
        if (n.inCheck(n.turn)) s.append(n.legalMoves().isEmpty() ? '#' : '+');
        return s.toString();
    }
}
