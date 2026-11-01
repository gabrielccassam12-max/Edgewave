package com.edgewave.combat;

import com.edgewave.particle.Fx;
import com.edgewave.util.Effects;
import com.edgewave.util.Snd;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The sword combo: diagonal, opposite diagonal, vertical, then a spinning finisher.
 * Every slash draws an arc of cubes, hurts what is in front and throws a crescent cutting wave.
 */
public final class Slash {
    private Slash() {}

    private static final class St {
        int combo;
        long last, next;
    }

    private static final Map<UUID, St> STATES = new HashMap<>();

    private static Vec3d rightOf(Vec3d look) {
        Vec3d r = look.crossProduct(new Vec3d(0, 1, 0));
        return r.lengthSquared() < 1e-4 ? new Vec3d(1, 0, 0) : r.normalize();
    }

    private static List<LivingEntity> targets(ServerWorld w, Box box, ServerPlayerEntity owner) {
        return w.getEntitiesByClass(LivingEntity.class, box, e -> e != owner && e.isAlive() && !e.isSpectator() && !e.isTeammate(owner));
    }

    private static void hurt(ServerWorld w, LivingEntity t, ServerPlayerEntity p, float amount) {
        t.damage(w.getDamageSources().playerAttack(p), amount);
    }

    private static void impact(ServerWorld w, Vec3d at, int n) {
        Fx.burst(w, "e_spark", at, n, 0.3, 0.3);
        Fx.burst(w, "e_glow", at, Math.max(2, n / 4), 0.15, 0.2);
        Fx.vel(w, "e_core", at, Vec3d.ZERO);
    }

    /** Call on every right click and every tick the button is held; it only slashes when the cooldown is over. */
    public static void fire(ServerPlayerEntity p) {
        ServerWorld w = (ServerWorld) p.getWorld();
        long now = w.getTime();
        St s = STATES.computeIfAbsent(p.getUuid(), k -> new St());
        if (now < s.next) return;
        if (now - s.last > 26) s.combo = 0;
        int step = s.combo % 4;
        s.combo++;
        s.last = now;
        boolean fin = step == 3;
        s.next = now + (fin ? 10 : 6);

        float dmg = fin ? 11f : 7f;
        p.swingHand(Hand.MAIN_HAND, true);
        Snd.play(w, p.getPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.3f + step * 0.1f);
        Snd.play(w, p.getPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.6f);
        if (fin) Snd.play(w, p.getPos(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 1.2f, 0.7f);
        Vec3d look = p.getRotationVec(1f);
        p.addVelocity(look.x * 0.3, 0, look.z * 0.3);
        p.velocityModified = true;

        slashFx(p, step, fin);

        Vec3d eye = p.getEyePos();
        for (LivingEntity e : targets(w, new Box(eye, eye).expand(4.5), p)) {
            Vec3d to = e.getPos().add(0, e.getHeight() / 2, 0).subtract(eye);
            double d = to.length();
            if (d > (fin ? 3.9 : 3.7)) continue;
            if (!fin && d > 0.8 && to.normalize().dotProduct(look) < 0.3) continue;
            hurt(w, e, p, dmg);
            Vec3d k = to.normalize().multiply(fin ? 0.9 : 0.35);
            e.addVelocity(k.x, fin ? 0.35 : 0.1, k.z);
            e.velocityModified = true;
            impact(w, e.getPos().add(0, e.getHeight() / 2, 0), 10);
        }
        wave(p, step, fin);
    }

    // ------------------------------------------------------------------ the arc drawn in front of the player
    private static void slashFx(ServerPlayerEntity p, int step, boolean spin) {
        ServerWorld w = (ServerWorld) p.getWorld();
        Effects.add(t -> {
            if (t > 3 || !p.isAlive()) return false;
            Vec3d look = p.getRotationVec(1f);
            Vec3d right = rightOf(look);
            Vec3d up = right.crossProduct(look);
            Vec3d eye = p.getEyePos().add(0, -0.3, 0);
            int n = 26;
            int from = (t - 1) * n / 3, to = t * n / 3;
            for (int k = from; k < to; k++) {
                double f = k / (double) (n - 1);
                double ang = Math.toRadians(-70 + 140 * f);
                for (double r = 1.7; r <= 3.1; r += 0.7) {
                    Vec3d dir;
                    if (spin) {
                        double a = f * Math.PI * 2;
                        dir = new Vec3d(Math.cos(a), 0, Math.sin(a));
                    } else if (step == 2) {
                        dir = look.multiply(Math.cos(ang)).add(up.multiply(Math.sin(ang)));
                    } else {
                        double sgn = step == 0 ? 1 : -1;
                        dir = look.multiply(Math.cos(ang)).add(right.multiply(Math.sin(ang) * sgn))
                                .add(up.multiply(Math.sin(ang) * 0.35 * sgn));
                    }
                    Fx.vel(w, "e_arc", eye.add(dir.multiply(r)), dir.multiply(0.04));
                }
                if (k % 4 == 0) Fx.vel(w, "e_spark", eye.add(look.multiply(2.6)), Fx.jitter(0.1));
            }
            return true;
        });
    }

    // ------------------------------------------------------------------ the crescent that flies away
    private static void wave(ServerPlayerEntity p, int step, boolean fin) {
        ServerWorld w = (ServerWorld) p.getWorld();
        float dmg = fin ? 13f : 6f;
        double half = fin ? 3.4 : 2.0;
        Vec3d dir = p.getRotationVec(1f);
        Vec3d right = rightOf(dir);
        Vec3d up = right.crossProduct(dir);
        // tilt of the crescent follows the slash: diagonal, opposite diagonal, vertical, flat
        double roll = switch (step) {
            case 0 -> Math.toRadians(35);
            case 1 -> Math.toRadians(-35);
            case 2 -> Math.toRadians(90);
            default -> 0.0;
        };
        Vec3d axis = right.multiply(Math.cos(roll)).add(up.multiply(Math.sin(roll)));
        Vec3d perp = up.multiply(Math.cos(roll)).subtract(right.multiply(Math.sin(roll)));
        Vec3d[] pos = {p.getEyePos().add(dir.multiply(1.0)).add(0, -0.3, 0)};
        Set<Integer> hit = new HashSet<>();
        int life = fin ? 16 : 12;
        Effects.add(t -> {
            if (t > life || !p.isAlive()) return false;
            Vec3d old = pos[0], next = old.add(dir.multiply(1.5));
            BlockHitResult bh = w.raycast(new RaycastContext(old, next, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, p));
            if (bh.getType() != HitResult.Type.MISS) {
                impact(w, bh.getPos(), fin ? 40 : 18);
                return false;
            }
            pos[0] = next;
            for (int i = 0; i <= 28; i++) {
                double a = Math.toRadians(-80 + 160 * i / 28.0);
                Vec3d off = axis.multiply(Math.sin(a) * half).add(perp.multiply((Math.cos(a) - 0.6) * half * 0.8));
                Fx.vel(w, "e_arc", next.add(off), dir.multiply(0.15));
                if (i % 4 == 0) Fx.vel(w, "e_core", next.add(off), Vec3d.ZERO);
            }
            for (LivingEntity e : targets(w, new Box(next, next).expand(half + 1.0), p)) {
                if (!hit.add(e.getId())) continue;
                hurt(w, e, p, dmg);
                e.addVelocity(dir.x * 0.7, 0.3, dir.z * 0.7);
                e.velocityModified = true;
                impact(w, e.getPos().add(0, 1, 0), 16);
            }
            return true;
        });
    }
}
