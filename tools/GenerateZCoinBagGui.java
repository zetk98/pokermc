import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;

/**
 * Tạo texture GUI ZCoin Bag 176x166 - style dispenser đơn giản.
 * Run: javac tools/GenerateZCoinBagGui.java && java -cp tools GenerateZCoinBagGui
 */
public class GenerateZCoinBagGui {
    static final int W = 176, H = 166;

    // Minecraft GUI colors
    static final int BG = 0xFFC6C6C6;       // light gray background
    static final int BORDER = 0xFF8B8B8B;    // darker gray border
    static final int SLOT = 0xFF787878;      // slot dark gray
    static final int SLOT_BORDER = 0xFF373737;

    static void px(int[] p, int x, int y, int c) {
        if (x >= 0 && x < W && y >= 0 && y < H) p[y * W + x] = c;
    }

    static void fill(int[] p, int x1, int y1, int x2, int y2, int c) {
        for (int y = y1; y < y2; y++)
            for (int x = x1; x < x2; x++)
                px(p, x, y, c);
    }

    static void rect(int[] p, int x, int y, int w, int h, int border, int fill) {
        fill(p, x, y, x + w, y + h, fill);
        for (int i = 0; i < 2; i++) {
            fill(p, x + i, y, x + w - i, y + 2, border);
            fill(p, x + i, y + h - 2, x + w - i, y + h, border);
            fill(p, x, y + i, x + 2, y + h - i, border);
            fill(p, x + w - 2, y + i, x + w, y + h - i, border);
        }
    }

    public static void main(String[] args) throws Exception {
        String out = "src/main/resources/assets/pokermc/textures/gui/zcoin_bag_gui.png";
        Files.createDirectories(new File(out).getParentFile().toPath());

        int[] pixels = new int[W * H];
        fill(pixels, 0, 0, W, H, BG);

        // Outer border
        rect(pixels, 0, 0, W, H, BORDER, BG);

        // 3x3 container slots (top) - dispenser layout: start at 62, 18
        int slotStartX = 62, slotStartY = 18;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int sx = slotStartX + col * 18;
                int sy = slotStartY + row * 18;
                rect(pixels, sx, sy, 16, 16, SLOT_BORDER, SLOT);
            }
        }

        // Player inventory slots (8, 84) - 9x3 + hotbar
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = 8 + col * 18;
                int sy = 84 + row * 18;
                rect(pixels, sx, sy, 16, 16, SLOT_BORDER, SLOT);
            }
        }

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, W, H, pixels, 0, W);
        ImageIO.write(img, "PNG", new File(out));
        System.out.println("Created: " + out);
    }
}
