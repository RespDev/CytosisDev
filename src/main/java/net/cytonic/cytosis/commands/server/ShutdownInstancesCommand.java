package net.cytonic.cytosis.commands.server;

import net.cytonic.cytosis.Cytosis;
import net.cytonic.cytosis.config.CytosisSettings;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;

import static net.cytonic.utils.MiniMessageTemplate.MM;

/**
 * A command for closing all instances of cytosis on a kubernetes cluster
 */
public class ShutdownInstancesCommand extends Command {

    /**
     * A command to close all instances
     */
    public ShutdownInstancesCommand() {
        super("shutdowninstances");
        setCondition((sender, _) -> sender.hasPermission("cytosis.commands.shutdowninstances"));
        var typeArg = ArgumentType.Word("type").from("cytosis", "proxy");
        typeArg.setCallback((sender, _) -> sender.sendMessage(MM."<red>ERROR"));
        typeArg.setSuggestionCallback((_, _, suggestion) -> {
            suggestion.addEntry(new SuggestionEntry("cytosis"));
            suggestion.addEntry(new SuggestionEntry("proxy"));
        });
        addSyntax((sender, context) -> {
            if (!sender.hasPermission("cytosis.commands.shutdowninstances")) {
                sender.sendMessage(MM."<RED>You do not have permission to use this command!");
                return;
            }
            if (!CytosisSettings.KUBERNETES_SUPPORTED) {
                sender.sendMessage(MM."<RED>This command is not supported on this server!");
                return;
            }
            String type = context.get(typeArg);
            if (type.equalsIgnoreCase("proxy")) {
                Cytosis.getContainerizedInstanceManager().shutdownAllProxyInstances();
                sender.sendMessage(MM."<GREEN>Shutting down Proxy instances!");
            } else if (type.equalsIgnoreCase("cytosis")) {
                Cytosis.getContainerizedInstanceManager().shutdownAllCytosisInstances();
                sender.sendMessage(MM."<GREEN>Shutting down Cytosis instances!");
            }
        }, typeArg);
    }
}
