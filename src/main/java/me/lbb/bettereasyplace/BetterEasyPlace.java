package me.lbb.bettereasyplace;

import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import me.lbb.bettereasyplace.config.Configs;
import net.fabricmc.api.ModInitializer;

/**
 * 模组主入口 / Main mod entry point.
 * <p>
 * BetterEasyPlace 是一个改进 Litematica 轻松放置功能的辅助模组。
 * 它在轻松放置激活时，允许玩家正常进食和使用烟花火箭（鞘翅加速），
 * 解决了轻松放置模式下这些原版操作被拦截的问题。
 * <p>
 * BetterEasyPlace is a companion mod that improves Litematica's Easy Place
 * functionality.  It allows the player to eat food and use firework rockets
 * (for elytra boosting) even when Easy Place is active — solving the problem
 * of those vanilla actions being blocked during Easy Place mode.
 *
 * @author LBB285
 */
public class BetterEasyPlace implements ModInitializer, IInitializationHandler {

    /**
     * Fabric 模组初始化 / Fabric mod initialization.
     * <p>
     * 注册 malilib 配置处理器，使配置项能通过 malilib 的配置系统持久化。
     * Registers the malilib config handler so that options can be persisted
     * through malilib's configuration system.
     */
    @Override
    public void onInitialize() {
        ConfigManager.getInstance().registerConfigHandler(Reference.MOD_ID, new Configs());
    }

    /**
     * 注册模组处理器（malilib 回调） / Register mod handlers (malilib callback).
     * <p>
     * 在 malilib 初始化完成后触发，用于从文件加载配置。
     * Called after malilib has finished initialising; loads the config from disk.
     */
    @Override
    public void registerModHandlers() {
        Configs.loadFromFile();
    }
}
