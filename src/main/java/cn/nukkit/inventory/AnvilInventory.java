package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAnvil;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemArmor;
import cn.nukkit.item.ItemTool;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.Position;
import cn.nukkit.level.sound.AnvilBreakSound;
import cn.nukkit.level.sound.AnvilUseSound;
import cn.nukkit.nbt.tag.CompoundTag;

import java.util.concurrent.ThreadLocalRandom;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class AnvilInventory extends ContainerInventory {

    public static final int TARGET = 0;
    public static final int SACRIFICE = 1;
    public static final int RESULT = 2;

    public AnvilInventory(Position position) {
        super(null, InventoryType.ANVIL);
        this.holder = new FakeBlockMenu(this, position);
    }

    @Override
    public FakeBlockMenu getHolder() {
        return (FakeBlockMenu) this.holder;
    }

    /**
     * Kept for backwards compatibility with plugins. Handles all anvil
     * operations, see {@link #onProcess}.
     */
    public boolean onRename(Player player, Item resultItem) {
        return this.onProcess(player, resultItem);
    }

    /**
     * Handles everything the vanilla anvil supports: renaming, repairing by
     * combining two items, repairing with materials, applying enchanted books
     * and combining enchantments, including experience costs.
     */
    public boolean onProcess(Player player, Item resultItem) {
        Item local = getItem(TARGET);
        Item second = getItem(SACRIFICE);

        if (local.getId() == Item.AIR || resultItem.getId() != local.getId() || resultItem.getCount() != local.getCount()) {
            //Item does not match target item. The result is computed by the server,
            //so only the item type and the count of the output are validated here.
            return false;
        }

        String name = resultItem.getCustomName();
        boolean renamed = name == null ? local.getCustomName() != null : !name.equals(local.getCustomName());

        if (second.getId() == Item.AIR && !renamed) {
            //with an empty sacrifice slot the anvil can only rename items
            return false;
        }

        Item result = local.clone();
        int repairCost = getRepairCost(local);
        int cost = getPriorWorkPenalty(repairCost);

        if (second.getId() != Item.AIR) {
            //vanilla pays the prior work penalty of both items and the
            //result inherits the higher work count
            int sacrificeRepairCost = getRepairCost(second);
            repairCost = Math.max(repairCost, sacrificeRepairCost);
            cost += getPriorWorkPenalty(sacrificeRepairCost);

            if (second.getId() == Item.ENCHANTED_BOOK) {
                int enchantmentCost = this.applyBookEnchantments(result, local, second);
                if (enchantmentCost == 0) {
                    //no enchantment of the book can be applied; like in
                    //vanilla the anvil shows no output for this
                    return false;
                }
                cost += enchantmentCost;
            } else if (second.getId() == local.getId() && second.getCount() == 1 && local.getMaxDurability() >= 0) {
                cost += 2 + this.repairByCombining(result, local, second);
            } else if (getRepairMaterial(local) == second.getId()) {
                cost += second.getCount();
                this.repairByMaterial(result, second.getCount());
            } else {
                return false;
            }
        }

        if (renamed) {
            if (name != null && name.length() > 30) {
                return false;
            }
            result.setCustomName(name);
            cost += 1;
        }

        if (cost <= 0) {
            //nothing the anvil changed
            return false;
        }

        if (!player.isCreative() && player.getExperienceLevel() < cost) {
            return false;
        }

        //the prior work penalty grows with every anvil use
        CompoundTag tag = result.getNamedTag();
        if (tag == null) {
            tag = new CompoundTag();
        }
        tag.putInt("RepairCost", repairCost + 1);
        result.setNamedTag(tag);

        if (second.getId() == Item.ENCHANTED_BOOK && second.getCount() > 1) {
            second.setCount(second.getCount() - 1);
            this.setItem(SACRIFICE, second);
        } else {
            this.clear(SACRIFICE);
        }
        this.clear(TARGET);

        for (Item leftover : player.getInventory().addItem(result)) {
            //the inventory was full, drop the rest instead of voiding it
            player.getLevel().dropItem(this.getHolder().add(0.5, 0.5, 0.5), leftover);
        }
        player.getInventory().sendContents(player);
        sendContents(player);

        if (!player.isCreative()) {
            player.setExperience(player.getExperience(), player.getExperienceLevel() - cost);
        }

        player.getLevel().addSound(new AnvilUseSound(player));

        this.damageAnvil(player);
        return true;
    }

    /**
     * Applies the compatible enchantments of an enchanted book onto the
     * result item. Returns the experience cost of the applied enchantments.
     */
    private int applyBookEnchantments(Item result, Item target, Item book) {
        int cost = 0;
        boolean targetIsBook = target.getId() == Item.ENCHANTED_BOOK;
        for (Enchantment enchantment : book.getEnchantments()) {
            if (enchantment.getId() < 0 || enchantment.getLevel() < 0) {
                continue;
            }

            //an enchanted book accepts every enchantment, on anything else it
            //must be applicable to the item and compatible with its enchantments
            if (!targetIsBook && (!enchantment.canEnchant(target) || !this.isCompatibleWithOthers(enchantment, target.getEnchantments()))) {
                continue;
            }

            int level = enchantment.getLevel();
            Enchantment localEnchantment = target.getEnchantment(enchantment.getId());
            if (localEnchantment != null) {
                level = localEnchantment.getLevel() == level ? level + 1 : Math.max(localEnchantment.getLevel(), level);
            }

            Enchantment applied = Enchantment.getEnchantment(enchantment.getId());
            level = Math.min(level, applied.getMaxLevel());
            applied.setLevel(level);
            result.addEnchantment(applied);
            cost += getAnvilMultiplier(applied.getId()) * level;
        }
        return cost;
    }

    /**
     * Repairs the result item by combining it with a second, identical item
     * (durability = min(both + 12% of max, max)) and merges their
     * enchantments. Returns the experience cost of the merged enchantments.
     */
    private int repairByCombining(Item result, Item target, Item sacrifice) {
        int max = target.getMaxDurability();
        int durability = (max - target.getDamage()) + (max - sacrifice.getDamage()) + (int) (max * 0.12);
        result.setDamage(Math.max(0, max - Math.min(durability, max)));

        int cost = 0;
        for (Enchantment enchantment : sacrifice.getEnchantments()) {
            if (enchantment.getId() < 0 || enchantment.getLevel() < 0) {
                continue;
            }

            Enchantment localEnchantment = target.getEnchantment(enchantment.getId());
            if (localEnchantment == null) {
                if (!this.isCompatibleWithOthers(enchantment, target.getEnchantments())) {
                    continue;
                }
                result.addEnchantment(enchantment);
                cost += getAnvilMultiplier(enchantment.getId()) * enchantment.getLevel();
            } else {
                int level = localEnchantment.getLevel() == enchantment.getLevel() ? localEnchantment.getLevel() + 1 : Math.max(localEnchantment.getLevel(), enchantment.getLevel());
                level = Math.min(level, localEnchantment.getMaxLevel());
                Enchantment applied = Enchantment.getEnchantment(localEnchantment.getId());
                applied.setLevel(level);
                result.addEnchantment(applied);
                cost += getAnvilMultiplier(localEnchantment.getId()) * level;
            }
        }
        return cost;
    }

    /**
     * Restores a quarter of the maximum durability per material item spent.
     */
    private void repairByMaterial(Item result, int materialCount) {
        int max = result.getMaxDurability();
        result.setDamage(Math.max(0, result.getDamage() - (max / 4) * materialCount));
    }

    private boolean isCompatibleWithOthers(Enchantment enchantment, Enchantment[] existing) {
        for (Enchantment other : existing) {
            if (other.getId() != enchantment.getId() && !other.isCompatibleWith(enchantment)) {
                return false;
            }
        }
        return true;
    }

    private void damageAnvil(Player player) {
        if (ThreadLocalRandom.current().nextInt(8) != 0) { //vanilla: ~12% chance per use
            return;
        }

        Position position = this.getHolder();
        Block block = position.getLevel().getBlock(position);
        if (!(block instanceof BlockAnvil)) {
            return;
        }

        int stage = (block.getDamage() >> 2) & 0x03;
        if (stage >= 2) {
            position.getLevel().setBlock(block, Block.get(Block.AIR), true);
            position.getLevel().addSound(new AnvilBreakSound(position));
        } else {
            int meta = (block.getDamage() & 0x03) | ((stage + 1) << 2);
            position.getLevel().setBlock(block, Block.get(Block.ANVIL, meta), true);
        }
    }

    private static int getRepairCost(Item item) {
        CompoundTag tag = item.getNamedTag();
        return tag != null && tag.contains("RepairCost") ? Math.max(0, tag.getInt("RepairCost")) : 0;
    }

    /**
     * The vanilla prior work penalty of an item: 2^n - 1 levels. The work
     * count is capped so the shift cannot overflow.
     */
    private static int getPriorWorkPenalty(int repairCost) {
        return repairCost > 0 ? (1 << Math.min(repairCost, 30)) - 1 : 0;
    }

    /**
     * The material that repairs the given item in an anvil.
     */
    private static int getRepairMaterial(Item item) {
        if (item instanceof ItemArmor) {
            switch (item.getTier()) {
                case ItemArmor.TIER_LEATHER:
                    return Item.LEATHER;
                case ItemArmor.TIER_CHAIN:
                case ItemArmor.TIER_IRON:
                    return Item.IRON_INGOT;
                case ItemArmor.TIER_GOLD:
                    return Item.GOLD_INGOT;
                case ItemArmor.TIER_DIAMOND:
                    return Item.DIAMOND;
            }
        } else if (item instanceof ItemTool) {
            switch (item.getTier()) {
                case ItemTool.TIER_WOODEN:
                    return Item.PLANKS;
                case ItemTool.TIER_STONE:
                    return Item.COBBLESTONE;
                case ItemTool.TIER_IRON:
                    return Item.IRON_INGOT;
                case ItemTool.TIER_GOLD:
                    return Item.GOLD_INGOT;
                case ItemTool.TIER_DIAMOND:
                    return Item.DIAMOND;
            }
        }
        return Item.AIR;
    }

    /**
     * Vanilla multipliers of the enchantment costs in an anvil operation.
     */
    private static int getAnvilMultiplier(int id) {
        switch (id) {
            case Enchantment.ID_PROTECTION_ALL:
            case Enchantment.ID_PROTECTION_FIRE:
            case Enchantment.ID_PROTECTION_FALL:
            case Enchantment.ID_PROTECTION_EXPLOSION:
            case Enchantment.ID_PROTECTION_PROJECTILE:
            case Enchantment.ID_WATER_BREATHING:
            case Enchantment.ID_WATER_WORKER:
                return 1;
            case Enchantment.ID_THORNS:
            case Enchantment.ID_SILK_TOUCH:
            case Enchantment.ID_BOW_INFINITY:
                return 8;
            case Enchantment.ID_DAMAGE_ALL:
            case Enchantment.ID_DAMAGE_SMITE:
            case Enchantment.ID_DAMAGE_ARTHROPODS:
            case Enchantment.ID_KNOCKBACK:
            case Enchantment.ID_EFFICIENCY:
            case Enchantment.ID_BOW_POWER:
            default:
                return 2;
            case Enchantment.ID_WATER_WALKER:
            case Enchantment.ID_FIRE_ASPECT:
            case Enchantment.ID_LOOTING:
            case Enchantment.ID_DURABILITY:
            case Enchantment.ID_FORTUNE_DIGGING:
            case Enchantment.ID_BOW_KNOCKBACK:
            case Enchantment.ID_BOW_FLAME:
            case Enchantment.ID_FORTUNE_FISHING:
            case Enchantment.ID_LURE:
                return 4;
        }
    }

    @Override
    public void onClose(Player who) {
        super.onClose(who);
        who.craftingType = Player.CRAFTING_SMALL;

        for (int i = 0; i < 2; ++i) {
            this.getHolder().getLevel().dropItem(this.getHolder().add(0.5, 0.5, 0.5), this.getItem(i));
            this.clear(i);
        }
    }

    @Override
    public void onOpen(Player who) {
        super.onOpen(who);
        who.craftingType = Player.CRAFTING_ANVIL;
    }
}
