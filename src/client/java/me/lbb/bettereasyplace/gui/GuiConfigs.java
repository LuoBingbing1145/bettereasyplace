package me.lbb.bettereasyplace.gui;

import java.util.List;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.util.StringUtils;
import me.lbb.bettereasyplace.Reference;
import me.lbb.bettereasyplace.config.Configs;
import me.lbb.bettereasyplace.config.Hotkeys;
import org.jetbrains.annotations.NotNull;

/**
 * 模组配置界面 / Mod configuration GUI.
 * <p>
 * 基于 malilib 的 {@link GuiConfigsBase}，提供两个标签页：
 * <ul>
 *   <li><b>通用（Generic）</b> — 显示 {@link Configs#OPTIONS} 中的所有布尔/枚举配置项</li>
 *   <li><b>快捷键（Hotkeys）</b> — 显示 {@link Hotkeys#HOTKEY_LIST} 中的所有快捷键绑定</li>
 * </ul>
 * Built on malilib's {@link GuiConfigsBase}, offering two tabs:
 * <ul>
 *   <li><b>Generic</b> — displays all boolean/enum options from {@link Configs#OPTIONS}</li>
 *   <li><b>Hotkeys</b> — displays all keybinds from {@link Hotkeys#HOTKEY_LIST}</li>
 * </ul>
 */
public class GuiConfigs extends GuiConfigsBase {
    /** 当前标签页，默认为通用 / Current active tab, defaults to Generic. */
    private static ConfigGuiTab tab = ConfigGuiTab.GENERIC;

    public GuiConfigs() {
        super(10, 50, Reference.MOD_ID, null, "bettereasyplace.gui.title.configs");
    }

    /**
     * 初始化 GUI 元素 / Initialize GUI elements.
     * <p>
     * 在顶部绘制标签页切换按钮，并根据当前标签页加载对应的配置列表。
     * Draws tab-switching buttons at the top and loads the config list for
     * the active tab.
     */
    @Override
    public void initGui() {
        super.initGui();
        this.clearOptions();

        int x = 10;
        int y = 26;

        for (ConfigGuiTab tab : ConfigGuiTab.values()) {
            x += this.createButton(x, y, tab);
        }
    }

    /**
     * 创建一个标签页切换按钮 / Create a tab-switching button.
     *
     * @param x   按钮 X 坐标 / button X coordinate
     * @param y   按钮 Y 坐标 / button Y coordinate
     * @param tab 目标标签页 / target tab
     * @return 按钮宽度 + 间距 / button width + gap
     */
    private int createButton(int x, int y, @NotNull ConfigGuiTab tab) {
        ButtonGeneric button = new ButtonGeneric(x, y, -1, 20, tab.getDisplayName());
        button.setEnabled(GuiConfigs.tab != tab);
        this.addButton(button, new ButtonListener(tab, this));

        return button.getWidth() + 2;
    }

    /**
     * 获取配置列表宽度 / Get the config list width.
     * <p>
     * 通用标签页需要足够宽以容纳布尔配置项的标签。
     * Generic tab needs enough width for the boolean option labels.
     */
    @Override
    protected int getConfigWidth() {
        ConfigGuiTab tab = GuiConfigs.tab;

        if (tab == ConfigGuiTab.GENERIC) {
            return 160;
        }

        return super.getConfigWidth();
    }

    /**
     * 是否启用快捷键搜索 / Whether to enable keybind search.
     * <p>
     * 仅在快捷键标签页启用搜索功能。
     * Only enable search on the Hotkeys tab.
     */
    @Override
    protected boolean useKeybindSearch() {
        return GuiConfigs.tab == ConfigGuiTab.HOTKEYS;
    }

    /**
     * 获取当前标签页的配置项列表 / Get the config list for the active tab.
     *
     * @return 打包好的配置项列表 / wrapped config option list
     */
    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        List<? extends IConfigBase> configs = GuiConfigs.tab == ConfigGuiTab.HOTKEYS ? Hotkeys.HOTKEY_LIST : Configs.OPTIONS;

        return ConfigOptionWrapper.createFor(configs);
    }

    /**
     * 配置变更回调 / Settings changed callback.
     * <p>
     * 在用户修改任何配置项后触发，刷新快捷键绑定状态。
     * Called after the user changes any option; refreshes keybind state.
     */
    @Override
    protected void onSettingsChanged() {
        super.onSettingsChanged();
        InputEventHandler.getKeybindManager().updateUsedKeys();
    }

    /**
     * 标签页切换按钮监听器 / Tab switching button listener.
     * <p>
     * 点击按钮时切换到对应的配置标签页并重建 GUI。
     * Switches to the corresponding config tab and rebuilds the GUI on click.
     */
    private record ButtonListener(ConfigGuiTab tab, GuiConfigs gui) implements IButtonActionListener {

        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            GuiConfigs.tab = this.tab;
            if (this.gui.minecraft != null) {
                this.gui.init(this.gui.minecraft, this.gui.width, this.gui.height);
            }
        }
    }

    /**
     * 配置界面标签页枚举 / Config GUI tab enum.
     */
    public enum ConfigGuiTab {
        /** 通用设置 / Generic settings. */
        GENERIC ("bettereasyplace.gui.button.configs.generic"),
        /** 快捷键设置 / Hotkey settings. */
        HOTKEYS ("bettereasyplace.gui.button.configs.hotkeys");

        private final String translationKey;

        ConfigGuiTab(String translationKey) {
            this.translationKey = translationKey;
        }

        /** 获取翻译后的显示名称 / Get the translated display name. */
        public String getDisplayName() {
            return StringUtils.translate(this.translationKey);
        }
    }
}
