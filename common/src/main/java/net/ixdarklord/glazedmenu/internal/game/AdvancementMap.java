package net.ixdarklord.glazedmenu.internal.game;

import net.ixdarklord.glazedmenu.internal.style.ConfigIcons;
import net.ixdarklord.glazedmenu.internal.style.ConfigStyle;
import net.ixdarklord.glazedmenu.internal.style.FillBatch;
import net.ixdarklord.glazedmenu.internal.style.GlazedBrand;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.FrameType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.Font;
import net.minecraft.util.Mth;
import net.ixdarklord.glazedmenu.internal.compat.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The advancements screen's map: a tab's advancements laid out as the game's own screen lays them, each in a column by
 * how far it is from the root and its parent halfway between its first and last, joined by lines, to drag around and
 * zoom. Done ones are lit and joined in the success color; a search or filter fades the ones it leaves out. The layout is
 * worked out once per tab (and again when the game tells of a change), and only the nodes in view are drawn.
 */
final class AdvancementMap {
    // A node's square, and the room each column and row takes (as on the game's screen: 28 by 27).
    private static final int NODE = 24;
    private static final int COLUMN = 34;
    private static final int ROW = 30;
    private static final int GRID = 30;
    private static final float MIN_ZOOM = 0.5F;
    private static final float MAX_ZOOM = 2;

    private final Owner owner;
    private final List<Node> nodes = new ArrayList<>();
    private int leaves;
    private int columns;
    // The tab the map was laid out and fitted for; null to fit it again on the next frame.
    @Nullable Advancement fitted;
    // The choice changed: it's brought into view on the next frame.
    boolean reveal;
    private double panX;
    private double panY;
    private float zoom = 1;
    private boolean dragging;
    // Where the map was drawn, for the mouse.
    private int left;
    private int top;
    private int width;
    private int height;

    /** What the map asks of the screen it's on. */
    interface Owner {
        boolean shown(Advancement node);

        boolean done(Advancement node);

        float percent(Advancement node);

        /** Whether the search and the filter leave it in. */
        boolean kept(Advancement node);

        net.minecraft.world.item.ItemStack icon(Advancement node);

        @Nullable Advancement chosen();

        void choose(Advancement holder);

        int typeColor(FrameType type);

        Font font();
    }

    /** A node of the map: where it sits, in columns and rows, and the one it's joined to on its left. */
    private static final class Node {
        final Advancement node;
        final DisplayInfo display;
        final int column;
        final @Nullable Node parent;
        float row;

        Node(Advancement node, DisplayInfo display, int column, @Nullable Node parent) {
            this.node = node;
            this.display = display;
            this.column = column;
            this.parent = parent;
        }
    }

    AdvancementMap(Owner owner) {
        this.owner = owner;
    }

    /** Lays a tab out again (its advancements changed); keeps where it's looked at unless it's another tab. */
    void layOut(@Nullable Advancement root) {
        this.nodes.clear();
        this.leaves = 0;
        this.columns = 0;
        if (root == null) return;
        if (this.owner.shown(root)) {
            this.place(root, 0, null);
        } else {
            for (Advancement child : this.after(root)) this.place(child, 0, null);
        }
        if (this.fitted != null && !this.fitted.equals(root)) this.fitted = null;
    }

    // The ones shown after a node: its children, or (past a hidden one) the children's own.
    private List<Advancement> after(Advancement node) {
        List<Advancement> after = new ArrayList<>();
        this.collectAfter(node, after);
        return after;
    }

    private void collectAfter(Advancement node, List<Advancement> into) {
        for (Advancement child : node.getChildren()) {
            if (this.owner.shown(child)) into.add(child);
            else this.collectAfter(child, into);
        }
    }

    // A tidy tree: each leaf a row of its own, in order; each parent halfway between its first and last child.
    private Node place(Advancement advancement, int column, @Nullable Node parent) {
        Node node = new Node(advancement, advancement.getDisplay(), column, parent);
        this.nodes.add(node);
        this.columns = Math.max(this.columns, column + 1);
        List<Advancement> after = this.after(advancement);
        if (after.isEmpty()) {
            node.row = this.leaves++;
            return node;
        }
        float first = -1;
        float last = 0;
        for (Advancement child : after) {
            Node placed = this.place(child, column + 1, node);
            if (first < 0) first = placed.row;
            last = placed.row;
        }
        node.row = (first + last) / 2;
        return node;
    }

    private int mapX(Node node) {
        return node.column * COLUMN;
    }

    private int mapY(Node node) {
        return Math.round(node.row * ROW);
    }

    // --- Drawing ---

    void extract(GuiGraphicsExtractor graphics, Advancement tab, int left, int top, int width, int height, int mouseX, int mouseY) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        if (!tab.equals(this.fitted)) this.fit(tab);
        if (this.reveal) this.revealChosen();
        this.clampPan();

        graphics.enableScissor(left, top, left + width, top + height);
        this.extractGrid(graphics);
        graphics.pose().pushMatrix();
        graphics.pose().translate(left + width / 2F, top + height / 2F);
        graphics.pose().scale(this.zoom, this.zoom);
        graphics.pose().translate((float) -this.panX, (float) -this.panY);

        int dim = ConfigStyle.withAlpha(ConfigStyle.mix(ConfigStyle.colors().panelBorder(), ConfigStyle.colors().text(), 0.35F), 0xD0);
        int lit = ConfigStyle.colors().success();
        // The lines first, so the nodes sit over them: from the parent's right, across half the gap, down, and in. A
        // little thicker than a hairline, whatever the zoom.
        int line = Math.max(2, Math.round(2 / this.zoom));
        // The grey ones first and the green ones over them, so a path to a done advancement runs green unbroken where
        // it shares its trunk with ones not done.
        // Every line's fills as one element for the GUI (FillBatch).
        FillBatch paths = FillBatch.start(graphics);
        for (int pass = 0; pass < 2; pass++) {
            for (Node node : this.nodes) {
                if (node.parent == null || this.owner.done(node.node) != (pass == 1)) continue;
                int fromX = this.mapX(node.parent) + NODE;
                int fromY = this.mapY(node.parent) + NODE / 2 - line / 2;
                int toX = this.mapX(node);
                int toY = this.mapY(node) + NODE / 2 - line / 2;
                int splitX = fromX + (toX - fromX - line) / 2;
                int color = pass == 1 ? lit : dim;
                paths.fill(fromX, fromY, splitX + line, fromY + line, color);
                paths.fill(splitX, Math.min(fromY, toY), splitX + line, Math.max(fromY, toY) + line, color);
                paths.fill(splitX, toY, toX, toY + line, color);
            }
        }
        paths.submit();

        Advancement chosen = this.owner.chosen();
        Node hovered = this.nodeAt(mouseX, mouseY);
        for (Node node : this.nodes) {
            if (!this.inView(node)) continue;
            this.extractNode(graphics, node, node == hovered, node.node.equals(chosen));
        }
        graphics.pose().popMatrix();
        graphics.disableScissor();

        if (hovered != null) {
            Font font = this.owner.font();
            List<FormattedCharSequence> tip = new ArrayList<>();
            tip.add(hovered.display.getTitle().copy().withStyle(ChatFormatting.BOLD).getVisualOrderText());
            tip.addAll(font.split(hovered.display.getDescription(), 200));
            graphics.setTooltipForNextFrame(font, tip, mouseX, mouseY);
        }
        if (this.nodes.isEmpty()) {
            ConfigStyle.centeredText(graphics, this.owner.font(), Component.translatable("advancements.empty"), left + width / 2, top + height / 2 - 4, width,
                    ConfigStyle.colors().textMuted());
        }
    }

    // A faint grid of dots behind the tree, moving with it.
    private void extractGrid(GuiGraphicsExtractor graphics) {
        float step = GRID * this.zoom;
        if (step < 8) return;
        int dot = ConfigStyle.withAlpha(ConfigStyle.colors().text(), 0x1C);
        double originX = this.left + this.width / 2.0 - this.panX * this.zoom;
        double originY = this.top + this.height / 2.0 - this.panY * this.zoom;
        double startX = originX - Math.ceil((originX - this.left) / step) * step;
        double startY = originY - Math.ceil((originY - this.top) / step) * step;
        // Thousands of dots, side by side: one element for the GUI, not one each (FillBatch).
        FillBatch dots = FillBatch.start(graphics);
        for (double y = startY; y < this.top + this.height; y += step) {
            for (double x = startX; x < this.left + this.width; x += step) {
                int px = (int) Math.round(x);
                int py = (int) Math.round(y);
                dots.fill(px, py, px + 1, py + 1, dot);
            }
        }
        dots.submit();
    }

    // A node, built up like a small badge: a shadow under it; a face shading from its color at the top to a deep shade
    // at the bottom, lit along its top edge and shaded along its bottom; a fine inner frame; its type's marks (a
    // goal's pips on its sides, a challenge's brackets in its corners, as the game's frames tell them apart); and its
    // item. A done one is green with a check on its corner; one under way has its progress along the bottom; one not
    // started is dimmed. The chosen one has a thin ring hugging its edge; one the search or filter leaves out is faded.
    private void extractNode(GuiGraphicsExtractor graphics, Node node, boolean hovered, boolean chosen) {
        int x = this.mapX(node);
        int y = this.mapY(node);
        boolean done = this.owner.done(node.node);
        float percent = this.owner.percent(node.node);
        FrameType kind = node.display.getFrame();
        int type = this.owner.typeColor(kind);
        int color = done ? ConfigStyle.colors().success() : type;
        int base = ConfigStyle.colors().popup() | 0xFF000000;
        float strength = done ? 0.55F : hovered ? 0.4F : 0.28F;

        // The node's fills under its item, as one element for the GUI (FillBatch).
        FillBatch face = FillBatch.start(graphics);
        // A soft shadow: a wide faint one, and a closer, darker one.
        ConfigStyle.rect(face, x + 1, y + 3, NODE, NODE, 0x30000000);
        ConfigStyle.rect(face, x + 1, y + 1, NODE, NODE, 0x48000000);
        if (chosen) border(face, x - 1, y - 1, NODE + 2, ConfigStyle.accent());
        ConfigStyle.rect(face, x, y, NODE, NODE, ConfigStyle.mix(base, color, strength * 0.45F));
        face.fillGradient(x + 1, y + 1, x + NODE - 1, y + NODE - 1, ConfigStyle.mix(base, color, strength), ConfigStyle.mix(base, color, strength * 0.3F));
        // Light from above: a sheen over its top half, a bright line along its top edge and a fainter one down its left;
        // shade along its bottom and right.
        face.fillGradient(x + 1, y + 1, x + NODE - 1, y + NODE / 2, 0x26FFFFFF, 0x00FFFFFF);
        face.fill(x + 2, y + 1, x + NODE - 2, y + 2, 0x60FFFFFF);
        face.fill(x + 1, y + 2, x + 2, y + NODE - 2, 0x24FFFFFF);
        face.fill(x + 2, y + NODE - 2, x + NODE - 2, y + NODE - 1, 0x50000000);
        face.fill(x + NODE - 2, y + 2, x + NODE - 1, y + NODE - 2, 0x30000000);
        border(face, x + 3, y + 3, NODE - 6, ConfigStyle.withAlpha(color, 0x48));
        border(face, x, y, NODE, ConfigStyle.withAlpha(color, done || hovered || chosen ? 0xFF : 0xB0));

        int mark = GlazedBrand.lift(type, 0.25F);
        if (kind == FrameType.GOAL) {
            // A pip at the middle of each side.
            int mid = NODE / 2;
            face.fill(x + mid - 1, y - 1, x + mid + 1, y + 1, mark);
            face.fill(x + mid - 1, y + NODE - 1, x + mid + 1, y + NODE + 1, mark);
            face.fill(x - 1, y + mid - 1, x + 1, y + mid + 1, mark);
            face.fill(x + NODE - 1, y + mid - 1, x + NODE + 1, y + mid + 1, mark);
        } else if (kind == FrameType.CHALLENGE) {
            // A bracket in each corner, inside the frame.
            int in = 2;
            int arm = 5;
            for (int corner = 0; corner < 4; corner++) {
                boolean right = corner % 2 == 1;
                boolean bottom = corner >= 2;
                int cx = right ? x + NODE - in - 1 : x + in;
                int cy = bottom ? y + NODE - in - 1 : y + in;
                face.fill(right ? cx - arm + 1 : cx, cy, right ? cx + 1 : cx + arm, cy + 1, mark);
                face.fill(cx, bottom ? cy - arm + 1 : cy, cx + 1, bottom ? cy + 1 : cy + arm, mark);
            }
        }

        face.submit();
        graphics.fakeItem(this.owner.icon(node.node), x + (NODE - 16) / 2, y + (NODE - 16) / 2);
        FillBatch over = FillBatch.start(graphics);
        if (!done && percent <= 0 && !hovered && !chosen) ConfigStyle.rect(over, x + 1, y + 1, NODE - 2, NODE - 2, ConfigStyle.withAlpha(base, 0x50));
        if (!done && percent > 0) {
            over.fill(x + 3, y + NODE - 4, x + NODE - 3, y + NODE - 2, 0xA0000000);
            over.fill(x + 3, y + NODE - 4, x + 3 + Math.round((NODE - 6) * percent), y + NODE - 2, ConfigStyle.colors().warning());
        }
        if (done) {
            // A small green tab on its top right corner, lit and shaded like the node, with the check on it.
            int badge = 9;
            int badgeX = x + NODE - badge + 2;
            int badgeY = y - 3;
            int green = ConfigStyle.colors().success();
            ConfigStyle.rect(over, badgeX + 1, badgeY + 1, badge, badge, 0x60000000);
            ConfigStyle.rect(over, badgeX, badgeY, badge, badge, ConfigStyle.mix(green, 0xFF000000, 0.45F));
            over.fillGradient(badgeX + 1, badgeY + 1, badgeX + badge - 1, badgeY + badge - 1, ConfigStyle.mix(green, 0xFFFFFFFF, 0.15F), ConfigStyle.mix(green, 0xFF000000, 0.2F));
            over.fill(badgeX + 2, badgeY + 1, badgeX + badge - 2, badgeY + 2, 0x80FFFFFF);
            over.fill(badgeX + 2, badgeY + badge - 2, badgeX + badge - 2, badgeY + badge - 1, 0x50000000);
            over.submit();
            ConfigIcons.CHECK.draw(graphics, badgeX + 1, badgeY + 2, badge - 2, 0x70000000);
            ConfigIcons.CHECK.draw(graphics, badgeX + 1, badgeY + 1, badge - 2, 0xFFFFFFFF);
        }
        // The fills over the item go before the check where it has one (the check sits on them), and here otherwise.
        if (!done) over.submit();
        if (!this.owner.kept(node.node)) ConfigStyle.rect(graphics, x - 1, y - 1, NODE + 2, NODE + 2, ConfigStyle.withAlpha(ConfigStyle.colors().popup(), 0xB8));
    }

    // A box's edge, a pixel wide, along the cut corners Glazed Menu's boxes have (without the glass outline's rim and
    // shadow, which would stand off it).
    private static void border(FillBatch fills, int x, int y, int size, int color) {
        fills.fill(x + 2, y, x + size - 2, y + 1, color);
        fills.fill(x + 2, y + size - 1, x + size - 2, y + size, color);
        fills.fill(x, y + 2, x + 1, y + size - 2, color);
        fills.fill(x + size - 1, y + 2, x + size, y + size - 2, color);
        fills.fill(x + 1, y + 1, x + 2, y + 2, color);
        fills.fill(x + size - 2, y + 1, x + size - 1, y + 2, color);
        fills.fill(x + 1, y + size - 2, x + 2, y + size - 1, color);
        fills.fill(x + size - 2, y + size - 2, x + size - 1, y + size - 1, color);
    }

    // --- Where it's looked at ---

    // A new tab: the whole tree where it fits (a small one a little larger), else as much of it as fits at a readable
    // size, around its root.
    private void fit(Advancement tab) {
        this.fitted = tab;
        float treeWidth = (this.columns - 1) * COLUMN + NODE;
        float treeHeight = Math.max(0, this.leaves - 1) * ROW + NODE;
        float fits = Math.min((this.width - 24) / treeWidth, (this.height - 24) / treeHeight);
        this.zoom = Mth.clamp(fits, 0.8F, 1.5F);
        this.panX = fits >= 0.8F ? treeWidth / 2 : (this.width / 2.0 - 16) / this.zoom;
        this.panY = treeHeight / 2;
        if (fits < 0.8F && !this.nodes.isEmpty()) this.panY = this.mapY(this.nodes.get(0)) + NODE / 2.0;
        this.reveal = false;
    }

    private void revealChosen() {
        this.reveal = false;
        Advancement chosen = this.owner.chosen();
        for (Node node : this.nodes) {
            if (!node.node.equals(chosen)) continue;
            double screenX = (this.mapX(node) + NODE / 2.0 - this.panX) * this.zoom;
            double screenY = (this.mapY(node) + NODE / 2.0 - this.panY) * this.zoom;
            double roomX = this.width / 2.0 - NODE * this.zoom;
            double roomY = this.height / 2.0 - NODE * this.zoom;
            if (Math.abs(screenX) > roomX || Math.abs(screenY) > roomY) {
                this.panX = this.mapX(node) + NODE / 2.0;
                this.panY = this.mapY(node) + NODE / 2.0;
            }
            return;
        }
    }

    // The tree can't be dragged off: some of it always stays in view.
    private void clampPan() {
        double treeWidth = (this.columns - 1) * COLUMN + NODE;
        double treeHeight = Math.max(0, this.leaves - 1) * ROW + NODE;
        double roomX = this.width / 2.0 / this.zoom;
        double roomY = this.height / 2.0 / this.zoom;
        this.panX = Mth.clamp(this.panX, Math.min(treeWidth / 2, roomX - 16), Math.max(treeWidth / 2, treeWidth - roomX + 16));
        this.panY = Mth.clamp(this.panY, Math.min(treeHeight / 2, roomY - 16), Math.max(treeHeight / 2, treeHeight - roomY + 16));
    }

    private boolean inView(Node node) {
        double screenX = (this.mapX(node) - this.panX) * this.zoom + this.width / 2.0;
        double screenY = (this.mapY(node) - this.panY) * this.zoom + this.height / 2.0;
        double size = (NODE + 4) * this.zoom;
        return screenX + size >= 0 && screenX - 4 <= this.width && screenY + size >= 0 && screenY - 4 <= this.height;
    }

    boolean isOver(double mouseX, double mouseY) {
        return mouseX >= this.left && mouseX < this.left + this.width && mouseY >= this.top && mouseY < this.top + this.height;
    }

    private @Nullable Node nodeAt(double mouseX, double mouseY) {
        if (!this.isOver(mouseX, mouseY)) return null;
        double mapX = (mouseX - this.left - this.width / 2.0) / this.zoom + this.panX;
        double mapY = (mouseY - this.top - this.height / 2.0) / this.zoom + this.panY;
        for (Node node : this.nodes) {
            int x = this.mapX(node);
            int y = this.mapY(node);
            if (mapX >= x && mapX < x + NODE && mapY >= y && mapY < y + NODE) return node;
        }
        return null;
    }

    // --- The mouse ---

    /** A press on a node chooses it at once; a press anywhere on the map starts a drag that moves it. */
    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isOver(mouseX, mouseY) || button != 0) return false;
        this.dragging = false;
        Node node = this.nodeAt(mouseX, mouseY);
        if (node != null) this.owner.choose(node.node);
        this.dragging = true;
        return true;
    }

    boolean mouseDragged(double dx, double dy) {
        if (!this.dragging) return false;
        this.panX -= dx / this.zoom;
        this.panY -= dy / this.zoom;
        return true;
    }

    /** The mouse let go: the drag ends. */
    boolean mouseReleased() {
        if (!this.dragging) return false;
        this.dragging = false;
        return true;
    }

    /** Zooms about the mouse, so the point under it stays put. */
    boolean mouseScrolled(double mouseX, double mouseY, double scroll) {
        if (!this.isOver(mouseX, mouseY) || scroll == 0) return false;
        float zoom = Mth.clamp(this.zoom * (scroll > 0 ? 1.15F : 1 / 1.15F), MIN_ZOOM, MAX_ZOOM);
        double offsetX = mouseX - this.left - this.width / 2.0;
        double offsetY = mouseY - this.top - this.height / 2.0;
        this.panX += offsetX / this.zoom - offsetX / zoom;
        this.panY += offsetY / this.zoom - offsetY / zoom;
        this.zoom = zoom;
        return true;
    }
}
