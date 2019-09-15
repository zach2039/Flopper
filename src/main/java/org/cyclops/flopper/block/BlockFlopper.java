package org.cyclops.flopper.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.item.ItemStack;
import net.minecraft.pathfinding.PathType;
import net.minecraft.state.BooleanProperty;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IEnviromentBlockReader;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.cyclops.cyclopscore.block.BlockTile;
import org.cyclops.cyclopscore.helper.FluidHelpers;
import org.cyclops.cyclopscore.helper.InventoryHelpers;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.cyclopscore.helper.TileHelpers;
import org.cyclops.cyclopscore.tileentity.CyclopsTileEntity;
import org.cyclops.flopper.tileentity.TileFlopper;

import java.util.function.Supplier;

/**
 * Fluid hopper block.
 * @author rubensworks
 */
public class BlockFlopper extends BlockTile {

    public static final DirectionProperty FACING = BlockStateProperties.FACING_EXCEPT_UP;
    public static final BooleanProperty ENABLED = BlockStateProperties.ENABLED;

    public BlockFlopper(Properties properties, Supplier<CyclopsTileEntity> tileEntitySupplier) {
        super(properties, tileEntitySupplier);
        MinecraftForge.EVENT_BUS.register(this);
        this.setDefaultState(this.stateContainer.getBaseState()
                .with(FACING, Direction.DOWN)
                .with(ENABLED, true));
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(FACING, ENABLED);
    }

    @Override
    public VoxelShape getShape(BlockState state, IBlockReader worldIn, BlockPos pos, ISelectionContext context) {
        return Blocks.HOPPER.getShape(state, worldIn, pos, context);
    }

    @Override
    public VoxelShape getRaytraceShape(BlockState state, IBlockReader worldIn, BlockPos pos) {
        return Blocks.HOPPER.getRaytraceShape(state, worldIn, pos);
    }

    @SuppressWarnings("unchecked")
    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        Direction direction = context.getFace().getOpposite();
        if (direction == Direction.UP) {
            direction = Direction.DOWN;
        }
        return this.getDefaultState()
                .with(FACING, direction)
                .with(ENABLED, true);
    }

    @Override
    public void onBlockAdded(BlockState state, World worldIn, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (oldState.getBlock() != state.getBlock()) {
            this.updateState(worldIn, pos, state);
        }
    }

    @Override
    public void neighborChanged(BlockState state, World worldIn, BlockPos pos, Block blockIn, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, worldIn, pos, blockIn, fromPos, isMoving);
        this.updateState(worldIn, pos, state);
    }

    private void updateState(World worldIn, BlockPos pos, BlockState state) {
        boolean notPowered = !worldIn.isBlockPowered(pos);
        if (notPowered != state.get(ENABLED)) {
            worldIn.setBlockState(pos, state.with(ENABLED, notPowered), 4);
        }
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public boolean doesSideBlockRendering(BlockState state, IEnviromentBlockReader world, BlockPos pos, Direction face) {
        return true;
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT_MIPPED;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.with(FACING, rot.rotate(state.get(FACING)));
    }

    public BlockState mirror(BlockState state, Mirror mirrorIn) {
        return state.rotate(mirrorIn.toRotation(state.get(FACING)));
    }

    @Override
    public boolean allowsMovement(BlockState state, IBlockReader worldIn, BlockPos pos, PathType type) {
        return false;
    }

    @Override
    public boolean onBlockActivated(BlockState blockState, World world, BlockPos blockPos, PlayerEntity player, Hand hand, BlockRayTraceResult rayTraceResult) {
        if (super.onBlockActivated(blockState, world, blockPos, player, hand, rayTraceResult)) {
            return true;
        }
        return TileHelpers.getCapability(world, blockPos, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY)
                .map(fluidHandler -> {
                    ItemStack itemStack = player.getHeldItem(hand);
                    if (itemStack.isEmpty()) {
                        if (BlockFlopperConfig.showContentsStatusMessageOnClick) {
                            // If the hand is empty, show the tank contents
                            FluidStack fluidStack = fluidHandler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
                            if (fluidStack.isEmpty()) {
                                player.sendStatusMessage(new StringTextComponent("0 / "
                                        + String.format("%,d", fluidHandler.getTankCapacity(0))), true);
                            } else {
                                player.sendStatusMessage(new StringTextComponent(L10NHelpers.localize(fluidStack.getTranslationKey()) + ": "
                                        + String.format("%,d", fluidStack.getAmount()) + " / "
                                        + String.format("%,d", fluidHandler.getTankCapacity(0))), true);
                            }
                            return true;
                        }
                    } else {
                        if (!player.isSneaking()
                                && FluidUtil.tryEmptyContainer(itemStack, fluidHandler, FluidHelpers.BUCKET_VOLUME, player, false).isSuccess()) {
                            // Move fluid from the item into the tank if not sneaking
                            ItemStack drainedItem = FluidUtil.tryEmptyContainer(itemStack, fluidHandler, FluidHelpers.BUCKET_VOLUME, player, true).getResult();
                            if (!player.isCreative()) {
                                InventoryHelpers.tryReAddToStack(player, itemStack, drainedItem);
                            }
                            return true;
                        } else if (player.isSneaking()
                                && FluidUtil.tryFillContainer(itemStack, fluidHandler, FluidHelpers.BUCKET_VOLUME, player, false).isSuccess()) {
                            // Move fluid from the tank into the item if sneaking
                            FluidActionResult result = FluidUtil.tryFillContainer(itemStack, fluidHandler, FluidHelpers.BUCKET_VOLUME, player, true);
                            if (result.isSuccess()) {
                                ItemStack filledItem = result.getResult();
                                if (!player.isCreative()) {
                                    InventoryHelpers.tryReAddToStack(player, itemStack, filledItem);
                                }
                            }
                            return true;
                        }
                    }
                    return false;
                })
                .orElse(false);
    }

    @Override
    public boolean hasComparatorInputOverride(BlockState state) {
        return true;
    }

    @Override
    public int getComparatorInputOverride(BlockState blockState, World worldIn, BlockPos pos) {
        return TileHelpers.getSafeTile(worldIn, pos, TileFlopper.class)
                .map(tile -> tile.getTank().getFluidAmount() * 8 / tile.getTank().getCapacity())
                .orElse(0);
    }

    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        // Force allow shift-right clicking with a fluid container passing through to this block
        if (!event.getItemStack().isEmpty()
                && event.getWorld().getBlockState(event.getPos()).getBlock() == this
                && event.getItemStack().getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY).isPresent()) {
            event.setUseBlock(Event.Result.ALLOW);
        }
    }

}
