/** Bên chơi: Trắng hoặc Đen. */
public enum Side {
    WHITE("Trắng"),
    BLACK("Đen");

    public final String vi;

    Side(String vi) {
        this.vi = vi;
    }

    public Side opposite() {
        return this == WHITE ? BLACK : WHITE;
    }
}
