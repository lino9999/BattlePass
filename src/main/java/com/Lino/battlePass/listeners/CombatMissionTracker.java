package com.Lino.battlePass.listeners;

import com.Lino.battlePass.BattlePass;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.projectiles.ProjectileSource;
import java.util.*;

/** Tracks combat missions and translates damage and entity targets. */
final class CombatMissionTracker {

    private final BattlePass plugin;

    CombatMissionTracker(BattlePass plugin) {
        this.plugin = plugin;
    }

    void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        EntityDamageEvent last = player.getLastDamageCause();
        if (last == null) {
            plugin.getMissionManager().progressMission(player, "DEATH", "UNKNOWN", 1);
            return;
        }

        plugin.getMissionManager().progressMission(player, "DEATH", enumerateDamageTypes(last), 1);
    }

    void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        int amount = calculateDamageTakenAmount(player, event);
        if (amount <= 0) return;

        plugin.getMissionManager().progressMission(player, "DAMAGE_TAKEN", enumerateDamageTypes(event), amount);
    }

    void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity trueDamager = getTrueDamager(event.getDamager());
        if (trueDamager instanceof Player player) {
            int amount = calculateDamageDealtAmount(event);
            if (amount <= 0) return;
            List<String> victimTypes = enumerateVictimTypes(event.getEntity());
            plugin.getMissionManager().progressMission(player, "DAMAGE_DEALT", victimTypes, amount);
        }
    }

    void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() != null) {
            Player killer = event.getEntity().getKiller();

            if (event.getEntity() instanceof Player) {
                plugin.getMissionManager().progressMission(killer, "KILL_PLAYER", "PLAYER", 1);
            } else {
                String entityType = event.getEntityType().name();
                plugin.getMissionManager().progressMission(killer, "KILL_MOB", entityType, 1);
            }
        }
    }

    private int calculateDamageTakenAmount(Player player, EntityDamageEvent event) {
        double finalDamage = event.getFinalDamage();
        double rawDamage = event.getDamage();

        // If player is blocking with a shield and took no final damage, the attack was blocked.
        if (player.isBlocking() && finalDamage <= 0) {
            return 0;
        }

        double effectiveDamage;
        if (finalDamage > 0) {
            effectiveDamage = finalDamage;
        } else if (rawDamage > 0) {
            effectiveDamage = rawDamage;
        } else if (finalDamage < 0) {
            effectiveDamage = Math.abs(finalDamage);
        } else {
            effectiveDamage = 0;
        }

        int amount = (int) Math.round(effectiveDamage);
        if (effectiveDamage > 0 && amount == 0) {
            amount = 1;
        }
        return Math.max(0, amount);
    }

    private int calculateDamageDealtAmount(EntityDamageByEntityEvent event) {
        double finalDamage = event.getFinalDamage();
        double rawDamage = event.getDamage();

        double effectiveDamage;
        if (finalDamage > 0) {
            effectiveDamage = finalDamage;
        } else if (rawDamage > 0) {
            effectiveDamage = rawDamage;
        } else if (finalDamage < 0) {
            effectiveDamage = Math.abs(finalDamage);
        } else {
            effectiveDamage = 0;
        }

        int amount = (int) Math.round(effectiveDamage);
        if (effectiveDamage > 0 && amount == 0) {
            amount = 1;
        }
        return Math.max(0, amount);
    }

    private List<String> enumerateVictimTypes(Entity victim) {
        List<String> targets = new ArrayList<>();
        targets.add(safeName(victim.getType().name()));
        if (victim instanceof Player) {
            targets.add("PLAYER");
        } else if (victim instanceof Monster) {
            targets.add("MONSTER");
            targets.add("MOB");
            targets.add("MOBS");
        } else if (victim instanceof Animals || victim instanceof WaterMob || victim instanceof Ambient) {
            targets.add("ANIMAL");
            targets.add("ANIMALS");
            targets.add("MOB");
            targets.add("MOBS");
        } else if (victim instanceof LivingEntity) {
            targets.add("MOB");
            targets.add("MOBS");
        }
        targets.add("ANY");
        return new ArrayList<>(new LinkedHashSet<>(targets));
    }

    private List<String> enumerateDamageTypes(EntityDamageEvent event) {
        List<String> result = new ArrayList<>();

        if (event instanceof EntityDamageByEntityEvent edbe) {
            Entity rawDamager = edbe.getDamager();

            if (rawDamager instanceof Projectile projectile) {
                result.add(safeName(projectile.getType().name()));

                ProjectileSource shooter = projectile.getShooter();
                if (shooter instanceof Entity shooterEntity) {
                    if (shooterEntity instanceof Player) {
                        result.add("PLAYER");
                    } else if (shooterEntity instanceof LivingEntity livingShooter) {
                        String mobBase = safeName(livingShooter.getType().name());
                        result.add(mobBase);
                        if (livingShooter instanceof Monster) {
                            result.add("MONSTER");
                            result.add("MOB");
                            result.add("MOBS");
                        }
                    } else {
                        result.add(safeName(shooterEntity.getType().name()));
                    }
                }
            } else {
                if (rawDamager instanceof Player) {
                    result.add("PLAYER");
                } else if (rawDamager instanceof LivingEntity living) {
                    result.add(safeName(living.getType().name()));
                    if (living instanceof Monster) {
                        result.add("MONSTER");
                        result.add("MOB");
                        result.add("MOBS");
                    }
                } else {
                    result.add(safeName(rawDamager.getType().name()));
                }
            }
        }

        result.add(safeName(getCauseName(event.getCause())));
        result.add("ANY");

        return new ArrayList<>(new LinkedHashSet<>(result));
    }

    private Entity getTrueDamager(Entity damager) {
        if (damager instanceof Projectile projectile) {
            ProjectileSource ps = projectile.getShooter();
            if (ps instanceof Entity shooterEntity) return shooterEntity;
        }
        return damager;
    }

    private String getCauseName(EntityDamageEvent.DamageCause cause) {
        switch (cause) {
            case FIRE, FIRE_TICK -> {
                return "FIRE";
            }
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> {
                return "EXPLOSION";
            }
            default -> {
                return cause.name();
            }
        }
    }

    private String safeName(String s) {
        return s == null ? "UNKNOWN" : s.replaceAll("[^A-Z0-9_]", "_").toUpperCase();
    }
}
