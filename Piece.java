/** Một quân cờ (bất biến). */
public record Piece(PieceType type, Side side) {

    /** Ký tự Unicode: bộ rỗng (♔..) cho quân Trắng, bộ đặc (♚..) cho quân Đen. */
    public char glyph() {
        return (char) ((side == Side.WHITE ? 0x2654 : 0x265A) + type.ordinal());
    }
}
