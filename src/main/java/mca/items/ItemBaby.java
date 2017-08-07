package mca.items;

import java.util.List;

import mca.core.MCA;
import mca.core.minecraft.AchievementsMCA;
import mca.data.NBTPlayerData;
import mca.data.PlayerMemory;
import mca.entity.EntityVillagerMCA;
import mca.enums.EnumDialogueType;
import mca.enums.EnumGender;
import mca.enums.EnumProfession;
import mca.enums.EnumRelation;
import mca.packets.PacketOpenBabyNameGUI;
import mca.util.TutorialManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import radixcore.constant.Font.Color;
import radixcore.constant.Font.Format;
import radixcore.constant.Time;

public class ItemBaby extends Item 
{
	private final boolean isBoy;

	public ItemBaby(boolean isBoy)
	{
		final String itemName = isBoy ? "BabyBoy" : "BabyGirl";

		this.isBoy = isBoy;
		this.setCreativeTab(MCA.getCreativeTabMain());
		this.setMaxStackSize(1);
		this.setUnlocalizedName(itemName);
	}

	@Override
	public void onUpdate(ItemStack itemStack, World world, Entity entity, int unknownInt, boolean unknownBoolean)
	{
		super.onUpdate(itemStack, world, entity, unknownInt, unknownBoolean);

		if (!world.isRemote)
		{
			if (!itemStack.hasTagCompound())
			{
				String ownerName = entity instanceof EntityPlayer ? entity.getName() : entity instanceof EntityVillagerMCA ? ((EntityVillagerMCA)entity).attributes.getSpouseName() : "Unknown";

				NBTTagCompound compound = new NBTTagCompound();

				compound.setString("name", "Unnamed");
				compound.setInteger("age", 0);
				compound.setString("owner", ownerName);
				compound.setBoolean("isInfected", false);
				
				itemStack.setTagCompound(compound);
				
				if (entity instanceof EntityPlayer)
				{
					EntityPlayer player = (EntityPlayer)entity;
					
					if (player.capabilities.isCreativeMode)
					{
						TutorialManager.sendMessageToPlayer(player, "You can name a baby retrieved from", "creative mode by right-clicking the air.");
					}
				}
			}

			else 
			{
				updateBabyGrowth(itemStack);
			}
		}
	}

	@Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ)
	{
		if (hand == EnumHand.OFF_HAND)
		{
			return EnumActionResult.FAIL;
		}
		
		ItemStack stack = player.getHeldItem(hand);
		
		int posX = pos.getX();
		int posY = pos.getY();
		int posZ = pos.getZ();
		
		if (!world.isRemote && isReadyToGrowUp(stack))
		{
			ItemBaby baby = (ItemBaby)stack.getItem();
			NBTPlayerData data = MCA.getPlayerData(player);
			boolean isPlayerMale = data.getGender() == EnumGender.MALE ? true : false;

			String motherName = "N/A";
			int motherId = 0;
			String fatherName = "N/A";
			int fatherId = 0;

			if (isPlayerMale)
			{
				motherName = data.getSpouseName();
				//motherId = data.getSpouseUUID();
				fatherName = player.getName();
				//fatherId = data.getPermanentId();
			}

			else
			{
				fatherName = data.getSpouseName();
				//fatherId = data.getSpouseUUID();
				motherName = player.getName();
				//motherId = data.getPermanentId();				
			}

			final EntityVillagerMCA child = new EntityVillagerMCA(world);
			child.attributes.setGender(baby.isBoy ? EnumGender.MALE : EnumGender.FEMALE);
			child.attributes.setIsChild(true);
			child.attributes.setName(stack.getTagCompound().getString("name"));
			child.attributes.setProfession(EnumProfession.Child);
			child.attributes.assignRandomSkin();
			child.attributes.assignRandomScale();
			
			//TODO set parents
			
			child.setPosition(posX, posY + 1, posZ);

			if (stack.getTagCompound().getBoolean("isInfected"))
			{
				child.attributes.setIsInfected(true);
			}
			
			world.spawnEntity(child);

			PlayerMemory childMemory = child.attributes.getPlayerMemory(player);
			childMemory.setHearts(100);
			childMemory.setDialogueType(EnumDialogueType.CHILDP);
			childMemory.setRelation(child.attributes.getGender() == EnumGender.MALE ? EnumRelation.SON : EnumRelation.DAUGHTER);
			player.inventory.setInventorySlotContents(player.inventory.currentItem, ItemStack.EMPTY);
			player.addStat(AchievementsMCA.babyToChild);

			data.setOwnsBaby(false);
		}

		return EnumActionResult.PASS;
	}	


	@Override
	public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) 
	{
		ItemStack stack = player.getHeldItem(hand);
		
		if (!world.isRemote && 
				stack.getTagCompound().getString("name").equals("Unnamed") && 
				hand != EnumHand.OFF_HAND)
		{
			ItemBaby baby = (ItemBaby) stack.getItem();
			MCA.getPacketHandler().sendPacketToPlayer(new PacketOpenBabyNameGUI(baby.isBoy), (EntityPlayerMP)player);
		}

		return super.onItemRightClick(world, player, hand);
	}

	@Override
	public boolean onEntityItemUpdate(EntityItem entityItem) 
	{
		//Happens on servers for some reason.
		if (entityItem.getEntityItem() != null && !entityItem.world.isRemote)
		{
			updateBabyGrowth(entityItem.getEntityItem());
		}
		
		return super.onEntityItemUpdate(entityItem);
	}

	@Override
	public void addInformation(ItemStack itemStack, EntityPlayer entityPlayer, List infoList, boolean unknown)
	{
		super.addInformation(itemStack, entityPlayer, infoList, unknown);

		if (itemStack.hasTagCompound())
		{
			//Text color is blue for boys, purple for girls.
			String textColor = ((ItemBaby)itemStack.getItem()).isBoy ? Color.AQUA : Color.LIGHTPURPLE;
			int ageInMinutes = itemStack.getTagCompound().getInteger("age");

			//Owner name is You for the current owner. Otherwise, the player's name.
			String ownerName = itemStack.getTagCompound().getString("owner");
			ownerName = ownerName.equals(entityPlayer.getName()) ? "You" : ownerName;

			infoList.add(textColor + "Name: " + Format.RESET + itemStack.getTagCompound().getString("name"));
			infoList.add(textColor + "Age: "  + Format.RESET + ageInMinutes + (ageInMinutes == 1 ? " minute" : " minutes"));
			infoList.add(textColor + "Parent: " + Format.RESET + ownerName);

			if (itemStack.getTagCompound().getBoolean("isInfected"))
			{
				infoList.add(Color.GREEN + "Infected!");
			}
			
			if (isReadyToGrowUp(itemStack))
			{
				infoList.add(Color.GREEN + "Ready to grow up!");
			}
		}
	}

	private void updateBabyGrowth(ItemStack itemStack)
	{
		if (itemStack != null && itemStack.hasTagCompound() && FMLCommonHandler.instance().getMinecraftServerInstance().getTickCounter() % Time.MINUTE == 0)
		{
			int age = itemStack.getTagCompound().getInteger("age");
			age++;
			itemStack.getTagCompound().setInteger("age", age);
		}
	}

	private boolean isReadyToGrowUp(ItemStack itemStack)
	{
		if (itemStack != null && itemStack.hasTagCompound())
		{
			final int ageInMinutes = itemStack.getTagCompound().getInteger("age");
			return ageInMinutes >= MCA.getConfig().babyGrowUpTime;
		}

		return false;
	}

	public boolean getIsBoy()
	{
		return isBoy;
	}
}
