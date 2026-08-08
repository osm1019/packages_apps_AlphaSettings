/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.alpha.settings.preferences

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.util.AttributeSet
import android.util.PathParser
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.settings.R
import kotlin.math.min

class QSTileIconShapePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle
) : Preference(context, attrs, defStyleAttr) {

    private var dialog: AlertDialog? = null
    private var recyclerView: RecyclerView? = null

    private val entries: Array<String> =
        context.resources.getStringArray(R.array.qs_tile_icon_shape_entries)

    private val entryValues: Array<String> =
        context.resources.getStringArray(R.array.qs_tile_icon_shape_values)

    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager) {
        super.onAttachedToHierarchy(preferenceManager)
        updateSummary()
    }

    override fun onDetached() {
        dialog?.dismiss()
        dialog = null
        recyclerView = null
        super.onDetached()
    }

    override fun onClick() {
        val ctx = context
        val currentKey = getCurrentShapeKey()
        val selectedIndex = indexOfValue(currentKey).coerceAtLeast(0)

        val content = LayoutInflater.from(ctx).inflate(R.layout.selector_item_view, null)

        recyclerView = content.findViewById<RecyclerView>(R.id.recycler_view).apply {
            setHasFixedSize(true)

            val isLandscape = ctx.resources.configuration.orientation ==
                    Configuration.ORIENTATION_LANDSCAPE
            val span = if (isLandscape) 2 else 1
            layoutManager = GridLayoutManager(ctx, span)
            adapter = ShapeAdapter(selectedIndex)

            post {
                val fraction = if (isLandscape) 0.75f else 0.6f
                val maxHeight = (ctx.resources.displayMetrics.heightPixels * fraction).toInt()
                layoutParams.height = maxHeight
                requestLayout()
            }
        }

        dialog = AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .also { dlg ->
                dlg.setOnDismissListener {
                    dialog = null
                    recyclerView = null
                }
                dlg.show()
                applyDialogWidth(dlg)
                tintDialogAccent(dlg)
            }
    }

    private fun getCurrentShapeKey(): String {
        val raw = Settings.System.getString(
            context.contentResolver,
            SETTING_KEY
        )
        return if (raw != null && QSTileIconShapes.isKnownKey(raw)) raw else QSTileIconShapes.DEFAULT_KEY
    }

    private fun indexOfValue(key: String): Int =
        entryValues.indexOfFirst { it == key }

    private fun updateSummary() {
        val idx = indexOfValue(getCurrentShapeKey())
        summary = if (idx >= 0) entries[idx] else entries.firstOrNull().orEmpty()
    }

    private fun createPreviewDrawable(shapeKey: String): Drawable {
        if (shapeKey == QSTileIconShapes.DEFAULT_KEY) {
            // No silhouette to draw: show the two states it keeps instead, inactive then active.
            return TileStateMorphPreviewDrawable(getThemeIconColor())
        }
        val pathData = QSTileIconShapes.pathForKey(shapeKey)
        return TileIconShapePreviewDrawable(pathData, getThemeIconColor())
    }

    private fun getThemeIconColor(): Int {
        val tv = TypedValue()
        val theme = context.theme

        if (theme.resolveAttribute(android.R.attr.colorControlNormal, tv, true)) {
            return if (tv.resourceId != 0) context.getColor(tv.resourceId) else tv.data
        }
        if (theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)) {
            return if (tv.resourceId != 0) context.getColor(tv.resourceId) else tv.data
        }
        return 0xff000000.toInt()
    }

    private fun applyDialogWidth(dlg: AlertDialog) {
        val w = dlg.window ?: return
        val ctx = context

        val wm = ctx.getSystemService(WindowManager::class.java)

        val boundsWidthPx = try {
            wm.currentWindowMetrics.bounds.width()
        } catch (_: Throwable) {
            ctx.resources.displayMetrics.widthPixels
        }

        val density = ctx.resources.displayMetrics.density

        val maxDp = if (ctx.resources.configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE) {
            DIALOG_MAX_WIDTH_DP_LANDSCAPE
        } else {
            DIALOG_MAX_WIDTH_DP
        }
        val maxWidthPx = (maxDp * density + 0.5f).toInt()

        val targetPx = (boundsWidthPx * 0.85f).toInt()
        w.setLayout(min(maxWidthPx, targetPx), WindowManager.LayoutParams.WRAP_CONTENT)
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun tintDialogAccent(dlg: AlertDialog) {
        val tv = TypedValue()
        val theme = context.theme
        if (!theme.resolveAttribute(android.R.attr.colorAccent, tv, true)) return

        val accent = if (tv.resourceId != 0) context.getColor(tv.resourceId) else tv.data
        if (accent == 0) return

        val w = dlg.window
        if (w != null) {
            val titleId = context.resources.getIdentifier("alertTitle", "id", "android")
            val titleView = if (titleId != 0) w.decorView.findViewById<TextView>(titleId) else null
            titleView?.setTextColor(accent)
        }

        dlg.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(accent)
    }

    private inner class ShapeAdapter(applied: Int) :
        RecyclerView.Adapter<ShapeAdapter.ShapeViewHolder>() {

        private var selectedIndex: Int = applied.coerceIn(0, entries.size - 1)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShapeViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.icon_option_small, parent, false)
            return ShapeViewHolder(v)
        }

        override fun onBindViewHolder(holder: ShapeViewHolder, position: Int) {
            val shapeKey = entryValues[position]

            holder.image.setImageDrawable(createPreviewDrawable(shapeKey))
            holder.image.setPadding(8, 8, 8, 8)
            holder.name.visibility = View.VISIBLE
            holder.name.text = entries[position]

            holder.itemView.isActivated = (position == selectedIndex)

            holder.itemView.setOnClickListener {
                val old = selectedIndex
                selectedIndex = position

                Settings.System.putString(
                    context.contentResolver,
                    SETTING_KEY,
                    shapeKey
                )

                summary = entries[position]
                callChangeListener(shapeKey)

                notifyItemChanged(old)
                notifyItemChanged(selectedIndex)

                dialog?.dismiss()
            }
        }

        override fun getItemCount(): Int = entries.size

        inner class ShapeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val name: TextView = itemView.findViewById(R.id.option_label)
            val image: ImageView = itemView.findViewById(R.id.option_thumbnail)
        }
    }

    /**
     * Preview for [QSTileIconShapes.DEFAULT_KEY]. It has no path, so draw what it actually gives
     * you: the AOSP tile pair, a stadium while inactive and a squircle while active. Corner radii
     * are expressed as a fraction of the cell so they read the same at any preview size — 50% and
     * 33%, matching SystemUI's 50dp and 24dp on a 72dp tile.
     */
    private class TileStateMorphPreviewDrawable(color: Int) : Drawable() {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }

        override fun draw(canvas: Canvas) {
            val r = bounds
            if (r.isEmpty) return

            val gap = r.width() * CELL_GAP_FRACTION
            val cellWidth = (r.width() - gap) / 2f
            val cellHeight = r.height().toFloat()
            val minSide = min(cellWidth, cellHeight)

            canvas.drawRoundRect(
                r.left.toFloat(), r.top.toFloat(),
                r.left + cellWidth, r.top + cellHeight,
                minSide * INACTIVE_CORNER_FRACTION, minSide * INACTIVE_CORNER_FRACTION,
                paint,
            )
            canvas.drawRoundRect(
                r.left + cellWidth + gap, r.top.toFloat(),
                r.right.toFloat(), r.top + cellHeight,
                minSide * ACTIVE_CORNER_FRACTION, minSide * ACTIVE_CORNER_FRACTION,
                paint,
            )
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        private companion object {
            const val CELL_GAP_FRACTION = 0.12f
            const val INACTIVE_CORNER_FRACTION = 0.5f
            const val ACTIVE_CORNER_FRACTION = 1f / 3f
        }
    }

    private class TileIconShapePreviewDrawable(
        pathData: String,
        color: Int,
    ) : Drawable() {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }

        private val path: Path = try {
            PathParser.createPathFromPathData(pathData)
        } catch (_: RuntimeException) {
            PathParser.createPathFromPathData(
                QSTileIconShapes.pathForKey(QSTileIconShapes.FALLBACK_PATH_KEY)
            )
        }

        override fun draw(canvas: Canvas) {
            val r = bounds
            if (r.isEmpty) return

            canvas.save()
            val sx = r.width() / 100f
            val sy = r.height() / 100f
            canvas.translate(r.left.toFloat(), r.top.toFloat())
            canvas.scale(sx, sy)
            canvas.drawPath(path, paint)
            canvas.restore()
        }

        override fun setAlpha(alpha: Int) {
            paint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            paint.colorFilter = colorFilter
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }

    companion object {
        private const val SETTING_KEY = "qs_tile_icon_shape"
        private const val DIALOG_MAX_WIDTH_DP = 320
        private const val DIALOG_MAX_WIDTH_DP_LANDSCAPE = 640
    }
}
