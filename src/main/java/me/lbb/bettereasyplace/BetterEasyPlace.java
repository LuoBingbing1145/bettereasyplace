package me.lbb.bettereasyplace;

import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import me.lbb.bettereasyplace.config.Configs;
import net.fabricmc.api.ModInitializer;

public class BetterEasyPlace implements ModInitializer, IInitializationHandler {
    @Override
    public void onInitialize() {
        ConfigManager.getInstance().registerConfigHandler(Reference.MOD_ID, new Configs());
    }

    @Override
    public void registerModHandlers() {
        Configs.loadFromFile();
    }
}
