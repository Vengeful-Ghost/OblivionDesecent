package com.example.obliviondescent;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Map;

public class LoadGameActivity extends AppCompatActivity {

    private DBManager dbManager;
    private String userId;
    private Dialog loadingDialog;

    // Slot views
    private TextView[] slotInfoViews = new TextView[3];
    private TextView[] slotInfoViews2 = new TextView[3];
    private MaterialButton[] loadBtns  = new MaterialButton[3];
    private MaterialButton[] deleteBtns = new MaterialButton[3];
    private MaterialButton[] newBtns   = new MaterialButton[3];

    // Loaded game data mapped to slots 1-3
    private Map<String, Object>[] slotData = new Map[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_load_game);
        hideSystemUI();

        dbManager = new DBManager();
        SharedPreferences prefs = getSharedPreferences("loginPrefs", MODE_PRIVATE);
        userId = prefs.getString("user_id", null);

        // Bind slot views
        slotInfoViews[0] = findViewById(R.id.slot1_info);
        slotInfoViews[1] = findViewById(R.id.slot2_info);
        slotInfoViews[2] = findViewById(R.id.slot3_info);
        slotInfoViews2[0] = findViewById(R.id.slot1_info2);
        slotInfoViews2[1] = findViewById(R.id.slot2_info2);
        slotInfoViews2[2] = findViewById(R.id.slot3_info2);
        loadBtns[0]  = findViewById(R.id.slot1_btn_load);
        loadBtns[1]  = findViewById(R.id.slot2_btn_load);
        loadBtns[2]  = findViewById(R.id.slot3_btn_load);
        deleteBtns[0] = findViewById(R.id.slot1_btn_delete);
        deleteBtns[1] = findViewById(R.id.slot2_btn_delete);
        deleteBtns[2] = findViewById(R.id.slot3_btn_delete);
        newBtns[0]   = findViewById(R.id.slot1_btn_new);
        newBtns[1]   = findViewById(R.id.slot2_btn_new);
        newBtns[2]   = findViewById(R.id.slot3_btn_new);

        findViewById(R.id.btn_back).setOnClickListener(v -> {
            Intent intent = new Intent(LoadGameActivity.this, MenuActivity.class);
            startActivity(intent);
            finish();
        });

        loadSlots();
    }

    private void loadSlots() {
        if (userId == null) return;
        showLoading("Loading saves...");

        dbManager.loadPlayerGames(userId, slots -> {
            // Map each game to its slotIndex (1-based → 0-based array)
            for (Map<String, Object> game : slots) {
                int idx = ((Number) game.get("slotIndex")).intValue() - 1;
                if (idx >= 0 && idx < 3) slotData[idx] = game;
            }

            runOnUiThread(() -> {
                hideLoading();
                for (int i = 0; i < 3; i++) {
                    bindSlot(i);
                }
            });
        });
    }

    @SuppressWarnings("unchecked")
    private void bindSlot(int i) {
        Map<String, Object> data = slotData[i];
        final int slotIndex = i + 1;

        if (data == null) {
            slotInfoViews[i].setText("Empty");
            slotInfoViews[i].setTextColor(0xFF888888);
            slotInfoViews2[i].setVisibility(View.GONE);
            loadBtns[i].setVisibility(View.GONE);
            deleteBtns[i].setVisibility(View.GONE);
            newBtns[i].setVisibility(View.VISIBLE);
            newBtns[i].setOnClickListener(v -> startNewGame(slotIndex));
        } else {
            int floor  = data.get("currentFloor")      != null ? ((Number) data.get("currentFloor")).intValue()  : 1;
            int hp     = data.get("health")             != null ? ((Number) data.get("health")).intValue()        : 100;
            int armor  = data.get("armorValue")         != null ? ((Number) data.get("armorValue")).intValue()    : 100;
            String weapon = data.get("equippedWeaponName") != null ? (String) data.get("equippedWeaponName")     : "Fists";

            slotInfoViews[i].setText("Floor " + floor);
            slotInfoViews[i].setTextColor(0xFFCCCCCC);

            slotInfoViews2[i].setText("HP: " + hp + "  |  Armor: " + armor + "  |  " + weapon);
            slotInfoViews2[i].setVisibility(View.VISIBLE);

            loadBtns[i].setVisibility(View.VISIBLE);
            deleteBtns[i].setVisibility(View.VISIBLE);
            newBtns[i].setVisibility(View.GONE);

            String gameId = (String) data.get("gameId");
            loadBtns[i].setOnClickListener(v -> loadGame(gameId));
            deleteBtns[i].setOnClickListener(v -> deleteGame(gameId, i));
        }
    }

    private void startNewGame(int slotIndex) {
        // Check slot limit — only allow if fewer than 3 games exist
        int occupied = 0;
        for (Map<String, Object> d : slotData) if (d != null) occupied++;
        if (occupied >= 3) {
            Toast.makeText(this, "Delete a slot to start a new game", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra("slot_index", slotIndex);
        // No game_id → GameActivity creates a new save
        startActivity(intent);
    }

    private void loadGame(String gameId) {
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra("game_id", gameId);
        startActivity(intent);
    }

    private void deleteGame(String gameId, int slotIndex) {
        showLoading("Deleting...");
        dbManager.deleteGame(userId, gameId, success -> {
            runOnUiThread(() -> {
                hideLoading();
                if (success) {
                    slotData[slotIndex] = null;
                    bindSlot(slotIndex);
                } else {
                    Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void showLoading(String message) {
        if (loadingDialog != null && loadingDialog.isShowing()) return;
        View view = getLayoutInflater().inflate(R.layout.dialog_loading, null);
        ((TextView) view.findViewById(R.id.txt_loading_message)).setText(message);
        loadingDialog = new Dialog(this, R.style.LoadingDialogStyle);
        loadingDialog.setContentView(view);
        loadingDialog.setCancelable(false);
        loadingDialog.show();
    }

    private void hideLoading() {
        if (loadingDialog != null && loadingDialog.isShowing()) loadingDialog.dismiss();
        loadingDialog = null;
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }
}