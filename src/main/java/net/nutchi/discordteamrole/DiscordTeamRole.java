package net.nutchi.discordteamrole;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.security.auth.login.LoginException;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

public final class DiscordTeamRole extends JavaPlugin {
    private JDA jda;
    private String token;
    private String guildId;
    private String redTeamName;
    private String blueTeamName;
    private String redRoleName;
    private String blueRoleName;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        token = getConfig().getString("token");
        guildId = getConfig().getString("guildId");
        redTeamName = getConfig().getString("redTeamName");
        blueTeamName = getConfig().getString("blueTeamName");
        redRoleName = getConfig().getString("redRoleName");
        blueRoleName = getConfig().getString("blueRoleName");

        if (token != null && guildId != null && redTeamName != null && blueTeamName != null
                && redRoleName != null && blueRoleName != null) {
            try {
                jda = JDABuilder.createDefault(token)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS)
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .build();
                getServer().getScheduler().runTaskTimer(this, () -> updateRole(), 0, 200);
            } catch(LoginException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateRole() {
        Guild guild = jda.getGuildById(guildId);
        if (guild != null) {
            List<String> redTeamEntries = getTeamEntries(redTeamName);
            List<String> blueTeamEntries = getTeamEntries(blueTeamName);

            List<Role> redRoles = guild.getRolesByName(redRoleName, false);
            List<Role> blueRoles = guild.getRolesByName(blueRoleName, false);

            if (redRoles.size() == 0) {
                guild.createRole().setName(redRoleName).setColor(Color.RED).queue();
            }
            if (blueRoles.size() == 0) {
                guild.createRole().setName(blueRoleName).setColor(Color.BLUE).queue();
            }

            redTeamEntries.forEach(e -> {
                List<Member> cachedMembers = guild.getMembersByNickname(e, true);
                if (cachedMembers.size() != 0) {
                    updateRedTeamRole(cachedMembers, guild, redRoles, blueRoles);
                } else {
                    guild.retrieveMembersByPrefix(e, 1).onSuccess(
                            members -> updateRedTeamRole(members, guild, redRoles, blueRoles));
                }
            });

            blueTeamEntries.forEach(e -> {
                List<Member> cachedMembers = guild.getMembersByNickname(e, true);
                if (cachedMembers.size() != 0) {
                    updateBlueTeamRole(cachedMembers, guild, redRoles, blueRoles);
                } else {
                    guild.retrieveMembersByPrefix(e, 1).onSuccess(
                            members -> updateBlueTeamRole(members, guild, redRoles, blueRoles));
                }
            });
        }
    }

    private void updateRedTeamRole(List<Member> members, Guild guild, List<Role> redRoles, List<Role> blueRoles) {
        members.forEach(m -> {
            if (!m.getRoles().stream().anyMatch(r -> redRoles.contains(r))) {
                redRoles.stream().findFirst().ifPresent(r -> guild.addRoleToMember(m, r).queue());
            };
            if (m.getRoles().stream().anyMatch(r -> blueRoles.contains(r))) {
                blueRoles.forEach(r -> guild.removeRoleFromMember(m, r).queue());
            }
        });
    }

    private void updateBlueTeamRole(List<Member> members, Guild guild, List<Role> redRoles, List<Role> blueRoles) {
        members.forEach(m -> {
            if (!m.getRoles().stream().anyMatch(r -> blueRoles.contains(r))) {
                blueRoles.stream().findFirst().ifPresent(r -> guild.addRoleToMember(m, r).queue());
            };
            if (m.getRoles().stream().anyMatch(r -> redRoles.contains(r))) {
                redRoles.forEach(r -> guild.removeRoleFromMember(m, r).queue());
            }
        });
    }

    private List<String> getTeamEntries(String name) {
        return getTeam(name).map(t -> t.getEntries().stream().collect(Collectors.toList())).orElse(new ArrayList<>());
    }

    private Optional<Team> getTeam(String name) {
        ScoreboardManager manager = getServer().getScoreboardManager();
        if (manager != null) {
            Team team = manager.getMainScoreboard().getTeam(name);
            if (team != null) {
                return Optional.of(team);
            }
        }
        return Optional.empty();
    }

    @Override
    public void onDisable() {
        if (jda != null) {
            jda.shutdownNow();
            jda = null;
        }
    }
}
