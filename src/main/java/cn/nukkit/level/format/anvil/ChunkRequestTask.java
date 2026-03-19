package cn.nukkit.level.format.anvil;

import cn.nukkit.Server;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntitySpawnable;
import cn.nukkit.level.Level;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.scheduler.AsyncTask;
import cn.nukkit.utils.Binary;

import java.io.IOException;
import java.nio.ByteOrder;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class ChunkRequestTask extends AsyncTask {
    protected final int levelId;

    protected final byte[] chunk;
    protected final int chunkX;
    protected final int chunkZ;

    protected byte[] blockEntities;

    public ChunkRequestTask(Level level, Chunk chunk) {
        this.levelId = level.getId();
        this.chunk = chunk.toFastBinary();
        this.chunkX = chunk.getX();
        this.chunkZ = chunk.getZ();

        byte[] buffer = new byte[0];

        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof BlockEntitySpawnable) {
                try {
                    buffer = Binary.appendBytes(buffer, NBTIO.write(((BlockEntitySpawnable) blockEntity).getSpawnCompound(), ByteOrder.BIG_ENDIAN, true));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

            }
        }

        this.blockEntities = buffer;
    }

    @Override
    public void onRun() {
        Chunk chunk = Chunk.fromFastBinary(this.chunk);
        this.setResult(Anvil.serializeNetworkChunkPayload(chunk, this.blockEntities));
    }

    @Override
    public void onCompletion(Server server) {
        Level level = server.getLevel(this.levelId);
        if (level != null && this.hasResult()) {
            level.chunkRequestCallback(this.chunkX, this.chunkZ, (byte[]) this.getResult());
        }
    }
}
