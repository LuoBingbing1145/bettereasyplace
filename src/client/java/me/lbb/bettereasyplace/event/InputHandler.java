package me.lbb.bettereasyplace.event;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.malilib.hotkeys.IKeyboardInputHandler;
import fi.dy.masa.malilib.hotkeys.IMouseInputHandler;
import me.lbb.bettereasyplace.Reference;
import me.lbb.bettereasyplace.config.Hotkeys;
import me.lbb.bettereasyplace.gui.GuiConfigs;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;

public class InputHandler implements IKeybindProvider, IKeyboardInputHandler, IMouseInputHandler {
    private static final InputHandler INSTANCE = new InputHandler();

    private InputHandler() {
    }

    public static InputHandler getInstance() {
        return INSTANCE;
    }

    @Override
    public void addKeysToMap(IKeybindManager manager) {
        for (IHotkey hotkey : Hotkeys.HOTKEY_LIST) {
            manager.addKeybindToMap(hotkey.getKeybind());
        }

        Hotkeys.OPEN_CONFIG_GUI.getKeybind().setCallback((action, key) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                GuiBase.openGui(new GuiConfigs());
                return true;
            }
            return false;
        });
    }

    @Override
    public void addHotkeys(@NotNull IKeybindManager manager) {
        manager.addHotkeysForCategory(Reference.MOD_ID, "bettereasyplace.hotkeys.category.general", Hotkeys.HOTKEY_LIST);
    }

    @Override
    public boolean onKeyInput(int keyCode, int scanCode, int modifiers, boolean eventKeyState) {
        return IKeyboardInputHandler.super.onKeyInput(keyCode, scanCode, modifiers, eventKeyState);
    }

    @Override
    public boolean onMouseClick(int mouseX, int mouseY, int eventButton, boolean eventButtonState) {
        return IMouseInputHandler.super.onMouseClick(mouseX, mouseY, eventButton, eventButtonState);
    }

    @Override
    public boolean onMouseScroll(int mouseX, int mouseY, double amount) {
        return IMouseInputHandler.super.onMouseScroll(mouseX, mouseY, amount);
    }
}
