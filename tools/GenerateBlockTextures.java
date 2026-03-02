import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;

/**
 * 32x32 poker table - clean poker style.
 * Run: javac tools/GenerateBlockTextures.java && java -cp tools GenerateBlockTextures
 */
public class GenerateBlockTextures {
    static final int S = 32;

    static final int GOLD   = 0xFFD4AF37;
    static final int FELT   = 0xFF0F6B18;
    static final int FELT_D = 0xFF0A5012;
    static final int FRAME  = 0xFF2A2520;
    static final int CHIP_R = 0xFFC03030;
    static final int CHIP_B = 0xFF3060B0;
    static final int CHIP_G = 0xFF208020;
    static final int CHIP_K = 0xFF252525;

    // 3x5 font POKER
    static final String[] P = {"111","101","111","100","100"};
    static final String[] O = {"111","101","101","101","111"};
    static final String[] K = {"101","110","100","110","101"};
    static final String[] E = {"111","100","111","100","111"};
    static final String[] R = {"111","101","111","110","101"};

    static void px(int[] p, int x, int y, int c) {
        if (x >= 0 && x < S && y >= 0 && y < S) p[y * S + x] = c;
    }

    static void fill(int[] p, int x1, int y1, int x2, int y2, int c) {
        for (int y = y1; y < y2; y++)
            for (int x = x1; x < x2; x++)
                px(p, x, y, c);
    }

    static void drawChar(int[] p, String[] g, int x, int y, int c) {
        for (int row = 0; row < 5; row++)
            for (int col = 0; col < g[row].length(); col++)
                if (g[row].charAt(col) == '1')
                    px(p, x + col, y + row, c);
    }

    public static void main(String[] args) throws Exception {
        String base = "src/main/resources/assets/pokermc/textures/block";
        Files.createDirectories(new File(base).toPath());

        // ========== TOP: Green felt + gold border + POKER + chips ==========
        int[] top = new int[S * S];
        fill(top, 0, 0, S, S, FELT);
        for (int i = 0; i < S; i++) {
            px(top, i, 0, GOLD);
            px(top, i, 1, GOLD);
            px(top, i, S-2, GOLD);
            px(top, i, S-1, GOLD);
            px(top, 0, i, GOLD);
            px(top, 1, i, GOLD);
            px(top, S-2, i, GOLD);
            px(top, S-1, i, GOLD);
        }
        fill(top, 2, 2, S-2, S-2, FELT);
        for (int y = 4; y < S-4; y += 2)
            for (int x = 4; x < S-4; x += 2)
                if ((x+y)%4 == 0) px(top, x, y, FELT_D);

        String[][] glyphs = {P,O,K,E,R};
        int gx = 4;
        for (int i = 0; i < 5; i++) {
            drawChar(top, glyphs[i], gx, 10, GOLD);
            gx += 4;
        }

        px(top, 6, 22, CHIP_R);
        px(top, 7, 22, CHIP_R);
        px(top, 24, 22, CHIP_B);
        px(top, 25, 22, CHIP_B);
        px(top, 15, 6, CHIP_G);
        px(top, 16, 6, CHIP_G);
        px(top, 15, 24, CHIP_K);
        px(top, 16, 24, CHIP_K);

        ImageIO.write(toImage(top, S), "PNG", new File(base + "/poker_table_top.png"));

        // ========== SIDE: Dark frame ==========
        int[] side = new int[S * S];
        fill(side, 0, 0, S, S, FRAME);
        for (int i = 0; i < 2; i++) {
            fill(side, i, 0, i+1, S, GOLD);
            fill(side, S-1-i, 0, S-i, S, GOLD);
            fill(side, 0, i, S, i+1, GOLD);
            fill(side, 0, S-1-i, S, S-i, GOLD);
        }

        ImageIO.write(toImage(side, S), "PNG", new File(base + "/poker_table_side.png"));

        System.out.println("Generated 32x32: poker_table_top.png");
        System.out.println("Generated 32x32: poker_table_side.png");
    }

    static BufferedImage toImage(int[] p, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, size, size, p, 0, size);
        return img;
    }
}
