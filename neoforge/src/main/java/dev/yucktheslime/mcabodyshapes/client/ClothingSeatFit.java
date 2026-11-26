package dev.yucktheslime.mcabodyshapes.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.LivingEntity;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Looks at an MCA clothing (or skin) texture and decides how the seat should
 * sample it: whether the jacket/outer overlay actually has pixels, whether
 * the outfit is a short skirt or dress that should hang over the seat, and
 * the last body-space Y that still has garment (so we do not stretch a waist
 * strap or a shoe row down the rest of the seat).
 */
final class ClothingSeatFit {
    static final Info DEFAULT = new Info(false, false, false, 24.0f, 24.0f, 8.0f, 8.0f, 8.0f, 8.0f);

    /**
     * @param jacket   true if the body-wear / jacket back has real clothing
     * @param skirt    true if lower-body clothing covers the thighs then
     *                 stops (a skirt, short dress, or similar), so the seat
     *                 should stay covered by that outer fabric
     * @param clampY   last body-space Y of inner-layer garment; slices past
     *                 this keep sampling that row instead of empty/shoe texels
     * @param jacketClampY last body-space Y of the jacket/legwear overlay
     * @param pantsY   first body-back row that is hip / pants / skirt fabric,
     *                 after any waist bow or strap so those details stay off
     *                 the seat's top face
     * @param jacketPantsY same as pantsY, on the jacket-back strip
     * @param shirtEndY    first body-space Y on the front strip that is no
     *                     longer the shirt (a belt, a pants waist, or a colour
     *                     break). The pregnancy belly stays in the rows above
     *                     this so the belt cannot smear across the bump.
     * @param jacketShirtEndY same as shirtEndY, on the jacket front
     */
    record Info(boolean jacket, boolean skirt, boolean backPockets, float clampY, float jacketClampY,
                float pantsY, float jacketPantsY, float shirtEndY, float jacketShirtEndY) {
    }

    private static final Map<ResourceLocation, Info> CACHE = new ConcurrentHashMap<>();

    private ClothingSeatFit() {
    }

    static Info of(LivingEntity entity) {
        if (entity == null) {
            return DEFAULT;
        }
        VillagerLike<?> villager;
        if (entity instanceof VillagerLike<?> typed) {
            villager = typed;
        } else {
            villager = CommonVillagerModel.getVillager(entity);
        }
        if (villager == null) {
            return DEFAULT;
        }
        String clothes = villager.getClothes();
        if (clothes == null || clothes.isBlank()) {
            return DEFAULT;
        }
        try {
            if (villager.isBurned() && clothes.contains("/normal/")) {
                clothes = clothes.replace("/normal/", "/burnt/");
            }
            if (!clothes.contains(":")) {
                clothes = "mca:" + clothes;
            }
            if (!clothes.endsWith(".png")) {
                clothes = clothes + ".png";
            }
            ResourceLocation id = ResourceLocation.parse(clothes);
            return CACHE.computeIfAbsent(id, ClothingSeatFit::analyze);
        } catch (Exception ignored) {
            return DEFAULT;
        }
    }

    private static Info analyze(ResourceLocation id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return DEFAULT;
        }
        Optional<Resource> resource = mc.getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            return DEFAULT;
        }
        try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
            if (image.getWidth() < 64 || image.getHeight() < 64) {
                return DEFAULT;
            }
            // Jacket / body-wear back: texOffs(16, 32) → (32, 36) 8×12.
            int jacketOpaque = opaque(image, 32, 36, 8, 12);
            int jacketLower = opaque(image, 32, 40, 8, 8);
            boolean jacket = jacketOpaque >= 8 && jacketLower >= 4;

            // Lower body back, upper / mid legs.
            float bodyLow = opaque(image, 32, 28, 8, 4) / 32.0f;
            float upLegs = (opaque(image, 12, 20, 4, 3) + opaque(image, 28, 52, 4, 3)) / 24.0f;
            float midLegs = (opaque(image, 12, 24, 4, 5) + opaque(image, 28, 56, 4, 5)) / 40.0f;
            boolean skirt = bodyLow >= 0.80f && upLegs >= 0.55f && midLegs < 0.35f;

            // Clamp is used to hold the last opaque *body-back* or jacket-back
            // row (the renderer caps at 12 so we never sample a split leg UV).
            float clampY = lastGarmentY(image, false);
            float jacketClampY = jacket ? lastGarmentY(image, true) : 24.0f;
            float pantsY = pantsStartY(image, false);
            float jacketPantsY = jacket ? pantsStartY(image, true) : 8.0f;
            float shirtEndY = frontShirtEndY(image, false);
            float jacketShirtEndY = jacket ? frontShirtEndY(image, true) : 8.0f;
            boolean backPockets = !skirt && detectBackPockets(image);
            return new Info(jacket, skirt, backPockets, clampY, jacketClampY, pantsY, jacketPantsY,
                    shirtEndY, jacketShirtEndY);
        } catch (IOException ignored) {
            return DEFAULT;
        }
    }

    /**
     * Walk down the body back and then the legs, and return the body-space
     * Y just after the last mostly-opaque garment row, stopping at the first
     * gap so a short skirt does not pick up shoes further down.
     */
    private static float lastGarmentY(NativeImage image, boolean overlay) {
        float last = overlay ? 8.0f : 8.0f;
        boolean seen = false;
        // Body rows y = 0..12 (or jacket rows with +16 v).
        int bodyV = overlay ? 36 : 20;
        for (int row = 0; row < 12; row++) {
            int op = overlay
                    ? opaque(image, 32, bodyV + row, 8, 1)
                    : opaque(image, 32, bodyV + row, 8, 1);
            if (op >= 4) {
                last = row + 1;
                seen = true;
            } else if (seen && row >= 8) {
                return last;
            }
        }
        // Legs: y = 12 + row. Overlay uses right/left legwear if present.
        for (int row = 0; row < 12; row++) {
            int op;
            if (overlay) {
                // Right legwear back (12, 36), left (12, 52).
                op = opaque(image, 12, 36 + row, 4, 1) + opaque(image, 12, 52 + row, 4, 1);
            } else {
                op = opaque(image, 12, 20 + row, 4, 1) + opaque(image, 28, 52 + row, 4, 1);
            }
            if (op >= 4) {
                last = 12.0f + row + 1;
                seen = true;
            } else if (seen) {
                return last;
            }
        }
        return last;
    }

    /**
     * First body-back row that is hip / pants / lower-skirt fabric, skipping
     * any mid-back waist bow or strap (bright highlight against darker cloth).
     * If the outfit has no such accessory, this is the lower third of the
     * torso, just above the hip seam.
     */
    /**
     * First body-space Y on the torso front (u 20..28) that no longer matches
     * the mid-shirt colour — the belt, a pants waist, or a similar break.
     * Returns 12 if the shirt runs the full torso.
     */
    private static float frontShirtEndY(NativeImage image, boolean overlay) {
        int bodyV = overlay ? 36 : 20;
        int u0 = 20;
        long r = 0, g = 0, b = 0;
        int n = 0;
        int width = image.getWidth();
        int height = image.getHeight();
        for (int row = 2; row <= 6; row++) {
            int py = bodyV + row;
            if (py < 0 || py >= height) {
                continue;
            }
            for (int i = 0; i < 8; i++) {
                int px = u0 + i;
                if (px < 0 || px >= width) {
                    continue;
                }
                int abgr = image.getPixelRGBA(px, py);
                if (((abgr >>> 24) & 0xFF) < 128) {
                    continue;
                }
                r += abgr & 0xFF;
                g += (abgr >>> 8) & 0xFF;
                b += (abgr >>> 16) & 0xFF;
                n++;
            }
        }
        if (n < 8) {
            return 12.0f;
        }
        float mr = r / (float) n, mg = g / (float) n, mb = b / (float) n;
        final float thresh2 = 70.0f * 70.0f;
        for (int row = 0; row < 12; row++) {
            int py = bodyV + row;
            if (py < 0 || py >= height) {
                continue;
            }
            int similar = 0, opaque = 0;
            for (int i = 0; i < 8; i++) {
                int px = u0 + i;
                if (px < 0 || px >= width) {
                    continue;
                }
                int abgr = image.getPixelRGBA(px, py);
                if (((abgr >>> 24) & 0xFF) < 128) {
                    continue;
                }
                opaque++;
                float dr = (abgr & 0xFF) - mr;
                float dg = ((abgr >>> 8) & 0xFF) - mg;
                float db = ((abgr >>> 16) & 0xFF) - mb;
                if (dr * dr + dg * dg + db * db < thresh2) {
                    similar++;
                }
            }
            if (opaque >= 4 && similar * 2 < opaque) {
                return row > 0 ? (float) row : 0.5f;
            }
        }
        return 12.0f;
    }

    private static float pantsStartY(NativeImage image, boolean overlay) {
        int bodyV = overlay ? 36 : 20;
        int lastStrap = -1;
        for (int row = 0; row < 12; row++) {
            if (isStrapRow(image, 32, bodyV + row)) {
                lastStrap = row;
            }
        }
        if (lastStrap >= 0) {
            return lastStrap + 1;
        }
        return 8.0f;
    }

    private static boolean isStrapRow(NativeImage image, int x, int y) {
        int light = 0;
        int dark = 0;
        int opaque = 0;
        int width = image.getWidth();
        int height = image.getHeight();
        if (y < 0 || y >= height) {
            return false;
        }
        for (int i = 0; i < 8; i++) {
            int px = x + i;
            if (px < 0 || px >= width) {
                continue;
            }
            int abgr = image.getPixelRGBA(px, y);
            int alpha = (abgr >>> 24) & 0xFF;
            if (alpha < 128) {
                continue;
            }
            opaque++;
            int r = abgr & 0xFF;
            int g = (abgr >>> 8) & 0xFF;
            int b = (abgr >>> 16) & 0xFF;
            int lum = (r + g + b) / 3;
            if (lum >= 180) {
                light++;
            }
            if (lum <= 140) {
                dark++;
            }
        }
        return opaque >= 4 && light >= 2 && dark >= 2;
    }


    /**
     * Detects a symmetric square back-pocket patch in the upper-leg-back
     * regions: right leg (12..16, 20..24) and left leg (28..32, 52..56).
     * Requires both top and inner trouser margins to contrast cleanly with a
     * darker pocket outline (either hollow with a lighter interior or a solid
     * square) so horizontal waist bands or stripes do not match.
     */
    private static boolean detectBackPockets(NativeImage image) {
        if (opaque(image, 12, 20, 4, 4) < 16 || opaque(image, 28, 52, 4, 4) < 16) {
            return false;
        }
        int symDiff = 0;
        for (int dy = 0; dy < 4; dy++) {
            for (int dx = 0; dx < 4; dx++) {
                symDiff += Math.abs(lum(image, 12 + dx, 20 + dy) - lum(image, 31 - dx, 52 + dy));
            }
        }
        if (symDiff / 16.0f > 12.0f) {
            return false;
        }
        float topAvg = (lum(image, 12, 20) + lum(image, 13, 20) + lum(image, 14, 20) + lum(image, 15, 20)) / 4.0f;
        float sideAvg = (lum(image, 12, 20) + lum(image, 12, 21) + lum(image, 12, 22) + lum(image, 12, 23)) / 4.0f;
        if (Math.abs(topAvg - sideAvg) > 15.0f) {
            return false;
        }
        int[] border = {
                lum(image, 13, 21), lum(image, 14, 21), lum(image, 15, 21),
                lum(image, 13, 22), lum(image, 15, 22),
                lum(image, 13, 23), lum(image, 14, 23), lum(image, 15, 23)
        };
        int bMin = 255;
        int bMax = 0;
        for (int v : border) {
            bMin = Math.min(bMin, v);
            bMax = Math.max(bMax, v);
        }
        if (bMax > topAvg - 15.0f || bMax > sideAvg - 15.0f) {
            return false;
        }
        int center = lum(image, 14, 22);
        return center >= bMax + 12 || (Math.max(bMax, center) - Math.min(bMin, center) <= 12);
    }

    private static int lum(NativeImage image, int x, int y) {
        int abgr = image.getPixelRGBA(x, y);
        int r = abgr & 0xFF;
        int g = (abgr >>> 8) & 0xFF;
        int b = (abgr >>> 16) & 0xFF;
        return (r + g + b) / 3;
    }

    private static int opaque(NativeImage image, int x, int y, int w, int h) {
        int count = 0;
        int width = image.getWidth();
        int height = image.getHeight();
        for (int yy = 0; yy < h; yy++) {
            int py = y + yy;
            if (py < 0 || py >= height) {
                continue;
            }
            for (int xx = 0; xx < w; xx++) {
                int px = x + xx;
                if (px < 0 || px >= width) {
                    continue;
                }
                int abgr = image.getPixelRGBA(px, py);
                int alpha = (abgr >>> 24) & 0xFF;
                if (alpha >= 128) {
                    count++;
                }
            }
        }
        return count;
    }
}
