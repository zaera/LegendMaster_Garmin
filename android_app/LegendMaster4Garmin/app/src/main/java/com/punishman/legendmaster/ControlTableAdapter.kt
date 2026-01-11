package com.punishman.legendmaster

import android.graphics.*
import android.text.InputType
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class ControlTableAdapter(private val rows: MutableList<ControlRow>) : RecyclerView.Adapter<ControlTableAdapter.VH>() {

    private val ICON_SIZE = 38
    private val COLS = 6

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvCpNum: TextView = v.findViewById(R.id.tvCpNum)
        val frames: List<FrameLayout> = listOf(
            v.findViewById(R.id.frameC), v.findViewById(R.id.frameD),
            v.findViewById(R.id.frameE), v.findViewById(R.id.frameF),
            v.findViewById(R.id.frameG), v.findViewById(R.id.frameH)
        )
        val imageIcons: List<ImageView> = listOf(
            v.findViewById(R.id.ivIconC), v.findViewById(R.id.ivIconD),
            v.findViewById(R.id.ivIconE), v.findViewById(R.id.ivIconF),
            v.findViewById(R.id.ivIconG), v.findViewById(R.id.ivIconH)
        )
        val textIcons: List<TextView> = listOf(
            v.findViewById(R.id.txtC), v.findViewById(R.id.txtD),
            v.findViewById(R.id.txtE), v.findViewById(R.id.txtF),
            v.findViewById(R.id.txtG), v.findViewById(R.id.txtH)
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_control_row, parent, false)
        return VH(v)
    }

    private fun getIconBitmap(ctx: android.content.Context, id: Int): Bitmap? {
        if (id < 33 || id > 212) return null
        val resId = if (id <= 122) R.drawable.legend1 else R.drawable.legend2
        val options = BitmapFactory.Options().apply { inScaled = false }
        val sheet = BitmapFactory.decodeResource(ctx.resources, resId, options) ?: return null
        val index = if (id <= 122) (id - 33) else (id - 123)
        val x = (index % COLS) * ICON_SIZE
        val y = (index / COLS) * ICON_SIZE
        return try { Bitmap.createBitmap(sheet, x, y, ICON_SIZE, ICON_SIZE) } catch (e: Exception) { null }
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val currentPos = holder.adapterPosition
        val row = rows[currentPos]

        holder.tvCpNum.text = row.cpNum.toString()

        holder.tvCpNum.setOnClickListener {

            val clickPos = holder.adapterPosition
            if (clickPos == RecyclerView.NO_POSITION) return@setOnClickListener

            showNumberInputDialog(it.context, rows[clickPos].cpNum) { res ->
                rows[clickPos].cpNum = res
                notifyItemChanged(clickPos)
            }
        }

        holder.tvCpNum.setOnLongClickListener {
            val clickPos = holder.adapterPosition
            if (clickPos == RecyclerView.NO_POSITION) return@setOnLongClickListener true

            AlertDialog.Builder(it.context, R.style.TableAlertDialogStyle)
                .setTitle("Delete record?")
                .setPositiveButton("Yes") { _, _ ->
                    rows.removeAt(clickPos)
                    notifyItemRemoved(clickPos)

                    notifyItemRangeChanged(clickPos, rows.size)
                }.setNegativeButton("No", null).show()
            true
        }

        row.icons.forEachIndexed { index, id ->
            val img = holder.imageIcons[index]
            val txt = holder.textIcons[index]

            if (id in 33..212) {
                img.visibility = View.VISIBLE
                txt.visibility = View.GONE
                img.setImageBitmap(getIconBitmap(img.context, id))
            } else if (id > 1000) {
                img.visibility = View.GONE
                txt.visibility = View.VISIBLE
                txt.text = (id - 1000).toChar().toString()
            } else {
                img.visibility = View.VISIBLE
                img.setImageBitmap(null)
                txt.visibility = View.GONE
            }

            holder.frames[index].setOnClickListener {
                val clickPos = holder.adapterPosition
                if (clickPos == RecyclerView.NO_POSITION) return@setOnClickListener

                showGridIconPicker(it.context, rows[clickPos].icons[index]) { newId ->
                    rows[clickPos].icons[index] = newId
                    notifyItemChanged(clickPos)
                }
            }
        }
    }

    private fun showNumberInputDialog(ctx: android.content.Context, current: Int, onRes: (Int) -> Unit) {
        val input = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current.toString())
            setTextColor(Color.WHITE)
            textSize = 36f
            gravity = Gravity.CENTER
            setSelection(text.length)
            background.mutate().setColorFilter(Color.parseColor("#55AAFF"), PorterDuff.Mode.SRC_ATOP)
        }

        val container = FrameLayout(ctx).apply {
            setPadding(80, 60, 80, 60)
            addView(input)
        }

        AlertDialog.Builder(ctx, R.style.TableAlertDialogStyle)
            .setTitle("CP Number (31-255)")
            .setView(container)
            .setPositiveButton("Ok") { _, _ ->
                val str = input.text.toString()
                var valInt = if (str.isEmpty()) 31 else (str.toIntOrNull() ?: 31)
                if (valInt < 1) valInt = 1
                if (valInt > 255) valInt = 255
                onRes(valInt)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showGridIconPicker(ctx: android.content.Context, cur: Int, onRes: (Int) -> Unit) {
        val root = FrameLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val scrollView = ScrollView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (ctx.resources.displayMetrics.heightPixels * 0.7).toInt()
            )
        }

        val grid = GridLayout(ctx).apply {
            columnCount = 6
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            setPadding(16, 16, 16, 180)
            setBackgroundColor(Color.WHITE)
        }

        scrollView.addView(grid)
        root.addView(scrollView)

        val fab = FloatingActionButton(ctx).apply {
            val size = (56 * ctx.resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, 48, 48)
            }
            setImageResource(android.R.drawable.ic_menu_delete)
            supportBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#55AAFF"))
            imageTintList = android.content.res.ColorStateList.valueOf(Color.BLACK)
        }

        root.addView(fab)

        val dialog = AlertDialog.Builder(ctx, R.style.TableAlertDialogStyle)
            .setTitle("Symbol select")
            .setView(root)
            .create()

        fab.setOnClickListener {
            onRes(0)
            dialog.dismiss()
        }

        for (i in 33..212) addPickerItem(ctx, grid, i, cur, true, onRes, dialog)
        for (i in 33..126) addPickerItem(ctx, grid, i + 1000, cur, false, onRes, dialog)

        dialog.show()
    }

    private fun addPickerItem(ctx: android.content.Context, grid: GridLayout, id: Int, cur: Int, isImg: Boolean, onRes: (Int) -> Unit, dialog: AlertDialog) {
        val item = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = GridLayout.LayoutParams().apply {
                width = (ctx.resources.displayMetrics.widthPixels / 7)
                height = GridLayout.LayoutParams.WRAP_CONTENT
                setMargins(4, 4, 4, 4)
            }

            setPadding(0, 12, 0, 12)
            if (id == cur) setBackgroundColor(Color.parseColor("#BBDEFB"))

            if (isImg) {
                val img = ImageView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(110, 110)
                    setImageBitmap(getIconBitmap(ctx, id))
                }
                addView(img)
            } else {
                val txt = TextView(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(110, 110)
                    gravity = Gravity.CENTER
                    text = (id - 1000).toChar().toString()
                    setTextColor(Color.BLACK)
                    textSize = 20f
                    typeface = Typeface.DEFAULT_BOLD
                }
                addView(txt)
            }
            setOnClickListener { onRes(id); dialog.dismiss() }
        }
        grid.addView(item)
    }

    override fun getItemCount() = rows.size
}