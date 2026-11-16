/**
 * Loại quân cờ. Thứ tự khai báo trùng với thứ tự ký tự Unicode
 * (♔ ♕ ♖ ♗ ♘ ♙) nên có thể dùng ordinal() để lấy glyph.
 */
public enum PieceType {
    KING('K', 0),
    QUEEN('Q', 900),
    ROOK('R', 500),
    BISHOP('B', 330),
    KNIGHT('N', 320),
    PAWN('P', 100);

    public final char letter;
    public final int value;

    PieceType(char letter, int value) {
        this.letter = letter;
        this.value = value;
    }
}
