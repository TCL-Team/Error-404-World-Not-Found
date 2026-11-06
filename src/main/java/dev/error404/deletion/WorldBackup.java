package dev.error404.deletion;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import net.minecraft.server.MinecraftServer;

import dev.error404.Error404;
import dev.error404.Error404Config;

/**
 * First rule of a mod that deletes your world: back the world up first.
 *
 * <p>Zips {@code <world>/region} into {@code <world>/error404_backups/<timestamp>.zip} on
 * session start. Skips the backup if a recent one exists (6h) or if the region folder is
 * oversized (2 GB) so nobody's disk fills up silently.</p>
 */
public final class WorldBackup {
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
	private static final long MAX_BYTES = 2L * 1024 * 1024 * 1024;
	private static final long MIN_INTERVAL_SECONDS = 6 * 60 * 60;

	private WorldBackup() {
	}

	public static Path backup(MinecraftServer server, boolean force) {
		if (!Error404Config.get().backupOnStart && !force) {
			return null;
		}

		Path worldRoot = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
		Path region = worldRoot.resolve("region");

		if (!Files.isDirectory(region)) {
			return null;
		}

		Path backupDir = worldRoot.resolve("error404_backups");

		try {
			Files.createDirectories(backupDir);

			if (!force && hasRecentBackup(backupDir)) {
				Error404.LOGGER.info("[error404] skipping world backup - a recent one already exists");
				return null;
			}

			Path out = backupDir.resolve(STAMP.format(Instant.now()) + ".zip");

			try (OutputStream fos = Files.newOutputStream(out);
				 ZipOutputStream zip = new ZipOutputStream(fos);
				 Stream<Path> files = Files.walk(region)) {

				long[] written = {0L};
				files.filter(Files::isRegularFile).forEach(file -> {
					try {
						if (written[0] > MAX_BYTES) {
							return;
						}

						byte[] data = Files.readAllBytes(file);
						written[0] += data.length;
						zip.putNextEntry(new ZipEntry(region.relativize(file).toString().replace('\\', '/')));
						zip.write(data);
						zip.closeEntry();
					} catch (IOException e) {
						Error404.LOGGER.warn("[error404] backup skipped file {}", file, e);
					}
				});
			}

			Error404.LOGGER.info("[error404] world backup written to {}", out);
			return out;
		} catch (IOException e) {
			Error404.LOGGER.error("[error404] world backup failed", e);
			return null;
		}
	}

	private static boolean hasRecentBackup(Path backupDir) throws IOException {
		Instant cutoff = Instant.now().minusSeconds(MIN_INTERVAL_SECONDS);

		try (Stream<Path> files = Files.list(backupDir)) {
			return files.filter(p -> p.toString().endsWith(".zip"))
					.anyMatch(p -> {
						try {
							return Files.getLastModifiedTime(p).toInstant().isAfter(cutoff);
						} catch (IOException e) {
							return false;
						}
					});
		}
	}
}
