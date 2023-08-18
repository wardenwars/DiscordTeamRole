package net.nutchi.discordteamrole;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Team;

import javax.security.auth.login.LoginException;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class DiscordTeamRole extends JavaPlugin {
    private JDA jda;
    private String guildId;
    private String redTeamName;
    private String blueTeamName;
    private String redRoleName;
    private String blueRoleName;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String token = getConfig().getString("token");
        guildId = getConfig().getString("guildId");
        redTeamName = getConfig().getString("redTeamName", "Red");
        blueTeamName = getConfig().getString("blueTeamName", "Blue");
        redRoleName = getConfig().getString("redRoleName", "Red");
        blueRoleName = getConfig().getString("blueRoleName", "Blue");

        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS)
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .build();
        } catch(LoginException e) {
            e.printStackTrace();
        }

        if (jda == null || guildId == null) {
            getServer().getPluginManager().disablePlugin(this);
        } else {
            getServer().getScheduler().runTaskTimer(this, this::updateRole, 0, 200);
        }
    }

    private void updateRole() {
        Guild guild = jda.getGuildById(guildId);
        if (guild != null) {
            List<String> redTeamEntries = getTeamEntries(redTeamName);
            List<String> blueTeamEntries = getTeamEntries(blueTeamName);

            List<Role> redRoles = guild.getRolesByName(redRoleName, false);
            List<Role> blueRoles = guild.getRolesByName(blueRoleName, false);

            if (redRoles.isEmpty()) {
                guild.createRole().setName(redRoleName).setColor(Color.RED).queue();
            }
            if (blueRoles.isEmpty()) {
                guild.createRole().setName(blueRoleName).setColor(Color.BLUE).queue();
            }

            redTeamEntries.forEach(entry -> {
                List<Member> cachedMembers = guild.getMembersByNickname(entry, true);
                if (!cachedMembers.isEmpty()) {
                    updateRedTeamRole(cachedMembers, guild, redRoles, blueRoles);
                } else {
                    guild.retrieveMembersByPrefix(entry, 1).onSuccess(
                            members -> updateRedTeamRole(members, guild, redRoles, blueRoles));
                }
            });

            blueTeamEntries.forEach(entry -> {
                List<Member> cachedMembers = guild.getMembersByNickname(entry, true);
                if (!cachedMembers.isEmpty()) {
                    updateBlueTeamRole(cachedMembers, guild, redRoles, blueRoles);
                } else {
                    guild.retrieveMembersByPrefix(entry, 1).onSuccess(
                            members -> updateBlueTeamRole(members, guild, redRoles, blueRoles));
                }
            });
        }
    }

    private void updateRedTeamRole(List<Member> members, Guild guild, List<Role> redRoles, List<Role> blueRoles) {
        members.forEach(m -> {
            if (m.getRoles().stream().noneMatch(redRoles::contains)) {
                redRoles.stream().findFirst().ifPresent(r -> guild.addRoleToMember(m, r).queue());
            }
            if (m.getRoles().stream().anyMatch(blueRoles::contains)) {
                blueRoles.forEach(r -> guild.removeRoleFromMember(m, r).queue());
            }
        });
    }

    private void updateBlueTeamRole(List<Member> members, Guild guild, List<Role> redRoles, List<Role> blueRoles) {
        members.forEach(m -> {
            if (m.getRoles().stream().noneMatch(blueRoles::contains)) {
                blueRoles.stream().findFirst().ifPresent(r -> guild.addRoleToMember(m, r).queue());
            }
            if (m.getRoles().stream().anyMatch(redRoles::contains)) {
                redRoles.forEach(r -> guild.removeRoleFromMember(m, r).queue());
            }
        });
    }

    private List<String> getTeamEntries(String name) {
        return getTeam(name).map(t -> new ArrayList<>(t.getEntries())).orElse(new ArrayList<>());
    }

    private Optional<Team> getTeam(String name) {
        return Optional.ofNullable(getServer().getScoreboardManager())
                .flatMap(m -> Optional.ofNullable(m.getMainScoreboard().getTeam(name)));
    }

    @Override
    public void onDisable() {
        if (jda != null) {
            try {
                removeRolesFromAllMembers().get(60, TimeUnit.SECONDS);
            } catch (TimeoutException | InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }

            jda.shutdownNow();
            jda = null;
        }
    }

    private CompletableFuture<Void> removeRolesFromAllMembers() {
        CompletableFuture<Void> task = new CompletableFuture<>();

        Guild guild = jda.getGuildById(guildId);
        if (guild != null) {
            getLogger().info("Removing roles from all members");

            guild.loadMembers()
                    .onSuccess(members -> {
                        List<Role> redRoles = guild.getRolesByName(redRoleName, false);
                        List<Role> blueRoles = guild.getRolesByName(blueRoleName, false);
                        List<Role> roles = Stream.of(redRoles, blueRoles).flatMap(Collection::stream).collect(Collectors.toList());

                        members.forEach(m -> guild.modifyMemberRoles(m, null, roles).onErrorMap(t -> null).complete());

                        task.complete(null);
                    })
                    .onError(task::completeExceptionally);
        } else {
            task.complete(null);
        }

        return task;
    }
}
