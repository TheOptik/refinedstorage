package com.refinedmods.refinedstorage.apiimpl.network.node;

import com.refinedmods.refinedstorage.RS;
import com.refinedmods.refinedstorage.api.network.node.ICoverable;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.api.util.IComparer;
import com.refinedmods.refinedstorage.apiimpl.API;
import com.refinedmods.refinedstorage.apiimpl.network.node.cover.CoverManager;
import com.refinedmods.refinedstorage.inventory.fluid.FluidInventory;
import com.refinedmods.refinedstorage.inventory.item.BaseItemHandler;
import com.refinedmods.refinedstorage.inventory.item.UpgradeItemHandler;
import com.refinedmods.refinedstorage.inventory.listener.NetworkNodeFluidInventoryListener;
import com.refinedmods.refinedstorage.inventory.listener.NetworkNodeInventoryListener;
import com.refinedmods.refinedstorage.item.UpgradeItem;
import com.refinedmods.refinedstorage.blockentity.DiskDriveBlockEntity;
import com.refinedmods.refinedstorage.blockentity.ImporterBlockEntity;
import com.refinedmods.refinedstorage.blockentity.config.IComparable;
import com.refinedmods.refinedstorage.blockentity.config.IType;
import com.refinedmods.refinedstorage.blockentity.config.IWhitelistBlacklist;
import com.refinedmods.refinedstorage.util.StackUtils;
import com.refinedmods.refinedstorage.util.LevelUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

public class ImporterNetworkNode extends NetworkNode implements IComparable, IWhitelistBlacklist, IType, ICoverable {
    public static final ResourceLocation ID = new ResourceLocation(RS.ID, "importer");

    private static final String NBT_COMPARE = "Compare";
    private static final String NBT_MODE = "Mode";
    private static final String NBT_TYPE = "Type";
    private static final String NBT_FLUID_FILTERS = "FLuidFilters";

    private final BaseItemHandler itemFilters = new BaseItemHandler(9).addListener(new NetworkNodeInventoryListener(this));
    private final FluidInventory fluidFilters = new FluidInventory(9).addListener(new NetworkNodeFluidInventoryListener(this));

    private final UpgradeItemHandler upgrades = (UpgradeItemHandler) new UpgradeItemHandler(4, UpgradeItem.Type.SPEED, UpgradeItem.Type.STACK).addListener(new NetworkNodeInventoryListener(this));
    private final CoverManager coverManager;
    private int compare = IComparer.COMPARE_NBT;
    private int mode = IWhitelistBlacklist.BLACKLIST;
    private int type = IType.ITEMS;
    private int currentSlot;

    public ImporterNetworkNode(Level level, BlockPos pos) {
        super(level, pos);
        this.coverManager = new CoverManager(this);
    }

    @Override
    public int getEnergyUsage() {
        return RS.SERVER_CONFIG.getImporter().getUsage() + upgrades.getEnergyUsage();
    }

    @Override
    public void update() {
        super.update();

        if (!canUpdate() || !level.isLoaded(pos)) {
            return;
        }

        if (type == IType.ITEMS) {
            BlockEntity facing = getFacingBlockEntity();
            IItemHandler handler = LevelUtils.getItemHandler(facing, getDirection().getOpposite());

            if (facing instanceof DiskDriveBlockEntity || handler == null) {
                return;
            }

            if (currentSlot >= handler.getSlots()) {
                currentSlot = 0;
            }

            if (ticks % upgrades.getSpeed() == 0 && handler.getSlots() > 0) {
                for (int i = 0; i < handler.getSlots() && handler.getStackInSlot(currentSlot).isEmpty(); i++) {
                    currentSlot = (currentSlot + 1) % handler.getSlots();
                }

                ItemStack stack = handler.getStackInSlot(currentSlot);

                if (IWhitelistBlacklist.acceptsItem(itemFilters, mode, compare, stack)) {
                    ItemStack probeResult = network.insertItem(stack, stack.getMaxStackSize(), Action.SIMULATE);
                    int maxExtractedItems = Math.min(upgrades.getStackInteractCount(), stack.getMaxStackSize());
                    maxExtractedItems = Math.min(maxExtractedItems, stack.getMaxStackSize() - probeResult.getCount());
                    ItemStack result = accumulateItemStack(handler, maxExtractedItems, currentSlot);

                    if (!result.isEmpty()) {
                        network.insertItemTracked(result, result.getCount());
                    }
                }
                currentSlot++;
            }
        } else if (type == IType.FLUIDS && ticks % upgrades.getSpeed() == 0) {
            IFluidHandler handler = LevelUtils.getFluidHandler(getFacingBlockEntity(), getDirection().getOpposite());

            if (handler != null) {
                FluidStack stack = handler.drain(FluidAttributes.BUCKET_VOLUME, IFluidHandler.FluidAction.SIMULATE);

                if (!stack.isEmpty() &&
                    IWhitelistBlacklist.acceptsFluid(fluidFilters, mode, compare, stack) &&
                    network.insertFluid(stack, stack.getAmount(), Action.SIMULATE).isEmpty()) {
                    FluidStack toDrain = handler.drain(FluidAttributes.BUCKET_VOLUME * upgrades.getStackInteractCount(), IFluidHandler.FluidAction.SIMULATE);

                    if (!toDrain.isEmpty()) {
                        FluidStack remainder = network.insertFluidTracked(toDrain, toDrain.getAmount());
                        if (!remainder.isEmpty()) {
                            toDrain.shrink(remainder.getAmount());
                        }

                        handler.drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
                    }
                }
            }
        }
    }

    @Override
    public int getCompare() {
        return compare;
    }

    @Override
    public void setCompare(int compare) {
        this.compare = compare;

        markDirty();
    }

    @Override
    public int getWhitelistBlacklistMode() {
        return mode;
    }

    @Override
    public void setWhitelistBlacklistMode(int mode) {
        this.mode = mode;

        markDirty();
    }

    @Override
    public void read(CompoundTag tag) {
        super.read(tag);

        if (tag.contains(CoverManager.NBT_COVER_MANAGER)) {
            this.coverManager.readFromNbt(tag.getCompound(CoverManager.NBT_COVER_MANAGER));
        }

        StackUtils.readItems(upgrades, 1, tag);
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public CompoundTag write(CompoundTag tag) {
        super.write(tag);

        tag.put(CoverManager.NBT_COVER_MANAGER, this.coverManager.writeToNbt());

        StackUtils.writeItems(upgrades, 1, tag);

        return tag;
    }

    @Override
    public CompoundTag writeConfiguration(CompoundTag tag) {
        super.writeConfiguration(tag);

        tag.putInt(NBT_COMPARE, compare);
        tag.putInt(NBT_MODE, mode);
        tag.putInt(NBT_TYPE, type);

        StackUtils.writeItems(itemFilters, 0, tag);

        tag.put(NBT_FLUID_FILTERS, fluidFilters.writeToNbt());

        return tag;
    }

    @Override
    public void readConfiguration(CompoundTag tag) {
        super.readConfiguration(tag);

        if (tag.contains(NBT_COMPARE)) {
            compare = tag.getInt(NBT_COMPARE);
        }

        if (tag.contains(NBT_MODE)) {
            mode = tag.getInt(NBT_MODE);
        }

        if (tag.contains(NBT_TYPE)) {
            type = tag.getInt(NBT_TYPE);
        }

        StackUtils.readItems(itemFilters, 0, tag);

        if (tag.contains(NBT_FLUID_FILTERS)) {
            fluidFilters.readFromNbt(tag.getCompound(NBT_FLUID_FILTERS));
        }
    }

    public IItemHandler getUpgrades() {
        return upgrades;
    }

    @Override
    public IItemHandler getDrops() {
        return getUpgrades();
    }

    @Override
    public int getType() {
        return level.isClientSide ? ImporterBlockEntity.TYPE.getValue() : type;
    }

    @Override
    public void setType(int type) {
        this.type = type;

        markDirty();
    }

    @Override
    public IItemHandlerModifiable getItemFilters() {
        return itemFilters;
    }

    @Override
    public FluidInventory getFluidFilters() {
        return fluidFilters;
    }

    @Override
    public CoverManager getCoverManager() {
        return coverManager;
    }


    private static ItemStack accumulateItemStack(IItemHandler handler, int maxExtractedItems, int currentSlot) {
        ItemStack stackWithTargetItem = handler.getStackInSlot(currentSlot).copy();
        ItemStack resultStack = null;

        for (int i = 0; i < handler.getSlots(); i++) {
            int index = (i + currentSlot) % handler.getSlots();
            ItemStack currentStack = handler.getStackInSlot(index);

            if (API.instance().getComparer().isEqual(currentStack, stackWithTargetItem, IComparer.COMPARE_NBT)) {

                if (resultStack == null) {
                    resultStack = handler.extractItem(index, maxExtractedItems, false);
                } else {
                    ItemStack currentExtractedStack = handler.extractItem(index, maxExtractedItems - resultStack.getCount(), false);
                    resultStack.setCount(resultStack.getCount() + currentExtractedStack.getCount());

                    if (resultStack.getCount() == maxExtractedItems) {
                        return resultStack;
                    }
                }
            }
        }

        return resultStack;
    }

}
