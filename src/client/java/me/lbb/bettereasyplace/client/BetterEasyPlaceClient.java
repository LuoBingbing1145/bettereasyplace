package me.lbb.bettereasyplace.client;

import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.StringUtils;
import fi.dy.masa.malilib.util.data.ModInfo;
import me.lbb.bettereasyplace.Reference;
import me.lbb.bettereasyplace.event.InputHandler;
import me.lbb.bettereasyplace.gui.GuiConfigs;
import net.fabricmc.api.ClientModInitializer;

/**
 * 客户端初始化入口 / Client-side initialization entry point.
 * <p>
 * 在客户端启动时注册输入处理器，将 {@link InputHandler} 同时注册为
 * 热键提供者、键盘输入处理器和鼠标输入处理器，确保模组的快捷键和
 * 输入拦截能够正常工作。
 * <p>
 * Registers the input handler during client startup.  {@link InputHandler}
 * is registered as a keybind provider, keyboard input handler, and mouse
 * input handler, so that the mod's hotkeys and input interception work
 * correctly.
 */
public class BetterEasyPlaceClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        InputEventHandler.getKeybindManager().registerKeybindProvider(InputHandler.getInstance());
        InputEventHandler.getInputManager().registerKeyboardInputHandler(InputHandler.getInstance());
        InputEventHandler.getInputManager().registerMouseInputHandler(InputHandler.getInstance());

        // 注册配置界面到 malilib 的配置屏幕注册表，以便启用配置页面快速切换下拉菜单。
        // 需要在 initGui() 之前预先注册，否则 buildConfigSwitcher() 中的自动注册
        // 存在 bug（thisMod 在注册后仍为 null），导致首次打开时不会显示下拉菜单。
        // Register the config screen with malilib's config screen registry so that
        // the config page quick-switch dropdown is available.  Pre-registration is
        // required because the auto-registration in buildConfigSwitcher() has a bug
        // (thisMod stays null after registration), preventing the dropdown from
        // showing on the first open.
        Registry.CONFIG_SCREEN.registerConfigScreenFactory(new ModInfo(
                Reference.MOD_ID,
                StringUtils.splitCamelCase(Reference.MOD_ID),
                GuiConfigs::new));
    }
}
