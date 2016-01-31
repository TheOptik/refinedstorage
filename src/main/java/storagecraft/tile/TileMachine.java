package storagecraft.tile;

import storagecraft.tile.settings.RedstoneMode;
import storagecraft.tile.settings.IRedstoneModeSetting;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.BlockPos;
import storagecraft.block.BlockMachine;

public abstract class TileMachine extends TileBase implements INetworkTile, IRedstoneModeSetting
{
	protected boolean connected = false;
	protected boolean redstoneControlled = true;

	private RedstoneMode redstoneMode = RedstoneMode.IGNORE;

	private BlockPos controllerPos;

	public void onConnected(TileController controller)
	{
		markDirty();

		connected = true;

		controllerPos = controller.getPos();

		worldObj.setBlockState(pos, worldObj.getBlockState(pos).withProperty(BlockMachine.CONNECTED, true));
	}

	public void onDisconnected()
	{
		markDirty();

		connected = false;

		if (!worldObj.isAirBlock(pos))
		{
			worldObj.setBlockState(pos, worldObj.getBlockState(pos).withProperty(BlockMachine.CONNECTED, false));
		}
	}

	@Override
	public void update()
	{
		super.update();

		if (!worldObj.isRemote && isConnected())
		{
			updateMachine();
		}
	}

	public boolean isConnected()
	{
		return connected;
	}

	@Override
	public RedstoneMode getRedstoneMode()
	{
		return redstoneMode;
	}

	@Override
	public void setRedstoneMode(RedstoneMode mode)
	{
		if (redstoneControlled)
		{
			markDirty();

			this.redstoneMode = mode;
		}
	}

	@Override
	public BlockPos getMachinePos()
	{
		return pos;
	}

	@Override
	public BlockPos getTilePos()
	{
		return pos;
	}

	public TileController getController()
	{
		return (TileController) worldObj.getTileEntity(controllerPos);
	}

	@Override
	public void fromBytes(ByteBuf buf)
	{
		boolean lastConnected = connected;

		connected = buf.readBoolean();

		if (connected)
		{
			controllerPos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
		}

		redstoneMode = RedstoneMode.getById(buf.readInt());

		if (lastConnected != connected)
		{
			worldObj.markBlockForUpdate(pos);
		}
	}

	@Override
	public void toBytes(ByteBuf buf)
	{
		buf.writeBoolean(connected);

		if (connected)
		{
			buf.writeInt(controllerPos.getX());
			buf.writeInt(controllerPos.getY());
			buf.writeInt(controllerPos.getZ());
		}

		buf.writeInt(redstoneMode.id);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt)
	{
		super.readFromNBT(nbt);

		if (nbt.hasKey(RedstoneMode.NBT))
		{
			redstoneMode = RedstoneMode.getById(nbt.getInteger(RedstoneMode.NBT));
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt)
	{
		super.writeToNBT(nbt);

		nbt.setInteger(RedstoneMode.NBT, redstoneMode.id);
	}

	public abstract int getEnergyUsage();

	public abstract void updateMachine();
}
