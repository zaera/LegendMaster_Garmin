package com.punishman.legendmaster

import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fileList: MutableList<String>

    private lateinit var adapter: ControlsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerViewControls)
        recyclerView.layoutManager = LinearLayoutManager(this)


        fileList = mutableListOf()
        adapter = ControlsAdapter(fileList) { fileName ->
            openEditor(fileName)
        }
        recyclerView.adapter = adapter

        val btnCreate = findViewById<FloatingActionButton>(R.id.btnCreate)
        val btnHelp = findViewById<ImageButton>(R.id.btnHelp)

        btnCreate.setOnClickListener {
            showCreateNameDialog()
        }

        btnHelp.setOnClickListener {
            showHelpDialog()
        }

        setupSwipeToDelete()
    }

    override fun onResume() {
        super.onResume()
        loadExistingControls()
    }

    private fun loadExistingControls() {
        val folder = File(filesDir, "controls")
        if (!folder.exists()) folder.mkdir()


        val loadedFiles = folder.listFiles { file -> file.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.toMutableList() ?: mutableListOf()

        fileList.clear()
        fileList.addAll(loadedFiles)


        adapter.notifyDataSetChanged()
    }

    private fun showHelpDialog() {
        val helpText = """
        GUIDE:
        
        1. Main Screen:
        - Use (+) to create a new control legend.
        - Swipe a row LEFT to delete the legend.
        - Tap a legend name to open the editor.
        
        2. Legend Editor:
        - Tap 'Name' (top) to rename the legend.
        - Tap CP number to change it (31-255).
        - Tap any cell (C to H) to select a symbol.
        - Symbols are from official ISOM 2024 legend.
        - Use EXPORT to copy JSON to clipboard.
        - Use IMPORT to load legend from text.
        - Maximum 99 controls per list.
        - Camera recognition works ONLY for CP numbers
        
        3. Garmin Watch:
        - Ensure Bluetooth is active.
        - Open 'Legend Master' app on your watch.
        - Press 'Send to Watch' in the editor.
        
        Tested with Enduro 3 and Fenix 3 HR
        Thanks to: laura coach Ann10_08 killkost michailova22
    """.trimIndent()

        val textView = TextView(this).apply {
            text = helpText
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(48, 32, 48, 32)

            setLineSpacing(0f, 1.2f)
        }

        val scrollView = ScrollView(this).apply {
            addView(textView)
        }

        AlertDialog.Builder(this, R.style.TableAlertDialogStyle)
            .setTitle("HOW TO USE")
            .setView(scrollView)
            .setPositiveButton("GOT IT", null)
            .show()
    }

    private fun showCreateNameDialog() {

        val names = resources.getStringArray(R.array.random_names)
        val randomName = if (names.isNotEmpty()) names.random() else "New Legend"

        val input = EditText(this).apply {
            setText(randomName)
            setTextColor(Color.WHITE)
            textSize = 28f
            setSelection(text.length)
            background.mutate().setColorFilter(Color.parseColor("#55AAFF"), PorterDuff.Mode.SRC_ATOP)
        }

        val container = FrameLayout(this).apply {
            setPadding(60, 40, 60, 40)
            addView(input)
        }

        AlertDialog.Builder(this, R.style.TableAlertDialogStyle)
            .setTitle("CREATE NEW LIST")
            .setView(container)
            .setPositiveButton("CREATE") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) openEditor(name)
                else Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun setupSwipeToDelete() {
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val fileName = fileList[position]

                AlertDialog.Builder(this@MainActivity, R.style.TableAlertDialogStyle)
                    .setTitle("DELETE CONTROLS?")
                    .setMessage("Are you sure you want to delete '$fileName'?")
                    .setPositiveButton("DELETE") { _, _ ->
                        val file = File(File(filesDir, "controls"), "$fileName.json")
                        if (file.exists()) file.delete()
                        fileList.removeAt(position)
                        adapter.notifyItemRemoved(position)
                    }
                    .setNegativeButton("CANCEL") { _, _ ->
                        adapter.notifyItemChanged(position)
                    }
                    .setOnCancelListener {
                        adapter.notifyItemChanged(position)
                    }
                    .show()
            }
        })
        touchHelper.attachToRecyclerView(recyclerView)
    }

    private fun openEditor(controlName: String) {
        val intent = Intent(this, ControlEditorActivity::class.java)
        intent.putExtra("CONTROL_NAME", controlName)
        startActivity(intent)
    }
}