package com.vitalis.rg.command;

import com.vitalis.rg.util.Perms;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Paper Brigadier wrapper around RgCommand (paper-plugin.yml has no YAML commands). */
public class AreaCommand implements BasicCommand {
    private final RgCommand commands;

    public AreaCommand(RgCommand commands) {
        this.commands = commands;
    }

    @Override
    public void execute(CommandSourceStack stack, String[] args) {
        if (!(stack.getExecutor() instanceof Player p)) {
            stack.getSender().sendMessage("Only players.");
            return;
        }
        commands.handle(p, args);
    }

    @Override
    public Collection<String> suggest(CommandSourceStack stack, String[] args) {
        CommandSender sender = stack.getSender();
        if (!(sender instanceof Player p) || !Perms.has(p, "use")) return List.of();
        if (args.length <= 1) {
            String q = args.length == 1 ? args[0].toLowerCase(Locale.ROOT) : "";
            List<String> out = new ArrayList<>();
            for (String o : List.of("pos1", "pos2", "remove", "help", "kick")) {
                if (o.startsWith(q)) out.add(o);
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("kick")) {
            String q = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (Player o : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (o.getName().toLowerCase(Locale.ROOT).startsWith(q)) out.add(o.getName());
            }
            return out;
        }
        // nick TAB-completion for add/del routes
        if (args.length == 3) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            String q = args[2].toLowerCase(Locale.ROOT);
            if (sub.equals("ownadd") || sub.equals("memadd")) {
                List<String> out = new ArrayList<>();
                for (Player o : org.bukkit.Bukkit.getOnlinePlayers()) {
                    if (o.getName().toLowerCase(Locale.ROOT).startsWith(q)) out.add(o.getName());
                }
                return out;
            }
            if (sub.equals("owndel") || sub.equals("memdel")) {
                com.vitalis.rg.model.Region r = commands.region(args[1]);
                if (r != null && Perms.canManage(p, r)) {
                    java.util.Map<UUID, String> map = sub.equals("owndel") ? r.owners : r.members;
                    List<String> out = new ArrayList<>();
                    for (java.util.Map.Entry<UUID, String> e : map.entrySet()) {
                        if (sub.equals("owndel") && e.getKey().equals(r.owner)) continue;
                        if (e.getValue().toLowerCase(Locale.ROOT).startsWith(q)) out.add(e.getValue());
                    }
                    return out;
                }
            }
        }
        return List.of();
    }
}
