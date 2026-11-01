package com.edgewave.combat;

import com.edgewave.item.SlashSword;
import com.edgewave.net.CdPayload;
import com.edgewave.particle.Fx;
import com.edgewave.util.Effects;
import com.edgewave.util.Snd;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Six sword skills on keys, each with its own shape and behaviour:
 * 0 Cyclone (flat spinning blades that pull in), 1 Cross Cut (delayed X in the air), 2 Dash (blink + scribbles),
 * 3 Blade Rain (vertical lines falling in an area), 4 Spiral Lance (long helix), 5 Orbit (blades that circle and strike).
 */
public final class Skills {
    private Skills() {}

    public static final int COUNT = 6;
    public static final int[] COOLDOWN = {120, 160, 100, 220, 140, 300};
    private static final Random R = Fx.R;
    private static final Map<UUID, long[]> CD = new HashMap<>();

    public static void use(ServerPlayerEntity p, int slot) {
        if (slot < 0 || slot >= COUNT || !p.isAlive()) return;
        if (!(p.getMainHandStack().getItem() instanceof SlashSword)) return;
        ServerWorld w = (ServerWorld) p.getWorld();
        long[] cd = CD.computeIfAbsent(p.getUuid(), k -> new long[COUNT]);
        if (w.getTime() < cd[slot]) return;
        boolean ok = switch (slot) {
            case 0 -> cyclone(w, p);
            case 1 -> crossCut(w, p);
            case 2 -> dash(w, p);
            case 3 -> rain(w, p);
            case 4 -> lance(w, p);
            default -> orbit(w, p);
        };
        if (!ok) return;
        cd[slot] = w.getTime() + COOLDOWN[slot];
        p.swingHand(Hand.MAIN_HAND, true);
        if (ServerPlayNetworking.canSend(p, CdPayload.ID)) ServerPlayNetworking.send(p, new CdPayload(slot, COOLDOWN[slot]));
    }

    // ------------------------------------------------------------------ helpers
    private static Vec3d aim(ServerWorld w, ServerPlayerEntity p, double range) {
        Vec3d eye = p.getEyePos(), end = eye.add(p.getRotationVec(1f).multiply(range));
        BlockHitResult bh = w.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        return bh.getType() == HitResult.Type.MISS ? end : bh.getPos();
    }

    private static Vec3d ground(ServerWorld w, Vec3d at, ServerPlayerEntity p) {
        BlockHitResult bh = w.raycast(new RaycastContext(at.add(0, 2, 0), at.add(0, -14, 0), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        return bh.getType() == HitResult.Type.MISS ? at : bh.getPos();
    }

    private static boolean freeAt(ServerWorld w, Vec3d feet) {
        BlockPos a = BlockPos.ofFloored(feet), b = a.up();
        return w.getBlockState(a).getCollisionShape(w, a).isEmpty() && w.getBlockState(b).getCollisionShape(w, b).isEmpty();
    }

    private static Vec3d walkFree(ServerWorld w, Vec3d start, Vec3d end) {
        int steps = (int) Math.max(1, Math.ceil(start.distanceTo(end) / 0.5));
        Vec3d last = start;
        for (int i = 1; i <= steps; i++) {
            Vec3d q = start.add(end.subtract(start).multiply((double) i / steps));
            if (!freeAt(w, q)) break;
            last = q;
        }
        return last;
    }

    private static Vec3d flat(Vec3d v) {
        Vec3d f = new Vec3d(v.x, 0, v.z);
        return f.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : f.normalize();
    }

    private static Vec3d mid(LivingEntity e) {
        return e.getPos().add(0, e.getHeight() / 2, 0);
    }

    // ------------------------------------------------------------------ 0. CYCLONE: flat blades sweeping at three heights, sucking enemies in
    private static boolean cyclone(ServerWorld w, ServerPlayerEntity p) {
        Snd.play(w, p.getPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.4f, 0.6f);
        Snd.play(w, p.getPos(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.6f);
        Effects.add(t -> {
            if (t > 16 || !p.isAlive()) return false;
            Vec3d c = p.getPos();
            for (int level = 0; level < 3; level++) {
                double h = 0.4 + level * 0.75;
                for (int tail = 0; tail < 5; tail++) {                       // each blade drags a fading tail
                    double a = t * 0.62 + level * 2.1 - tail * 0.16;
                    Vec3d dir = new Vec3d(Math.cos(a), 0, Math.sin(a));
                    for (double r = 1.2 + level * 0.3; r <= 4.8; r += 0.45)
                        Fx.vel(w, tail == 0 ? "e_core" : "e_arc", c.add(dir.x * r, h, dir.z * r), new Vec3d(-dir.z * 0.1, 0, dir.x * 0.1));
                }
            }
            if (t <= 9) {
                for (LivingEntity e : Slash.targets(w, new Box(c, c).expand(5.5), p)) {
                    Vec3d in = c.subtract(e.getPos());
                    Vec3d n = flat(in);
                    e.addVelocity(n.x * 0.16, 0.03, n.z * 0.16);
                    e.velocityModified = true;
                }
            }
            if (t == 10) {
                Snd.play(w, c, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.8f, 1.7f);
                for (LivingEntity e : Slash.targets(w, new Box(c, c).expand(5.2), p)) {
                    Slash.hurt(w, e, p, 10f);
                    Vec3d out = flat(e.getPos().subtract(c));
                    e.addVelocity(out.x * 1.1, 0.45, out.z * 1.1);
                    e.velocityModified = true;
                    Slash.impact(w, mid(e), 16);
                }
                Effects.groundRing(w, c.add(0, 0.1, 0), 5.5, 8, "e_arc", "e_spark");
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 1. CROSS CUT: marks a spot, then an X appears in the air
    private static boolean crossCut(ServerWorld w, ServerPlayerEntity p) {
        Vec3d look = p.getRotationVec(1f);
        Vec3d c = aim(w, p, 14).subtract(look.multiply(0.6));
        Vec3d right = Slash.rightOf(look);
        Vec3d up = right.crossProduct(look).normalize();
        Vec3d d1 = right.add(up).normalize(), d2 = right.subtract(up).normalize();
        Snd.play(w, p.getPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.9f);
        Effects.add(t -> {
            if (t > 14 || !p.isAlive()) return false;
            double len = t <= 12 ? 5.0 * t / 12.0 : 5.0;
            if (t < 12) {                                                  // warning: thin sparks creeping along the X
                for (double r = -len; r <= len; r += 0.7) {
                    Fx.vel(w, "e_spark", c.add(d1.multiply(r)), Vec3d.ZERO);
                    Fx.vel(w, "e_spark", c.add(d2.multiply(r)), Vec3d.ZERO);
                }
                return true;
            }
            if (t == 12) {
                Snd.play(w, c, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 0.9f, 1.8f);
                Snd.play(w, c, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.2f, 0.6f);
                Slash.line(w, "e_core", c.add(d1.multiply(-5.5)), c.add(d1.multiply(5.5)), 0.28, Vec3d.ZERO);
                Slash.line(w, "e_core", c.add(d2.multiply(-5.5)), c.add(d2.multiply(5.5)), 0.28, Vec3d.ZERO);
                Slash.line(w, "e_arc", c.add(d1.multiply(-6.5)), c.add(d1.multiply(6.5)), 0.22, Vec3d.ZERO);
                Slash.line(w, "e_arc", c.add(d2.multiply(-6.5)), c.add(d2.multiply(6.5)), 0.22, Vec3d.ZERO);
                Fx.burst(w, "e_glow", c, 6, 0.2, 0.1);
                Fx.burst(w, "e_spark", c, 50, 0.6, 0.1);
                for (LivingEntity e : Slash.targets(w, new Box(c, c).expand(5.5), p)) {
                    Vec3d rel = mid(e).subtract(c);
                    boolean on = Math.abs(rel.dotProduct(look)) < 1.6 + e.getHeight() * 0.3
                            && (distToLine(rel, d1) < 1.3 || distToLine(rel, d2) < 1.3) && rel.length() < 6.5;
                    if (!on) continue;
                    Slash.hurt(w, e, p, 16f);
                    e.addVelocity(0, 0.5, 0);
                    e.velocityModified = true;
                    Slash.impact(w, mid(e), 20);
                }
            }
            return true;
        });
        return true;
    }

    private static double distToLine(Vec3d rel, Vec3d dir) {
        return rel.subtract(dir.multiply(rel.dotProduct(dir))).length();
    }

    // ------------------------------------------------------------------ 2. DASH: blink through enemies, each one is then scribbled with cuts
    private static boolean dash(ServerWorld w, ServerPlayerEntity p) {
        Vec3d start = p.getPos();
        Vec3d dir = flat(p.getRotationVec(1f));
        Vec3d end = walkFree(w, start, start.add(dir.multiply(9)));
        if (end.distanceTo(start) < 1.5) return false;
        Snd.play(w, start, SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 1.9f);
        Snd.play(w, start, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.5f);
        Slash.line(w, "e_glow", start.add(0, 0.9, 0), end.add(0, 0.9, 0), 0.3, Fx.jitter(0.02));
        Slash.line(w, "e_arc", start.add(0, 1.4, 0), end.add(0, 1.4, 0), 0.25, Vec3d.ZERO);
        Fx.burst(w, "e_core", start.add(0, 1, 0), 3, 0.1, 0.1);
        List<LivingEntity> hit = new ArrayList<>();
        Vec3d seg = end.subtract(start);
        double len = seg.length();
        for (LivingEntity e : Slash.targets(w, new Box(start, end).expand(2.2), p)) {
            Vec3d rel = e.getPos().subtract(start);
            double along = rel.dotProduct(dir);
            if (along < -0.5 || along > len + 1.0 || distToLine(rel.subtract(new Vec3d(0, rel.y, 0)), dir) > 1.8) continue;
            hit.add(e);
        }
        p.requestTeleport(end.x, end.y, end.z);
        p.fallDistance = 0;
        Effects.add(t -> {
            if (t > 6 || !p.isAlive()) return false;
            if (t == 1) Fx.burst(w, "e_glow", end.add(0, 1, 0), 10, 0.3, 0.2);
            for (LivingEntity e : hit) {
                if (!e.isAlive()) continue;
                if (t == 2 || t == 5) {                                    // scribble: random short cuts through the victim
                    Vec3d m = mid(e);
                    for (int i = 0; i < 4; i++) {
                        Vec3d a = new Vec3d(R.nextGaussian(), R.nextGaussian() * 0.6, R.nextGaussian()).normalize().multiply(1.9);
                        Slash.line(w, "e_arc", m.subtract(a), m.add(a), 0.2, Vec3d.ZERO);
                    }
                    Fx.vel(w, "e_core", m, Vec3d.ZERO);
                    Slash.hurt(w, e, p, 6f);
                    e.timeUntilRegen = 0;
                    Snd.play(w, m, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 1.4f + R.nextFloat() * 0.5f);
                }
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 3. BLADE RAIN: vertical lines fall, marked on the ground first
    private static boolean rain(ServerWorld w, ServerPlayerEntity p) {
        Vec3d centre = ground(w, aim(w, p, 15), p);
        Snd.play(w, centre, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0f, 2.0f);
        Effects.groundRing(w, centre.add(0, 0.1, 0), 6, 10, "e_arc", "e_spark");
        List<double[]> pending = new ArrayList<>();                         // {x, y, z, dueTick}
        Effects.add(t -> {
            if (t > 40 || !p.isAlive()) return false;
            if (t <= 28 && t % 2 == 0) {
                for (int i = 0; i < 2; i++) {
                    double a = R.nextDouble() * Math.PI * 2, r = Math.sqrt(R.nextDouble()) * 5.6;
                    Vec3d g = ground(w, centre.add(Math.cos(a) * r, 1, Math.sin(a) * r), p);
                    pending.add(new double[]{g.x, g.y, g.z, t + 8});
                    Fx.circle(w, "e_arc", g.add(0, 0.1, 0), 0.9, 10, 0, 0);
                }
            }
            for (int i = pending.size() - 1; i >= 0; i--) {
                double[] q = pending.get(i);
                Vec3d g = new Vec3d(q[0], q[1], q[2]);
                if (t < q[3]) {
                    Fx.vel(w, "e_spark", g.add(0, 0.2, 0), new Vec3d(0, 0.05, 0));
                    continue;
                }
                pending.remove(i);
                Slash.line(w, "e_arc", g.add(0, 11, 0), g, 0.3, Vec3d.ZERO);
                Slash.line(w, "e_core", g.add(0, 5, 0), g, 0.5, Vec3d.ZERO);
                Fx.burst(w, "e_spark", g.add(0, 0.3, 0), 22, 0.35, 0.4);
                Fx.circle(w, "e_core", g.add(0, 0.1, 0), 1.2, 12, 0, 0);
                Snd.play(w, g, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 0.7f, 1.8f);
                for (LivingEntity e : Slash.targets(w, new Box(g, g).expand(1.7), p)) {
                    if (e.getPos().subtract(g).multiply(1, 0, 1).length() > 1.7) continue;
                    Slash.hurt(w, e, p, 8f);
                    e.timeUntilRegen = 0;
                    Slash.impact(w, mid(e), 12);
                }
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 4. SPIRAL LANCE: a short charge, then a very long double helix
    private static boolean lance(ServerWorld w, ServerPlayerEntity p) {
        Snd.play(w, p.getPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.8f);
        Effects.add(t -> {
            if (t > 10 || !p.isAlive()) return false;
            Vec3d look = p.getRotationVec(1f).normalize();
            Vec3d from = p.getEyePos().add(look.multiply(0.9)).add(0, -0.3, 0);
            if (t <= 6) {                                                  // charge: cubes converge on the blade
                for (int i = 0; i < 5; i++) {
                    Vec3d off = Fx.jitter(1.0);
                    Fx.vel(w, "e_glow", from.add(off), off.multiply(-0.12));
                }
                p.setVelocity(p.getVelocity().multiply(0.5, 1, 0.5));
                p.velocityModified = true;
                return true;
            }
            Vec3d far = p.getEyePos().add(look.multiply(26));
            BlockHitResult bh = w.raycast(new RaycastContext(p.getEyePos(), far, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
            Vec3d end = bh.getType() == HitResult.Type.MISS ? far : bh.getPos();
            Vec3d seg = end.subtract(from);
            double len = seg.length();
            Vec3d dir = len < 0.01 ? look : seg.normalize();
            Vec3d right = Slash.rightOf(dir), up = right.crossProduct(dir).normalize();
            double phase = (t - 7) * 0.9;
            for (double d = 0; d <= len; d += 0.3) {
                Vec3d c = from.add(dir.multiply(d));
                Fx.vel(w, "e_core", c, Vec3d.ZERO);
                for (int s = 0; s < 2; s++) {
                    double a = d * 1.25 + phase + s * Math.PI;
                    Vec3d off = right.multiply(Math.cos(a) * 0.75).add(up.multiply(Math.sin(a) * 0.75));
                    Fx.vel(w, "e_arc", c.add(off), dir.multiply(0.1));
                }
            }
            if (t == 7) {
                Snd.play(w, from, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.5f);
                Fx.burst(w, "e_glow", end, 6, 0.3, 0.2);
                Fx.burst(w, "e_spark", end, 40, 0.5, 0.3);
                for (LivingEntity e : Slash.targets(w, new Box(from, end).expand(1.6), p)) {
                    Vec3d rel = mid(e).subtract(from);
                    double along = rel.dotProduct(dir);
                    if (along < 0 || along > len + 1 || distToLine(rel, dir) > 1.6) continue;
                    Slash.hurt(w, e, p, 16f);
                    e.addVelocity(dir.x * 1.4, 0.3, dir.z * 1.4);
                    e.velocityModified = true;
                    Slash.impact(w, mid(e), 18);
                }
            }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ 5. ORBIT: four blades circle you and strike nearby enemies
    private static boolean orbit(ServerWorld w, ServerPlayerEntity p) {
        Snd.play(w, p.getPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, 1.0f, 1.6f);
        Effects.groundRing(w, p.getPos().add(0, 0.1, 0), 3, 8, "e_arc", "e_spark");
        Effects.add(t -> {
            if (t > 140 || !p.isAlive()) return false;
            Vec3d c = p.getPos().add(0, 1.0, 0);
            double base = t * 0.33;
            for (int k = 0; k < 4; k++) {
                double a = base + k * Math.PI / 2;
                double h = 0.25 * Math.sin(t * 0.15 + k);
                Vec3d pos = c.add(Math.cos(a) * 2.1, h, Math.sin(a) * 2.1);
                Vec3d tan = new Vec3d(-Math.sin(a), 0, Math.cos(a));
                Slash.line(w, "e_core", pos.subtract(tan.multiply(0.5)), pos.add(tan.multiply(0.5)), 0.25, Vec3d.ZERO);
                for (int tail = 1; tail <= 4; tail++) {
                    double a2 = a - tail * 0.13;
                    Fx.vel(w, "e_arc", c.add(Math.cos(a2) * 2.1, h, Math.sin(a2) * 2.1), Vec3d.ZERO);
                }
            }
            if (t % 12 == 0) {
                LivingEntity best = null;
                double bd = 49;
                for (LivingEntity e : Slash.targets(w, new Box(c, c).expand(7), p)) {
                    double d = e.squaredDistanceTo(p);
                    if (d < bd) {
                        bd = d;
                        best = e;
                    }
                }
                if (best != null) {
                    double a = base + ((t / 12) % 4) * Math.PI / 2;
                    Vec3d from = c.add(Math.cos(a) * 2.1, 0, Math.sin(a) * 2.1);
                    Vec3d to = mid(best);
                    Slash.line(w, "e_core", from, to, 0.3, Vec3d.ZERO);
                    Slash.line(w, "e_arc", from, to, 0.18, Fx.jitter(0.02));
                    Slash.hurt(w, best, p, 5f);
                    best.timeUntilRegen = 0;
                    Slash.impact(w, to, 12);
                    Snd.play(w, to, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.7f);
                }
            }
            return true;
        });
        return true;
    }
}
