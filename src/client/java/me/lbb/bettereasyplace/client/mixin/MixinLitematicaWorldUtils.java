package me.lbb.bettereasyplace.client.mixin;

import fi.dy.masa.litematica.util.WorldUtils;
import me.lbb.bettereasyplace.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 注入 Litematica 的 {@link WorldUtils}，允许在轻松放置激活时进行原版操作 /
 * Mixin into litematica's {@link WorldUtils} to allow vanilla actions
 * while Easy Place mode is active.
 * <p>
 * <h3>解决的问题 / Problem Solved</h3>
 * Litematica 的轻松放置模式在激活时会拦截所有右键操作。本 Mixin 在以下三个关键
 * 入口点提前检测玩家当前意图，并在适当时跳过轻松放置拦截逻辑：
 * <ol>
 *   <li>{@code easyPlaceOnUseTick}   — 每 tick 执行，取消以阻止自动切换物品打断当前动作</li>
 *   <li>{@code handleEasyPlace}      — 按键事件，返回 false 放行原版右键处理</li>
 *   <li>{@code handlePlacementRestriction} — 右键限制检查，返回 false 不阻止右键</li>
 * </ol>
 * <p>
 * <h3>支持的 bypass 操作 / Supported Bypass Actions</h3>
 * <ul>
 *   <li><b>进食 / Eating</b> — 手持食物右键进食不被拦截（{@link Configs#ALLOW_EATING}）</li>
 *   <li><b>烟花火箭 / Firework Rockets</b> — 鞘翅飞行时不拦截烟花加速（{@link Configs#ALLOW_FIREWORK}）</li>
 *   <li><b>潜影盒 / Shulker Box</b> — 手持潜影盒放置或右键已放置的潜影盒打开不被拦截（{@link Configs#ALLOW_SHULKER_BOX}）</li>
 *   <li><b>黑名单 / Blacklist</b> — 黑名单中的方块不拦截放置（{@link Configs#ENABLE_BLOCK_BLACKLIST}）</li>
 * </ul>
 * <p>
 * Litematica's Easy Place mode intercepts all right-click actions when active.
 * This mixin pre-checks at three key entry points whether the player is
 * performing an allowed bypass action, and skips Easy Place interception
 * when appropriate:
 * <ol>
 *   <li>{@code easyPlaceOnUseTick}   — per-tick action; cancel to prevent item
 *       switching from interrupting the current action</li>
 *   <li>{@code handleEasyPlace}      — key event; return false to let the vanilla
 *       right-click action proceed</li>
 *   <li>{@code handlePlacementRestriction} — right-click restriction check;
 *       return false to allow the right-click through</li>
 * </ol>
 * <p>
 * <h3>Supported Bypass Actions</h3>
 * <ul>
 *   <li><b>Eating</b> — food right-click not blocked ({@link Configs#ALLOW_EATING})</li>
 *   <li><b>Firework Rockets</b> — elytra boosting not blocked ({@link Configs#ALLOW_FIREWORK})</li>
 *   <li><b>Shulker Box</b> — placing or opening shulker boxes not blocked
 *       ({@link Configs#ALLOW_SHULKER_BOX})</li>
 *   <li><b>Blacklist</b> — blacklisted block placement not blocked
 *       ({@link Configs#ENABLE_BLOCK_BLACKLIST})</li>
 * </ul>
 *
 * @see Configs#ALLOW_EATING
 * @see Configs#ALLOW_FIREWORK
 */
@Mixin(value = WorldUtils.class, remap = false)
public abstract class MixinLitematicaWorldUtils {

    /**
     * 拦截每 tick 的轻松放置执行 / Intercept per-tick easy place execution.
     * <p>
     * 当玩家正在进食或使用烟花时，取消此 tick 的轻松放置操作，
     * 防止 {@code InventoryUtils.schematicWorldPickBlock} 切换物品打断当前动作。
     * When the player is eating or using fireworks, cancels this tick's easy place
     * action so that item switching (via {@code schematicWorldPickBlock}) does not
     * interrupt the ongoing action.
     */
    @Inject(method = "easyPlaceOnUseTick", at = @At("HEAD"), cancellable = true)
    private static void bettereasyplace$easyPlaceOnUseTick(Minecraft mc, CallbackInfo ci) {
        if (shouldSkipEasyPlace(mc)) {
            ci.cancel();
        }
    }

    /**
     * 拦截按键触发的轻松放置 / Intercept key-press easy place handling.
     * <p>
     * 返回 {@code false} 表示"事件未被消费"，原版右键逻辑继续执行。
     * Returning {@code false} means "event not consumed", allowing the vanilla
     * right-click logic to run.
     */
    @Inject(method = "handleEasyPlace", at = @At("HEAD"), cancellable = true)
    private static void bettereasyplace$handleEasyPlace(Minecraft mc, CallbackInfoReturnable<Boolean> cir) {
        if (shouldSkipEasyPlace(mc)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 拦截右键放置限制检查 / Intercept right-click placement restriction check.
     * <p>
     * 返回 {@code false} 表示"限制不生效"，右键操作被放行。
     * Returning {@code false} means "restriction not in effect", letting the
     * right-click through.
     */
    @Inject(method = "handlePlacementRestriction", at = @At("HEAD"), cancellable = true)
    private static void bettereasyplace$handlePlacementRestriction(Minecraft mc, CallbackInfoReturnable<Boolean> cir) {
        if (shouldSkipEasyPlace(mc)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 判断是否应跳过轻松放置拦截 / Determine whether Easy Place interception should be skipped.
     * <p>
     * 当玩家当前正在执行被允许的 bypass 操作（进食、烟花、潜影盒或黑名单方块）时返回 {@code true}。
     * Returns {@code true} when the player is currently performing an allowed
     * bypass action (eating, fireworks, shulker boxes, or blacklisted blocks).
     *
     * @param mc Minecraft 客户端实例 / the Minecraft client instance
     * @return true 如果应跳过轻松放置 / true if easy place should be skipped
     */
    @Unique
    private static boolean shouldSkipEasyPlace(@NotNull Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return false;
        }

        if (shouldAllowEating(player)) {
            return true;
        }

        if (shouldAllowFirework(player)) {
            return true;
        }

        if (shouldAllowShulkerBox(mc, player)) {
            return true;
        }

        return shouldAllowBlockPlacement(player);
    }

    /**
     * 检查是否应允许进食 / Check if eating should be allowed.
     * <p>
     * 同时满足以下条件时返回 {@code true}：
     * <ol>
     *   <li>配置项 {@code ALLOW_EATING} 已启用</li>
     *   <li>主手或副手持有可食用物品</li>
     * </ol>
     * 注意：此处不检查玩家是否真正可以进食（饱食度、创造模式等），
     * 而是将判断完全交给原版逻辑。这确保了金苹果等"总能食用"的食物
     * 在饱食度满时也不会被轻松放置拦截。
     * <p>
     * Returns {@code true} when ALL of the following conditions are met:
     * <ol>
     *   <li>{@code ALLOW_EATING} config is enabled</li>
     *   <li>Either the main hand or offhand holds an edible item</li>
     * </ol>
     * Note: we intentionally do NOT check whether the player can actually eat
     * (hunger level, creative mode, etc.).  That decision is left to vanilla
     * logic so that "always edible" foods like golden apples are not blocked
     * by Easy Place even when the hunger bar is full.
     */
    @Unique
    private static boolean shouldAllowEating(LocalPlayer player) {
        if (!Configs.ALLOW_EATING.getBooleanValue()) {
            return false;
        }

        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        // 1.20.5+ 食物改用 DataComponent, isEdible() 已移除 / Food uses DataComponent in 1.20.5+
        return mainHand.has(DataComponents.FOOD) || offHand.has(DataComponents.FOOD);
    }

    /**
     * 检查是否应允许使用烟花 / Check if firework use should be allowed.
     * <p>
     * 同时满足以下条件时返回 {@code true}：
     * <ol>
     *   <li>配置项 {@code ALLOW_FIREWORK} 已启用</li>
     *   <li>玩家正在鞘翅滑翔中</li>
     *   <li>主手或副手持有烟花火箭</li>
     * </ol>
     * Returns {@code true} when ALL of the following conditions are met:
     * <ol>
     *   <li>{@code ALLOW_FIREWORK} config is enabled</li>
     *   <li>The player is currently elytra-gliding</li>
     *   <li>Either the main hand or offhand holds a firework rocket</li>
     * </ol>
     */
    @Unique
    private static boolean shouldAllowFirework(LocalPlayer player) {
        if (!Configs.ALLOW_FIREWORK.getBooleanValue()) {
            return false;
        }

        // 玩家未使用鞘翅飞行 / Player is not flying with elytra
        if (!player.isFallFlying()) {
            return false;
        }

        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        return mainHand.getItem() instanceof FireworkRocketItem
                || offHand.getItem() instanceof FireworkRocketItem;
    }

    /**
     * 判断方块是否真的有交互（消耗右键事件） /
     * Check if the block genuinely consumes right-click interaction.
     * <p>
     * 用 instanceof 精确列出真正会消耗右键交互的方块基类，避免反射的误判
     * （反射方案会把 StairBlock、FenceBlock 等覆写 use() 但实际不消耗
     * 右键交互的方块误判为可交互）。
     * Uses instanceof checks on known interactable block types, avoiding false
     * positives from reflection (which misidentifies StairBlock, FenceBlock, etc.
     * as interactable because they override use() for non-interaction purposes).
     *
     * @param block 要检测的方块 / the block to check
     * @return true 如果方块消耗右键交互 / true if the block consumes right-click interaction
     */
    @Unique
    private static boolean isBlockInteractable(@NotNull Block block) {
        // 所有容器类方块（箱子、熔炉、漏斗、发射器、信标、音符盒、床等）
        // All container blocks (chest, furnace, hopper, dispenser, beacon, bed, etc.)
        if (block instanceof BaseEntityBlock) {
            return true;
        }

        // 非容器的可交互方块 / Non-container interactable blocks
        return block instanceof LeverBlock
                || block instanceof ButtonBlock
                || block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof FenceGateBlock
                || block instanceof NoteBlock
                || block instanceof CraftingTableBlock
                || block instanceof AnvilBlock
                || block instanceof RepeaterBlock
                || block instanceof ComparatorBlock
                || block instanceof RespawnAnchorBlock
                || block instanceof ComposterBlock
                || block instanceof StonecutterBlock
                || block instanceof GrindstoneBlock
                || block instanceof CartographyTableBlock
                || block instanceof LoomBlock
                || block instanceof CakeBlock
                || block instanceof DragonEggBlock
                || block instanceof SweetBerryBushBlock
                || block instanceof CaveVinesBlock
                || block instanceof CaveVinesPlantBlock
                || block instanceof BedBlock;
    }

    /**
     * 检查是否应允许潜影盒操作 / Check if shulker box actions should be allowed.
     * <p>
     * 解决原版 litematica 的两个痛点：
     * <ol>
     *   <li><b>打开潜影盒</b> — 无论手持何物，右键已放置的潜影盒都能打开</li>
     *   <li><b>放置潜影盒</b> — 手持潜影盒时：
     *     <ul>
     *       <li>准星指向无交互方块（石头、楼梯等）→ 直接放置，无需潜行</li>
     *       <li>准星指向有交互方块（拉杆等）+ 未潜行 → 不 bypass，被轻松放置阻止</li>
     *       <li>准星指向有交互方块 + 潜行 → 放置（原版潜行强制方块放置）</li>
     *     </ul>
     *   </li>
     * </ol>
     * <p>
     * Solves two pain points of vanilla litematica:
     * <ol>
     *   <li><b>Opening</b> — right-click a placed shulker box to open it,
     *       regardless of held item</li>
     *   <li><b>Placing</b> — while holding a shulker box:
     *     <ul>
     *       <li>Looking at non-interactable block (stone, stairs, etc.) → place directly, no sneak needed</li>
     *       <li>Looking at interactable block (lever, etc.) + no sneak → blocked by Easy Place</li>
     *       <li>Looking at interactable block + sneak → place (sneak forces block placement in vanilla)</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * @param mc     Minecraft 客户端实例 / the Minecraft client instance
     * @param player 本地玩家 / the local player
     * @return true 如果应跳过轻松放置 / true if easy place should be skipped
     */
    @Unique
    private static boolean shouldAllowShulkerBox(@NotNull Minecraft mc, @NotNull LocalPlayer player) {
        if (!Configs.ALLOW_SHULKER_BOX.getBooleanValue()) {
            return false;
        }

        // 优先检查：准星指向已放置的潜影盒 → 始终允许打开
        // Priority check: crosshair on a placed shulker box → always allow opening
        if (mc.level != null
                && mc.hitResult instanceof BlockHitResult blockHit) {
            BlockState state = mc.level.getBlockState(blockHit.getBlockPos());
            if (state.getBlock() instanceof ShulkerBoxBlock) {
                return true;
            }
        }

        // 检查是否手持潜影盒
        // Check if holding a shulker box
        boolean holdsShulkerBox = false;
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock) {
            holdsShulkerBox = true;
        }
        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock) {
            holdsShulkerBox = true;
        }

        if (!holdsShulkerBox) {
            return false;
        }

        // 潜行 → 始终允许放置（原版潜行强制跳过方块交互，直接执行 itemStack.useOn）
        // Sneaking → always allow placing (vanilla sneak forces block placement)
        if (player.isShiftKeyDown()) {
            return true;
        }

        // 未潜行：检查准星方块是否可交互
        // 可交互（拉杆等）→ 不 bypass，让轻松放置阻止
        // 不可交互（石头、楼梯等）→ bypass，原版直接放置
        // Not sneaking: check if the looked-at block is interactable
        // Interactable (lever, etc.) → don't bypass, let Easy Place block it
        // Non-interactable (stone, stairs, etc.) → bypass, vanilla places the block
        if (mc.level != null
                && mc.hitResult instanceof BlockHitResult blockHit) {
            BlockState state = mc.level.getBlockState(blockHit.getBlockPos());
            return !isBlockInteractable(state.getBlock());
        }

        // 未看向任何方块（实体或空中）→ 允许放置
        // Not looking at a block (entity or air) → allow placement
        return true;
    }

    /**
     * 检查是否应允许放置黑名单方块 / Check if placing a blacklisted block should be allowed.
     * <p>
     * 同时满足以下条件时返回 {@code true}：
     * <ol>
     *   <li>配置项 {@code ENABLE_BLOCK_BLACKLIST} 已启用</li>
     *   <li>玩家主手或副手持有方块物品</li>
     *   <li>该方块的注册 ID 在黑名单中</li>
     * </ol>
     * Returns {@code true} when ALL of the following conditions are met:
     * <ol>
     *   <li>{@code ENABLE_BLOCK_BLACKLIST} config is enabled</li>
     *   <li>The player holds a block item in main or off hand</li>
     *   <li>The block's registry ID is in the blacklist</li>
     * </ol>
     */
    @Unique
    private static boolean shouldAllowBlockPlacement(LocalPlayer player) {
        if (!Configs.ENABLE_BLOCK_BLACKLIST.getBooleanValue()) {
            return false;
        }

        List<String> blacklist = Configs.BLOCK_BLACKLIST.getStrings();
        if (blacklist.isEmpty()) {
            return false;
        }

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof BlockItem) {
            String blockId = BuiltInRegistries.ITEM.getKey(mainHand.getItem()).toString();
            if (blacklist.contains(blockId)) {
                return true;
            }
        }

        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof BlockItem) {
            String blockId = BuiltInRegistries.ITEM.getKey(offHand.getItem()).toString();
            return blacklist.contains(blockId);
        }

        return false;
    }
}
