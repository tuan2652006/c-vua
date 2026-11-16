/**
 * Một nước đi. Ô được đánh số 0..63 theo hàng: 0 = a8, 7 = h8, 56 = a1, 63 = h1.
 * {@code promotion} chỉ khác null khi tốt phong cấp.
 */
public record Move(int from, int to, PieceType promotion) {

    /** Tên ô theo ký hiệu đại số, ví dụ 52 -> "e2". */
    public static String name(int sq) {
        return "" + (char) ('a' + sq % 8) + (8 - sq / 8);
    }

    @Override
    public String toString() {
        return name(from) + name(to) + (promotion == null ? "" : String.valueOf(Character.toLowerCase(promotion.letter)));
    }
}
