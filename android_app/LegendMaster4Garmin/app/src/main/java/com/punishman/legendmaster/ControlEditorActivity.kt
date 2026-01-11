package com.punishman.legendmaster

import android.app.Activity
import android.content.*
import android.graphics.*
import android.os.*
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import com.garmin.android.connectiq.*
import java.io.File
import java.util.Collections
import org.json.JSONArray

class ControlEditorActivity : AppCompatActivity() {

    private lateinit var rows: MutableList<ControlRow>
    private lateinit var adapter: ControlTableAdapter
    private lateinit var tvNameDisplay: TextView
    private var originalFileName: String? = null
    private val APP_ID = "YOUR_GARMIN_APP_ID_HERE"


    private var connectIQ: ConnectIQ? = null
    private var isSdkReady = false

    private var isMenuOpen = false
    private val uiHandler = Handler(Looper.getMainLooper())

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra("SCAN_JSON")?.let { importFromJson(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control_editor)

        tvNameDisplay = findViewById(R.id.tvControlNameDisplay)
        val rvTable = findViewById<RecyclerView>(R.id.rvControlTable)

        val btnMenu = findViewById<FloatingActionButton>(R.id.btnMenuFAB)
        val btnAdd = findViewById<FloatingActionButton>(R.id.btnAddRow)
        val btnScan = findViewById<FloatingActionButton>(R.id.btnScan)
        val btnClear = findViewById<FloatingActionButton>(R.id.btnClearAll)

        originalFileName = intent.getStringExtra("CONTROL_NAME")
        tvNameDisplay.text = originalFileName
        tvNameDisplay.setOnClickListener { showNameEditDialog() }

        rows = loadData()
        adapter = ControlTableAdapter(rows)
        rvTable.layoutManager = LinearLayoutManager(this)
        rvTable.adapter = adapter


        setupGarminSDK()


        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(r: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder): Boolean {
                val from = vh.adapterPosition
                val to = t.adapterPosition
                Collections.swap(rows, from, to)
                adapter.notifyItemMoved(from, to)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {
                val pos = vh.adapterPosition
                rows.removeAt(pos)
                adapter.notifyItemRemoved(pos)
                saveToFile()
            }
        }).attachToRecyclerView(rvTable)

        fun toggleMenu(open: Boolean) {
            isMenuOpen = open
            val vis = if (open) View.VISIBLE else View.GONE
            btnAdd.visibility = vis
            btnScan.visibility = vis
            btnClear.visibility = vis
            btnMenu.setImageResource(if (open) android.R.drawable.ic_menu_close_clear_cancel else android.R.drawable.ic_menu_sort_by_size)
        }

        btnMenu.setOnClickListener { toggleMenu(!isMenuOpen) }

        btnAdd.setOnClickListener {
            val nextNum = if (rows.isEmpty()) 31 else (rows.last().cpNum + 1).coerceAtMost(255)
            rows.add(ControlRow(nextNum))
            adapter.notifyItemInserted(rows.size - 1)
            rvTable.scrollToPosition(rows.size - 1)
            saveToFile()
            toggleMenu(false)
        }

        btnScan.setOnClickListener {
            scanLauncher.launch(Intent(this, ScanActivity::class.java))
            toggleMenu(false)
        }

        btnClear.setOnClickListener {
            AlertDialog.Builder(this, R.style.TableAlertDialogStyle).setTitle("Clear all?").setPositiveButton("Yes") { _, _ ->
                rows.clear()
                adapter.notifyDataSetChanged()
                saveToFile()
                toggleMenu(false)
            }.setNegativeButton("No", null).show()
        }

        rvTable.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING && isMenuOpen) {
                    uiHandler.removeCallbacksAndMessages(null)
                    uiHandler.postDelayed({ toggleMenu(false) }, 500)
                }
            }
        })

        findViewById<Button>(R.id.btnSaveAndExit).setOnClickListener { if (saveToFile()) finish() }
        findViewById<Button>(R.id.btnExport).setOnClickListener { exportToJson() }
        findViewById<Button>(R.id.btnImport).setOnClickListener { showImportDialog() }


        findViewById<Button>(R.id.btnSendToWatch).setOnClickListener { sendToGarmin() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveToFile()
                finish()
            }
        })
    }

    private fun setupGarminSDK() {
        connectIQ = ConnectIQ.getInstance(this, ConnectIQ.IQConnectType.WIRELESS)
        connectIQ?.initialize(this, true, object : ConnectIQ.ConnectIQListener {
            override fun onSdkReady() {
                isSdkReady = true
                Log.d("GARMIN", "SDK Ready")
            }
            override fun onInitializeError(status: ConnectIQ.IQSdkErrorStatus) {
                isSdkReady = false
                Log.e("GARMIN", "SDK Init Error: $status")
            }
            override fun onSdkShutDown() {
                isSdkReady = false
            }
        })
    }

    private fun sendToGarmin() {
        val ciq = connectIQ ?: return

        if (!isSdkReady) {
            Toast.makeText(this, "Garmin SDK not ready. Reconnecting...", Toast.LENGTH_SHORT).show()
            setupGarminSDK()
            return
        }

        if (rows.isEmpty()) {
            Toast.makeText(this, "Table is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val devices = ciq.knownDevices
        if (!devices.isNullOrEmpty()) {
            val data = rows.map { r ->
                val list = mutableListOf<Any?>()
                list.add(r.cpNum)
                r.icons.forEach { id ->
                    when {

                        id == 0 -> list.add(null)


                        id in 33..122 -> list.add(id - 33)
                        id in 123..212 -> list.add((id - 123) + 90)


                        id > 1000 -> list.add((id - 1000).toChar().toString())


                        else -> list.add(id)
                    }
                }
                list
            }

            ciq.sendMessage(devices[0], IQApp(APP_ID), data) { device, app, status ->
                runOnUiThread {
                    Toast.makeText(this@ControlEditorActivity, "Watch: $status", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "Device not found in ConnectIQ app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importFromJson(json: String) {
        try {
            val imported = Gson().fromJson(json, Array<ControlRow>::class.java) ?: return
            rows.clear()
            imported.forEach { rows.add(ControlRow(it.cpNum.coerceIn(31, 255), it.icons ?: IntArray(6))) }
            adapter.notifyDataSetChanged()
            saveToFile()
            Toast.makeText(this, "Import Success", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Wrong Data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportToJson() {
        try {
            val jsonStr = Gson().toJson(rows)
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("LegendData", jsonStr))
            Toast.makeText(this, "JSON copied", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { }
    }

    private fun showImportDialog() {
        val input = EditText(this).apply {
            hint = "Paste JSON here..."
            setTextColor(Color.WHITE)
            gravity = Gravity.TOP
            setLines(5)
            background.mutate().setColorFilter(Color.parseColor("#55AAFF"), PorterDuff.Mode.SRC_ATOP)
        }
        val container = FrameLayout(this).apply { setPadding(60, 40, 60, 40); addView(input) }
        AlertDialog.Builder(this, R.style.TableAlertDialogStyle).setTitle("Import JSON").setView(container)
            .setPositiveButton("Import") { _, _ -> importFromJson(input.text.toString().trim()) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showNameEditDialog() {
        val input = EditText(this).apply {
            setText(tvNameDisplay.text.toString())
            setTextColor(Color.WHITE)
            textSize = 24f
            setSelection(text.length)
            background.mutate().setColorFilter(Color.parseColor("#55AAFF"), PorterDuff.Mode.SRC_ATOP)
        }
        val container = FrameLayout(this).apply { setPadding(60, 40, 60, 40); addView(input) }
        AlertDialog.Builder(this, R.style.TableAlertDialogStyle).setTitle("Edit Name").setView(container)
            .setPositiveButton("OK") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) tvNameDisplay.text = newName
            }.setNegativeButton("Cancel", null).show()
    }

    private fun loadData(): MutableList<ControlRow> {
        val folder = File(filesDir, "controls").apply { if (!exists()) mkdirs() }
        val file = File(folder, "$originalFileName.json")
        return if (file.exists()) {
            try { Gson().fromJson(file.readText(), Array<ControlRow>::class.java).toMutableList() }
            catch (e: Exception) { mutableListOf(ControlRow(31)) }
        } else mutableListOf(ControlRow(31))
    }

    private fun saveToFile(): Boolean {
        val name = tvNameDisplay.text.toString().trim()
        if (name.isEmpty()) return false
        val folder = File(filesDir, "controls").apply { if (!exists()) mkdirs() }
        if (originalFileName != null && originalFileName != name) File(folder, "$originalFileName.json").delete()
        File(folder, "$name.json").writeText(Gson().toJson(rows))
        originalFileName = name
        return true
    }

    override fun onDestroy() {
        super.onDestroy()

        try {
            connectIQ?.shutdown(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}