package net.ixdarklord.glazedmenu.internal.gui.setup;

import net.ixdarklord.glazedmenu.api.config.ConfigColorScheme;
import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.FillBatch;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

/**
 * The small pictures of the game's screens in the setup: each screen drawn in a box, in Glazed Menu's look (in the color
 * scheme chosen) or as the game draws it, from plain boxes and lines rather than the screens themselves, so a picture
 * costs a few dozen fills and needs no world. Glazed Menu's pictures move a little (a choice gliding down a list, a bar
 * filling), on a clock that holds still when the player turns animations off.
 */
final class SetupPreview {
    // The colors that tell one row's icon from another's, turned toward the chosen scheme.
    private static final int[] ICONS = {0xFF8B6CF6, 0xFF4FC3F7, 0xFF4CB86A, 0xFFF07A3A, 0xFFE86BA8, 0xFFF6B04A, 0xFF6C86F8};

    private SetupPreview() {}

    /** A box to draw a picture in, measured in fractions of its width and height. */
    static final class Mock {
        final GuiGraphicsExtractor graphics;
        final int x;
        final int y;
        final int width;
        final int height;
        final float time;
        final ConfigColorScheme colors;

        Mock(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float time) {
            this.graphics = graphics;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.time = time;
            this.colors = ConfigStyle.colors();
        }

        // The mock-up's boxes, gathered as one element for the GUI (FillBatch) until something else is drawn.
        private @Nullable FillBatch fills;

        FillBatch fills() {
            if (this.fills == null) this.fills = FillBatch.start(this.graphics);
            return this.fills;
        }

        /** Hands the boxes gathered so far to the GUI: before anything else is drawn over them, and when it's done. */
        void flush() {
            if (this.fills != null) this.fills.submit();
            this.fills = null;
        }

        int px(float fraction) {
            return this.x + Math.round(fraction * this.width);
        }

        int py(float fraction) {
            return this.y + Math.round(fraction * this.height);
        }

        void fill(float fx, float fy, float fw, float fh, int color) {
            this.fills().fill(this.px(fx), this.py(fy), this.px(fx + fw), this.py(fy + fh), color);
        }

        void box(float fx, float fy, float fw, float fh, int color) {
            int left = this.px(fx);
            int top = this.py(fy);
            ConfigStyle.rect(this.fills(), left, top, this.px(fx + fw) - left, this.py(fy + fh) - top, color);
        }

        void outline(float fx, float fy, float fw, float fh, int color) {
            int left = this.px(fx);
            int top = this.py(fy);
            int right = this.px(fx + fw);
            int bottom = this.py(fy + fh);
            this.fills().fill(left + 1, top, right - 1, top + 1, color);
            this.fills().fill(left + 1, bottom - 1, right - 1, bottom, color);
            this.fills().fill(left, top + 1, left + 1, bottom - 1, color);
            this.fills().fill(right - 1, top + 1, right, bottom - 1, color);
        }

        /** A glass panel: see-through in the scheme's color, with an accent along its top edge. */
        void glass(float fx, float fy, float fw, float fh) {
            this.box(fx, fy, fw, fh, this.colors.panel());
            this.outline(fx, fy, fw, fh, this.colors.panelBorder());
            this.fills().fill(this.px(fx) + 1, this.py(fy), this.px(fx + fw) - 1, this.py(fy) + 1, ConfigStyle.withAlpha(ConfigStyle.accent(), 0xC0));
        }

        /** A bar: the top and bottom bars of Glazed Menu's screens. */
        void bar(float fx, float fy, float fw, float fh) {
            this.box(fx, fy, fw, fh, this.colors.bar());
            this.outline(fx, fy, fw, fh, this.colors.panelBorder());
        }

        void button(float fx, float fy, float fw, float fh) {
            this.box(fx, fy, fw, fh, this.colors.button());
            this.outline(fx, fy, fw, fh, this.colors.panelBorder());
        }

        /** The main action: the flowing gradient. */
        void primary(float fx, float fy, float fw, float fh) {
            int left = this.px(fx);
            int top = this.py(fy);
            this.flush();
            GlazedBrand.fill(this.graphics, left, top, this.px(fx + fw) - left, this.py(fy + fh) - top, 0xFF, 0.2F);
        }

        /** A line of text, as a bar of its color. */
        void text(float fx, float fy, float fw, int color) {
            int thickness = Math.max(1, Math.round(this.height * 0.022F));
            this.fills().fill(this.px(fx), this.py(fy), this.px(fx + fw), this.py(fy) + thickness, color);
        }

        void text(float fx, float fy, float fw) {
            this.text(fx, fy, fw, ConfigStyle.withAlpha(this.colors.textDim(), 0xB0));
        }

        /** Bold text, like a title: taller and brighter. */
        void title(float fx, float fy, float fw) {
            int thickness = Math.max(2, Math.round(this.height * 0.034F));
            this.fills().fill(this.px(fx), this.py(fy), this.px(fx + fw), this.py(fy) + thickness, this.colors.text());
        }

        /** A title's accent mark and the title after it. */
        void heading(float fx, float fy, float fw) {
            int thickness = Math.max(2, Math.round(this.height * 0.034F));
            this.fills().fill(this.px(fx), this.py(fy) - 1, this.px(fx) + 1, this.py(fy) + thickness + 1, ConfigStyle.accent());
            this.title(fx + 0.015F, fy, fw);
        }

        /** A square icon {@code size} of the height across, in one of the row colors. */
        void icon(float fx, float fy, float size, int which) {
            int side = Math.max(2, Math.round(size * this.height));
            int left = this.px(fx);
            int top = this.py(fy);
            int color = GlazedBrand.identity(ICONS[Math.floorMod(which, ICONS.length)]);
            ConfigStyle.rect(this.fills(), left, top, side, side, ConfigStyle.withAlpha(color, 0xD8));
            this.fills().fill(left + 1, top + 1, left + side - 1, top + Math.max(1, side / 3), ConfigStyle.withAlpha(0xFFFFFFFF, 0x30));
        }

        /** One of Glazed Menu's own icons, centered on a point, about {@code size} of the height across. */
        void glyph(ConfigIcons.Icon icon, float cx, float cy, float size, int color) {
            int side = Math.max(4, Math.round(size * this.height));
            this.flush();
            icon.draw(this.graphics, this.px(cx) - side / 2, this.py(cy) - side / 2, side, color);
        }

        /** A switch: its track, on or off. */
        void toggle(float fx, float fy, boolean on) {
            float fw = 0.06F;
            float fh = 0.05F;
            this.box(fx, fy, fw, fh, on ? ConfigStyle.accent() : this.colors.toggleOff());
            float knob = fh * this.height / this.width;
            this.box(on ? fx + fw - knob - 0.004F : fx + 0.004F, fy + 0.008F, knob, fh - 0.016F, this.colors.knob());
        }

        /** A slider: a filled track and its handle. */
        void slider(float fx, float fy, float fw, float amount) {
            this.fill(fx, fy, fw, 0.014F, this.colors.toggleOff());
            this.fill(fx, fy, fw * amount, 0.014F, ConfigStyle.accent());
            this.box(fx + fw * amount - 0.008F, fy - 0.018F, 0.016F, 0.05F, this.colors.knob());
        }

        /** Where a highlight gliding down {@code count} rows is now: a whole number while it rests, between while it moves. */
        float glide(int count, float seconds) {
            float steps = this.time / seconds;
            int step = (int) steps;
            float part = Math.clamp((steps - step - 0.7F) / 0.3F, 0, 1);
            part = part * part * (3 - 2 * part);
            return (step % count + part) % count;
        }

        /** A highlight on the row a glide is at, for rows {@code step} apart from {@code fy}. */
        void glideRow(float fx, float fy, float fw, float rowHeight, float step, int count, float seconds) {
            float at = this.glide(count, seconds);
            this.box(fx, fy + at * step, fw, rowHeight, ConfigStyle.withAlpha(ConfigStyle.accent(), 0x38));
            this.fill(fx, fy + at * step + 0.01F, 0.006F, rowHeight - 0.02F, ConfigStyle.accent());
        }

        // --- The game's own look ---

        /** The game's button: grey, lit along its top, with its label in the middle. */
        void vanillaButton(float fx, float fy, float fw, float fh) {
            int left = this.px(fx);
            int top = this.py(fy);
            int right = this.px(fx + fw);
            int bottom = this.py(fy + fh);
            this.fills().fill(left, top, right, bottom, 0xFF000000);
            this.fills().fill(left + 1, top + 1, right - 1, bottom - 1, 0xFF6F6F6F);
            this.fills().fill(left + 1, top + 1, right - 1, top + 2, 0xFFA5A5A5);
            this.fills().fill(left + 1, bottom - 2, right - 1, bottom - 1, 0xFF4A4A4A);
            int label = Math.round((right - left) * 0.4F);
            int center = (left + right) / 2;
            int middle = (top + bottom) / 2;
            this.fills().fill(center - label / 2, middle - 1, center + label / 2, middle + Math.max(1, Math.round(this.height * 0.02F)) - 1, 0xFFE0E0E0);
        }

        /** The game's text: a white line centered on {@code cx}. */
        void vanillaText(float cx, float fy, float fw, int color) {
            this.text(cx - fw / 2, fy, fw, color);
        }

        /** The game's list: a darkened band between two lines. */
        void vanillaList(float fy, float fh) {
            this.fill(0, fy, 1, fh, 0x66000000);
            this.fill(0, fy, 1, 0.006F, 0x50FFFFFF);
            this.fill(0, fy + fh - 0.006F, 1, 0.006F, 0x50FFFFFF);
        }

        /** The game's text field: black, with a grey edge. */
        void vanillaField(float fx, float fy, float fw, float fh) {
            this.fill(fx, fy, fw, fh, 0xFFA0A0A0);
            int left = this.px(fx);
            int top = this.py(fy);
            this.fills().fill(left + 1, top + 1, this.px(fx + fw) - 1, this.py(fy + fh) - 1, 0xFF000000);
        }
    }

    // --- The backdrop ---

    /** The panorama (or the world, behind an in-game screen): a sky over hills and a few trees. */
    static void scene(Mock m, int dim) {
        m.fills().fillGradient(m.x, m.y, m.x + m.width, m.py(0.62F), 0xFF6D9BD8, 0xFFBFD8F0);
        m.fill(0, 0.55F, 1, 0.08F, 0xFF7FA0B8);
        m.fills().fillGradient(m.x, m.py(0.62F), m.x + m.width, m.y + m.height, 0xFF5E9442, 0xFF3D6A2C);
        for (int i = 0; i < 6; i++) {
            float tx = 0.06F + i * 0.17F + (i % 2) * 0.04F;
            m.fill(tx + 0.02F, 0.52F, 0.012F, 0.12F, 0xFF5A3E26);
            m.fill(tx, 0.42F, 0.052F, 0.12F, 0xFF2F6B2A);
        }
        m.fill(0.84F, 0.12F, 0.07F, 0.11F, 0xFFFFF4C4);
        if (dim > 0) m.fill(0, 0, 1, 1, dim << 24);
    }

    /** Glazed Menu's backdrop: the scene under the scheme's tint. */
    static void glazedScene(Mock m) {
        scene(m, 0);
        m.fill(0, 0, 1, 1, ConfigStyle.withAlpha(m.colors.backdrop(), 0x70));
    }

    /** The game's backdrop for its menus: the scene, blurred and darkened. */
    static void vanillaScene(Mock m) {
        m.fills().fillGradient(m.x, m.y, m.x + m.width, m.y + m.height, 0xFF5F7FA8, 0xFF47663A);
        m.fill(0, 0, 1, 1, 0x80000000);
    }

    /** Glazed Menu's top bar: a title, and a search at its right. */
    static void topBar(Mock m, boolean search) {
        m.bar(0.03F, 0.04F, 0.94F, 0.1F);
        m.heading(0.05F, 0.075F, 0.14F);
        if (search) {
            m.box(0.58F, 0.06F, 0.3F, 0.06F, m.colors.field());
            m.outline(0.58F, 0.06F, 0.3F, 0.06F, m.colors.fieldBorder());
            m.glyph(ConfigIcons.SEARCH, 0.6F, 0.09F, 0.045F, m.colors.textMuted());
            m.text(0.625F, 0.085F, 0.12F, ConfigStyle.withAlpha(m.colors.textMuted(), 0x90));
        }
        m.glyph(ConfigIcons.CLOSE, 0.93F, 0.09F, 0.05F, m.colors.textDim());
    }

    /** A ribbon of tabs, the chosen one underlined (and gliding from tab to tab). */
    static void ribbon(Mock m, float fy, int tabs, float tabWidth) {
        float gap = 0.01F;
        for (int i = 0; i < tabs; i++) {
            float tx = 0.03F + i * (tabWidth + gap);
            m.button(tx, fy, tabWidth, 0.08F);
            m.text(tx + tabWidth * 0.2F, fy + 0.035F, tabWidth * 0.6F);
        }
        float at = m.glide(tabs, 2.4F);
        m.fill(0.03F + at * (tabWidth + gap), fy + 0.07F, tabWidth, 0.012F, ConfigStyle.accent());
    }

    /** A row of a list: an icon, a name and a line under it. */
    static void row(Mock m, float fx, float fy, float fw, float fh, int which) {
        m.box(fx, fy, fw, fh, ConfigStyle.withAlpha(m.colors.button(), 0x60));
        float icon = fh - 0.03F;
        m.icon(fx + 0.01F, fy + 0.015F, icon, which);
        float textX = fx + 0.02F + icon * m.height / m.width;
        m.text(textX, fy + fh * 0.28F, (fw - (textX - fx)) * (0.45F + which % 3 * 0.1F), m.colors.text());
        m.text(textX, fy + fh * 0.6F, (fw - (textX - fx)) * 0.7F);
    }

    /** Signal bars, as a server's ping. */
    static void signal(Mock m, float fx, float fy, int strength) {
        for (int i = 0; i < 4; i++) {
            float h = 0.012F + i * 0.01F;
            m.fill(fx + i * 0.008F, fy + 0.042F - h, 0.005F, h, i < strength ? m.colors.success() : m.colors.toggleOff());
        }
    }

    // --- Glazed Menu's screens ---

    static void modList(Mock m) {
        glazedScene(m);
        topBar(m, true);
        m.primary(0.03F, 0.17F, 0.08F, 0.07F);
        m.button(0.12F, 0.17F, 0.15F, 0.07F);
        m.button(0.28F, 0.17F, 0.13F, 0.07F);
        m.button(0.88F, 0.17F, 0.09F, 0.07F);
        m.glass(0.03F, 0.27F, 0.4F, 0.69F);
        for (int i = 0; i < 6; i++) row(m, 0.04F, 0.29F + i * 0.11F, 0.38F, 0.1F, i);
        m.glideRow(0.04F, 0.29F, 0.38F, 0.1F, 0.11F, 6, 1.8F);
        m.glass(0.45F, 0.27F, 0.52F, 0.69F);
        m.icon(0.48F, 0.31F, 0.16F, (int) m.glide(6, 1.8F));
        m.title(0.62F, 0.33F, 0.22F);
        m.text(0.62F, 0.4F, 0.14F);
        m.box(0.62F, 0.44F, 0.07F, 0.04F, ConfigStyle.withAlpha(ConfigStyle.accent(), 0x50));
        m.primary(0.48F, 0.53F, 0.11F, 0.07F);
        for (int i = 0; i < 3; i++) m.button(0.6F + i * 0.12F, 0.53F, 0.11F, 0.07F);
        m.text(0.48F, 0.66F, 0.44F);
        m.text(0.48F, 0.71F, 0.4F);
        m.text(0.48F, 0.76F, 0.3F);
        m.text(0.48F, 0.84F, 0.1F, ConfigStyle.accent());
        m.fill(0.6F, 0.85F, 0.34F, 0.004F, m.colors.panelBorder());
        m.text(0.48F, 0.9F, 0.36F);
    }

    static void titleShelf(Mock m) {
        scene(m, 0);
        m.primary(0.05F, 0.08F, 0.34F, 0.1F);
        m.text(0.05F, 0.21F, 0.2F, 0xD0FFFFFF);
        float at = m.glide(4, 2.2F);
        for (int i = 0; i < 4; i++) {
            float cx = 0.05F + i * 0.23F;
            float lift = Math.max(0, 1 - Math.abs(at - i)) * 0.02F;
            if (i == 0) m.primary(cx, 0.56F - lift, 0.21F, 0.24F);
            else m.glass(cx, 0.56F - lift, 0.21F, 0.24F);
            m.glyph(i == 0 ? ConfigIcons.PLAY : i == 1 ? ConfigIcons.PERSON : i == 2 ? ConfigIcons.SERVER : ConfigIcons.GLOBE,
                    cx + 0.105F, 0.64F - lift, 0.1F, 0xFFFFFFFF);
            m.text(cx + 0.04F, 0.73F - lift, 0.13F, 0xE0FFFFFF);
        }
        m.bar(0.05F, 0.85F, 0.9F, 0.1F);
        ConfigIcons.Icon[] icons = {ConfigIcons.GEAR, ConfigIcons.CONFIGS, ConfigIcons.GLOBE, ConfigIcons.EYE};
        for (int i = 0; i < icons.length; i++) {
            m.button(0.07F + i * 0.08F, 0.865F, 0.07F, 0.07F);
            m.glyph(icons[i], 0.105F + i * 0.08F, 0.9F, 0.05F, m.colors.textDim());
        }
        m.button(0.82F, 0.865F, 0.11F, 0.07F);
        m.glyph(ConfigIcons.EXIT, 0.875F, 0.9F, 0.05F, m.colors.error());
    }

    static void titleSidebar(Mock m) {
        scene(m, 0);
        m.box(0, 0, 0.34F, 1, m.colors.panel());
        m.fill(0.34F, 0, 0.004F, 1, m.colors.panelBorder());
        m.primary(0.04F, 0.07F, 0.26F, 0.09F);
        m.text(0.04F, 0.19F, 0.16F, m.colors.textDim());
        ConfigIcons.Icon[] icons = {ConfigIcons.PLAY, ConfigIcons.PERSON, ConfigIcons.SERVER, ConfigIcons.GLOBE, ConfigIcons.CONFIGS, ConfigIcons.GEAR};
        for (int i = 0; i < icons.length; i++) {
            float ry = 0.27F + i * 0.1F;
            if (i == 0) m.primary(0.03F, ry, 0.28F, 0.08F);
            else m.button(0.03F, ry, 0.28F, 0.08F);
            m.glyph(icons[i], 0.065F, ry + 0.04F, 0.05F, i == 0 ? 0xFFFFFFFF : m.colors.textDim());
            m.text(0.095F, ry + 0.033F, 0.12F, i == 0 ? 0xFFFFFFFF : m.colors.text());
        }
        m.glideRow(0.03F, 0.37F, 0.28F, 0.08F, 0.1F, 5, 1.6F);
        m.button(0.03F, 0.88F, 0.28F, 0.08F);
        m.glyph(ConfigIcons.EXIT, 0.065F, 0.92F, 0.05F, m.colors.error());
    }

    static void vanillaTitle(Mock m) {
        scene(m, 0x30);
        m.fill(0.24F, 0.1F, 0.52F, 0.14F, 0xFF3A3A3A);
        m.fill(0.25F, 0.11F, 0.5F, 0.12F, 0xFF9C9C9C);
        m.fill(0.25F, 0.11F, 0.5F, 0.03F, 0xFFCFCFCF);
        m.text(0.62F, 0.26F, 0.12F, 0xFFFFFF55);
        for (int i = 0; i < 3; i++) m.vanillaButton(0.3F, 0.4F + i * 0.11F, 0.4F, 0.09F);
        m.vanillaButton(0.3F, 0.76F, 0.195F, 0.09F);
        m.vanillaButton(0.505F, 0.76F, 0.195F, 0.09F);
        m.vanillaButton(0.22F, 0.76F, 0.07F, 0.09F);
        m.vanillaButton(0.71F, 0.76F, 0.07F, 0.09F);
        m.text(0.02F, 0.94F, 0.16F, 0xC0FFFFFF);
        m.text(0.78F, 0.94F, 0.2F, 0xC0FFFFFF);
    }

    static void pauseDock(Mock m) {
        scene(m, 0x30);
        m.bar(0.03F, 0.04F, 0.94F, 0.12F);
        m.heading(0.05F, 0.085F, 0.12F);
        m.slider(0.6F, 0.095F, 0.14F, 0.6F);
        m.toggle(0.8F, 0.075F, true);
        m.toggle(0.88F, 0.075F, false);
        float start = 0.5F - (5 * 0.15F + 4 * 0.015F) / 2;
        ConfigIcons.Icon[] icons = {ConfigIcons.PLAY, ConfigIcons.TROPHY, ConfigIcons.CHART, ConfigIcons.GEAR, ConfigIcons.EXIT};
        float at = m.glide(5, 1.8F);
        for (int i = 0; i < 5; i++) {
            float tx = start + i * 0.165F;
            float lift = Math.max(0, 1 - Math.abs(at - i)) * 0.025F;
            if (i == 0) m.primary(tx, 0.7F - lift, 0.15F, 0.2F);
            else m.glass(tx, 0.7F - lift, 0.15F, 0.2F);
            m.glyph(icons[i], tx + 0.075F, 0.77F - lift, 0.08F, i == 0 ? 0xFFFFFFFF : i == 4 ? m.colors.error() : m.colors.textDim());
            m.text(tx + 0.035F, 0.85F - lift, 0.08F, i == 0 ? 0xFFFFFFFF : m.colors.textDim());
        }
    }

    static void pauseCards(Mock m) {
        scene(m, 0x60);
        m.glass(0.2F, 0.1F, 0.6F, 0.8F);
        m.heading(0.23F, 0.15F, 0.14F);
        m.primary(0.23F, 0.24F, 0.54F, 0.12F);
        m.glyph(ConfigIcons.PLAY, 0.27F, 0.3F, 0.06F, 0xFFFFFFFF);
        m.text(0.3F, 0.293F, 0.16F, 0xFFFFFFFF);
        ConfigIcons.Icon[] icons = {ConfigIcons.TROPHY, ConfigIcons.CHART, ConfigIcons.GEAR, ConfigIcons.CONFIGS, ConfigIcons.LAN, ConfigIcons.CHAT};
        for (int i = 0; i < 6; i++) {
            float cx = 0.23F + i % 3 * 0.185F;
            float cy = 0.4F + i / 3 * 0.19F;
            m.button(cx, cy, 0.17F, 0.17F);
            m.glyph(icons[i], cx + 0.085F, cy + 0.065F, 0.07F, m.colors.textDim());
            m.text(cx + 0.045F, cy + 0.12F, 0.08F);
        }
        float at = m.glide(6, 1.5F);
        int i = Math.round(at) % 6;
        m.outline(0.23F + i % 3 * 0.185F, 0.4F + i / 3 * 0.19F, 0.17F, 0.17F, ConfigStyle.accent());
        m.button(0.23F, 0.79F, 0.54F, 0.07F);
        m.text(0.44F, 0.82F, 0.12F, m.colors.error());
    }

    static void pauseSidebar(Mock m) {
        scene(m, 0x20);
        m.box(0, 0, 0.32F, 1, m.colors.panel());
        m.fill(0.32F, 0, 0.004F, 1, m.colors.panelBorder());
        m.heading(0.04F, 0.07F, 0.14F);
        m.primary(0.03F, 0.16F, 0.26F, 0.09F);
        m.glyph(ConfigIcons.PLAY, 0.065F, 0.205F, 0.05F, 0xFFFFFFFF);
        ConfigIcons.Icon[] icons = {ConfigIcons.TROPHY, ConfigIcons.CHART, ConfigIcons.GEAR, ConfigIcons.CONFIGS, ConfigIcons.LAN};
        for (int i = 0; i < icons.length; i++) {
            float ry = 0.28F + i * 0.1F;
            m.button(0.03F, ry, 0.26F, 0.08F);
            m.glyph(icons[i], 0.065F, ry + 0.04F, 0.05F, m.colors.textDim());
            m.text(0.095F, ry + 0.033F, 0.12F, m.colors.text());
        }
        m.glideRow(0.03F, 0.28F, 0.26F, 0.08F, 0.1F, 5, 1.6F);
        m.button(0.03F, 0.88F, 0.26F, 0.08F);
        m.glyph(ConfigIcons.EXIT, 0.065F, 0.92F, 0.05F, m.colors.error());
        m.text(0.095F, 0.913F, 0.12F, m.colors.error());
    }

    static void vanillaPause(Mock m) {
        scene(m, 0x90);
        m.vanillaText(0.5F, 0.1F, 0.14F, 0xFFFFFFFF);
        m.vanillaButton(0.28F, 0.2F, 0.44F, 0.09F);
        for (int i = 0; i < 3; i++) {
            m.vanillaButton(0.28F, 0.32F + i * 0.11F, 0.215F, 0.09F);
            m.vanillaButton(0.505F, 0.32F + i * 0.11F, 0.215F, 0.09F);
        }
        m.vanillaButton(0.28F, 0.7F, 0.44F, 0.09F);
    }

    static void options(Mock m) {
        glazedScene(m);
        topBar(m, true);
        ribbon(m, 0.17F, 6, 0.145F);
        for (int i = 0; i < 9; i++) {
            float cx = 0.03F + i % 3 * 0.317F;
            float cy = 0.29F + i / 3 * 0.225F;
            m.glass(cx, cy, 0.3F, 0.2F);
            m.text(cx + 0.02F, cy + 0.045F, 0.14F + i % 2 * 0.05F, m.colors.text());
            m.text(cx + 0.02F, cy + 0.09F, 0.2F);
            if (i % 3 == 1) m.slider(cx + 0.02F, cy + 0.15F, 0.25F, 0.3F + 0.4F * (0.5F + 0.5F * (float) Math.sin(m.time * 1.3F + i)));
            else m.toggle(cx + 0.02F, cy + 0.13F, (i + (int) (m.time / 1.6F)) % 3 != 0);
        }
    }

    static void vanillaOptions(Mock m) {
        vanillaScene(m);
        m.vanillaText(0.5F, 0.06F, 0.1F, 0xFFFFFFFF);
        m.vanillaList(0.13F, 0.73F);
        for (int i = 0; i < 6; i++) {
            m.vanillaButton(0.2F, 0.17F + i * 0.11F, 0.29F, 0.09F);
            m.vanillaButton(0.51F, 0.17F + i * 0.11F, 0.29F, 0.09F);
        }
        m.vanillaButton(0.33F, 0.89F, 0.34F, 0.08F);
    }

    static void controls(Mock m) {
        glazedScene(m);
        topBar(m, true);
        float key = 0.058F;
        int lit = (int) m.glide(10, 0.9F);
        for (int r = 0; r < 4; r++) {
            int count = 13 - r;
            for (int i = 0; i < count; i++) {
                float kx = 0.04F + r * 0.03F + i * (key + 0.008F);
                float ky = 0.2F + r * 0.1F;
                int kind = (r * 7 + i * 3) % 9;
                int fill = kind == 0 ? ConfigStyle.withAlpha(m.colors.error(), 0x90) : kind < 4 ? ConfigStyle.withAlpha(ConfigStyle.accent(), 0x70) : m.colors.button();
                if (r == 1 && i == lit) fill = ConfigStyle.accent();
                m.box(kx, ky, key, 0.085F, fill);
                m.outline(kx, ky, key, 0.085F, m.colors.panelBorder());
            }
        }
        m.box(0.22F, 0.6F, 0.36F, 0.085F, m.colors.button());
        m.outline(0.22F, 0.6F, 0.36F, 0.085F, m.colors.panelBorder());
        m.box(0.86F, 0.2F, 0.1F, 0.24F, m.colors.button());
        m.fill(0.91F, 0.2F, 0.004F, 0.1F, m.colors.panelBorder());
        m.box(0.865F, 0.205F, 0.04F, 0.09F, ConfigStyle.withAlpha(ConfigStyle.accent(), 0x70));
        m.glass(0.03F, 0.72F, 0.94F, 0.24F);
        for (int i = 0; i < 3; i++) {
            m.text(0.05F, 0.77F + i * 0.06F, 0.2F, m.colors.text());
            m.box(0.8F, 0.755F + i * 0.06F, 0.14F, 0.045F, m.colors.button());
        }
    }

    static void keyBinds(Mock m) {
        glazedScene(m);
        topBar(m, true);
        m.glass(0.03F, 0.17F, 0.25F, 0.79F);
        for (int i = 0; i < 7; i++) {
            m.icon(0.045F, 0.2F + i * 0.1F, 0.05F, i);
            m.text(0.08F, 0.215F + i * 0.1F, 0.1F + i % 3 * 0.03F);
        }
        m.glideRow(0.035F, 0.19F, 0.24F, 0.07F, 0.1F, 7, 2F);
        m.glass(0.3F, 0.17F, 0.67F, 0.79F);
        for (int i = 0; i < 8; i++) {
            float ry = 0.2F + i * 0.095F;
            m.text(0.33F, ry + 0.025F, 0.18F + i % 3 * 0.05F, m.colors.text());
            boolean conflict = i == 3;
            m.box(0.74F, ry, 0.15F, 0.07F, conflict ? ConfigStyle.withAlpha(m.colors.error(), 0x60) : m.colors.button());
            m.outline(0.74F, ry, 0.15F, 0.07F, conflict ? m.colors.error() : m.colors.panelBorder());
            m.text(0.785F, ry + 0.03F, 0.06F, m.colors.text());
            m.glyph(ConfigIcons.RESET, 0.925F, ry + 0.035F, 0.045F, m.colors.textMuted());
        }
    }

    static void vanillaKeyList(Mock m) {
        vanillaScene(m);
        m.vanillaText(0.5F, 0.06F, 0.12F, 0xFFFFFFFF);
        m.vanillaList(0.13F, 0.73F);
        for (int i = 0; i < 6; i++) {
            float ry = 0.16F + i * 0.115F;
            m.text(0.2F, ry + 0.035F, 0.18F, 0xFFFFFFFF);
            m.vanillaButton(0.55F, ry, 0.18F, 0.09F);
            m.vanillaButton(0.74F, ry, 0.08F, 0.09F);
        }
        m.vanillaButton(0.2F, 0.89F, 0.29F, 0.08F);
        m.vanillaButton(0.51F, 0.89F, 0.29F, 0.08F);
    }

    static void worlds(Mock m, boolean servers) {
        glazedScene(m);
        topBar(m, true);
        m.glass(0.03F, 0.17F, 0.52F, 0.79F);
        if (servers) m.text(0.05F, 0.71F, 0.1F, ConfigStyle.accent());
        int rows = servers ? 4 : 6;
        for (int i = 0; i < rows; i++) {
            row(m, 0.045F, 0.19F + i * 0.125F, 0.49F, 0.11F, i + 1);
            if (servers) signal(m, 0.49F, 0.22F + i * 0.125F, 4 - i % 3);
            else m.text(0.44F, 0.22F + i * 0.125F, 0.08F, m.colors.textMuted());
        }
        if (servers) row(m, 0.045F, 0.75F, 0.49F, 0.11F, 5);
        m.glideRow(0.045F, 0.19F, 0.49F, 0.11F, 0.125F, rows, 1.9F);
        m.glass(0.57F, 0.17F, 0.4F, 0.79F);
        int picture = m.py(0.21F);
        m.fills().fillGradient(m.px(0.6F), picture, m.px(0.94F), m.py(0.45F), 0xFF6D9BD8, 0xFF5E9442);
        m.outline(0.6F, 0.21F, 0.34F, 0.24F, m.colors.panelBorder());
        m.title(0.6F, 0.5F, 0.2F);
        m.text(0.6F, 0.57F, 0.3F);
        m.text(0.6F, 0.62F, 0.24F);
        m.text(0.6F, 0.67F, 0.27F);
        m.primary(0.6F, 0.76F, 0.34F, 0.08F);
        m.glyph(servers ? ConfigIcons.CONNECT : ConfigIcons.PLAY, 0.77F, 0.8F, 0.05F, 0xFFFFFFFF);
        m.button(0.6F, 0.86F, 0.165F, 0.07F);
        m.button(0.775F, 0.86F, 0.165F, 0.07F);
    }

    static void vanillaWorlds(Mock m, boolean servers) {
        vanillaScene(m);
        m.vanillaText(0.5F, 0.05F, 0.14F, 0xFFFFFFFF);
        if (!servers) m.vanillaField(0.3F, 0.1F, 0.4F, 0.065F);
        m.vanillaList(0.19F, 0.6F);
        for (int i = 0; i < 4; i++) {
            float ry = 0.22F + i * 0.14F;
            m.fill(0.24F, ry, 0.075F, 0.12F, i == 0 ? 0xFF7A7A7A : 0xFF555555);
            m.text(0.33F, ry + 0.02F, 0.2F, 0xFFFFFFFF);
            m.text(0.33F, ry + 0.06F, 0.3F, 0xFF909090);
            if (servers) signal(m, 0.72F, ry, 4 - i % 3);
        }
        m.outline(0.23F, 0.215F, 0.54F, 0.13F, 0xFFFFFFFF);
        for (int i = 0; i < 3; i++) m.vanillaButton(0.16F + i * 0.23F, 0.81F, 0.22F, 0.075F);
        for (int i = 0; i < 4; i++) m.vanillaButton(0.16F + i * 0.1725F, 0.9F, 0.1625F, 0.075F);
    }

    static void createWorld(Mock m) {
        glazedScene(m);
        topBar(m, false);
        ribbon(m, 0.17F, 3, 0.18F);
        m.box(0.03F, 0.3F, 0.55F, 0.07F, m.colors.field());
        m.outline(0.03F, 0.3F, 0.55F, 0.07F, m.colors.fieldBorder());
        m.text(0.05F, 0.33F, 0.18F, m.colors.text());
        int chosen = (int) m.glide(3, 2F);
        ConfigIcons.Icon[] icons = {ConfigIcons.PERSON, ConfigIcons.STAR, ConfigIcons.SKULL};
        for (int i = 0; i < 3; i++) {
            float cx = 0.03F + i * 0.19F;
            if (i == chosen) m.box(cx, 0.41F, 0.17F, 0.3F, ConfigStyle.withAlpha(ConfigStyle.accent(), 0x50));
            m.glass(cx, 0.41F, 0.17F, 0.3F);
            if (i == chosen) m.outline(cx, 0.41F, 0.17F, 0.3F, ConfigStyle.accent());
            m.glyph(icons[i], cx + 0.085F, 0.5F, 0.1F, i == 2 ? m.colors.error() : m.colors.text());
            m.text(cx + 0.04F, 0.6F, 0.09F, m.colors.text());
            m.text(cx + 0.03F, 0.65F, 0.11F);
        }
        m.button(0.03F, 0.75F, 0.27F, 0.08F);
        m.button(0.31F, 0.75F, 0.27F, 0.08F);
        m.glass(0.61F, 0.3F, 0.36F, 0.56F);
        for (int i = 0; i < 5; i++) {
            m.icon(0.63F, 0.34F + i * 0.1F, 0.06F, i);
            m.text(0.67F, 0.36F + i * 0.1F, 0.16F + i % 2 * 0.06F);
        }
        m.bar(0.03F, 0.88F, 0.94F, 0.09F);
        m.primary(0.77F, 0.895F, 0.18F, 0.06F);
        m.button(0.57F, 0.895F, 0.18F, 0.06F);
    }

    static void vanillaCreateWorld(Mock m) {
        vanillaScene(m);
        for (int i = 0; i < 3; i++) m.vanillaButton(0.22F + i * 0.19F, 0.03F, 0.18F, 0.08F);
        m.fill(0, 0.11F, 1, 0.006F, 0x50FFFFFF);
        m.text(0.3F, 0.18F, 0.12F, 0xFFA0A0A0);
        m.vanillaField(0.3F, 0.22F, 0.4F, 0.08F);
        m.vanillaButton(0.3F, 0.36F, 0.4F, 0.09F);
        m.text(0.3F, 0.47F, 0.36F, 0xFFA0A0A0);
        m.vanillaButton(0.3F, 0.53F, 0.4F, 0.09F);
        m.vanillaButton(0.3F, 0.64F, 0.4F, 0.09F);
        m.fill(0, 0.86F, 1, 0.006F, 0x50FFFFFF);
        m.vanillaButton(0.2F, 0.89F, 0.29F, 0.08F);
        m.vanillaButton(0.51F, 0.89F, 0.29F, 0.08F);
    }

    static void editWorld(Mock m) {
        glazedScene(m);
        topBar(m, false);
        m.box(0.03F, 0.18F, 0.55F, 0.07F, m.colors.field());
        m.outline(0.03F, 0.18F, 0.55F, 0.07F, m.colors.fieldBorder());
        m.text(0.05F, 0.21F, 0.2F, m.colors.text());
        ConfigIcons.Icon[] icons = {ConfigIcons.FOLDER, ConfigIcons.COPY, ConfigIcons.RESTART, ConfigIcons.TRASH};
        for (int i = 0; i < 4; i++) {
            float cx = 0.03F + i % 2 * 0.28F;
            float cy = 0.3F + i / 2 * 0.28F;
            m.glass(cx, cy, 0.27F, 0.25F);
            m.glyph(icons[i], cx + 0.05F, cy + 0.07F, 0.07F, i == 3 ? m.colors.error() : ConfigStyle.accent());
            m.text(cx + 0.1F, cy + 0.06F, 0.12F, m.colors.text());
            m.text(cx + 0.03F, cy + 0.14F, 0.2F);
            m.text(cx + 0.03F, cy + 0.18F, 0.16F);
        }
        m.glass(0.61F, 0.18F, 0.36F, 0.68F);
        m.fills().fillGradient(m.px(0.64F), m.py(0.22F), m.px(0.94F), m.py(0.46F), 0xFF6D9BD8, 0xFF5E9442);
        m.title(0.64F, 0.51F, 0.18F);
        for (int i = 0; i < 4; i++) m.text(0.64F, 0.58F + i * 0.06F, 0.22F + i % 2 * 0.05F);
        m.bar(0.03F, 0.88F, 0.94F, 0.09F);
        m.primary(0.77F, 0.895F, 0.18F, 0.06F);
    }

    static void vanillaEditWorld(Mock m) {
        vanillaScene(m);
        m.vanillaText(0.5F, 0.07F, 0.12F, 0xFFFFFFFF);
        m.text(0.3F, 0.15F, 0.1F, 0xFFA0A0A0);
        m.vanillaField(0.3F, 0.19F, 0.4F, 0.08F);
        for (int i = 0; i < 5; i++) m.vanillaButton(0.3F, 0.31F + i * 0.1F, 0.4F, 0.085F);
        m.vanillaButton(0.3F, 0.86F, 0.195F, 0.085F);
        m.vanillaButton(0.505F, 0.86F, 0.195F, 0.085F);
    }

    /** A popup over the dimmed screen under it. */
    static void popup(Mock m, float fx, float fy, float fw, float fh) {
        glazedScene(m);
        m.fill(0, 0, 1, 1, ConfigStyle.withAlpha(m.colors.backdrop(), 0x80));
        m.box(fx + 0.01F, fy + 0.015F, fw, fh, 0x66000000);
        m.box(fx, fy, fw, fh, m.colors.popup());
        m.outline(fx, fy, fw, fh, m.colors.panelBorder());
        m.fill(fx + 0.005F, fy, fw - 0.01F, 0.008F, ConfigStyle.accent());
    }

    /** A server as it answers: its icon, name, players and signal. */
    static void serverStrip(Mock m, float fx, float fy, float fw) {
        m.box(fx, fy, fw, 0.13F, ConfigStyle.withAlpha(m.colors.button(), 0x80));
        m.icon(fx + 0.01F, fy + 0.015F, 0.1F, 2);
        m.text(fx + 0.09F, fy + 0.035F, fw * 0.35F, m.colors.text());
        m.text(fx + 0.09F, fy + 0.08F, fw * 0.5F);
        signal(m, fx + fw - 0.05F, fy + 0.03F, 1 + (int) (m.time * 1.5F) % 4);
    }

    static void editServer(Mock m) {
        popup(m, 0.12F, 0.1F, 0.76F, 0.8F);
        m.glyph(ConfigIcons.SERVER, 0.165F, 0.16F, 0.05F, ConfigStyle.accent());
        m.title(0.19F, 0.15F, 0.16F);
        for (int i = 0; i < 2; i++) {
            m.text(0.15F, 0.25F + i * 0.15F, 0.08F, m.colors.textMuted());
            m.box(0.15F, 0.29F + i * 0.15F, 0.4F, 0.07F, m.colors.field());
            m.outline(0.15F, 0.29F + i * 0.15F, 0.4F, 0.07F, i == 1 ? ConfigStyle.accent() : m.colors.fieldBorder());
            m.text(0.17F, 0.32F + i * 0.15F, 0.14F + i * 0.06F, m.colors.text());
        }
        m.button(0.15F, 0.56F, 0.4F, 0.07F);
        m.glass(0.58F, 0.25F, 0.27F, 0.38F);
        m.icon(0.67F, 0.29F, 0.12F, 2);
        m.text(0.62F, 0.45F, 0.18F, m.colors.text());
        m.text(0.62F, 0.5F, 0.14F);
        signal(m, 0.69F, 0.54F, 1 + (int) (m.time * 1.5F) % 4);
        m.primary(0.62F, 0.78F, 0.23F, 0.07F);
        m.button(0.38F, 0.78F, 0.22F, 0.07F);
    }

    static void vanillaForm(Mock m, int fields) {
        vanillaScene(m);
        m.vanillaText(0.5F, 0.08F, 0.14F, 0xFFFFFFFF);
        for (int i = 0; i < fields; i++) {
            m.text(0.3F, 0.2F + i * 0.16F, 0.1F, 0xFFA0A0A0);
            m.vanillaField(0.3F, 0.24F + i * 0.16F, 0.4F, 0.08F);
        }
        if (fields > 1) m.vanillaButton(0.3F, 0.56F, 0.4F, 0.09F);
        m.vanillaButton(0.3F, 0.72F, 0.4F, 0.09F);
        m.vanillaButton(0.3F, 0.83F, 0.4F, 0.09F);
    }

    static void directConnect(Mock m) {
        popup(m, 0.2F, 0.2F, 0.6F, 0.6F);
        m.glyph(ConfigIcons.CONNECT, 0.245F, 0.26F, 0.05F, ConfigStyle.accent());
        m.title(0.27F, 0.25F, 0.18F);
        m.box(0.23F, 0.34F, 0.54F, 0.08F, m.colors.field());
        m.outline(0.23F, 0.34F, 0.54F, 0.08F, ConfigStyle.accent());
        m.text(0.25F, 0.375F, 0.2F, m.colors.text());
        if ((int) (m.time * 2) % 2 == 0) m.fill(0.455F, 0.355F, 0.004F, 0.05F, m.colors.text());
        serverStrip(m, 0.23F, 0.46F, 0.54F);
        m.primary(0.56F, 0.68F, 0.21F, 0.08F);
        m.button(0.33F, 0.68F, 0.21F, 0.08F);
    }

    static void loading(Mock m) {
        scene(m, 0);
        m.fill(0, 0, 1, 1, ConfigStyle.withAlpha(m.colors.backdrop(), 0xA0));
        int cx = m.px(0.5F);
        int cy = m.py(0.4F);
        float radius = m.height * 0.2F;
        int dots = 28;
        float done = (m.time * 0.18F) % 1.2F;
        for (int i = 0; i < dots; i++) {
            double angle = Math.PI * 2 * i / dots - Math.PI / 2;
            int dx = cx + (int) Math.round(Math.cos(angle) * radius);
            int dy = cy + (int) Math.round(Math.sin(angle) * radius);
            boolean filled = i / (float) dots < done;
            int color = filled ? GlazedBrand.now(i / (float) dots) : ConfigStyle.withAlpha(m.colors.textMuted(), 0x60);
            m.fills().fill(dx - 1, dy - 1, dx + 2, dy + 2, color);
        }
        m.icon(0.5F - 0.1F * m.height / m.width, 0.3F, 0.2F, 2);
        for (int i = 0; i < 3; i++) {
            float ry = 0.7F + i * 0.075F;
            boolean finished = done > (i + 1) * 0.33F;
            m.box(0.36F, ry, 0.02F, 0.04F, finished ? m.colors.success() : m.colors.toggleOff());
            m.text(0.4F, ry + 0.012F, 0.16F + i * 0.03F, finished ? m.colors.text() : m.colors.textMuted());
            m.text(0.6F, ry + 0.012F, 0.04F, m.colors.textMuted());
        }
    }

    static void vanillaLoading(Mock m) {
        m.fill(0, 0, 1, 1, 0xFF2E241C);
        m.fill(0, 0, 1, 1, 0x60000000);
        m.vanillaText(0.5F, 0.3F, 0.22F, 0xFFFFFFFF);
        int cells = 9;
        float cell = 0.026F;
        float left = 0.5F - cells * cell / 2;
        int lit = (int) (m.time * 14) % (cells * cells);
        for (int i = 0; i < cells * cells; i++) {
            if (i > lit) break;
            m.fill(left + i % cells * cell, 0.42F + i / cells * cell * m.width / m.height, cell, cell * m.width / m.height, i % 5 == 0 ? 0xFF4FA03A : 0xFF7A7A7A);
        }
    }

    static void chat(Mock m) {
        scene(m, 0);
        m.glass(0.02F, 0.24F, 0.62F, 0.62F);
        for (int i = 0; i < 3; i++) {
            if (i == 0) m.box(0.035F, 0.26F, 0.1F, 0.06F, ConfigStyle.withAlpha(ConfigStyle.accent(), 0x60));
            else m.button(0.035F + i * 0.11F, 0.26F, 0.1F, 0.06F);
            m.text(0.055F + i * 0.11F, 0.285F, 0.06F, m.colors.text());
        }
        // The newest message slides in at the bottom, the others moving up for it.
        float arrive = (m.time % 2.5F) / 0.35F;
        float slide = arrive < 1 ? (1 - arrive * arrive) * 0.1F : 0;
        for (int i = 0; i < 5; i++) {
            float ry = 0.35F + i * 0.1F + slide;
            if (ry > 0.78F) continue;
            m.icon(0.035F, ry, 0.07F, i + (int) (m.time / 2.5F));
            m.text(0.075F, ry + 0.01F, 0.08F, GlazedBrand.identity(ICONS[(i + 3) % ICONS.length]));
            m.text(0.075F, ry + 0.045F, 0.24F + i % 3 * 0.08F, m.colors.text());
        }
        m.bar(0.02F, 0.88F, 0.96F, 0.09F);
        m.text(0.04F, 0.92F, 0.22F, m.colors.text());
        m.primary(0.9F, 0.89F, 0.07F, 0.07F);
        m.glyph(ConfigIcons.SEND, 0.935F, 0.925F, 0.045F, 0xFFFFFFFF);
        m.button(0.82F, 0.89F, 0.07F, 0.07F);
        m.glyph(ConfigIcons.STAR, 0.855F, 0.925F, 0.045F, m.colors.textDim());
    }

    static void vanillaChat(Mock m) {
        scene(m, 0);
        for (int i = 0; i < 6; i++) {
            m.fill(0.01F, 0.42F + i * 0.075F, 0.52F, 0.075F, 0x80000000);
            m.text(0.02F, 0.445F + i * 0.075F, 0.2F + i % 3 * 0.1F, 0xFFFFFFFF);
        }
        m.fill(0.01F, 0.9F, 0.98F, 0.075F, 0x80000000);
        m.text(0.02F, 0.925F, 0.18F, 0xFFFFFFFF);
    }

    static void death(Mock m) {
        scene(m, 0);
        m.fills().fillGradient(m.x, m.y, m.x + m.width, m.y + m.height, 0x50600000, 0xA0A00000);
        float arrive = Math.min(1, (m.time % 6F) / 0.6F);
        float band = 0.32F * (arrive * arrive * (3 - 2 * arrive));
        m.fill(0, 0.46F - band / 2, 1, band, ConfigStyle.withAlpha(0xFF14080A, 0xD0));
        m.fill(0, 0.46F - band / 2, 1, 0.006F, m.colors.error());
        m.fill(0, 0.46F + band / 2 - 0.006F, 1, 0.006F, m.colors.error());
        if (arrive >= 1) {
            m.glyph(ConfigIcons.SKULL, 0.5F, 0.38F, 0.08F, m.colors.error());
            m.title(0.36F, 0.44F, 0.28F);
            m.text(0.4F, 0.5F, 0.2F);
            m.box(0.31F, 0.54F, 0.18F, 0.07F, m.colors.error());
            m.button(0.51F, 0.54F, 0.18F, 0.07F);
        }
        for (int i = 0; i < 4; i++) {
            m.glass(0.16F + i * 0.175F, 0.7F, 0.16F, 0.13F);
            m.text(0.18F + i * 0.175F, 0.73F, 0.06F, m.colors.textMuted());
            m.title(0.18F + i * 0.175F, 0.77F, 0.08F);
        }
    }

    static void vanillaDeath(Mock m) {
        scene(m, 0);
        m.fills().fillGradient(m.x, m.y, m.x + m.width, m.y + m.height, 0x60500000, 0xA0803030);
        m.fill(0.32F, 0.2F, 0.36F, 0.05F, 0xFFFFFFFF);
        m.vanillaText(0.5F, 0.32F, 0.2F, 0xFFFFFFFF);
        m.vanillaText(0.5F, 0.38F, 0.12F, 0xFFFFFF55);
        m.vanillaButton(0.3F, 0.5F, 0.4F, 0.09F);
        m.vanillaButton(0.3F, 0.61F, 0.4F, 0.09F);
    }

    static void disconnected(Mock m) {
        popup(m, 0.22F, 0.12F, 0.56F, 0.76F);
        m.box(0.46F, 0.18F, 0.08F * m.height / m.width * 1.6F, 0.13F, ConfigStyle.withAlpha(m.colors.error(), 0x40));
        m.glyph(ConfigIcons.WARNING, 0.5F, 0.245F, 0.07F, m.colors.error());
        m.title(0.38F, 0.36F, 0.24F);
        m.text(0.3F, 0.43F, 0.4F);
        m.text(0.33F, 0.48F, 0.34F);
        serverStrip(m, 0.26F, 0.54F, 0.48F);
        m.button(0.26F, 0.76F, 0.07F, 0.07F);
        m.glyph(ConfigIcons.COPY, 0.295F, 0.795F, 0.04F, m.colors.textDim());
        m.button(0.35F, 0.76F, 0.18F, 0.07F);
        m.primary(0.55F, 0.76F, 0.19F, 0.07F);
    }

    static void vanillaMessage(Mock m, int lines) {
        vanillaScene(m);
        m.vanillaText(0.5F, 0.3F, 0.18F, 0xFFAAAAAA);
        for (int i = 0; i < lines; i++) m.vanillaText(0.5F, 0.4F + i * 0.06F, 0.5F - i % 2 * 0.1F, 0xFFFFFFFF);
        m.vanillaButton(0.3F, 0.45F + lines * 0.06F + 0.06F, 0.4F, 0.09F);
    }

    static void advancements(Mock m) {
        glazedScene(m);
        topBar(m, true);
        for (int i = 0; i < 6; i++) {
            if (i == 0) m.box(0.03F + i * 0.09F, 0.17F, 0.08F, 0.1F, ConfigStyle.withAlpha(ConfigStyle.accent(), 0x60));
            else m.button(0.03F + i * 0.09F, 0.17F, 0.08F, 0.1F);
            m.icon(0.05F + i * 0.09F, 0.19F, 0.06F, i);
        }
        m.glass(0.03F, 0.3F, 0.94F, 0.66F);
        for (int i = 0; i < 5; i++) {
            float ry = 0.33F + i * 0.12F;
            m.icon(0.05F, ry, 0.09F, i + 2);
            m.text(0.11F, ry + 0.015F, 0.18F + i % 2 * 0.06F, m.colors.text());
            m.text(0.11F, ry + 0.055F, 0.35F);
            float amount = i == 1 ? 0.3F + 0.6F * (0.5F + 0.5F * (float) Math.sin(m.time)) : (5 - i) / 5F;
            m.fill(0.68F, ry + 0.035F, 0.26F, 0.02F, m.colors.toggleOff());
            m.fill(0.68F, ry + 0.035F, 0.26F * amount, 0.02F, amount >= 1 ? m.colors.success() : ConfigStyle.accent());
        }
    }

    static void vanillaAdvancements(Mock m) {
        scene(m, 0x90);
        for (int i = 0; i < 5; i++) m.fill(0.15F + i * 0.08F, 0.06F, 0.07F, 0.08F, i == 0 ? 0xFFC6C6C6 : 0xFF8B8B8B);
        m.fill(0.14F, 0.13F, 0.72F, 0.8F, 0xFFC6C6C6);
        m.fill(0.16F, 0.2F, 0.68F, 0.7F, 0xFF3B2A1C);
        m.text(0.16F, 0.16F, 0.14F, 0xFF404040);
        float[][] nodes = {{0.22F, 0.5F}, {0.36F, 0.36F}, {0.36F, 0.64F}, {0.52F, 0.36F}, {0.52F, 0.64F}, {0.68F, 0.5F}};
        m.fill(0.25F, 0.53F, 0.47F, 0.01F, 0xFFFFFFFF);
        m.fill(0.39F, 0.39F, 0.005F, 0.28F, 0xFFFFFFFF);
        m.fill(0.55F, 0.39F, 0.005F, 0.28F, 0xFFFFFFFF);
        for (int i = 0; i < nodes.length; i++) {
            m.fill(nodes[i][0], nodes[i][1], 0.06F, 0.09F, i < 3 ? 0xFFE8C04A : 0xFF8B8B8B);
            m.fill(nodes[i][0] + 0.012F, nodes[i][1] + 0.02F, 0.036F, 0.05F, 0xFF5A7FA8);
        }
    }

    static void stats(Mock m) {
        glazedScene(m);
        topBar(m, true);
        ribbon(m, 0.17F, 4, 0.16F);
        m.glass(0.03F, 0.29F, 0.94F, 0.67F);
        m.fill(0.05F, 0.34F, 0.9F, 0.006F, m.colors.panelBorder());
        int sorted = (int) m.glide(4, 2.6F);
        for (int c = 0; c < 4; c++) m.text(0.4F + c * 0.14F, 0.31F, 0.08F, c == sorted ? ConfigStyle.accent() : m.colors.textMuted());
        for (int i = 0; i < 6; i++) {
            float ry = 0.37F + i * 0.095F;
            if (i % 2 == 0) m.box(0.04F, ry, 0.92F, 0.085F, ConfigStyle.withAlpha(m.colors.button(), 0x50));
            m.icon(0.05F, ry + 0.012F, 0.06F, i);
            m.text(0.09F, ry + 0.035F, 0.16F + i % 3 * 0.04F, m.colors.text());
            for (int c = 0; c < 4; c++) m.text(0.4F + c * 0.14F, ry + 0.035F, 0.03F + (i * 3 + c) % 4 * 0.012F);
        }
    }

    static void vanillaStats(Mock m) {
        vanillaScene(m);
        m.vanillaText(0.5F, 0.05F, 0.12F, 0xFFFFFFFF);
        for (int i = 0; i < 4; i++) m.vanillaButton(0.14F + i * 0.18F, 0.1F, 0.17F, 0.08F);
        m.vanillaList(0.2F, 0.64F);
        for (int i = 0; i < 8; i++) {
            m.text(0.2F, 0.23F + i * 0.075F, 0.2F, 0xFFFFFFFF);
            m.text(0.72F, 0.23F + i * 0.075F, 0.08F, 0xFFFFFFFF);
        }
        m.vanillaButton(0.33F, 0.88F, 0.34F, 0.08F);
    }

    static void packs(Mock m) {
        glazedScene(m);
        topBar(m, true);
        m.glass(0.03F, 0.17F, 0.36F, 0.79F);
        m.text(0.05F, 0.2F, 0.1F, ConfigStyle.accent());
        for (int i = 0; i < 4; i++) {
            row(m, 0.045F, 0.25F + i * 0.12F, 0.33F, 0.105F, i + 3);
            m.glyph(ConfigIcons.UP, 0.355F, 0.28F + i * 0.12F, 0.035F, m.colors.textMuted());
            m.glyph(ConfigIcons.DOWN, 0.355F, 0.32F + i * 0.12F, 0.035F, m.colors.textMuted());
        }
        m.glideRow(0.045F, 0.25F, 0.33F, 0.105F, 0.12F, 4, 2.2F);
        for (int i = 0; i < 6; i++) {
            float tx = 0.42F + i % 3 * 0.187F;
            float ty = 0.17F + i / 3 * 0.4F;
            m.glass(tx, ty, 0.175F, 0.37F);
            m.icon(tx + 0.04F, ty + 0.04F, 0.16F, i);
            m.text(tx + 0.02F, ty + 0.24F, 0.12F, m.colors.text());
            m.text(tx + 0.02F, ty + 0.29F, 0.14F);
        }
    }

    static void vanillaPacks(Mock m) {
        vanillaScene(m);
        m.vanillaText(0.5F, 0.05F, 0.14F, 0xFFFFFFFF);
        for (int side = 0; side < 2; side++) {
            float lx = 0.06F + side * 0.48F;
            m.fill(lx, 0.14F, 0.4F, 0.68F, 0x66000000);
            m.vanillaText(lx + 0.2F, 0.16F, 0.14F, 0xFFFFFFFF);
            for (int i = 0; i < 4 - side * 2; i++) {
                m.fill(lx + 0.01F, 0.23F + i * 0.14F, 0.07F, 0.11F, 0xFF6A6A6A);
                m.text(lx + 0.1F, 0.25F + i * 0.14F, 0.18F, 0xFFFFFFFF);
                m.text(lx + 0.1F, 0.29F + i * 0.14F, 0.24F, 0xFF909090);
            }
        }
        m.vanillaButton(0.2F, 0.88F, 0.29F, 0.08F);
        m.vanillaButton(0.51F, 0.88F, 0.29F, 0.08F);
    }

    static void language(Mock m) {
        glazedScene(m);
        topBar(m, true);
        m.glass(0.03F, 0.17F, 0.94F, 0.79F);
        for (int i = 0; i < 12; i++) {
            float cx = 0.05F + i % 2 * 0.46F;
            float ry = 0.2F + i / 2 * 0.125F;
            boolean chosen = i == 2;
            m.box(cx, ry, 0.44F, 0.105F, chosen ? ConfigStyle.withAlpha(ConfigStyle.accent(), 0x48) : ConfigStyle.withAlpha(m.colors.button(), 0x60));
            if (chosen) m.outline(cx, ry, 0.44F, 0.105F, ConfigStyle.accent());
            m.box(cx + 0.015F, ry + 0.03F, 0.05F, 0.05F, GlazedBrand.identity(ICONS[i % ICONS.length]));
            m.text(cx + 0.08F, ry + 0.03F, 0.12F + i % 3 * 0.04F, m.colors.text());
            m.text(cx + 0.08F, ry + 0.065F, 0.08F);
            if (chosen) m.glyph(ConfigIcons.CHECK, cx + 0.4F, ry + 0.052F, 0.05F, ConfigStyle.accent());
        }
        m.glideRow(0.05F, 0.2F, 0.44F, 0.105F, 0.25F, 3, 1.7F);
    }

    static void vanillaLanguage(Mock m) {
        vanillaScene(m);
        m.vanillaText(0.5F, 0.05F, 0.12F, 0xFFFFFFFF);
        m.vanillaList(0.12F, 0.72F);
        for (int i = 0; i < 9; i++) m.vanillaText(0.5F, 0.15F + i * 0.075F, 0.18F + i % 3 * 0.05F, 0xFFFFFFFF);
        m.outline(0.3F, 0.135F + 2 * 0.075F, 0.4F, 0.06F, 0xFFFFFFFF);
        m.vanillaButton(0.2F, 0.88F, 0.29F, 0.08F);
        m.vanillaButton(0.51F, 0.88F, 0.29F, 0.08F);
    }

    static void gameRules(Mock m) {
        glazedScene(m);
        topBar(m, true);
        m.glass(0.03F, 0.17F, 0.24F, 0.79F);
        for (int i = 0; i < 6; i++) {
            m.icon(0.045F, 0.2F + i * 0.1F, 0.05F, i);
            m.text(0.08F, 0.215F + i * 0.1F, 0.12F);
        }
        m.glideRow(0.035F, 0.19F, 0.23F, 0.07F, 0.1F, 6, 2.1F);
        m.glass(0.29F, 0.17F, 0.68F, 0.79F);
        for (int i = 0; i < 7; i++) {
            float ry = 0.2F + i * 0.105F;
            if (i == 2) m.fill(0.3F, ry, 0.005F, 0.08F, m.colors.modified());
            m.text(0.32F, ry + 0.015F, 0.2F + i % 3 * 0.05F, m.colors.text());
            m.text(0.32F, ry + 0.05F, 0.3F);
            if (i % 3 == 2) {
                m.box(0.8F, ry + 0.01F, 0.13F, 0.06F, m.colors.field());
                m.outline(0.8F, ry + 0.01F, 0.13F, 0.06F, m.colors.fieldBorder());
                m.text(0.82F, ry + 0.035F, 0.04F, m.colors.text());
            } else {
                m.toggle(0.85F, ry + 0.015F, (i + (int) (m.time / 2F)) % 2 == 0);
            }
        }
    }

    static void vanillaGameRules(Mock m) {
        vanillaScene(m);
        m.vanillaText(0.5F, 0.05F, 0.12F, 0xFFFFFFFF);
        m.vanillaList(0.12F, 0.74F);
        for (int i = 0; i < 6; i++) {
            float ry = 0.15F + i * 0.115F;
            if (i % 3 == 0) {
                m.vanillaText(0.5F, ry + 0.03F, 0.16F, 0xFFFFFF55);
                continue;
            }
            m.text(0.2F, ry + 0.035F, 0.22F, 0xFFFFFFFF);
            m.vanillaButton(0.64F, ry, 0.16F, 0.09F);
        }
        m.vanillaButton(0.2F, 0.89F, 0.29F, 0.08F);
        m.vanillaButton(0.51F, 0.89F, 0.29F, 0.08F);
    }

    static void warnings(Mock m) {
        popup(m, 0.2F, 0.2F, 0.6F, 0.6F);
        float pulse = 0.5F + 0.5F * (float) Math.sin(m.time * 3);
        m.glyph(ConfigIcons.WARNING, 0.25F, 0.28F, 0.07F, ConfigStyle.mix(m.colors.warning(), 0xFFFFFFFF, pulse * 0.3F));
        m.title(0.29F, 0.265F, 0.24F);
        m.text(0.24F, 0.37F, 0.5F);
        m.text(0.24F, 0.42F, 0.46F);
        m.text(0.24F, 0.47F, 0.3F);
        m.box(0.24F, 0.54F, 0.025F, 0.04F, m.colors.toggleOff());
        m.text(0.28F, 0.555F, 0.2F);
        m.button(0.24F, 0.68F, 0.165F, 0.07F);
        m.button(0.415F, 0.68F, 0.165F, 0.07F);
        m.primary(0.59F, 0.68F, 0.17F, 0.07F);
    }

    static void social(Mock m) {
        popup(m, 0.1F, 0.08F, 0.8F, 0.84F);
        m.title(0.13F, 0.13F, 0.2F);
        for (int i = 0; i < 3; i++) {
            if (i == 0) m.box(0.13F + i * 0.13F, 0.2F, 0.12F, 0.07F, ConfigStyle.withAlpha(ConfigStyle.accent(), 0x60));
            else m.button(0.13F + i * 0.13F, 0.2F, 0.12F, 0.07F);
            m.text(0.155F + i * 0.13F, 0.23F, 0.07F, m.colors.text());
        }
        m.box(0.56F, 0.2F, 0.31F, 0.07F, m.colors.field());
        m.outline(0.56F, 0.2F, 0.31F, 0.07F, m.colors.fieldBorder());
        for (int i = 0; i < 5; i++) {
            float ry = 0.3F + i * 0.11F;
            m.box(0.13F, ry, 0.74F, 0.095F, ConfigStyle.withAlpha(m.colors.button(), 0x60));
            m.icon(0.14F, ry + 0.012F, 0.07F, i + 4);
            m.text(0.19F, ry + 0.02F, 0.12F, m.colors.text());
            m.text(0.19F, ry + 0.055F, 0.26F);
            ConfigIcons.Icon[] icons = {ConfigIcons.CHAT, ConfigIcons.EYE_OFF, ConfigIcons.FLAG};
            for (int b = 0; b < 3; b++) {
                m.button(0.68F + b * 0.062F, ry + 0.015F, 0.055F, 0.065F);
                m.glyph(icons[b], 0.7075F + b * 0.062F, ry + 0.0475F, 0.04F, b == 2 ? m.colors.error() : m.colors.textDim());
            }
        }
        m.glideRow(0.13F, 0.3F, 0.74F, 0.095F, 0.11F, 5, 1.8F);
    }

    static void vanillaSocial(Mock m) {
        scene(m, 0x90);
        m.vanillaText(0.5F, 0.06F, 0.18F, 0xFFFFFFFF);
        for (int i = 0; i < 3; i++) m.vanillaButton(0.2F + i * 0.205F, 0.12F, 0.19F, 0.08F);
        m.fill(0.2F, 0.23F, 0.6F, 0.6F, 0xFF2B2B2B);
        for (int i = 0; i < 4; i++) {
            float ry = 0.26F + i * 0.14F;
            m.fill(0.22F, ry, 0.56F, 0.12F, 0xFF404040);
            m.fill(0.23F, ry + 0.02F, 0.05F, 0.08F, 0xFF7A5A40);
            m.text(0.3F, ry + 0.05F, 0.14F, 0xFFFFFFFF);
            m.vanillaButton(0.63F, ry + 0.02F, 0.065F, 0.08F);
            m.vanillaButton(0.7F, ry + 0.02F, 0.065F, 0.08F);
        }
        m.vanillaButton(0.33F, 0.87F, 0.34F, 0.08F);
    }

    static void shaders(Mock m) {
        glazedScene(m);
        topBar(m, true);
        int chosen = (int) m.glide(6, 2.4F);
        for (int i = 0; i < 6; i++) {
            float tx = 0.03F + i % 3 * 0.2F;
            float ty = 0.17F + i / 3 * 0.4F;
            m.glass(tx, ty, 0.19F, 0.37F);
            m.fills().fillGradient(m.px(tx + 0.01F), m.py(ty + 0.02F), m.px(tx + 0.18F), m.py(ty + 0.2F),
                    GlazedBrand.identity(ICONS[i % ICONS.length]), 0xFF101820);
            m.text(tx + 0.02F, ty + 0.25F, 0.12F, m.colors.text());
            m.text(tx + 0.02F, ty + 0.3F, 0.14F);
            if (i == chosen) m.outline(tx, ty, 0.19F, 0.37F, ConfigStyle.accent());
        }
        m.glass(0.64F, 0.17F, 0.33F, 0.79F);
        m.title(0.67F, 0.22F, 0.18F);
        for (int i = 0; i < 4; i++) m.text(0.67F, 0.3F + i * 0.06F, 0.22F + i % 2 * 0.04F);
        m.primary(0.67F, 0.78F, 0.27F, 0.08F);
        m.button(0.67F, 0.68F, 0.27F, 0.07F);
    }

    static void vanillaShaders(Mock m) {
        vanillaScene(m);
        m.vanillaText(0.5F, 0.05F, 0.16F, 0xFFFFFFFF);
        m.vanillaList(0.12F, 0.68F);
        for (int i = 0; i < 6; i++) m.vanillaText(0.5F, 0.16F + i * 0.1F, 0.2F + i % 2 * 0.06F, 0xFFFFFFFF);
        m.outline(0.3F, 0.14F, 0.4F, 0.07F, 0xFFFFFFFF);
        for (int i = 0; i < 4; i++) m.vanillaButton(0.1F + i * 0.205F, 0.86F, 0.19F, 0.08F);
    }

    /** The settings' screen of a mod's config, for the look step: the screens every mod's settings open in. */
    static void configScreen(Mock m) {
        glazedScene(m);
        topBar(m, true);
        m.glass(0.03F, 0.17F, 0.26F, 0.66F);
        for (int i = 0; i < 6; i++) {
            m.icon(0.045F, 0.2F + i * 0.1F, 0.05F, i);
            m.text(0.08F, 0.215F + i * 0.1F, 0.1F + i % 3 * 0.04F);
        }
        m.glideRow(0.035F, 0.19F, 0.25F, 0.07F, 0.1F, 6, 2F);
        m.glass(0.31F, 0.17F, 0.66F, 0.66F);
        for (int i = 0; i < 5; i++) {
            float ry = 0.21F + i * 0.12F;
            m.text(0.34F, ry + 0.01F, 0.18F + i % 2 * 0.06F, m.colors.text());
            m.text(0.34F, ry + 0.05F, 0.3F);
            if (i % 2 == 0) m.toggle(0.86F, ry + 0.01F, i != 2);
            else m.slider(0.76F, ry + 0.03F, 0.17F, 0.35F + i * 0.1F);
        }
        m.bar(0.03F, 0.86F, 0.94F, 0.1F);
        m.button(0.05F, 0.88F, 0.07F, 0.06F);
        m.glyph(ConfigIcons.UNDO, 0.085F, 0.91F, 0.04F, m.colors.textDim());
        m.button(0.13F, 0.88F, 0.07F, 0.06F);
        m.glyph(ConfigIcons.REDO, 0.165F, 0.91F, 0.04F, m.colors.textDim());
        m.button(0.62F, 0.88F, 0.16F, 0.06F);
        m.primary(0.8F, 0.88F, 0.15F, 0.06F);
    }
}
