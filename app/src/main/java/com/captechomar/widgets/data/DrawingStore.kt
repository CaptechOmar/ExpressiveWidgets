package com.captechomar.widgets.data

import android.content.Context
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * One pen movement. [type] is [INK], [ERASE] or [CLEAR] (a whole-canvas wipe, so Clear can be undone like any stroke).
 * [pts] holds x, y pairs as fractions of the canvas width.
 */
class Stroke(val type: Int, val pts: FloatArray) {
    companion object {
        const val INK = 0
        const val ERASE = 1
        const val CLEAR = 2
    }
}

/** A saved drawing. [aspect] is height / width of the canvas it was drawn on. */
class Drawing(val aspect: Float, val strokes: List<Stroke>)

/** Keeps each drawing widget's strokes in a small file of its own. */
object DrawingStore {
    const val INK_W = 0.012f
    const val ERASE_W = 0.05f

    private fun file(context: Context, id: Int) = File(context.applicationContext.filesDir, "drawing_$id.bin")

    fun load(context: Context, id: Int): Drawing? = try {
        val f = file(context, id)
        if (!f.exists()) null
        else DataInputStream(f.inputStream().buffered()).use { s ->
            s.readInt() // format version
            val aspect = s.readFloat()
            val strokes = ArrayList<Stroke>()
            repeat(s.readInt()) {
                val type = s.readByte().toInt()
                val pts = FloatArray(s.readInt()) { s.readFloat() }
                strokes += Stroke(type, pts)
            }
            Drawing(aspect, strokes)
        }
    } catch (_: Throwable) {
        null
    }

    fun save(context: Context, id: Int, aspect: Float, strokes: List<Stroke>) {
        // everything before the last wipe is invisible, so it is not kept
        val last = strokes.indexOfLast { it.type == Stroke.CLEAR }
        val kept = if (last >= 0) strokes.drop(last + 1) else strokes
        try {
            val tmp = File(file(context, id).path + ".tmp")
            DataOutputStream(tmp.outputStream().buffered()).use { s ->
                s.writeInt(1)
                s.writeFloat(aspect)
                s.writeInt(kept.size)
                for (st in kept) {
                    s.writeByte(st.type)
                    s.writeInt(st.pts.size)
                    for (v in st.pts) s.writeFloat(v)
                }
            }
            tmp.renameTo(file(context, id))
        } catch (_: Throwable) {
        }
    }

    fun delete(context: Context, id: Int) {
        file(context, id).delete()
    }
}
