package me.lbb.bettereasyplace.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.lbb.bettereasyplace.gui.GuiConfigs;

/**
 * ModMenu 集成 / ModMenu integration.
 * <p>
 * 实现 {@link ModMenuApi} 接口，使得玩家可以在 ModMenu 模组列表中
 * 点击本模组的配置按钮来打开配置界面。
 * <p>
 * Implements the {@link ModMenuApi} interface so that players can open
 * the mod's configuration GUI by clicking the config button next to
 * this mod in the ModMenu mod list.
 */
public class ModMenuImpl implements ModMenuApi {
    /**
     * 提供配置界面工厂 / Provide the config screen factory.
     *
     * @return 一个工厂方法，接收父屏幕并返回新的配置界面 /
     *         a factory that receives the parent screen and returns a new config GUI
     */
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return (screen) -> {
            GuiConfigs gui = new GuiConfigs();
            gui.setParent(screen);
            return gui;
        };
    }
}
