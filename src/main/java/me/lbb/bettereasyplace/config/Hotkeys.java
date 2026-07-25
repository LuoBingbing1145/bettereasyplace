package me.lbb.bettereasyplace.config;

import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.config.IConfigBase;
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
 * 所有 {@link IHotkey} 实现（包括 {@code ConfigHotkey} 和 {@code ConfigBooleanHotkeyed}）
 * 必须加入 {@link #HOTKEY_LIST}，其 keybind 才会被注册到 malilib 按键系统并生效。
 * <p>
 * Manages declaration, registration, and malilib hotkey system integration
 * for all mod keybinds.  Every {@link IHotkey} implementation (including
 * {@code ConfigHotkey} and {@code ConfigBooleanHotkeyed}) must be in
 * {@link #HOTKEY_LIST} for its keybind to be registered and functional.
 */
public class Hotkeys implements IKeybindProvider {
    /** 打开配置界面的快捷键，默认 B + C / Hotkey to open the config GUI, defaults to B + C. */
    public static final ConfigHotkey OPEN_CONFIG_GUI = new ConfigHotkey(
            "openConfigGui",
            "B,C",
            "Opens the BetterEasyPlace configuration GUI");

    /** 所有快捷键的不可变列表 / Immutable list of all hotkeys.
     * <p>
     * 注意：{@code ConfigBooleanHotkeyed} 同时实现了 {@link IConfigBase} 和 {@link IHotkey}，
     * 需要在 Configs.OPTIONS（通用配置）和 HOTKEY_LIST（快捷键注册）两处都列出。
     * <p>
     * Note: {@code ConfigBooleanHotkeyed} implements both {@code IConfigBase} and
     * {@code IHotkey} — it must appear in both {@code Configs.OPTIONS} (generic config)
     * and {@code HOTKEY_LIST} (keybind registration).
     */
    public static final List<IHotkey> HOTKEY_LIST = ImmutableList.of(
            OPEN_CONFIG_GUI,
            Configs.ALLOW_EATING,
            Configs.ALLOW_FIREWORK,
            Configs.ENABLE_BLOCK_BLACKLIST
    );

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
