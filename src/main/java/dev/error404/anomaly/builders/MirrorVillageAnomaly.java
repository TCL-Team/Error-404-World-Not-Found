package dev.error404.anomaly.builders;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;

import dev.error404.anomaly.Anomaly;
import dev.error404.anomaly.AnomalyType;

/**
 * MIRROR_VILLAGE - three huts around a lamp post. Every villager inside is frozen, silent,
 * and facing the same direction: the player's approach.
 */
public class MirrorVillageAnomaly implements Anomaly {
	@Override
	public AnomalyType type() {
		return AnomalyType.MIRROR_VILLAGE;
	}

	@Override
	public int footprint() {
		return 16;
	}

	@Override
	public void build(ServerLevel level, BlockPos centre, RandomSource random) {
		int ox = centre.getX();
		int oz = centre.getZ();

		for (int i = 0; i < 3; i++) {
			double angle = (Math.PI * 2 / 3) * i + 0.4;
			int hx = ox + (int) (Math.cos(angle) * 8);
			int hz = oz + (int) (Math.sin(angle) * 8);
			hut(level, hx, hz, random);
		}

		int ly = StructureKit.ground(level, ox, oz);
		StructureKit.set(level, ox, ly, oz, Blocks.OAK_FENCE.defaultBlockState());
		StructureKit.set(level, ox, ly + 1, oz, Blocks.OAK_FENCE.defaultBlockState());
		StructureKit.set(level, ox, ly + 2, oz, Blocks.TORCH.defaultBlockState());

		StructureKit.chest(level, ox, ly + 1, oz + 1, Direction.NORTH, List.of(
				StructureKit.fragment(),
				StructureKit.writtenBook("gaon", List.of(
						"gaon me koi bolta nahi.",
						"lekin sab dekh rahe hain.",
						"aur sab ek hi taraf dekh rahe hain."))
		));
	}

	private void hut(ServerLevel level, int hx, int hz, RandomSource random) {
		int y = StructureKit.ground(level, hx, hz);
		StructureKit.clearArea(level, hx, y + 2, hz, 4, 5, 1);

		for (int x = -2; x <= 2; x++) {
			for (int z = -2; z <= 2; z++) {
				StructureKit.set(level, hx + x, y - 1, hz + z, Blocks.COBBLESTONE.defaultBlockState());

				for (int h = 0; h < 3; h++) {
					boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2;

					if (wall) {
						StructureKit.set(level, hx + x, y + h, hz + z, Blocks.OAK_PLANKS.defaultBlockState());
					}
				}
			}
		}

		StructureKit.door(level, hx, y, hz + 2, Direction.NORTH, false);
		StructureKit.set(level, hx, y + 2, hz, Blocks.BOOKSHELF.defaultBlockState());

		Villager villager = EntityType.VILLAGER.create(level, EntitySpawnReason.STRUCTURE);

		if (villager != null) {
			villager.setPos(hx + 0.5, y + 0.1, hz + 0.5);
			villager.setNoAi(true);
			villager.setSilent(true);
			villager.setCustomName(net.minecraft.network.chat.Component.literal("witness"));
			villager.setCustomNameVisible(false);
			villager.setYRot(180.0f);
			level.addFreshEntity(villager);
		}
	}

	@Override
	public void tick(ServerLevel level, BlockPos centre, RandomSource random, int ageTicks) {
		// Keep the villagers pinned to the sky: they never move, never speak.
		for (Villager villager : level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(centre).inflate(24))) {
			villager.setNoAi(true);
			villager.setSilent(true);
		}
	}

	@Override
	public List<String> discoveryLines() {
		return List.of("ERR_404_D: 3 entities found, 0 responsive");
	}

	@Override
	public boolean canRevoke() {
		return false;
	}
}
