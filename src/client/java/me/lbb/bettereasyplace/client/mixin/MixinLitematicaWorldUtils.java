package me.lbb.bettereasyplace.client.mixin;

import fi.dy.masa.litematica.util.WorldUtils;
import me.lbb.bettereasyplace.client.util.EasyPlacePlacementUtils;
import me.lbb.bettereasyplace.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 注入 Litematica 的旧版轻松放置流程 {@link WorldUtils}，允许在轻松放置激活时进行原版操作 /
 * Mixin into litematica's Easy Place flow {@link WorldUtils} to allow
 * vanilla actions while Easy Place mode is active.
 * <p>
 * 本类为瘦委托：所有业务逻辑位于 {@link EasyPlacePlacementUtils}，此处仅保留
 * 注入点。
 * <p>
 * This class is a thin delegate: all business logic lives in
 * {@link EasyPlacePlacementUtils}, only the injection points remain here.
 * <p>
 * <h3>解决的问题 / Problem Solved</h3>
 * Litematica 的轻松放置模式在激活时会拦截所有右键操作。本 Mixin 在以下三个关键
 * 入口点提前检测玩家当前意图，并在适当时跳过轻松放置拦截逻辑：
 * <ol>
 *   <li>{@code easyPlaceOnUseTick}   — 每 tick 执行，取消以阻止自动切换物品打断当前动作</li>
 *   <li>{@code handleEasyPlace}      — 按键事件，返回 false 放行原版右键处理</li>
 *   <li>{@code handlePlacementRestriction} — 右键限制检查，返回 false 不阻止右键</li>
 * </ol>
 * 另有 {@code doEasyPlaceAction} 的 HEAD 注入处理液体/含水方块放置，RETURN 注入
 * 在放置成功时触发挥手动画。
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
 * Additionally {@code doEasyPlaceAction} gets a HEAD injection for
 * liquid/waterlogged placement and a RETURN injection that swings the hand on
 * successful placement (1.20.X only; litematica 1.21+ provides
 * {@code easyPlaceSwingHand} natively).
 *
 * @see EasyPlacePlacementUtils
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
        if (EasyPlacePlacementUtils.shouldSkipEasyPlace(mc)) {
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
        if (EasyPlacePlacementUtils.shouldSkipEasyPlace(mc)) {
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
        if (EasyPlacePlacementUtils.shouldSkipEasyPlace(mc)) {
            cir.setReturnValue(false);
            return;
        }

        // 液体放置支持：禁止空桶回收原理图中的液体
        // Liquid placement support: prevent empty bucket from picking up schematic liquids
        if (EasyPlacePlacementUtils.shouldRestrictLiquidPickup(mc)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * 修改轻松放置保护范围的硬编码常量 / Modify the hardcoded Easy Place protection range.
     * <p>
     * Litematica 在 {@code placementRestrictionInEffect} 中硬编码了
     * {@code isPositionWithinRangeOfSchematicRegions(pos, 2)} 的 range=2。
     * 此注入将该常量替换为 {@link Configs#EASY_PLACE_PROTECTION_RANGE} 的值，
     * 允许用户自定义投影区域周围的保护格数。
     * <p>
     * Litematica hardcodes {@code range = 2} in the call to
     * {@code isPositionWithinRangeOfSchematicRegions(pos, 2)} inside
     * {@code placementRestrictionInEffect}. This injection replaces that
     * constant with the value of {@link Configs#EASY_PLACE_PROTECTION_RANGE},
     * allowing users to customize the protection range around schematic regions.
     *
     * @param original Litematica 原始的硬编码值 2 / Litematica's original hardcoded value 2
     * @return 用户配置的保护范围值 / the user-configured protection range
     */
    @ModifyConstant(method = "placementRestrictionInEffect", constant = @Constant(intValue = 2))
    private static int bettereasyplace$modifyProtectionRange(int original) {
        return Configs.EASY_PLACE_PROTECTION_RANGE.getIntegerValue();
    }

    /**
     * 注入轻松放置核心动作 / Inject into the core easy place action.
     * <p>
     * 委托 {@link EasyPlacePlacementUtils#tryHandleEasyPlacePlacement} 处理
     * 液体/含水方块的放置；未命中液体目标时不动回调，让 litematica 原始流程继续。
     * <p>
     * Delegates to {@link EasyPlacePlacementUtils#tryHandleEasyPlacePlacement}
     * to handle liquid/waterlogged placement; leaves the callback untouched
     * unless a liquid target was handled, so litematica's original flow proceeds.
     */
    @Inject(method = "doEasyPlaceAction", at = @At("HEAD"), cancellable = true)
    private static void bettereasyplace$doEasyPlaceAction(Minecraft mc, CallbackInfoReturnable<InteractionResult> cir) {
        EasyPlacePlacementUtils.tryHandleEasyPlacePlacement(mc, cir);
    }

    /**
     * 轻松放置成功后挥动手部 / Swing the player's hand on successful Easy Place.
     * <p>
     * <b>[移植自 Litematica 1.21+ 的 easyPlaceSwingHand 功能]</b>
     * 此处将其移植到 1.20.X 版本，注入点选在 {@code doEasyPlaceAction} 的
     * RETURN 处以同时覆盖 litematica 原版放置路径和本模组的液体/含水方块路径。
     * <p>
     * <b>[Backported from Litematica 1.21+ easyPlaceSwingHand feature]</b>
     * Backported here to 1.20.X, injected at RETURN of {@code doEasyPlaceAction}
     * to cover both litematica's vanilla placement path and this mod's
     * liquid/waterlogged placement path.
     * <p>
     * 在 {@code doEasyPlaceAction} 返回 {@link InteractionResult#SUCCESS} 且配置项
     * {@link Configs#EASY_PLACE_SWING_HAND} 启用时，触发一次玩家主手挥动动画。
     * 无论方块是通过 litematica 原版逻辑放置，还是通过我们的液体/含水方块
     * 逻辑放置，只要结果是 SUCCESS 就会触发。
     * <p>
     * When {@code doEasyPlaceAction} returns {@link InteractionResult#SUCCESS} and
     * {@link Configs#EASY_PLACE_SWING_HAND} is enabled, triggers a main-hand swing
     * animation.  This fires regardless of whether the block was placed via
     * litematica's vanilla logic or via our liquid/waterlogged handling.
     */
    @Inject(method = "doEasyPlaceAction", at = @At("RETURN"))
    private static void bettereasyplace$swingHandOnPlace(Minecraft mc, CallbackInfoReturnable<InteractionResult> cir) {
        if (!Configs.EASY_PLACE_SWING_HAND.getBooleanValue()) {
            return;
        }

        if (cir.getReturnValue() != InteractionResult.SUCCESS) {
            return;
        }

        LocalPlayer player = mc.player;
        if (player != null) {
            player.swing(InteractionHand.MAIN_HAND);
        }
    }
}
