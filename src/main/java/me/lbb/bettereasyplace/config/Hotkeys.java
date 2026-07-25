package me.lbb.bettereasyplace.config;

import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import me.lbb.bettereasyplace.Reference;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 快捷键定义与注册 / Hotkey definitions and registration.
 * <p>
 * 管理模组所有快捷键的声明、注册与 malilib 热键系统的集成。
 * 每个快捷键在此处声明为 {@link ConfigHotkey} 字段，并加入 {@link #HOTKEY_LIST}
 * 即可自动出现在配置界面和热键系统中。
 * <p>
 * Manages declaration, registration, and malilib hotkey system integration
 * for all mod keybinds.  Declare each keybind as a {@link ConfigHotkey} field
 * here and add it to {@link #HOTKEY_LIST} — it will then automatically appear
 * in the config GUI and hotkey system.
 */
public class Hotkeys implements IKeybindProvider {
    /** 打开配置界面的快捷键，默认 B + C / Hotkey to open the config GUI, defaults to B + C. */
    public static final ConfigHotkey OPEN_CONFIG_GUI = new ConfigHotkey(
            "openConfigGui",
            "B,C",
            "Opens the BetterEasyPlace configuration GUI")
            .apply("bettereasyplace.hotkeys");

    /** 所有快捷键的不可变列表 / Immutable list of all hotkeys. */
    public static final List<IHotkey> HOTKEY_LIST = ImmutableList.of(OPEN_CONFIG_GUI);

    /**
     * 将快捷键按键绑定注册到热键管理器 / Register keybind mappings with the hotkey manager.
     *
     * @param manager malilib 的按键绑定管理器 / malilib's keybind manager
     */
    @Override
    public void addKeysToMap(IKeybindManager manager) {
        for (IHotkey hotkey : HOTKEY_LIST) {
            manager.addKeybindToMap(hotkey.getKeybind());
        }
    }

    /**
     * 将快捷键添加到对应的分类中 / Add hotkeys to their respective category.
     *
     * @param manager malilib 的按键绑定管理器 / malilib's keybind manager
     */
    @Override
    public void addHotkeys(@NotNull IKeybindManager manager) {
        manager.addHotkeysForCategory(Reference.MOD_ID, "bettereasyplace.hotkeys.category.general", HOTKEY_LIST);
    }
}
