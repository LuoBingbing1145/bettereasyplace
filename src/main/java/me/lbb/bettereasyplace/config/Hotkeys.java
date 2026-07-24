package me.lbb.bettereasyplace.config;

import java.util.List;
import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import me.lbb.bettereasyplace.Reference;
import org.jetbrains.annotations.NotNull;

public class Hotkeys implements IKeybindProvider {
    public static final ConfigHotkey OPEN_CONFIG_GUI = new ConfigHotkey(
            "openConfigGui",
            "B,C",
            "Opens the BetterEasyPlace configuration GUI");

    public static final List<IHotkey> HOTKEY_LIST = ImmutableList.of(OPEN_CONFIG_GUI);

    @Override
    public void addKeysToMap(IKeybindManager manager) {
        for (IHotkey hotkey : HOTKEY_LIST) {
            manager.addKeybindToMap(hotkey.getKeybind());
        }
    }

    @Override
    public void addHotkeys(@NotNull IKeybindManager manager) {
        manager.addHotkeysForCategory(Reference.MOD_ID, "bettereasyplace.hotkeys.category.general", HOTKEY_LIST);
    }
}
