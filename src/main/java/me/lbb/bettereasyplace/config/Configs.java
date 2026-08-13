package me.lbb.bettereasyplace.config;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.ConfigBooleanHotkeyed;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.JsonUtils;
import me.lbb.bettereasyplace.Reference;

import java.io.File;

/**
 * 模组配置管理 / Mod configuration manager.
 * <p>
 * 管理所有通用配置项（非快捷键）的加载与保存。
 * 配置以 JSON 格式存储在 {@code config/bettereasyplace.json}。
 * 新增配置项只需在此类中添加字段并加入 {@link #OPTIONS} 列表即可自动持久化。
 * <p>
 * Manages loading and saving of all generic (non-hotkey) configuration options.
 * The config is stored as JSON in {@code config/bettereasyplace.json}.
 * To add a new option, simply declare a field here and include it in the
 * {@link #OPTIONS} list — persistence is handled automatically.
 */
public class Configs implements IConfigHandler {
    private static final String CONFIG_FILE_NAME = Reference.MOD_ID + ".json";

    /**
     * 允许进食 / Allow Eating.
     * <p>
     * 启用后，手持食物按下右键进食时，不会被轻松放置模式拦截。
     * When enabled, right-clicking to eat while holding food will not be
     * blocked by Easy Place mode.
     */
    public static final ConfigBooleanHotkeyed ALLOW_EATING = new ConfigBooleanHotkeyed(
            "allowEating",
            false,
            "",
            "If enabled, holding food and eating will not be blocked by Easy Place");

    /**
     * 允许使用烟花 / Allow Firework.
     * <p>
     * 启用后，鞘翅飞行时手持烟花火箭按下右键，不会被轻松放置模式拦截。
     * When enabled, right-clicking to use a firework rocket while flying with
     * an elytra will not be blocked by Easy Place mode.
     */
    public static final ConfigBooleanHotkeyed ALLOW_FIREWORK = new ConfigBooleanHotkeyed(
            "allowFirework",
            false,
            "",
            "If enabled, using fireworks while flying with elytra will not be blocked by Easy Place");

    /**
     * 允许潜影盒 / Allow Shulker Box.
     * <p>
     * 启用后，在轻松放置模式下可以正常使用潜影盒：
     * <ul>
     *   <li>右键已放置的潜影盒可以打开（无论手持何物）</li>
     *   <li>手持潜影盒右键直接放置（绕过轻松放置，由原版处理）</li>
     * </ul>
     * When enabled, shulker boxes work normally in Easy Place mode:
     * <ul>
     *   <li>Right-click a placed shulker box to open it (regardless of held item)</li>
     *   <li>Right-click while holding a shulker box to place it (bypasses Easy Place)</li>
     * </ul>
     */
    public static final ConfigBooleanHotkeyed ALLOW_SHULKER_BOX = new ConfigBooleanHotkeyed(
            "allowShulkerBox",
            false,
            "",
            "If enabled, placing and opening shulker boxes will not be blocked by Easy Place");

    /**
     * 启用拦截方块黑名单 / Enable Block Blacklist.
     * <p>
     * 启用后，黑名单中的方块将不会被轻松放置模式拦截，允许直接放置。
     * When enabled, blocks in the blacklist will not be blocked by Easy Place
     * mode and can be placed directly.
     */
    public static final ConfigBooleanHotkeyed ENABLE_BLOCK_BLACKLIST = new ConfigBooleanHotkeyed(
            "enableBlockBlacklist",
            false,
            "",
            "If enabled, blocks in the blacklist will not be blocked by Easy Place");

    /**
     * 方块黑名单 / Block Blacklist.
     * <p>
     * 轻松放置模式下不会被拦截的方块 ID 列表（每行一个，如 {@code minecraft:chest}）。
     * 仅当 {@link #ENABLE_BLOCK_BLACKLIST} 启用时生效。
     * <p>
     * A list of block IDs (one per line, e.g. {@code minecraft:chest}) that will
     * not be blocked by Easy Place.  Only takes effect when
     * {@link #ENABLE_BLOCK_BLACKLIST} is enabled.
     */
    public static final ConfigStringList BLOCK_BLACKLIST = new ConfigStringList(
            "blockBlacklist",
            ImmutableList.of(),
            "List of block IDs that will not be blocked by Easy Place mode");


    /**
     * 允许液体放置 / Allow Liquid Placement.
     * <p>
     * 启用后，轻松放置模式下原理图中的液体源方块（水源、岩浆源）将被正确处理：
     * 自动切换到对应的桶并精确放置液体。放置后空桶不会被允许回收液体，
     * 保护原理图中的液体不被意外移除。
     * <b>暂不支持含水方块放置。</b>
     * <p>
     * When enabled, liquid source blocks (water source, lava source) in the
     * schematic will be properly handled by Easy Place mode: the correct bucket
     * will be auto-picked and the liquid placed precisely. After placement,
     * the empty bucket will not be allowed to pick up the liquid, protecting
     * schematic liquids from accidental removal.
     */
    public static final ConfigBooleanHotkeyed ALLOW_LIQUID_PLACEMENT = new ConfigBooleanHotkeyed(
            "allowLiquidPlacement",
            false,
            "",
            "[Experimental] If enabled, liquid source blocks in the schematic will be handled by Easy Place (auto-pick bucket, precise placement, prevent accidental removal)");

    /**
     * 允许含水方块放置 / Allow Waterlogged Block Placement.
     * <p>
     * 启用后，轻松放置模式下原理图中的含水方块（如含水台阶、含水楼梯等）将被正确处理：
     * 自动先放水源再放方块（方块放入水中自动含水），或者对已放置的方块用水桶右键含水。
     * 需要背包中同时有对应方块和水桶。放置后空桶不会被允许回收液体。
     * <b>需要同时启用"允许液体放置"。</b>
     * <p>
     * When enabled, waterlogged blocks in the schematic (e.g. waterlogged slabs,
     * stairs, etc.) will be properly handled by Easy Place mode: water is placed
     * first, then the block is placed in water (becoming waterlogged), or an
     * existing block is waterlogged with a bucket. Requires both the block and a
     * water bucket in the inventory. Empty buckets are prevented from picking up
     * the placed water.
     * <b>Requires "Allow Liquid Placement" to also be enabled.</b>
     */
    public static final ConfigBooleanHotkeyed ALLOW_WATERLOGGED_PLACEMENT = new ConfigBooleanHotkeyed(
            "allowWaterloggedPlacement",
            false,
            "",
            "[Experimental] If enabled, waterlogged blocks in the schematic will be handled by Easy Place (place water first, then block; or waterlog existing blocks). Requires both block and water bucket in inventory. Requires \"Allow Liquid Placement\" to also be enabled.");

    /**
     * 轻松放置手部挥动 / Easy Place Swing Hand.
     * <p>
     * <b>[移植自 Litematica 1.21+]</b>
     * 此处将其移植到 1.20.X 版本，以在旧版提供一致的体验。
     * <p>
     * <b>[Backported from Litematica 1.21+]</b>
     * It is backported here to provide a consistent experience on 1.20.X.
     * <p>
     * 启用后，轻松放置模式每次成功放置方块时，玩家的手会执行一次挥动动画，
     * 使放置操作看起来更自然。禁用则手部保持静止。
     * <p>
     * When enabled, the player's hand performs a swing animation each time a
     * block is successfully placed via Easy Place mode, making placement feel
     * more natural. When disabled, the hand remains static.
     */
    public static final ConfigBooleanHotkeyed EASY_PLACE_SWING_HAND = new ConfigBooleanHotkeyed(
            "easyPlaceSwingHand",
            false,
            "",
            "If enabled, the player's hand swings when Easy Place places a block");

    /**
     * 轻松放置保护范围 / Easy Place Protection Range.
     * <p>
     * 控制投影子区域周围的保护范围（格数）。在此范围内的非原理图方块放置
     * 将被阻止，防止在投影边缘误放方块。设为 0 则无保护。
     * 此配置通过 Mixin 修改 Litematica 内部硬编码的值（默认 2）。
     * <p>
     * Controls the protection range (in blocks) around schematic sub-regions.
     * Non-schematic block placement within this range is blocked to prevent
     * accidental misplacement near the schematic edges. Set to 0 for no protection.
     * This config overrides Litematica's internal hardcoded value (default 2).
     */
    public static final ConfigInteger EASY_PLACE_PROTECTION_RANGE = new ConfigInteger(
            "easyPlaceProtectionRange",
            2,
            0,
            16,
            false,
            "The protection range (in blocks) around schematic regions\nwhere non-schematic block placement is blocked.\n0 = no protection, 2 = default Litematica behavior");

    /** 所有通用配置项的不可变列表，用于批量读写 / Immutable list of all generic options for batch read/write. */
    public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
            ALLOW_EATING,
            ALLOW_FIREWORK,
            ALLOW_LIQUID_PLACEMENT,
            ALLOW_WATERLOGGED_PLACEMENT,
            ALLOW_SHULKER_BOX,
            EASY_PLACE_SWING_HAND,
            EASY_PLACE_PROTECTION_RANGE,
            ENABLE_BLOCK_BLACKLIST,
            BLOCK_BLACKLIST
    );

    /**
     * 从 JSON 文件加载配置 / Load configuration from the JSON file.
     * <p>
     * 分别读取 {@code Generic} 和 {@code Hotkeys} 两个分类下的配置项。
     * Reads options under both the {@code Generic} and {@code Hotkeys} categories.
     */
    public static void loadFromFile() {
        File configFile = new File(FileUtils.getConfigDirectory(), CONFIG_FILE_NAME);

        if (configFile.exists() && configFile.isFile() && configFile.canRead()) {
            JsonElement element = JsonUtils.parseJsonFile(configFile);

            if (element != null && element.isJsonObject()) {
                JsonObject root = element.getAsJsonObject();

                ConfigUtils.readConfigBase(root, "Generic", OPTIONS);
                ConfigUtils.readConfigBase(root, "Hotkeys", Hotkeys.HOTKEY_LIST);
            }
        }
    }

    /**
     * 保存配置到 JSON 文件 / Save configuration to the JSON file.
     * <p>
     * 将 {@code Generic} 和 {@code Hotkeys} 分类下的配置项分别写入 JSON。
     * Writes options under the {@code Generic} and {@code Hotkeys} categories
     * into the JSON file.
     */
    public static void saveToFile() {
        File dir = FileUtils.getConfigDirectory();

        if ((dir.exists() && dir.isDirectory()) || dir.mkdirs()) {
            JsonObject root = new JsonObject();

            ConfigUtils.writeConfigBase(root, "Generic", OPTIONS);
            ConfigUtils.writeConfigBase(root, "Hotkeys", Hotkeys.HOTKEY_LIST);

            JsonUtils.writeJsonToFile(root, new File(dir, CONFIG_FILE_NAME));
        }
    }

    /**
     * malilib 回调：加载配置 / malilib callback: load config.
     * <p>
     * 例如通过 ModMenu 打开配置界面并重置时触发。
     * Triggered e.g. when the config screen is opened via ModMenu and reset.
     */
    @Override
    public void load() {
        loadFromFile();
    }

    /**
     * malilib 回调：保存配置 / malilib callback: save config.
     * <p>
     * 在配置界面中修改任何选项后自动触发。
     * Triggered automatically whenever any option is changed in the config GUI.
     */
    @Override
    public void save() {
        saveToFile();
    }
}
