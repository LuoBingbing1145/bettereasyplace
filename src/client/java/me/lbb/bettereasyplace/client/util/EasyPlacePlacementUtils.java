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
 * 从 {@code MixinLitematicaWorldUtils} 抽出的公共逻辑，供各注入点复用：
 * 液体/含水方块的精确放置、进食/烟花/潜影盒/黑名单 bypass 判定、
 * 空桶回收原理图液体的限制检查，以及放置协议相关的反射调用。
 * <p>
 * Shared logic extracted from {@code MixinLitematicaWorldUtils}, reused by all
 * injection points: precise liquid/waterlogged placement, eating/firework/
 * shulker box/blacklist bypass checks, empty-bucket pickup restriction, and
 * reflection calls for the placement protocol.
 * <p>
 * litematica 的放置协议方法（{@code applyPlacementProtocolV3}、
 * {@code applyCarpetProtocolHitVec}、{@code easyPlaceIsPositionCached}、
 * {@code setEasyPlaceLastPickBlockTime}）为 public 直接调用；
 * 仅 {@code applyPlacementFacing} 与 {@code applyBlockSlabProtocol} 是
 * private，通过反射调用。
 * <p>
 * litematica's placement protocol methods ({@code applyPlacementProtocolV3},
 * {@code applyCarpetProtocolHitVec}, {@code easyPlaceIsPositionCached},
 * {@code setEasyPlaceLastPickBlockTime}) are public and called directly; only
 * {@code applyPlacementFacing} and {@code applyBlockSlabProtocol} are private
 * and invoked via reflection.
 * <p>
 * 气泡柱（{@link BubbleColumnBlock}）没有对应的可拾取物品，其放置方式与水源
 * 相同（放置水源后由下方灵魂沙/岩浆块生成气泡柱），因此本工具将其视作液体源
 * 处理：自动切水桶放置，且客户端该位置已有水源即视为已放置（防止每 tick 重复
 * 放水导致水扩散）。
 * <p>
 * A bubble column ({@link BubbleColumnBlock}) has no pickable item and is
 * placed exactly like a water source (placing water on top of soul sand/magma
 * generates the bubbles), so it is treated as a liquid source here: the water
 * bucket is picked automatically, and an existing client-side water source at
 * the position counts as already placed (preventing per-tick re-placement that
 * would spread water outward).
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
     * Check before litematica's core Easy Place action whether the schematic
     * target is a liquid source block.  If so, trace with fluid targeting,
     * auto-pick the correct bucket, and place the liquid precisely.  After
     * placement the empty bucket is prevented from picking up the liquid,
     * protecting schematic liquids from accidental removal.
     * <p>
     * 只有真正处理了液体/含水目标时才会设置返回值；其他情况不动回调，
     * 让 litematica 的原始流程继续执行。
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

        // 仅处理液体源方块（含气泡柱）/ Only handle liquid source blocks (incl. bubble columns)
        if (!isLiquidSource(stateSchematic)) {
            return;
        }

        handleLiquidSourcePlacement(mc, player, schematicWorld, pos, stateSchematic, stateClient, cir);
    }

    /**
     * 处理液体源方块的放置 / Handle liquid source block placement.
     * <p>
     * 检查是否已放置相同液体（气泡柱特例见 {@link #isLiquidPlacementDone}）→
     * 检查位置缓存 → 获取水桶/岩浆桶（气泡柱无物品映射，显式用水桶）→
     * 自动切换 → 在原理图坐标精确放置并发包。
     * <p>
     * Check for already-placed liquid (bubble column special case in
     * {@link #isLiquidPlacementDone}) → position cache → get the bucket
     * (bubble columns have no item mapping, use the water bucket explicitly) →
     * auto-pick → place precisely at the schematic position and send the packet.
     */
    private static void handleLiquidSourcePlacement(@NotNull Minecraft mc, @NotNull LocalPlayer player,
                                                    @NotNull WorldSchematic schematicWorld, @NotNull BlockPos pos,
                                                    @NotNull BlockState stateSchematic, @NotNull BlockState stateClient,
                                                    CallbackInfoReturnable<InteractionResult> cir) {

        // 检查是否已在正确位置放置了相同液体（含气泡柱特例）
        // Check if the same liquid is already placed (incl. bubble column special case)
        if (isLiquidPlacementDone(stateSchematic, stateClient)) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        // 检查位置缓存（防止短时间内重复放置）/ Check position cache
        if (WorldUtils.easyPlaceIsPositionCached(pos)) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }

        // 获取放置液体所需的物品（水桶/岩浆桶）/ Get required build item (bucket)
        // 气泡柱（bubble_column）没有对应的物品：MaterialCache 走 getCloneItemStack
        // 回退路径时会得到空栈（气泡柱不是可拾取物品）。但气泡柱的放置方式与水源
        // 完全相同（放水后由下方灵魂沙/岩浆块生成气泡），因此显式使用水桶。
        // A bubble column has no item mapping: MaterialCache's getCloneItemStack
        // fallback yields an empty stack (bubble columns are not pickable items).
        // Its placement is identical to a water source (placing water on top of
        // soul sand/magma generates the bubbles), so use the water bucket explicitly.
        ItemStack requiredStack = isBubbleColumn(stateSchematic)
                ? new ItemStack(Items.WATER_BUCKET)
                : MaterialCache.getInstance().getRequiredBuildItemForState(stateSchematic);
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
        WorldUtils.setEasyPlaceLastPickBlockTime();

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
            if (WorldUtils.easyPlaceIsPositionCached(pos)) {
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
                hitPos = WorldUtils.applyPlacementProtocolV3(pos, stateSchematic, hitPos);
            } else if (protocol == EasyPlaceProtocol.V2) {
                hitPos = WorldUtils.applyCarpetProtocolHitVec(pos, stateSchematic, hitPos);
            } else if (protocol == EasyPlaceProtocol.SLAB_ONLY) {
                hitPos = invokeApplyBlockSlabProtocol(pos, stateSchematic, hitPos);
            }

            BlockHitResult placementHit = new BlockHitResult(
                    hitPos, correctedDir, liquidTrace.getBlockPos(), liquidTrace.isInside());
            if (mc.gameMode != null) {
                mc.gameMode.useItemOn(player, hand, placementHit);
            }

            WorldUtils.setEasyPlaceLastPickBlockTime();
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

        if (WorldUtils.easyPlaceIsPositionCached(pos)) {
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

        WorldUtils.setEasyPlaceLastPickBlockTime();
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

        if (WorldUtils.easyPlaceIsPositionCached(pos)) {
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

        WorldUtils.setEasyPlaceLastPickBlockTime();
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

        // 食物判定走 Item#isEdible：主手或副手任一物品可食用即放行
        // Food check via Item#isEdible: pass if either hand holds an edible item
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
     *   <li>玩家视线指向原理图中存在液体源方块（含气泡柱）的位置</li>
     * </ol>
     * 这可以保护原理图中的液体源不被意外回收。
     * <p>
     * Returns {@code true} (restrict the action) when ALL of the following hold:
     * <ol>
     *   <li>{@code ALLOW_LIQUID_PLACEMENT} config is enabled</li>
     *   <li>The player holds an empty bucket in main or off hand</li>
     *   <li>The player is looking at a position where the schematic has a liquid source (incl. bubble columns)</li>
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
     * 气泡柱（{@link BubbleColumnBlock}）虽然不是 {@code LiquidBlock}，但其放置方式
     * 与水源完全相同（放置水源后由下方灵魂沙/岩浆块生成气泡柱），因此也被视为
     * "液体源"，使液体放置流程（自动切水桶、精确放置、空桶回收保护）能够处理气泡柱。
     * <p>
     * Returns {@code true} only when the block is a {@link LiquidBlock} (water or lava)
     * and the fluid level is 0 (source).  Flowing water/lava returns {@code false}.
     * <p>
     * A bubble column ({@link BubbleColumnBlock}) is not a {@code LiquidBlock},
     * but it is placed exactly like a water source (placing water on top of
     * soul sand/magma generates the bubbles), so it is also treated as a
     * "liquid source" so the liquid placement flow (auto-pick bucket, precise
     * placement, empty bucket pickup protection) can handle it.
     *
     * @param state 待检查的方块状态 / the block state to check
     * @return true 如果是液体源方块 / true if it is a liquid source block
     */
    private static boolean isLiquidSource(@NotNull BlockState state) {
        Block block = state.getBlock();
        if (block instanceof LiquidBlock) {
            // LiquidBlock.LEVEL == 0 表示源头 / LEVEL == 0 means source
            return state.getValue(LiquidBlock.LEVEL) == 0;
        }
        // 气泡柱：不是 LiquidBlock，但放置方式与水源相同
        // Bubble column: not a LiquidBlock, but placed like a water source
        return block instanceof BubbleColumnBlock;
    }

    /**
     * 检查方块状态是否为气泡柱 / Check if a block state is a bubble column.
     * <p>
     * 气泡柱由水源下方的灵魂沙/岩浆块生成，无法作为物品直接拾取，
     * 放置方式是放置水源。/ A bubble column is generated by soul sand or magma
     * below a water source; it cannot be picked up as an item, and is placed
     * by placing water.
     *
     * @param state 待检查的方块状态 / the block state to check
     * @return true 如果是气泡柱 / true if it is a bubble column
     */
    private static boolean isBubbleColumn(@NotNull BlockState state) {
        return state.getBlock() instanceof BubbleColumnBlock;
    }

    /**
     * 检查液体放置是否已完成 / Check whether the liquid placement is complete.
     * <p>
     * 除"同方块已就位"外，气泡柱还有一个特例：客户端该位置已有水源即视为已放置。
     * 气泡柱由水源与下方灵魂沙/岩浆块生成，转化需要若干 tick，若下方方块尚未放置
     * 则永远不会转化。若此处不视为已放置，每个 tick 都会重复触发放置——在已有水
     * 源上放水时，原版水桶 use 逻辑会把水放到击中面相邻的格子（水不是
     * LiquidBlockContainer），导致水不断向四周扩散。
     * <p>
     * Besides "the same block is in place", a bubble column has a special case:
     * a client-side water source at the position also counts as placed, because
     * the bubble column is generated from water by the soul sand/magma below and
     * the conversion takes several ticks (or never happens if the block below is
     * missing). Without this, placement would re-trigger every tick — and placing
     * water onto existing water makes the vanilla bucket use logic spill it to
     * the block adjacent to the hit face (water is not a LiquidBlockContainer),
     * spreading water outward.
     *
     * @param stateSchematic 原理图方块状态 / the schematic block state
     * @param stateClient    客户端方块状态 / the client block state
     * @return true 如果放置已完成 / true if the placement is done
     */
    private static boolean isLiquidPlacementDone(@NotNull BlockState stateSchematic, @NotNull BlockState stateClient) {
        if (stateSchematic.getBlock() == stateClient.getBlock() && isLiquidSource(stateClient)) {
            return true;
        }
        return isBubbleColumn(stateSchematic)
                && stateClient.getBlock() == Blocks.WATER
                && isLiquidSource(stateClient);
    }

    // ================================================================
    // 反射助手 / Reflection helpers
    // 1.20.X 的 litematica 中，applyPlacementFacing 与 applyBlockSlabProtocol
    // 是 private 方法，必须反射调用；其余放置协议方法均为 public，直接调用。
    // In litematica for 1.20.X, applyPlacementFacing and applyBlockSlabProtocol
    // are private and must be invoked via reflection; all other placement
    // protocol methods are public and called directly.
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
}
