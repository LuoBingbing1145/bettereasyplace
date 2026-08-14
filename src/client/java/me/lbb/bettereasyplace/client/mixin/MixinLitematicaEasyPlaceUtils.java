package me.lbb.bettereasyplace.client.mixin;

import fi.dy.masa.litematica.util.EasyPlaceUtils;
import me.lbb.bettereasyplace.client.util.EasyPlacePlacementUtils;
import me.lbb.bettereasyplace.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 注入 Litematica 的新版轻松放置流程 {@link EasyPlaceUtils}，允许在轻松放置激活时进行原版操作 /
 * Mixin into litematica's new Easy Place flow {@link EasyPlaceUtils} to allow
 * vanilla actions while Easy Place mode is active.
 * <p>
 * litematica 0.26.12 引入了由 {@code EASY_PLACE_POST_REWRITE} 配置开关的新流程，
 * 触发入口为 {@code MixinClientPlayerInteractionManager_easyPlace}（注入
 * interactBlock），核心逻辑全部位于 {@link EasyPlaceUtils}。本类是新流程的
 * 配套注入，与旧流程的 {@link MixinLitematicaWorldUtils} 一一对应：
 * <ol>
 *   <li>{@code shouldDoEasyPlaceActions} — 返回 false 使新流程跳过轻松放置处理，放行原版右键</li>
 *   <li>{@code handlePlacementRestriction} — 返回 false 不阻止右键；返回 true 禁止空桶回收液体</li>
 *   <li>{@code easyPlaceOnUseTick}       — 每 tick 执行（hold 模式），取消以阻止切换物品打断当前动作</li>
 *   <li>{@code placementRestrictionInEffect} — 保护范围常量替换（对应旧流程的 @ModifyConstant）</li>
 *   <li>{@code handleEasyPlace}          — 核心动作入口，委托共享逻辑处理液体/含水方块放置</li>
 * </ol>
 * <p>
 * litematica 0.26.12 introduced a new flow toggled by the
 * {@code EASY_PLACE_POST_REWRITE} config, triggered by
 * {@code MixinClientPlayerInteractionManager_easyPlace} (injecting
 * interactBlock) with all core logic living in {@link EasyPlaceUtils}.  This
 * class is its companion mixin, mirroring the legacy flow's
 * {@link MixinLitematicaWorldUtils}:
 * <ol>
 *   <li>{@code shouldDoEasyPlaceActions} — return false to skip Easy Place handling and let the vanilla click through</li>
 *   <li>{@code handlePlacementRestriction} — return false to allow the click; return true to block empty-bucket liquid pickup</li>
 *   <li>{@code easyPlaceOnUseTick}       — per-tick (hold mode); cancel to prevent item switching from interrupting the current action</li>
 *   <li>{@code placementRestrictionInEffect} — protection range constant replacement (mirrors the legacy @ModifyConstant)</li>
 *   <li>{@code handleEasyPlace}          — core action entry; delegates liquid/waterlogged placement to the shared logic</li>
 * </ol>
 *
 * @see EasyPlacePlacementUtils
 * @see MixinLitematicaWorldUtils
 */
@Mixin(value = EasyPlaceUtils.class, remap = false)
public abstract class MixinLitematicaEasyPlaceUtils {

    /**
     * 拦截新流程的轻松放置判定 / Intercept the new flow's Easy Place decision.
     * <p>
     * 当玩家正在执行被允许的 bypass 操作（进食、烟花、潜影盒或黑名单方块）时
     * 返回 {@code false}，使 {@code MixinClientPlayerInteractionManager_easyPlace}
     * 跳过轻松放置处理，原版右键逻辑继续执行。
     * <p>
     * Returns {@code false} while the player is performing an allowed bypass
     * action (eating, fireworks, shulker boxes, or blacklisted blocks), making
     * {@code MixinClientPlayerInteractionManager_easyPlace} skip Easy Place
     * handling so the vanilla right-click logic proceeds.
     */
    @Inject(method = "shouldDoEasyPlaceActions", at = @At("HEAD"), cancellable = true)
    private static void bettereasyplace$shouldDoEasyPlaceActions(CallbackInfoReturnable<Boolean> cir) {
        if (EasyPlacePlacementUtils.shouldSkipEasyPlace(Minecraft.getInstance())) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 拦截新流程的右键放置限制检查 / Intercept the new flow's placement restriction check.
     * <p>
     * 返回 {@code false} 表示"限制不生效"，右键操作被放行；
     * 返回 {@code true} 禁止空桶回收原理图中的液体。
     * Returning {@code false} means "restriction not in effect", letting the
     * right-click through; returning {@code true} blocks empty-bucket pickup
     * of schematic liquids.
     */
    @Inject(method = "handlePlacementRestriction", at = @At("HEAD"), cancellable = true)
    private static void bettereasyplace$handlePlacementRestriction(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();

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
     * 拦截新流程每 tick 的轻松放置执行 / Intercept the new flow's per-tick easy place execution.
     * <p>
     * 当玩家正在进食或使用烟花时，取消此 tick 的轻松放置操作，
     * 防止 {@code InventoryUtils.schematicWorldPickBlock} 切换物品打断当前动作。
     * When the player is eating or using fireworks, cancels this tick's easy place
     * action so that item switching (via {@code schematicWorldPickBlock}) does not
     * interrupt the ongoing action.
     */
    @Inject(method = "easyPlaceOnUseTick", at = @At("HEAD"), cancellable = true)
    private static void bettereasyplace$easyPlaceOnUseTick(CallbackInfo ci) {
        if (EasyPlacePlacementUtils.shouldSkipEasyPlace(Minecraft.getInstance())) {
            ci.cancel();
        }
    }

    /**
     * 修改新流程保护范围的硬编码常量 / Modify the new flow's hardcoded protection range.
     * <p>
     * 与旧流程 {@link MixinLitematicaWorldUtils} 中的同名注入一致：新流程的
     * {@code placementRestrictionInEffect} 同样硬编码了
     * {@code isPositionWithinRangeOfSchematicRegions(pos, 2)} 的 range=2，
     * 此注入替换为 {@link Configs#EASY_PLACE_PROTECTION_RANGE} 的值。
     * <p>
     * Mirrors the same-name injection in the legacy flow
     * {@link MixinLitematicaWorldUtils}: the new flow's
     * {@code placementRestrictionInEffect} also hardcodes {@code range = 2}
     * in {@code isPositionWithinRangeOfSchematicRegions(pos, 2)}, which this
     * injection replaces with {@link Configs#EASY_PLACE_PROTECTION_RANGE}.
     *
     * @param original Litematica 原始的硬编码值 2 / Litematica's original hardcoded value 2
     * @return 用户配置的保护范围值 / the user-configured protection range
     */
    @ModifyConstant(method = "placementRestrictionInEffect", constant = @Constant(intValue = 2))
    private static int bettereasyplace$modifyProtectionRange(int original) {
        return Configs.EASY_PLACE_PROTECTION_RANGE.getIntegerValue();
    }

    /**
     * 注入新流程的轻松放置核心动作 / Inject into the new flow's core easy place action.
     * <p>
     * 该私有方法同时覆盖点击流程（{@code handleEasyPlaceWithMessage}）和
     * hold 模式（{@code easyPlaceOnUseTick}）两条路径。委托
     * {@link EasyPlacePlacementUtils#tryHandleEasyPlacePlacement} 处理液体/
     * 含水方块的放置；未命中液体目标时不动回调，让 litematica 原始流程继续。
     * 模组执行了放置时返回 {@code SUCCESS}（≠PASS），使
     * {@code handleEasyPlaceWithMessage} 消费事件，防止空桶把刚放置的液体回收。
     * <p>
     * This private method covers both the click path
     * ({@code handleEasyPlaceWithMessage}) and hold mode
     * ({@code easyPlaceOnUseTick}).  Delegates to
     * {@link EasyPlacePlacementUtils#tryHandleEasyPlacePlacement} for
     * liquid/waterlogged placement; leaves the callback untouched unless a
     * liquid target was handled.  Returns {@code SUCCESS} (≠PASS) after a
     * placement so {@code handleEasyPlaceWithMessage} consumes the event,
     * preventing the empty bucket from picking the liquid back up.
     */
    @Inject(method = "handleEasyPlace", at = @At("HEAD"), cancellable = true)
    private static void bettereasyplace$handleEasyPlace(CallbackInfoReturnable<InteractionResult> cir) {
        EasyPlacePlacementUtils.tryHandleEasyPlacePlacement(Minecraft.getInstance(), cir);
    }
}
