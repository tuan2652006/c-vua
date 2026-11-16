import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Một ván cờ: lưu lịch sử để đi lại (undo), phát hiện kết thúc ván. */
public final class Game {

    public enum Status { ONGOING, CHECKMATE, STALEMATE, FIFTY_MOVE, INSUFFICIENT, REPETITION }

    private Board board;
    private final List<Board> history = new ArrayList<>();
    private final List<Move> moves = new ArrayList<>();
    private final List<String> san = new ArrayList<>();
    private final List<Piece> captured = new ArrayList<>(); // phần tử null nếu nước đó không ăn quân
    private final List<String> keys = new ArrayList<>();
    private List<Move> legal;
    private Status status;

    public Game() {
        reset();
    }

    public void reset() {
        board = Board.initial();
        history.clear();
        moves.clear();
        san.clear();
        captured.clear();
        keys.clear();
        keys.add(board.key());
        recompute();
    }

    private void recompute() {
        legal = board.legalMoves();
        if (legal.isEmpty()) {
            status = board.inCheck(board.turn()) ? Status.CHECKMATE : Status.STALEMATE;
        } else if (board.halfmoveClock() >= 100) {
            status = Status.FIFTY_MOVE;
        } else if (board.insufficientMaterial()) {
            status = Status.INSUFFICIENT;
        } else {
            String k = keys.get(keys.size() - 1);
            long count = keys.stream().filter(k::equals).count();
            status = count >= 3 ? Status.REPETITION : Status.ONGOING;
        }
    }

    public Board board() {
        return board;
    }

    public Side turn() {
        return board.turn();
    }

    public Status status() {
        return status;
    }

    public List<Move> legalMoves() {
        return legal;
    }

    public List<Move> legalMovesFrom(int square) {
        List<Move> out = new ArrayList<>();
        for (Move m : legal) if (m.from() == square) out.add(m);
        return out;
    }

    public void play(Move m) {
        if (status != Status.ONGOING) return;
        if (!legal.contains(m)) throw new IllegalArgumentException("Nước đi không hợp lệ: " + m);
        san.add(board.toSan(m));
        captured.add(board.captured(m));
        history.add(board);
        moves.add(m);
        board = board.copy();
        board.apply(m);
        keys.add(board.key());
        recompute();
    }

    public boolean canUndo() {
        return !history.isEmpty();
    }

    public void undo() {
        if (history.isEmpty()) return;
        int last = history.size() - 1;
        board = history.remove(last);
        moves.remove(last);
        san.remove(last);
        captured.remove(last);
        keys.remove(keys.size() - 1);
        recompute();
    }

    public Move lastMove() {
        return moves.isEmpty() ? null : moves.get(moves.size() - 1);
    }

    public List<String> sanList() {
        return Collections.unmodifiableList(san);
    }

    /** Các quân đã bị ăn (bỏ qua nước không ăn quân). */
    public List<Piece> capturedPieces() {
        List<Piece> out = new ArrayList<>();
        for (Piece p : captured) if (p != null) out.add(p);
        return out;
    }
}
