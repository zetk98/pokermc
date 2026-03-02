package com.pokermc.config;

/** @deprecated Dùng CasinoCraftConfig.get() */
@Deprecated
public class TradeConfig {
    public static CasinoCraftConfig get() {
        return CasinoCraftConfig.get();
    }

    public static void reload() {
        CasinoCraftConfig.reload();
    }
}
