package me.lbb.bettereasyplace.client;

import fi.dy.masa.malilib.event.InputEventHandler;
import me.lbb.bettereasyplace.event.InputHandler;
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
    }
}
