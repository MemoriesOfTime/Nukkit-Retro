package cn.nukkit.command.defaults;

import cn.nukkit.Nukkit;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginDescription;
import cn.nukkit.utils.TextFormat;

import java.util.List;
import java.util.Locale;

/**
 * Created on 2015/11/12 by xtypr.
 * Package cn.nukkit.command.defaults in project Nukkit .
 */
public class VersionCommand extends VanillaCommand {

    public VersionCommand(String name) {
        super(name,
                "%nukkit.command.version.description",
                "%nukkit.command.version.usage",
                new String[]{"ver", "about"}
        );
        this.setPermission("nukkit.command.version");
        this.commandParameters.clear();
        this.commandParameters.put("default", new CommandParameter[]{
                new CommandParameter("pluginName", CommandParameter.ARG_TYPE_STRING, true)
        });
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }

        if (args.length == 0) {
            String minVersion = ProtocolInfo.getMinecraftVersion(ProtocolInfo.SUPPORTED_PROTOCOLS.get(0));
            sender.sendMessage(
                    TextFormat.YELLOW + "#########################################\n"
                            + TextFormat.RED + sender.getServer().getName() + TextFormat.DARK_AQUA + "-" + TextFormat.LIGHT_PURPLE + Nukkit.CODENAME + "\n"
                            + TextFormat.GOLD + "Multiversion: " + TextFormat.AQUA + minVersion + " - " + ProtocolInfo.MINECRAFT_VERSION_NETWORK + "\n"
                            + TextFormat.YELLOW + "#########################################"
            );
            return true;
        }

        String pluginName = String.join(" ", args);
        Plugin exactPlugin = sender.getServer().getPluginManager().getPlugin(pluginName);
        boolean found = false;

        if (exactPlugin == null) {
            String lowerName = pluginName.toLowerCase(Locale.ROOT);
            for (Plugin plugin : sender.getServer().getPluginManager().getPlugins().values()) {
                if (plugin.getName().toLowerCase(Locale.ROOT).contains(lowerName)) {
                    exactPlugin = plugin;
                    found = true;
                }
            }
        } else {
            found = true;
        }

        if (found) {
            PluginDescription desc = exactPlugin.getDescription();
            sender.sendMessage(TextFormat.DARK_GREEN + desc.getName() + TextFormat.WHITE + " version " + TextFormat.DARK_GREEN + desc.getVersion());
            if (desc.getDescription() != null) {
                sender.sendMessage(desc.getDescription());
            }
            if (desc.getWebsite() != null) {
                sender.sendMessage("Website: " + desc.getWebsite());
            }
            List<String> authors = desc.getAuthors();
            if (authors.size() == 1) {
                sender.sendMessage("Author: " + authors.get(0));
            } else if (authors.size() >= 2) {
                sender.sendMessage("Authors: " + String.join(", ", authors));
            }
        } else {
            sender.sendMessage(new TranslationContainer("nukkit.command.version.noSuchPlugin"));
        }
        return true;
    }
}
