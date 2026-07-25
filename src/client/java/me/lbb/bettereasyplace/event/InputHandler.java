package me.lbb.bettereasyplace.event;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.*;
import me.lbb.bettereasyplace.Reference;
import me.lbb.bettereasyplace.config.Hotkeys;
import me.lbb.bettereasyplace.gui.GuiConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 输入事件处理器 / Input event handler.
 * <p>
 * 单例模式，同时实现三个接口以完整接管模组的输入处理：
 * <ul>
 *   <li>{@link IKeybindProvider}  — 注册快捷键绑定与回调</li>
 *   <li>{@link IKeyboardInputHandler} — 处理键盘事件</li>
 *   <li>{@link IMouseInputHandler}    — 处理鼠标事件</li>
 * </ul>
 * 当前主要功能是将 {@code OPEN_CONFIG_GUI} 快捷键绑定到打开配置界面的操作。
 * <p>
 * Singleton that implements three interfaces for complete input handling:
 * <ul>
 *   <li>{@link IKeybindProvider}  — register keybinds and callbacks</li>
 *   <li>{@link IKeyboardInputHandler} — handle keyboard events</li>
 *   <li>{@link IMouseInputHandler}    — handle mouse events</li>
 * </ul>
 * Currently its main job is binding the {@code OPEN_CONFIG_GUI} hotkey to
 * the config screen.
 */
public class InputHandler implements IKeybindProvider, IKeyboardInputHandler, IMouseInputHandler {
    /** 单例实例 / Singleton instance. */
    private static final InputHandler INSTANCE = new InputHandler();

    private InputHandler() {
    }

    /** 获取单例 / Get the singleton instance. */
    public static InputHandler getInstance() {
        return INSTANCE;
    }

    /**
     * 注册按键绑定映射 / Register keybind mappings.
     * <p>
     * 遍历所有已声明快捷键，将其底层按键绑定添加到管理器；
     * 同时为 {@code OPEN_CONFIG_GUI} 注入回调——按下时打开配置界面。
     * <p>
     * Iterates all declared hotkeys to add their underlying keybinds to
     * the manager, and injects a callback for {@code OPEN_CONFIG_GUI}
     * that opens the config GUI on activation.
     */
    @Override
    public void addKeysToMap(IKeybindManager manager) {
        for (IHotkey hotkey : Hotkeys.HOTKEY_LIST) {
            manager.addKeybindToMap(hotkey.getKeybind());
        }

        // 打开配置界面回调 / Config GUI open callback
        Hotkeys.OPEN_CONFIG_GUI.getKeybind().setCallback((action, key) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                GuiBase.openGui(new GuiConfigs());
                return true;
            }
            return false;
        });
    }

    /**
     * 将快捷键注册到分类标签页 / Add hotkeys under their category tab.
     */
    @Override
    public void addHotkeys(@NotNull IKeybindManager manager) {
        manager.addHotkeysForCategory(Reference.MOD_ID, "bettereasyplace.hotkeys.category.general", Hotkeys.HOTKEY_LIST);
    }

    /**
     * 键盘输入事件 / Keyboard input event.
     * <p>
     * 当前直接委托给父接口默认实现（不做额外处理），保留扩展点。
     * Currently delegates to the default interface implementation (no extra
     * processing), leaving room for future expansion.
     */
    @Override
    public boolean onKeyInput(KeyEvent event, boolean eventKeyState) {
        return IKeyboardInputHandler.super.onKeyInput(event, eventKeyState);
    }

    /**
     * 鼠标点击事件 / Mouse click event.
     * <p>
     * 当前直接委托给父接口默认实现（不做额外处理），保留扩展点。
     * Currently delegates to the default interface implementation (no extra
     * processing), leaving room for future expansion.
     */
    @Override
    public boolean onMouseClick(MouseButtonEvent event, boolean eventButtonState) {
        return IMouseInputHandler.super.onMouseClick(event, eventButtonState);
    }

    /**
     * 鼠标滚轮事件 / Mouse scroll event.
     * <p>
     * 当前直接委托给父接口默认实现（不做额外处理），保留扩展点。
     * Currently delegates to the default interface implementation (no extra
     * processing), leaving room for future expansion.
     */
    @Override
    public boolean onMouseScroll(double horizontalAmount, double verticalAmount, double mouseDelta) {
        return IMouseInputHandler.super.onMouseScroll(horizontalAmount, verticalAmount, mouseDelta);
    }
}
