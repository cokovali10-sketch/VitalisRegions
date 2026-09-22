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
            for (String o : List.of("pos1", "pos2", "remove", "help")) {
                if (o.startsWith(q)) out.add(o);
            }
            return out;
        }
        return List.of();
    }
}
