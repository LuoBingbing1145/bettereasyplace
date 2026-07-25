package me.lbb.bettereasyplace.client.mixin;

import fi.dy.masa.litematica.util.WorldUtils;
import me.lbb.bettereasyplace.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 注入 Litematica 的 {@link WorldUtils}，允许在轻松放置激活时进食和使用烟花 /
 * Mixin into litematica's {@link WorldUtils} to allow eating and firework usage
 * while Easy Place mode is active.
 * <p>
 * <h3>解决的问题 / Problem Solved</h3>
 * Litematica 的轻松放置模式在激活时会拦截所有右键操作，导致玩家无法进食或使用
 * 烟花火箭（鞘翅加速）。本 Mixin 在以下三个关键入口点提前检测玩家是否正在
 * 进食或使用烟花，如果是则跳过轻松放置的拦截逻辑：
 * <ol>
 *   <li>{@code easyPlaceOnUseTick}   — 每 tick 执行，取消以阻止自动切换物品打断进食/烟花</li>
 *   <li>{@code handleEasyPlace}      — 按键事件，返回 false 放行原版右键处理</li>
 *   <li>{@code handlePlacementRestriction} — 右键限制检查，返回 false 不阻止右键</li>
 * </ol>
 * <p>
 * Litematica's Easy Place mode intercepts all right-click actions when active,
 * preventing the player from eating or using firework rockets (elytra boost).
 * This mixin pre-checks at three key entry points whether the player is
 * currently trying to eat or use fireworks, and skips Easy Place interception
 * when appropriate:
 * <ol>
 *   <li>{@code easyPlaceOnUseTick}   — per-tick action; cancel to prevent item
 *       switching from interrupting eating/firework use</li>
 *   <li>{@code handleEasyPlace}      — key event; return false to let the vanilla
 *       right-click action proceed</li>
 *   <li>{@code handlePlacementRestriction} — right-click restriction check;
 *       return false to allow the right-click through</li>
 * </ol>
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
     * 当玩家当前正在执行被允许的 bypass 操作（进食或烟花）时返回 {@code true}。
     * Returns {@code true} when the player is currently performing an allowed
     * bypass action (eating or using fireworks).
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

        return mainHand.getItem().isEdible() || offHand.getItem().isEdible();
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
            if (blacklist.contains(blockId)) {
                return true;
            }
        }

        return false;
    }
}
