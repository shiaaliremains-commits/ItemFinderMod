package my.itemfinder.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

class FinderScreen : Screen(Component.literal("Item Finder")) {

    class Entry(val stack: ItemStack, val total: Int, val boxes: List<Box>, val name: String)

    private val cols = 9
    private val cell = 24
    private val panelW = 252
    private var rowsVis = 6
    private var panelH = 0
    private var px = 0
    private var py = 0
    private var gx = 0
    private var gy = 0
    private var scroll = 0
    private var dragging = false
    private var dbg = ""
    private var hovered: Entry? = null
    private lateinit var search: EditBox
    private var all = ArrayList<Entry>()
    private var shown = ArrayList<Entry>()

    private fun c(v: Long): Int = v.toInt()
    private var wasDown = false
    private var pendingEntry: Entry? = null

    private fun leftDown(): Boolean {
        try {
            val mc = Minecraft.getInstance()
            val mh = Minecraft::class.java.getField("mouseHandler").get(mc)
            val m = mh.javaClass.methods.firstOrNull { it.name == "isLeftPressed" && it.parameterCount == 0 }
            if (m != null) return m.invoke(mh) as Boolean
        } catch (t: Throwable) {
        }
        return false
    }

    fun commit() {
        val e = pendingEntry ?: return
        pendingEntry = null
        Highlights.add(Minecraft.getInstance(), e.boxes)
        DebugLog.log("selected " + e.name)
        onClose()
    }

    private fun fmt(n: Int): String {
        if (n >= 1000000) return (n / 1000000).toString() + "M"
        if (n >= 1000) return (n / 1000).toString() + "k"
        return n.toString()
    }

    private fun buildEntries() {
        val mc = Minecraft.getInstance()
        val dim = Store.dimOf(mc)
        val totals = LinkedHashMap<Item, Int>()
        val where = HashMap<Item, ArrayList<Box>>()
        for (b in Store.boxes) {
            if (b.dim != dim) continue
            for ((item, count) in b.items) {
                totals.merge(item, count) { a, d -> a + d }
                where.getOrPut(item) { ArrayList() }.add(b)
            }
        }
        all = ArrayList()
        for ((item, total) in totals) {
            val st = ItemStack(item)
            all.add(Entry(st, total, where[item] ?: ArrayList(), st.hoverName.string))
        }
        all.sortBy { e -> e.name.lowercase() }
    }

    private fun refilter() {
        val q = search.value.lowercase().trim()
        shown = ArrayList(all.filter { e -> e.name.lowercase().contains(q) })
        scroll = scroll.coerceIn(0, maxScroll())
    }

    private fun maxScroll(): Int {
        val rows = (shown.size + cols - 1) / cols
        return (rows - rowsVis).coerceAtLeast(0)
    }

    override fun init() {
        buildEntries()
        rowsVis = ((height - 56 - 40 - 20) / cell).coerceIn(2, 6)
        panelH = 56 + rowsVis * cell + 6 + 26 + 8
        px = (width - panelW) / 2
        py = (height - panelH) / 2
        gx = px + 12
        gy = py + 56
        search = EditBox(font, px + 30, py + 33, panelW - 42, 18, Component.literal("Search"))
        search.setHint(Component.literal("Search items..."))
        search.setResponder { _ -> scroll = 0; refilter() }
        addWidget(search)
        setInitialFocus(search)
        wasDown = leftDown()
        DebugLog.log("screen opened entries=" + all.size.toString() + " w=" + width.toString() + " h=" + height.toString())
        refilter()
    }

    private fun entryAt(mx: Double, my: Double): Entry? {
        if (mx < gx || my < gy) return null
        val col = ((mx - gx) / cell).toInt()
        val row = ((my - gy) / cell).toInt()
        if (col >= cols || row >= rowsVis) return null
        if (((mx - gx) % cell) >= 22.0 || ((my - gy) % cell) >= 22.0) return null
        val idx = (scroll + row) * cols + col
        return if (idx >= 0 && idx < shown.size) shown[idx] else null
    }

    private fun pix(g: GuiGraphicsExtractor, x: Int, y: Int, s: Int, rows: List<String>, colors: Map<Char, Int>) {
        for ((r, row) in rows.withIndex()) {
            for ((i, ch) in row.withIndex()) {
                val col = colors[ch] ?: continue
                g.fill(x + i * s, y + r * s, x + i * s + s, y + r * s + s, col)
            }
        }
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        g.fill(0, 0, width, height, c(0xB0050810))
        g.fill(px - 2, py - 2, px + panelW + 2, py + panelH + 2, c(0xFF010409))
        g.fill(px - 1, py - 1, px + panelW + 1, py + panelH + 1, c(0xFF30363D))
        g.fill(px, py, px + panelW, py + panelH, c(0xFF161B22))
        g.fill(px, py, px + panelW, py + 26, c(0xFF0D1117))
        val q = panelW / 4
        g.fill(px, py + 26, px + q, py + 29, c(0xFF3FB950))
        g.fill(px + q, py + 26, px + 2 * q, py + 29, c(0xFF58A6FF))
        g.fill(px + 2 * q, py + 26, px + 3 * q, py + 29, c(0xFFBC8CFF))
        g.fill(px + 3 * q, py + 26, px + panelW, py + 29, c(0xFFF0883E))

        val chest = listOf(
            "DDDDDDDDDDDD", "DBBBBBBBBBBD", "DBBBBBBBBBBD", "DDDDDDDDDDDD",
            "DBBBBYYBBBBD", "DBBBBYYBBBBD", "DBBBBBBBBBBD", "DDDDDDDDDDDD"
        )
        pix(g, px + 10, py + 5, 2, chest, mapOf('D' to c(0xFF5A3410), 'B' to c(0xFFB5651D), 'Y' to c(0xFFFFD33D)))
        g.text(font, "Item Finder", px + 42, py + 9, c(0xFFFFD33D))
        val cnt = "Boxes: " + Store.boxes.size.toString()
        g.text(font, cnt, px + panelW - font.width(cnt) - 10, py + 9, c(0xFF3FB950))

        val mag = listOf(
            "..####...", ".#....#..", "#......#.", "#......#.", "#......#.",
            ".#....#..", "..####.#.", ".......##", "........#"
        )
        pix(g, px + 14, py + 36, 1, mag, mapOf('#' to c(0xFF58A6FF)))

        val hover = entryAt(mouseX.toDouble(), mouseY.toDouble())
        hovered = hover
        val downNow = leftDown()
        if (downNow && !wasDown && hover != null) {
            pendingEntry = hover
            FinderPending.screen = this
            dbg = "poll click on " + hover.name
        }
        wasDown = downNow
        for (i in 0 until cols * rowsVis) {
            val sx = gx + (i % cols) * cell
            val sy = gy + (i / cols) * cell
            val idx = scroll * cols + i
            val e = if (idx < shown.size) shown[idx] else null
            val isHover = e != null && e === hover
            g.fill(sx, sy, sx + 22, sy + 22, c(if (isHover) 0xFFFFD33D else 0xFF010409))
            g.fill(sx + 1, sy + 1, sx + 22, sy + 22, c(if (isHover) 0xFFFFD33D else 0xFF30363D))
            g.fill(sx + 1, sy + 1, sx + 21, sy + 21, c(if (isHover) 0xFF1B3A66 else 0xFF0D1117))
            if (e != null) {
                g.item(e.stack, sx + 3, sy + 3)
                g.itemDecorations(font, e.stack, sx + 3, sy + 3, if (e.total > 1) fmt(e.total) else null)
            }
        }
        if (shown.isEmpty()) {
            g.text(font, "No items found. Open some chests first!", gx + 8, gy + 40, c(0xFFF0883E))
        }

        val tx = px + 234
        val th = rowsVis * cell - 2
        g.fill(tx, gy, tx + 8, gy + th, c(0xFF010409))
        g.fill(tx + 1, gy + 1, tx + 8, gy + th, c(0xFF30363D))
        g.fill(tx + 1, gy + 1, tx + 7, gy + th - 1, c(0xFF0D1117))
        val ms = maxScroll()
        val thumbH = if (ms == 0) th - 2 else 24
        val thumbY = if (ms == 0) gy + 1 else gy + 1 + ((th - 2 - thumbH) * scroll) / ms
        val tc = if (ms == 0) 0xFF30363D else 0xFF58A6FF
        g.fill(tx + 1, thumbY, tx + 7, thumbY + thumbH, c(tc))
        g.fill(tx + 1, thumbY, tx + 7, thumbY + 1, c(0xFFFFFFFF))

        val fy = gy + rowsVis * cell + 4
        g.fill(px + 12, fy, px + panelW - 12, fy + 22, c(0xFF010409))
        g.fill(px + 13, fy + 1, px + panelW - 12, fy + 22, c(0xFF30363D))
        g.fill(px + 13, fy + 1, px + panelW - 13, fy + 21, c(0xFF0D1117))
        if (hover != null) {
            val info = hover.name + "  x" + hover.total.toString() + "  in " + hover.boxes.size.toString() + " box"
            g.text(font, info, px + 18, fy + 7, c(0xFF3FB950))
        } else {
            g.text(font, "Click an item to highlight its box for 30s", px + 18, fy + 7, c(0xFF8B949E))
        }

        search.extractRenderState(g, mouseX, mouseY, delta)
    }

    private fun setScrollFrom(my: Double) {
        val ms = maxScroll()
        if (ms == 0) return
        val th = (rowsVis * cell - 2).toDouble()
        val f = ((my - gy) / th).coerceIn(0.0, 1.0)
        scroll = Math.round(f * ms).toInt()
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() == 0) {
            val mx = event.x()
            val my = event.y()
            val e = hovered ?: entryAt(mx, my)
            if (e != null) {
                Highlights.add(Minecraft.getInstance(), e.boxes)
                onClose()
                return true
            }
            if (mx >= px + 232 && mx <= px + 244 && my >= gy && my <= gy + rowsVis * cell) {
                dragging = true
                setScrollFrom(my)
                return true
            }
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dx: Double, dy: Double): Boolean {
        if (dragging) {
            setScrollFrom(event.y())
            return true
        }
        return super.mouseDragged(event, dx, dy)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        dragging = false
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(mx: Double, my: Double, sx: Double, sy: Double): Boolean {
        val dir = if (sy > 0) -1 else 1
        scroll = (scroll + dir).coerceIn(0, maxScroll())
        return true
    }

    override fun isPauseScreen(): Boolean = false
}

object FinderPending {
    var screen: FinderScreen? = null
}
