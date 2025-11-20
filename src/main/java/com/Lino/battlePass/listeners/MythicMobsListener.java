package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MythicMobsListener implements Listener {

    private final BattlePass plugin;

    public MythicMobsListener(BattlePass plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        if (event.getKiller() instanceof Player) {
            Player player = (Player) event.getKiller();
            // Ottiene l'Internal Name del mob (es. "SkeletonKing")
            String mobInternalName = event.getMobType().getInternalName();

            // Invia il progresso al gestore delle missioni
            // Tipo missione: KILL_MYTHIC_MOB
            plugin.getMissionManager().progressMission(player, "KILL_MYTHIC_MOB", mobInternalName, 1);
        }
    }
}