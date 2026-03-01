package com.example.gesturerecord

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gesturerecord.data.GestureCombination
import com.example.gesturerecord.service.GestureAccessibilityService
import com.example.gesturerecord.service.OverlayService
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory((application as GestureApp).database.gestureDao())
    }

    private lateinit var adapter: GestureCombinationAdapter

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(this)) {
            checkAccessibilityPermission()
        } else {
            Toast.makeText(this, "需要顯示在其他應用程式上層權限", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupRecyclerView()

        findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            showAddDialog()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.combinations.collect { list ->
                    adapter.submitList(list)
                }
            }
        }
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        adapter = GestureCombinationAdapter(
            onClick = { combo ->
                openOverlayForCombo(combo)
            },
            onMoreOptionsClick = { view, combo ->
                showPopupMenu(view, combo)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun showAddDialog() {
        val editText = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("新增手勢組合")
            .setView(editText)
            .setPositiveButton("新增") { _, _ ->
                val name = editText.text.toString()
                if (name.isNotBlank()) {
                    viewModel.addCombination(name)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showPopupMenu(view: View, combo: GestureCombination) {
        val popup = PopupMenu(this, view)
        popup.menu.add(0, 1, 0, "編輯名稱")
        popup.menu.add(0, 2, 0, "複製")
        // TODO: Reorder could be added to RecyclerView touch helper later
        popup.menu.add(0, 3, 0, "刪除")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    val editText = EditText(this)
                    editText.setText(combo.name)
                    AlertDialog.Builder(this)
                        .setTitle("編輯名稱")
                        .setView(editText)
                        .setPositiveButton("儲存") { _, _ ->
                            val newName = editText.text.toString()
                            if (newName.isNotBlank()) {
                                viewModel.updateCombinationName(combo, newName)
                            }
                        }
                        .setNegativeButton("取消", null)
                        .show()
                    true
                }
                2 -> {
                    viewModel.duplicateCombination(combo)
                    true
                }
                3 -> {
                    viewModel.deleteCombination(combo)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun openOverlayForCombo(combo: GestureCombination) {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
            return
        }

        if (GestureAccessibilityService.instance == null) {
            checkAccessibilityPermission()
            return
        }

        // Start overlay service
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra("COMBINATION_ID", combo.id)
            putExtra("COMBINATION_NAME", combo.name)
        }
        startForegroundService(intent) // Uses specialUse foreground type
        finish() // Close main app to show overlay on home screen / other apps
    }

    private fun checkAccessibilityPermission() {
        if (GestureAccessibilityService.instance == null) {
            AlertDialog.Builder(this)
                .setTitle("需要無障礙權限")
                .setMessage("點擊確定前往設定開啟「GestureRecord」的無障礙服務，以便能在其他應用程式上執行點擊與手勢。")
                .setPositiveButton("確定") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }
}
