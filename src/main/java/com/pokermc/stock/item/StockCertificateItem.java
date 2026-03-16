package com.pokermc.stock.item;

import com.pokermc.PokerMod;
import com.pokermc.common.component.PokerComponents;
import com.pokermc.stock.game.StockType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * Stock Certificate Item - represents ownership of stock shares.
 * Components store ticker symbol and share count.
 */
public class StockCertificateItem extends Item {

    public StockCertificateItem(Settings settings) {
        super(settings);
    }

    /**
     * Get the ticker symbol from the certificate.
     */
    public static String getTicker(ItemStack stack) {
        String ticker = stack.get(PokerComponents.STOCK_TICKER);
        return ticker != null ? ticker : "";
    }

    /**
     * Set the ticker symbol on the certificate.
     */
    public static void setTicker(ItemStack stack, String ticker) {
        stack.set(PokerComponents.STOCK_TICKER, ticker);
    }

    /**
     * Get the number of shares the certificate represents.
     */
    public static int getShareCount(ItemStack stack) {
        Integer shares = stack.get(PokerComponents.STOCK_SHARES);
        return shares != null ? shares : 0;
    }

    /**
     * Set the number of shares on the certificate.
     */
    public static void setShareCount(ItemStack stack, int shares) {
        stack.set(PokerComponents.STOCK_SHARES, Math.max(1, shares));
    }

    /**
     * Create a stock certificate for the given stock type and quantity.
     */
    public static ItemStack createCertificate(StockType stockType, int quantity) {
        ItemStack stack = new ItemStack(PokerMod.STOCK_CERTIFICATE_ITEM);
        setTicker(stack, stockType.getTicker());
        setShareCount(stack, quantity);
        return stack;
    }

    @Override
    public Text getName(ItemStack stack) {
        String ticker = getTicker(stack);
        int shares = getShareCount(stack);
        StockType type = StockType.byTicker(ticker);

        if (type != null && shares > 0) {
            return Text.literal(shares + "x " + ticker + " Stock Certificate")
                    .formatted(type.getColor());
        }

        return Text.literal("Blank Stock Certificate");
    }

    /**
     * Check if this is a valid certificate.
     */
    public static boolean isValid(ItemStack stack) {
        if (!(stack.getItem() instanceof StockCertificateItem)) return false;
        String ticker = getTicker(stack);
        int shares = getShareCount(stack);
        return !ticker.isEmpty() && shares > 0 && StockType.byTicker(ticker) != null;
    }
}
