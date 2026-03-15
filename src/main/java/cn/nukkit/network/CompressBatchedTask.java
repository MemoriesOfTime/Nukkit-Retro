package cn.nukkit.network;

import cn.nukkit.Server;
import cn.nukkit.scheduler.AsyncTask;
import cn.nukkit.utils.Zlib;

import java.util.ArrayList;
import java.util.List;

/**
 * author: MagicDroidX
 * Nukkit Project
 */
public class CompressBatchedTask extends AsyncTask {

    public int level = 7;
    public byte[] data;
    public byte[] finalData;
    public int channel = 0;
    public List<String> targets = new ArrayList<>();
    public boolean rawDeflate = false;

    public CompressBatchedTask(byte[] data, List<String> targets) {
        this(data, targets, 7, false);
    }

    public CompressBatchedTask(byte[] data, List<String> targets, int level) {
        this(data, targets, level, false);
    }

    public CompressBatchedTask(byte[] data, List<String> targets, int level, boolean rawDeflate) {
        this(data, targets, level, 0, rawDeflate);
    }

    public CompressBatchedTask(byte[] data, List<String> targets, int level, int channel) {
        this(data, targets, level, channel, false);
    }

    public CompressBatchedTask(byte[] data, List<String> targets, int level, int channel, boolean rawDeflate) {
        this.data = data;
        this.targets = targets;
        this.level = level;
        this.channel = channel;
        this.rawDeflate = rawDeflate;
    }

    @Override
    public void onRun() {
        try {
            this.finalData = this.rawDeflate ? Zlib.deflateRaw(this.data, this.level) : Zlib.deflate(this.data, this.level);
            this.data = null;
        } catch (Exception e) {
            //ignore
        }
    }

    @Override
    public void onCompletion(Server server) {
        server.broadcastPacketsCallback(this.finalData, this.targets);
    }
}
