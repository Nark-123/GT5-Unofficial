package gregtech.common.pollution;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;

import gregtech.GTMod;
import gregtech.api.hazards.HazardProtection;
import gregtech.api.pollution.IPollutionEngine;

public final class PollutionEffects {

    private static final int CHUNKS_PER_PASS = 32;
    private static final int EFFECT_DURATION = 60;

    private int chunkIndex;

    public void tick(World world, IPollutionEngine engine) {
        tickEntities(world, engine);
        tickBlocks(world, engine);
    }

    private void tickEntities(World world, IPollutionEngine engine) {
        for (Object object : world.loadedEntityList) {
            if (!(object instanceof EntityLivingBase)) continue;

            EntityLivingBase entity = (EntityLivingBase) object;
            if (entity.isDead) continue;
            if (entity instanceof EntityPlayerMP && ((EntityPlayerMP) entity).capabilities.isCreativeMode) continue;
            if (HazardProtection.isWearingFullGasHazmat(entity)) continue;

            int x = MathHelper.floor_double(entity.posX);
            int y = MathHelper.floor_double(entity.posY + entity.getEyeHeight());
            int z = MathHelper.floor_double(entity.posZ);
            if (y < 0 || y >= 256) continue;

            int pollution = engine.get(x, y, z);

            if (pollution > GTMod.proxy.mPollutionSmogLimit) applySmog(entity, pollution, world);
            if (pollution > GTMod.proxy.mPollutionPoisonLimit) applyPoison(entity, pollution, world);
        }
    }

    private void applySmog(EntityLivingBase entity, int pollution, World world) {
        int amplifier = Math.min(3, pollution / 400000);
        int potion;

        switch (world.rand.nextInt(3)) {
            case 0:
                potion = Potion.weakness.id;
                break;
            case 1:
                potion = Potion.moveSlowdown.id;
                break;
            default:
                potion = Potion.digSlowdown.id;
        }

        entity.addPotionEffect(new PotionEffect(potion, EFFECT_DURATION, amplifier));
    }

    private void applyPoison(EntityLivingBase entity, int pollution, World world) {
        int amplifier = Math.min(3, pollution / 500000);
        int potion;

        switch (world.rand.nextInt(4)) {
            case 0:
                potion = Potion.confusion.id;
                amplifier = 1;
                break;
            case 1:
                potion = Potion.poison.id;
                break;
            case 2:
                potion = Potion.blindness.id;
                amplifier = 1;
                break;
            default:
                potion = Potion.hunger.id;
                amplifier = 0;
        }

        entity.addPotionEffect(new PotionEffect(potion, EFFECT_DURATION, amplifier));
    }

    private void tickBlocks(World world, IPollutionEngine engine) {
        if (!(world.getChunkProvider() instanceof ChunkProviderServer)) return;

        List<?> chunks = ((ChunkProviderServer) world.getChunkProvider()).loadedChunks;
        if (chunks.isEmpty()) return;

        int count = Math.min(CHUNKS_PER_PASS, chunks.size());

        for (int i = 0; i < count; i++) {
            if (chunkIndex >= chunks.size()) chunkIndex = 0;
            Chunk chunk = (Chunk) chunks.get(chunkIndex++);

            int x = (chunk.xPosition << 4) + world.rand.nextInt(16);
            int z = (chunk.zPosition << 4) + world.rand.nextInt(16);
            int y = MathHelper.clamp_int(world.getHeightValue(x, z) + world.rand.nextInt(7) - 3, 1, 254);

            int pollution = engine.get(x, y, z);
            if (pollution <= GTMod.proxy.mPollutionVegetationLimit) continue;

            damageBlock(world, x, y, z, pollution > GTMod.proxy.mPollutionSourRainLimit);
        }
    }

    private void damageBlock(World world, int x, int y, int z, boolean sourRain) {
        Block block = world.getBlock(x, y, z);
        Material material = block.getMaterial();

        if (block == Blocks.air || block == Blocks.stone || block == Blocks.sand || block == Blocks.deadbush) return;

        if (block == Blocks.leaves || block == Blocks.leaves2 || material == Material.leaves) {
            world.setBlockToAir(x, y, z);
            return;
        }

        if (block == Blocks.reeds || block == Blocks.vine || block == Blocks.waterlily || block == Blocks.wheat
            || block == Blocks.cactus || material == Material.cactus || block == Blocks.melon_block
            || block == Blocks.melon_stem || block == Blocks.red_flower || block == Blocks.yellow_flower
            || block == Blocks.carrots || block == Blocks.potatoes || block == Blocks.pumpkin
            || block == Blocks.pumpkin_stem || block == Blocks.cocoa) {
            block.dropBlockAsItem(world, x, y, z, world.getBlockMetadata(x, y, z), 0);
            world.setBlockToAir(x, y, z);
            return;
        }

        if (block == Blocks.tallgrass || block == Blocks.sapling || material == Material.plants) {
            world.setBlock(x, y, z, Blocks.deadbush);
            return;
        }

        if (block == Blocks.mossy_cobblestone) {
            world.setBlock(x, y, z, Blocks.cobblestone);
            return;
        }

        if (block == Blocks.grass || material == Material.grass) {
            world.setBlock(x, y, z, Blocks.dirt);
            return;
        }

        if (block == Blocks.farmland || block == Blocks.dirt) {
            world.setBlock(x, y, z, Blocks.sand);
            return;
        }

        if (sourRain && world.isRaining() && world.getBlock(x, y + 1, z) == Blocks.air
            && world.canBlockSeeTheSky(x, y + 1, z)) {
            if (block == Blocks.cobblestone) world.setBlock(x, y, z, Blocks.gravel);
            else if (block == Blocks.gravel) world.setBlock(x, y, z, Blocks.sand);
        }
    }
}
