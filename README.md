# Cờ vua Java Swing (JDK 21)

Game cờ vua desktop viết bằng Java 21 + Swing, không cần thư viện ngoài.

## Chạy

```bash
mkdir out
javac -d out *.java
java -cp out ChessApp
```

Hoặc import cả thư mục vào IntelliJ IDEA / Eclipse / NetBeans (JDK 21) và chạy lớp `ChessApp`.

## Tính năng

- Đầy đủ luật: nhập thành, bắt tốt qua đường, phong cấp (chọn Hậu/Xe/Tượng/Mã), chiếu, chiếu hết
- Hòa: bí nước, luật 50 nước, không đủ quân chiếu hết, lặp lại thế cờ 3 lần
- Thao tác chuột: bấm-bấm hoặc kéo-thả; gợi ý các nước đi hợp lệ; tô sáng nước vừa đi và vua bị chiếu
- Chơi với máy (3 mức: Dễ / Trung bình / Khó, máy chạy ở luồng nền nên giao diện không bị đơ) hoặc hai người chơi
- Đi lại (Ctrl+Z), ván mới (Ctrl+N), lật bàn cờ (Ctrl+F)
- Danh sách nước đi theo ký hiệu đại số (SAN) và các quân đã bị ăn

## Cấu trúc mã nguồn

| File | Vai trò |
|---|---|
| `Side`, `PieceType`, `Piece`, `Move` | Kiểu dữ liệu cơ bản |
| `Board` | Trạng thái bàn cờ, sinh nước đi hợp lệ, SAN |
| `Game` | Lịch sử ván, undo, phát hiện kết thúc ván |
| `Ai` | Negamax + alpha-beta + tìm kiếm yên lặng |
| `BoardPanel` | Vẽ bàn cờ và xử lý chuột |
| `ChessApp` | Cửa sổ chính, menu, điều khiển chế độ chơi |

Engine đã được kiểm tra bằng perft trên 5 vị trí chuẩn (vị trí đầu, Kiwipete, và 3 vị trí khó khác).
