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

public class GuiConfigs extends GuiConfigsBase {
    private static ConfigGuiTab tab = ConfigGuiTab.GENERIC;

    public GuiConfigs() {
        super(10, 50, Reference.MOD_ID, null, "bettereasyplace.gui.title.configs");
    }

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

    private int createButton(int x, int y, @NotNull ConfigGuiTab tab) {
        ButtonGeneric button = new ButtonGeneric(x, y, -1, 20, tab.getDisplayName());
        button.setEnabled(GuiConfigs.tab != tab);
        this.addButton(button, new ButtonListener(tab, this));

        return button.getWidth() + 2;
    }

    @Override
    protected int getConfigWidth() {
        ConfigGuiTab tab = GuiConfigs.tab;

        if (tab == ConfigGuiTab.GENERIC) {
            return 140;
        }

        return super.getConfigWidth();
    }

    @Override
    protected boolean useKeybindSearch() {
        return GuiConfigs.tab == ConfigGuiTab.HOTKEYS;
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        List<? extends IConfigBase> configs = GuiConfigs.tab == ConfigGuiTab.HOTKEYS ? Hotkeys.HOTKEY_LIST : Configs.OPTIONS;

        return ConfigOptionWrapper.createFor(configs);
    }

    @Override
    protected void onSettingsChanged() {
        super.onSettingsChanged();
        InputEventHandler.getKeybindManager().updateUsedKeys();
    }

    private record ButtonListener(ConfigGuiTab tab, GuiConfigs gui) implements IButtonActionListener {

        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            GuiConfigs.tab = this.tab;
            if (this.gui.minecraft != null) {
                this.gui.init(this.gui.minecraft, this.gui.width, this.gui.height);
            }
        }
    }

    public enum ConfigGuiTab {
        GENERIC ("bettereasyplace.gui.button.configs.generic"),
        HOTKEYS ("bettereasyplace.gui.button.configs.hotkeys");

        private final String translationKey;

        ConfigGuiTab(String translationKey) {
            this.translationKey = translationKey;
        }

        public String getDisplayName() {
            return StringUtils.translate(this.translationKey);
        }
    }
}
