package co.marcin.novaguilds.listener;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import co.marcin.novaguilds.basic.NovaGuild;
import co.marcin.novaguilds.basic.NovaPlayer;
import co.marcin.novaguilds.runnable.RunnableRaid;
import co.marcin.novaguilds.utils.StringUtils;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import co.marcin.novaguilds.NovaGuilds;
import co.marcin.novaguilds.basic.NovaRegion;

public class MoveListener implements Listener {
	private final NovaGuilds plugin;

	public MoveListener(NovaGuilds novaGuilds) {
		plugin = novaGuilds;
	}

	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
		Location from = event.getFrom();
		Location to = event.getTo();
		
		NovaRegion fromRegion = plugin.getRegionManager().getRegionAtLocation(from);
		NovaRegion toRegion = plugin.getRegionManager().getRegionAtLocation(to);

		final Player player = event.getPlayer();
		
		//entering
		if(fromRegion == null) {
			if(toRegion != null) {
				//TODO: TEST BORDER PARTICLES
				List<Block> blocks = plugin.getRegionManager().getBorderBlocks(toRegion);
				for(Block block : blocks) {
					block.getLocation().setY(block.getLocation().getY()+1);
					block.getWorld().playEffect(block.getLocation(), Effect.SMOKE, 100);
				}

				HashMap<String,String> vars = new HashMap<>();
				vars.put("GUILDNAME",toRegion.getGuildName());
				vars.put("PLAYERNAME",player.getName());
				plugin.sendMessagesMsg(player, "chat.region.entered", vars);

				NovaPlayer nPlayer = plugin.getPlayerManager().getPlayerByPlayer(player);
				nPlayer.setAtRegion(toRegion);

				//TODO add config
				if(nPlayer.hasGuild()) {
					if(!nPlayer.getGuild().getName().equalsIgnoreCase(toRegion.getGuildName())) {
						final NovaGuild guildDefender = plugin.getGuildManager().getGuildByRegion(toRegion);

						if(nPlayer.getGuild().isWarWith(guildDefender)) {
							if(!guildDefender.isRaid()) {
								if(NovaGuilds.systemSeconds() - plugin.timeRest > guildDefender.getTimeRest()) {
									guildDefender.createRaid(nPlayer.getGuild());
									plugin.guildRaids.add(guildDefender);
								} else {
									long timeWait = plugin.timeRest - (NovaGuilds.systemSeconds() - guildDefender.getTimeRest());
									vars.put("TIMEREST", StringUtils.secondsToString(timeWait));

									plugin.sendMessagesMsg(player, "chat.raid.resting", vars);
								}
							}

							if(guildDefender.isRaid()) {
								guildDefender.getRaid().addPlayerOccupying(nPlayer);
								Runnable task = new RunnableRaid(plugin);
								plugin.worker.schedule(task, 1, TimeUnit.SECONDS);
							}
						}

						plugin.broadcastGuild(plugin.getGuildManager().getGuildByRegion(toRegion), "chat.region.notifyguild.entered", vars);
					}
				}
			}
		}
		
		//exiting
		if(fromRegion != null) {
			if(toRegion == null) {
				HashMap<String,String> vars = new HashMap<>();
				vars.put("GUILDNAME", fromRegion.getGuildName());
				plugin.sendMessagesMsg(event.getPlayer(), "chat.region.exited", vars);

				NovaPlayer nPlayer = plugin.getPlayerManager().getPlayerByPlayer(event.getPlayer());
				NovaGuild guild = plugin.getGuildManager().getGuildByRegion(fromRegion);

				if(nPlayer.hasGuild()) {
					if(nPlayer.getGuild().isWarWith(guild)) {
						if(guild.isRaid()) {
							guild.getRaid().removePlayerOccupying(nPlayer);

							if(guild.getRaid().getPlayersOccupyingCount() == 0) {
								guild.getRaid().resetProgress();
								plugin.resetWarBar(guild);
								plugin.resetWarBar(nPlayer.getGuild());
								plugin.debug("progress: " + guild.getRaid().getProgress());
								guild.getRaid().updateInactiveTime();
							}
						}
					}
				}
			}
		}
	}
}
