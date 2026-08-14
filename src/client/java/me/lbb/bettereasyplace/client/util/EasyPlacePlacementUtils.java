package me.lbb.bettereasyplace.client.util;

import fi.dy.masa.litematica.materials.MaterialCache;
import fi.dy.masa.litematica.util.*;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import me.lbb.bettereasyplace.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 轻松放置注入的共享逻辑 / Shared logic for the Easy Place injections.
 * <p>
 * 从 {@code MixinLitematicaWorldUtils} 抽出的公共逻辑，被 litematica 的两套
 * 轻松放置流程共用：
 * <ul>
 *   <li><b>旧流程</b>（{@code EASY_PLACE_POST_REWRITE = false}）— 由
 *       {@code MixinLitematicaWorldUtils} 在 {@link WorldUtils#doEasyPlaceAction}
 *       中调用 {@link #tryHandleEasyPlacePlacement}</li>
 *   <li><b>新流程</b>（{@code EASY_PLACE_POST_REWRITE = true}）— 由
 *       {@code MixinLitematicaEasyPlaceUtils} 在 {@link EasyPlaceUtils} 的
 *       私有方法 {@code handleEasyPlace} 中调用同一入口</li>
 * </ul>
 * <p>
 * 涉及 litematica 已标记废弃的协议编码方法时一律通过反射调用（不产生 javac
 * 废弃警告，且行为与直接调用一致）；缓存/计时器反射助手优先尝试新流程的
 * {@link EasyPlaceUtils}（protected），失败时回退到旧流程的 {@link WorldUtils}。
 * <p>
 * Logic extracted from {@code MixinLitematicaWorldUtils}, shared by both of
 * litematica's Easy Place flows:
 * <ul>
 *   <li><b>Old flow</b> ({@code EASY_PLACE_POST_REWRITE = false}) — invoked from
 *       {@link WorldUtils#doEasyPlaceAction} via
 *       {@code MixinLitematicaWorldUtils}</li>
 *   <li><b>New flow</b> ({@code EASY_PLACE_POST_REWRITE = true}) — invoked from
 *       the private {@code handleEasyPlace} method of {@link EasyPlaceUtils} via
 *       {@code MixinLitematicaEasyPlaceUtils}</li>
 * </ul>
 * <p>
 * Deprecated protocol-encoding methods of litematica are invoked via reflection
 * (which produces no javac deprecation warnings and behaves identically to a
 * direct call). The cache/timer reflection helpers try the new flow's
 * {@link EasyPlaceUtils} (protected) first, then fall back to the old flow's
 * {@link WorldUtils}.
 */
public final class EasyPlacePlacementUtils {

    private EasyPlacePlacementUtils() {
    }

    /**
     * 尝试处理液体/含水方块的轻松放置 / Try to handle liquid/waterlogged Easy Place.
     * <p>
     * 在 litematica 的轻松放置核心动作执行前检查原理图目标位置是否为液体源方块。
     * 如果是，则用含流体的射线追踪定位液体，自动切换对应桶并精确放置。
     * 放置完成后空桶不会被允许回收液体，保护原理图中的液体不被意外移除。
     * <p>
     * 只有真正处理了液体/含水目标时才会设置返回值；其他情况不动回调，
     * 让 litematica 的原始流程继续执行。
     * <p>
     * Check before litematica's core Easy Place action whether the schematic
     * target is a liquid source block.  If so, trace with fluid targeting,
     * auto-pick the correct bucket, and place the liquid precisely.  After
     * placement the empty bucket is prevented from picking up the liquid,
     * protecting schematic liquids from accidental removal.
     * <p>
     * The callback is only touched when a liquid/waterlogged target was actually
     * handled; otherwise litematica's original flow proceeds untouched.
     *
     * @param mc  Minecraft 客户端实例 / the Minecraft client instance
     * @param cir 注入回调 / the injection callback
     */
    public static void tryHandleEasyPlacePlacement(@NotNull Minecraft mc, CallbackInfoReturnable<InteractionResult> cir) {
        if (!Configs.ALLOW_LIQUID_PLACEMENT.getBooleanValue()) {
            return;
        }

        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        WorldSchematic schematicWorld = SchematicWorldHandler.getSchematicWorld();
        if (schematicWorld == null) {
            return;
        }

        // 射线追踪时包含流体，使原理图中的液体可见
        // Trace with fluid targeting so liquids in the schematic are visible
        // respectRenderRange = true: 尊重渲染层范围，单层模式下不会误放置其他层的水
        // respectRenderRange = true: respect render layer range to avoid placing
        // water in non-rendered layers when using single-layer mode
        double range = fi.dy.masa.litematica.config.Configs.Generic.EASY_PLACE_VANILLA_REACH.getBooleanValue() ? 4.5 : 6.0;
        BlockHitResult liquidTrace = RayTraceUtils.traceToSchematicWorld(player, range, true, true);
        if (liquidTrace == null || liquidTrace.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos pos = liquidTrace.getBlockPos();
        BlockState stateSchematic = schematicWorld.getBlockState(pos);

        // 提前获取客户端状态 / Get client state early
        if (mc.level == null) {
            return;
        }
        BlockState stateClient = mc.level.getBlockState(pos);

        // ================================================================
        // 含水方块处理（独立配置项）/ Waterlogged block handling (separate config)
        // 原理图中有含水方块时，需要先放水再放方块（或先放方块再含水）
        // When the schematic has a waterlogged block, we need to place
        // water first, then the block (or waterlog an existing block)
        // ================================================================
        if (Configs.ALLOW_WATERLOGGED_PLACEMENT.getBooleanValue() && isWaterlogged(stateSchematic)) {
            handleWaterloggedPlacement(mc, player, schematicWorld, pos,
                    stateSchematic, stateClient, cir, liquidTrace);
            return;
        }

        // 仅处理液体源方块 / Only handle liquid source blocks
        if (!isLiquidSource(stateSchematic)) {
            return;
        }

        handleLiquidSourcePlacement(mc, player, schematicWorld, pos, stateSchematic, stateClient, cir);
    }

    /**
     * 处理液体源方块的放置 / Handle liquid source block placement.
     * <p>
     * 检查是否已放置相同液体 → 检查位置缓存 → 获取水桶/岩浆桶 →
     * 自动切换 → 在原理图坐标精确放置并发包。
     * <p>
     * Check for already-placed liquid → position cache → get the bucket →
     * auto-pick → place precisely at the schematic position and send the packet.
     */
    private static void handleLiquidSourcePlacement(@NotNull Minecraft mc, @NotNull LocalPlayer player,
                                                    @NotNull WorldSchematic schematicWorld, @NotNull BlockPos pos,
                                                    @NotNull BlockState stateSchematic, @NotNull BlockState stateClient,
                                                    CallbackInfoReturnable<InteractionResult> cir) {

        // mc.level 已在调用入口处判空，此处防御性检查满足 IDE 空安全分析
        // mc.level is null-checked at the entry point; this defensive guard
        // satisfies IDE null-safety analysis for the argument below
        if (mc.level == null) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        // 检查是否已在正确位置放置了相同液体 / Check if the same liquid is already placed
        if (stateSchematic.getBlock() == stateClient.getBlock() && isLiquidSource(stateClient)) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        // 检查位置缓存（防止短时间内重复放置）/ Check position cache
        if (invokeEasyPlaceIsPositionCached(pos)) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        // 获取放置液体所需的物品（水桶/岩浆桶）/ Get required build item (bucket)
        ItemStack requiredStack = MaterialCache.getInstance().getRequiredBuildItemForState(stateSchematic);
        if (requiredStack.isEmpty()) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        // 自动切换到对应的桶（先切换，后验证）/ Auto-pick the bucket first, then verify
        InventoryUtils.schematicWorldPickBlock(requiredStack, pos, schematicWorld, mc);

        // 检查切换后玩家手中是否持有对应的桶 / Verify the player now holds the matching bucket
        InteractionHand hand = EntityUtils.getUsedHandForItem(player, requiredStack);
        if (hand == null) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        // ================================================================
        // 液体放置：BucketItem 没有重写 useOn，所以 useItemOn 对水桶无效。
        // 使用 emptyContents(null) 在客户端精确放置 + useItem 发包到服务端。
        // 注意：useItem 内部会做独立射线追踪，服务端放置位置可能与客户端
        // 略有偏差，但水桶必须走此路径才能正确触发物品交换（桶→空桶）。
        //
        // Liquid placement: BucketItem does NOT override useOn, so useItemOn
        // is a no-op for buckets. Use emptyContents(null) for precise client
        // placement + useItem for server packet & item swap.
        // Note: useItem does its own ray tracing internally, so the server
        // position may differ slightly from the client position.
        // ================================================================

        // 拿到实际的桶物品 / Get the actual bucket item in the player's hand
        ItemStack heldStack = player.getItemInHand(hand);
        if (!(heldStack.getItem() instanceof BucketItem bucketItem)) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        // 客户端：直接在原理图坐标放置液体
        // Client: place liquid directly at schematic position
        bucketItem.emptyContents(player, mc.level, pos, null);

        // 服务端：发包并触发客户端物品交换（桶→空桶）
        // Server: send packet and trigger client item swap (bucket → empty bucket)
        if (mc.gameMode != null) {
            mc.gameMode.useItem(player, hand);
        }

        // 更新放置计时，防止下一tick立即再次处理 / Update placement timer
        invokeSetEasyPlaceLastPickBlockTime();

        cir.setReturnValue(InteractionResult.SUCCESS);
    }

    /**
     * 处理含水方块的放置 / Handle waterlogged block placement.
     * <p>
     * 含水方块需要两个步骤：先放水，再放方块（方块放入水中自动含水）。
     * 如果方块已放置但未含水，则用水桶右键方块使其含水。
     * 如果水已放置，让标准轻松放置流程处理方块放置。
     * <p>
     * Waterlogged blocks require two steps: place water first, then place the
     * block (blocks placed in water become waterlogged automatically).
     * If the block is already placed but not waterlogged, waterlog it with a bucket.
     * If water is already placed, let the standard easy place flow handle the block.
     */
    private static void handleWaterloggedPlacement(@NotNull Minecraft mc, @NotNull LocalPlayer player,
                                                   @NotNull WorldSchematic schematicWorld, @NotNull BlockPos pos, @NotNull BlockState stateSchematic,
                                                   @NotNull BlockState stateClient, CallbackInfoReturnable<InteractionResult> cir,
                                                   @NotNull BlockHitResult liquidTrace) {

        // 已经完全正确放置（含水方块已就位）/ Already correctly placed
        if (stateSchematic.getBlock() == stateClient.getBlock() && isWaterlogged(stateClient)) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        // 方块已放置但未含水 → 用水桶右键使其含水
        // Block placed but not waterlogged → waterlog it with a bucket
        if (stateClient.getBlock() == stateSchematic.getBlock() && !isWaterlogged(stateClient)) {
            waterlogExistingBlock(mc, player, schematicWorld, pos, cir);
            return;
        }

        // 任何液体（水源或流动水）→ 获取原理图要求的方块放置
        // 不能回退到原始 doEasyPlaceAction，因为原始方法使用不含流体的
        // 射线追踪，射线会穿过水打到水面后的方块上，导致放置位置错误。
        // Any liquid (source or flowing) → place the required block.
        // We cannot fall back to the original doEasyPlaceAction because it
        // uses ray tracing WITHOUT fluids, so the ray passes through water
        // and hits the block behind, causing incorrect placement position.
        if (stateClient.getBlock() instanceof LiquidBlock) {
            if (invokeEasyPlaceIsPositionCached(pos)) {
                cir.setReturnValue(InteractionResult.FAIL);
                return;
            }

            ItemStack requiredStack = MaterialCache.getInstance().getRequiredBuildItemForState(stateSchematic);
            if (requiredStack.isEmpty()) {
                cir.setReturnValue(InteractionResult.FAIL);
                return;
            }

            // 切换到原理图要求的方块 / Switch to the required block
            InventoryUtils.schematicWorldPickBlock(requiredStack, pos, schematicWorld, mc);

            // 验证切换成功 / Verify the switch succeeded
            InteractionHand hand = EntityUtils.getUsedHandForItem(player, requiredStack);
            if (hand == null) {
                cir.setReturnValue(InteractionResult.FAIL);
                return;
            }

            // 1) applyPlacementFacing 修正 Direction（HALF: TOP→DOWN, BOTTOM→UP）
            //    传入 AIR 而非水，因为 applyPlacementFacing 预期客户端是空气或同类方块
            // 2) 协议方法编码 FACING 等属性到 hitVec（如 V3 编码到 x 坐标）
            //    litematica 原版 doEasyPlaceAction 也是这样两步走的
            // 1) applyPlacementFacing corrects Direction (HALF: TOP→DOWN, BOTTOM→UP)
            //    Pass AIR instead of water since the method expects air or same-type block
            // 2) Protocol method encodes FACING etc. into hitVec (e.g. V3 encodes into x)
            //    Same two-step approach as litematica's original doEasyPlaceAction
            Direction correctedDir = invokeApplyPlacementFacing(
                    stateSchematic, liquidTrace.getDirection(),
                    Blocks.AIR.defaultBlockState());

            Vec3 hitPos = liquidTrace.getLocation();
            EasyPlaceProtocol protocol = PlacementHandler.getEffectiveProtocolVersion();
            if (protocol == EasyPlaceProtocol.V3) {
                hitPos = invokeApplyPlacementProtocolV3(pos, stateSchematic, hitPos);
            } else if (protocol == EasyPlaceProtocol.V2) {
                hitPos = invokeApplyCarpetProtocolHitVec(pos, stateSchematic, hitPos);
            } else if (protocol == EasyPlaceProtocol.SLAB_ONLY) {
                hitPos = invokeApplyBlockSlabProtocol(pos, stateSchematic, hitPos);
            }

            BlockHitResult placementHit = new BlockHitResult(
                    hitPos, correctedDir, liquidTrace.getBlockPos(), liquidTrace.isInside());
            if (mc.gameMode != null) {
                mc.gameMode.useItemOn(player, hand, placementHit);
            }

            invokeSetEasyPlaceLastPickBlockTime();
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }

        // 空位 → 先放水；液体已由上方分支处理；其他方块 → FAIL
        // Air → place water first; liquid handled above; other blocks → FAIL
        if (!stateClient.isAir() && !(stateClient.getBlock() instanceof LiquidBlock)) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        placeWaterForWaterlogged(mc, player, schematicWorld, pos, cir);
    }

    /**
     * 在含水方块位置先放置水 / Place water first at the waterlogged block position.
     * <p>
     * 切换到水桶，直接在原理图坐标放置水源。
     * 下一 tick 标准轻松放置流程会将方块放入水中自动含水。
     * <p>
     * Switch to water bucket and place a water source at the schematic position.
     * On the next tick the standard easy place flow will place the block in water.
     */
    private static void placeWaterForWaterlogged(@NotNull Minecraft mc, @NotNull LocalPlayer player,
                                                 @NotNull WorldSchematic schematicWorld, @NotNull BlockPos pos,
                                                 CallbackInfoReturnable<InteractionResult> cir) {

        // mc.level 已在调用入口处判空，此处防御性检查满足 IDE 空安全分析
        // mc.level is null-checked at the entry point; this defensive guard
        // satisfies IDE null-safety analysis for the argument below
        if (mc.level == null) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        if (invokeEasyPlaceIsPositionCached(pos)) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        ItemStack waterBucket = new ItemStack(Items.WATER_BUCKET);

        // 切换到水桶 / Switch to water bucket
        InventoryUtils.schematicWorldPickBlock(waterBucket, pos, schematicWorld, mc);

        // 验证切换成功 / Verify the switch
        InteractionHand hand = EntityUtils.getUsedHandForItem(player, waterBucket);
        if (hand == null) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        // 客户端直接在原理图坐标放水，服务端通过 useItem 发包
        // BucketItem 没有 useOn，所以必须走 useItem 路径
        // Client: place water directly at schematic position
        // Server: send packet via useItem (BucketItem has no useOn)
        ItemStack heldStack = player.getItemInHand(hand);
        if (heldStack.getItem() instanceof BucketItem bucketItem) {
            bucketItem.emptyContents(player, mc.level, pos, null);
        }

        if (mc.gameMode != null) {
            mc.gameMode.useItem(player, hand);
        }

        invokeSetEasyPlaceLastPickBlockTime();
        cir.setReturnValue(InteractionResult.SUCCESS);
    }

    /**
     * 用水桶右键已放置的方块使其含水 / Waterlog an already-placed block with a bucket.
     * <p>
     * 方块已被标准轻松放置放到原理图位置，但尚未含水。
     * 切换到水桶并用 useItem 触发原版含水逻辑。
     * <p>
     * The block was already placed by standard easy place but is not waterlogged.
     * Switch to water bucket and use useItem to trigger vanilla waterlogging.
     */
    private static void waterlogExistingBlock(@NotNull Minecraft mc, @NotNull LocalPlayer player,
                                              @NotNull WorldSchematic schematicWorld, @NotNull BlockPos pos,
                                              CallbackInfoReturnable<InteractionResult> cir) {

        if (invokeEasyPlaceIsPositionCached(pos)) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        ItemStack waterBucket = new ItemStack(Items.WATER_BUCKET);

        // 切换到水桶 / Switch to water bucket
        InventoryUtils.schematicWorldPickBlock(waterBucket, pos, schematicWorld, mc);

        // 验证切换成功 / Verify the switch
        InteractionHand hand = EntityUtils.getUsedHandForItem(player, waterBucket);
        if (hand == null) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        // 用原版 useItem 触发含水（BucketItem 检测到 LiquidBlockContainer 会在原位置含水）
        // Use vanilla useItem to trigger waterlogging (BucketItem detects
        // LiquidBlockContainer and waterlogs at the clicked position)
        if (mc.gameMode != null) {
            mc.gameMode.useItem(player, hand);
        }

        invokeSetEasyPlaceLastPickBlockTime();
        cir.setReturnValue(InteractionResult.SUCCESS);
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
    public static boolean shouldSkipEasyPlace(@NotNull Minecraft mc) {
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

    /**
     * 检查是否应禁止玩家用空桶回收原理图中的液体 /
     * Check if the player should be prevented from picking up a schematic liquid
     * with an empty bucket.
     * <p>
     * 同时满足以下条件时返回 {@code true}（即限制该操作）：
     * <ol>
     *   <li>配置项 {@code ALLOW_LIQUID_PLACEMENT} 已启用</li>
     *   <li>玩家主手或副手持有空桶</li>
     *   <li>玩家视线指向原理图中存在液体源方块的位置</li>
     * </ol>
     * 这可以保护原理图中的液体源不被意外回收。
     * <p>
     * Returns {@code true} (restrict the action) when ALL of the following hold:
     * <ol>
     *   <li>{@code ALLOW_LIQUID_PLACEMENT} config is enabled</li>
     *   <li>The player holds an empty bucket in main or off hand</li>
     *   <li>The player is looking at a position where the schematic has a liquid source</li>
     * </ol>
     * This protects schematic liquid sources from accidental removal.
     *
     * @param mc Minecraft 客户端实例 / the Minecraft client instance
     * @return true 如果应禁止空桶回收液体 / true if empty bucket pickup should be restricted
     */
    public static boolean shouldRestrictLiquidPickup(@NotNull Minecraft mc) {
        if (!Configs.ALLOW_LIQUID_PLACEMENT.getBooleanValue()) {
            return false;
        }

        LocalPlayer player = mc.player;
        if (player == null) {
            return false;
        }

        // 检查是否持有空桶 / Check if holding empty bucket
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        if (!mainHand.is(Items.BUCKET) && !offHand.is(Items.BUCKET)) {
            return false;
        }

        // 需要原理图世界 / Need schematic world
        WorldSchematic schematicWorld = SchematicWorldHandler.getSchematicWorld();
        if (schematicWorld == null) {
            return false;
        }

        // 检查视线目标 / Check look target
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return false;
        }

        BlockHitResult blockHit = (BlockHitResult) mc.hitResult;
        BlockPos pos = blockHit.getBlockPos();

        // 检查目标位置的原理图方块是否为液体源或含水方块
        // Check if schematic has liquid source or waterlogged block at target
        BlockState schematicState = schematicWorld.getBlockState(pos);
        if (isLiquidSource(schematicState)) {
            return true;
        }
        return Configs.ALLOW_WATERLOGGED_PLACEMENT.getBooleanValue() && isWaterlogged(schematicState);
    }

    /**
     * 检查方块状态是否为含水方块 / Check if a block state is waterlogged.
     * <p>
     * 含水方块是同时包含水和固体方块的方块（如含水台阶、含水楼梯等）。
     * 在 Minecraft 中通过 {@code WATERLOGGED} 属性标记。
     * <p>
     * A waterlogged block contains both water and a solid block (e.g. waterlogged
     * slabs, stairs, etc.).  Marked by the {@code WATERLOGGED} property in Minecraft.
     *
     * @param state 待检查的方块状态 / the block state to check
     * @return true 如果是含水方块 / true if the block is waterlogged
     */
    private static boolean isWaterlogged(@NotNull BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED)
                && state.getValue(BlockStateProperties.WATERLOGGED);
    }

    /**
     * 检查方块状态是否为液体源方块 / Check if a block state is a liquid source block.
     * <p>
     * 仅当方块是 {@link LiquidBlock}（水或岩浆）且流体等级为 0（源头）时返回 {@code true}。
     * 流动水/流动岩浆返回 {@code false}。
     * <p>
     * Returns {@code true} only when the block is a {@link LiquidBlock} (water or lava)
     * and the fluid level is 0 (source).  Flowing water/lava returns {@code false}.
     *
     * @param state 待检查的方块状态 / the block state to check
     * @return true 如果是液体源方块 / true if it is a liquid source block
     */
    private static boolean isLiquidSource(@NotNull BlockState state) {
        Block block = state.getBlock();
        if (!(block instanceof LiquidBlock)) {
            return false;
        }
        // LiquidBlock.LEVEL == 0 表示源头 / LEVEL == 0 means source
        return state.getValue(LiquidBlock.LEVEL) == 0;
    }

    // ================================================================
    // 反射助手 / Reflection helpers
    // litematica 0.26.12 将放置协议相关方法标记为 @Deprecated，其中
    // setEasyPlaceLastPickBlockTime 还带有 forRemoval=true。为了在编译期
    // 不产生废弃警告，且兼容新旧两套流程，这里统一走反射调用。
    // litematica 0.26.12 marks the placement protocol methods as @Deprecated
    // (setEasyPlaceLastPickBlockTime even with forRemoval=true). To avoid
    // compile-time deprecation warnings and stay compatible with both flows,
    // all of them are invoked via reflection here.
    // ================================================================

    /**
     * 通过反射调用 WorldUtils 的私有方法 applyPlacementFacing /
     * Call WorldUtils' private method applyPlacementFacing via reflection.
     * <p>
     * 该方法是 litematica 用于根据原理图方块状态修正放置方向的逻辑。
     * 例：原理图上半砖 → 修正 Direction 为 DOWN，使 SlabBlock.getStateForPlacement 返回 TOP。
     * This method contains litematica's logic for correcting placement direction
     * based on the schematic block state (e.g. top-slab → forces Direction.DOWN).
     */
    private static Direction invokeApplyPlacementFacing(BlockState schematicState, Direction originalDir, BlockState clientState) {
        try {
            java.lang.reflect.Method method = WorldUtils.class.getDeclaredMethod(
                    "applyPlacementFacing", BlockState.class, Direction.class, BlockState.class);
            method.setAccessible(true);
            return (Direction) method.invoke(null, schematicState, originalDir, clientState);
        } catch (Exception e) {
            return originalDir;
        }
    }

    /**
     * 通过反射调用 WorldUtils 的私有方法 applyBlockSlabProtocol /
     * Call WorldUtils' private method applyBlockSlabProtocol via reflection.
     */
    private static Vec3 invokeApplyBlockSlabProtocol(BlockPos pos, BlockState state, Vec3 hitPos) {
        try {
            java.lang.reflect.Method method = WorldUtils.class.getDeclaredMethod(
                    "applyBlockSlabProtocol", BlockPos.class, BlockState.class, Vec3.class);
            method.setAccessible(true);
            return (Vec3) method.invoke(null, pos, state, hitPos);
        } catch (Exception e) {
            return hitPos;
        }
    }

    /**
     * 通过反射调用 WorldUtils 的废弃方法 applyPlacementProtocolV3 /
     * Call WorldUtils' deprecated method applyPlacementProtocolV3 via reflection.
     */
    private static Vec3 invokeApplyPlacementProtocolV3(BlockPos pos, BlockState state, Vec3 hitPos) {
        try {
            java.lang.reflect.Method method = WorldUtils.class.getDeclaredMethod(
                    "applyPlacementProtocolV3", BlockPos.class, BlockState.class, Vec3.class);
            method.setAccessible(true);
            return (Vec3) method.invoke(null, pos, state, hitPos);
        } catch (Exception e) {
            return hitPos;
        }
    }

    /**
     * 通过反射调用 WorldUtils 的废弃方法 applyCarpetProtocolHitVec /
     * Call WorldUtils' deprecated method applyCarpetProtocolHitVec via reflection.
     */
    private static Vec3 invokeApplyCarpetProtocolHitVec(BlockPos pos, BlockState state, Vec3 hitPos) {
        try {
            java.lang.reflect.Method method = WorldUtils.class.getDeclaredMethod(
                    "applyCarpetProtocolHitVec", BlockPos.class, BlockState.class, Vec3.class);
            method.setAccessible(true);
            return (Vec3) method.invoke(null, pos, state, hitPos);
        } catch (Exception e) {
            return hitPos;
        }
    }

    /**
     * 通过反射调用位置缓存检查 / Call the position cache check via reflection.
     * <p>
     * 优先尝试新流程的 {@link EasyPlaceUtils#easyPlaceIsPositionCached}
     * （protected），失败时回退到旧流程的 {@code WorldUtils}（private）。
     * 两套流程在 litematica 0.26.12 中共享同一个位置缓存。
     * <p>
     * Try the new flow's {@link EasyPlaceUtils#easyPlaceIsPositionCached}
     * (protected) first, then fall back to the old flow's {@code WorldUtils}
     * (private).  Both flows share the same position cache in litematica 0.26.12.
     *
     * @param pos 要检查的位置 / the position to check
     * @return true 如果位置已缓存 / true if the position is cached
     */
    private static boolean invokeEasyPlaceIsPositionCached(@NotNull BlockPos pos) {
        try {
            java.lang.reflect.Method method = EasyPlaceUtils.class.getDeclaredMethod(
                    "easyPlaceIsPositionCached", BlockPos.class);
            method.setAccessible(true);
            return (boolean) method.invoke(null, pos);
        } catch (NoSuchMethodException e) {
            // 回退到旧流程的 WorldUtils / Fall back to the old flow's WorldUtils
        } catch (Exception e) {
            return false;
        }

        try {
            java.lang.reflect.Method method = WorldUtils.class.getDeclaredMethod(
                    "easyPlaceIsPositionCached", BlockPos.class);
            method.setAccessible(true);
            return (boolean) method.invoke(null, pos);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 通过反射更新放置计时 / Update the placement timer via reflection.
     * <p>
     * 优先尝试新流程的 {@link EasyPlaceUtils#setEasyPlaceLastPickBlockTime}
     * （protected，非废弃），失败时回退到旧流程的 {@code WorldUtils}
     * （已标记 forRemoval=true）。
     * <p>
     * Try the new flow's {@link EasyPlaceUtils#setEasyPlaceLastPickBlockTime}
     * (protected, not deprecated) first, then fall back to the old flow's
     * {@code WorldUtils} (marked forRemoval=true).
     */
    private static void invokeSetEasyPlaceLastPickBlockTime() {
        try {
            java.lang.reflect.Method method = EasyPlaceUtils.class.getDeclaredMethod(
                    "setEasyPlaceLastPickBlockTime");
            method.setAccessible(true);
            method.invoke(null);
        } catch (NoSuchMethodException e) {
            // 回退到旧流程的 WorldUtils / Fall back to the old flow's WorldUtils
            try {
                java.lang.reflect.Method method = WorldUtils.class.getDeclaredMethod(
                        "setEasyPlaceLastPickBlockTime");
                method.setAccessible(true);
                method.invoke(null);
            } catch (Exception ignored) {
                // Ignore - placement timer is a best-effort optimization
            }
        } catch (Exception e) {
            // Ignore - placement timer is a best-effort optimization
        }
    }
}
