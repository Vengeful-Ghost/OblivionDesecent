package com.example.obliviondescent;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Co-op lobby screen.
 * The host creates a party with a 6-letter join code; up to 3 more players join by code
 * or by accepting an invite. The lobby lives in Firestore (lobbies/{code}) and every
 * phone listens to it live, so joins, leaves and the start signal show up instantly.
 */
public class LobbyActivity extends AppCompatActivity {

    /** Extra: join this party code right away (used when accepting an invite). */
    public static final String EXTRA_JOIN_CODE = "join_code";

    private DBManager dbManager;
    private String userId;
    private String username;

    private String lobbyCode = null; // null while not in a party
    private boolean isHost = false;
    private ListenerRegistration lobbyListener;
    private Map<String, Object> currentPlayers = new HashMap<>();

    private View joinSection;
    private View partySection;
    private EditText inputCode;
    private TextView txtCode;
    private TextView txtPartyTitle;
    private TextView txtStatus;
    private LinearLayout playersContainer;
    private MaterialButton btnStart;
    private Dialog loadingDialog;

    /**
     * Sets up the screen and, when opened from an invite, joins that party immediately.
     * @param savedInstanceState previously saved instance state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby);
        hideSystemUI();

        // Keep buttons clear of the camera cutout
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            v.setPadding(cutout.left, cutout.top, cutout.right, cutout.bottom);
            return insets;
        });

        dbManager = new DBManager();
        SharedPreferences prefs = getSharedPreferences("loginPrefs", MODE_PRIVATE);
        userId   = prefs.getString("user_id", null);
        username = prefs.getString("username", "Player");

        joinSection      = findViewById(R.id.join_section);
        partySection     = findViewById(R.id.party_section);
        inputCode        = findViewById(R.id.input_code);
        txtCode          = findViewById(R.id.txt_code);
        txtPartyTitle    = findViewById(R.id.txt_party_title);
        txtStatus        = findViewById(R.id.txt_lobby_status);
        playersContainer = findViewById(R.id.players_container);
        btnStart         = findViewById(R.id.btn_start);

        findViewById(R.id.btn_host).setOnClickListener(v -> hostParty());
        findViewById(R.id.btn_join).setOnClickListener(v -> joinParty(inputCode.getText().toString()));
        findViewById(R.id.btn_invite).setOnClickListener(v -> showInviteDialog());
        findViewById(R.id.btn_leave).setOnClickListener(v -> leaveParty());
        findViewById(R.id.btn_back_lobby).setOnClickListener(v -> finish());
        txtCode.setOnClickListener(v -> copyCode());
        btnStart.setOnClickListener(v -> startRun());

        showPartySection(false);

        String inviteCode = getIntent().getStringExtra(EXTRA_JOIN_CODE);
        if (inviteCode != null) joinParty(inviteCode);
    }

    @Override
    protected void onStart() {
        super.onStart();
        SoundManager.registerMenuMusic(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        SoundManager.unregisterMenuMusic();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
    }

    /**
     * Leaving the screen leaves the party: a guest frees their slot, a host closes the lobby.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (lobbyCode != null) leaveParty();
    }

    /* ============================================================
       HOST / JOIN / LEAVE
       ============================================================ */

    private void hostParty() {
        if (userId == null) return;
        showLoading("Creating party...");
        dbManager.createLobby(userId, username, code -> {
            hideLoading();
            if (isDestroyed()) {
                if (code != null) dbManager.closeLobby(code);
                return;
            }
            if (code == null) {
                toast("Couldn't create a party. Check your connection.");
                return;
            }
            enterParty(code, true);
        });
    }

    private void joinParty(String rawCode) {
        if (userId == null) return;
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        if (code.length() != DBManager.LOBBY_CODE_LENGTH) {
            inputCode.setError("Party codes are " + DBManager.LOBBY_CODE_LENGTH + " characters");
            return;
        }
        showLoading("Joining party...");
        dbManager.joinLobby(code, userId, username, error -> {
            hideLoading();
            if (isDestroyed()) {
                if (error == null) dbManager.leaveLobby(code, userId);
                return;
            }
            if (error != null) {
                toast(error);
                return;
            }
            enterParty(code, false);
        });
    }

    private void enterParty(String code, boolean host) {
        lobbyCode = code;
        isHost = host;
        txtCode.setText(code);
        btnStart.setVisibility(host ? View.VISIBLE : View.GONE);
        btnStart.setEnabled(true);
        showPartySection(true);
        lobbyListener = dbManager.listenToLobby(code, this::onLobbyChanged);
    }

    private void leaveParty() {
        if (lobbyCode == null) return;
        if (isHost) dbManager.closeLobby(lobbyCode);
        else        dbManager.leaveLobby(lobbyCode, userId);
        resetToJoin();
    }

    private void resetToJoin() {
        if (lobbyListener != null) {
            lobbyListener.remove();
            lobbyListener = null;
        }
        lobbyCode = null;
        isHost = false;
        currentPlayers = new HashMap<>();
        showPartySection(false);
    }

    private void showPartySection(boolean inParty) {
        joinSection.setVisibility(inParty ? View.GONE : View.VISIBLE);
        partySection.setVisibility(inParty ? View.VISIBLE : View.GONE);
        if (!inParty) {
            btnStart.setVisibility(View.GONE);
            txtPartyTitle.setText("Party");
            txtStatus.setText("Host a party or join one to begin");
            playersContainer.removeAllViews();
        }
    }

    /* ============================================================
       LIVE LOBBY UPDATES
       ============================================================ */

    @SuppressWarnings("unchecked")
    private void onLobbyChanged(Map<String, Object> lobby) {
        if (lobbyCode == null) return;
        if (lobby == null) {
            if (!isHost) toast("The host closed the party");
            resetToJoin();
            return;
        }

        Map<String, Object> players = (Map<String, Object>) lobby.get("players");
        if (players == null) players = new HashMap<>();
        if (!players.containsKey(userId)) {
            toast("You are no longer in this party");
            resetToJoin();
            return;
        }
        currentPlayers = players;
        renderPlayers(players, (String) lobby.get("hostId"));

        if ("started".equals(lobby.get("status"))) {
            Number seed = (Number) lobby.get("seed");
            onRunStarted(seed != null ? seed.longValue() : 0L);
        }
    }

    @SuppressWarnings("unchecked")
    private void renderPlayers(Map<String, Object> players, String hostId) {
        String[] names = new String[DBManager.MAX_PARTY_SIZE];
        String[] tags  = new String[DBManager.MAX_PARTY_SIZE];
        for (Map.Entry<String, Object> entry : players.entrySet()) {
            Map<String, Object> player = (Map<String, Object>) entry.getValue();
            Object slotValue = player.get("slot");
            int slot = slotValue != null ? ((Number) slotValue).intValue() : -1;
            if (slot < 0 || slot >= names.length) continue;
            String id = entry.getKey();
            names[slot] = (String) player.get("name");
            if (id.equals(hostId))      tags[slot] = id.equals(userId) ? "HOST • YOU" : "HOST";
            else if (id.equals(userId)) tags[slot] = "YOU";
            else                        tags[slot] = "";
        }

        playersContainer.removeAllViews();
        for (int i = 0; i < names.length; i++) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_stat_row, playersContainer, false);
            TextView label = row.findViewById(R.id.stat_label);
            TextView value = row.findViewById(R.id.stat_value);
            if (names[i] != null) {
                label.setText((i + 1) + ".  " + names[i]);
                label.setTextColor(getColor(R.color.bone_white));
                value.setText(tags[i]);
            } else {
                label.setText((i + 1) + ".  Empty slot");
                value.setText("");
            }
            playersContainer.addView(row);
        }

        txtPartyTitle.setText("Party  " + players.size() + "/" + DBManager.MAX_PARTY_SIZE);
        txtStatus.setText(isHost ? "Start whenever everyone is in" : "Waiting for the host to start...");
    }

    /* ============================================================
       START
       ============================================================ */

    private void startRun() {
        if (!isHost || lobbyCode == null) return;
        btnStart.setEnabled(false);
        String code = lobbyCode;
        long seed = new Random().nextLong();
        // The run must exist before anyone is sent into it
        CoopSession.createRun(code, userId, success -> {
            if (success) {
                dbManager.startLobby(code, seed);
            } else {
                btnStart.setEnabled(true);
                toast("Couldn't start the run. Check your connection.");
            }
        });
    }

    /**
     * Every phone in the party receives the start signal and the shared seed here, then enters the run.
     * The lobby stays "started" so nobody new can join; the host deletes it when the run ends.
     */
    @SuppressWarnings("unchecked")
    private void onRunStarted(long seed) {
        Object me = currentPlayers.get(userId);
        Object slot = me instanceof Map ? ((Map<String, Object>) me).get("slot") : null;

        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(CoopSession.EXTRA_CODE, lobbyCode);
        intent.putExtra(CoopSession.EXTRA_SEED, seed);
        intent.putExtra(CoopSession.EXTRA_HOST, isHost);
        intent.putExtra(CoopSession.EXTRA_SLOT, slot != null ? ((Number) slot).intValue() : 0);

        resetToJoin(); // clears lobbyCode, so onDestroy doesn't leave or close the lobby
        SoundManager.stopMenuMusic();
        startActivity(intent);
        finish();
    }

    /* ============================================================
       INVITES
       ============================================================ */

    /** Lists the players you follow who aren't in the party yet, each with an Invite action. */
    private void showInviteDialog() {
        if (lobbyCode == null || userId == null) return;

        final Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setContentView(R.layout.dialog_invite_friends);
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        LinearLayout list = dialog.findViewById(R.id.invite_list);
        TextView empty = dialog.findViewById(R.id.invite_empty);
        dialog.findViewById(R.id.btn_close_invite).setOnClickListener(v -> dialog.dismiss());

        dbManager.loadFollowing(userId, ids -> dbManager.loadPlayersByIds(ids, friends ->
                runOnUiThread(() -> {
                    list.removeAllViews();
                    for (LeaderboardItem friend : friends) {
                        if (!currentPlayers.containsKey(friend.userId)) addInviteRow(list, friend);
                    }
                    if (list.getChildCount() == 0) {
                        empty.setText("No friends to invite.\nFollow players from the Stats leaderboard.");
                        empty.setVisibility(View.VISIBLE);
                    } else {
                        empty.setVisibility(View.GONE);
                    }
                })));

        dialog.show();
        hideSystemUIOnWindow(dialog.getWindow());
    }

    private void addInviteRow(LinearLayout list, LeaderboardItem friend) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_stat_row, list, false);
        TextView name   = row.findViewById(R.id.stat_label);
        TextView action = row.findViewById(R.id.stat_value);
        name.setText(friend.username);
        action.setText("INVITE");
        action.setOnClickListener(v -> {
            String code = lobbyCode;
            if (code == null) return;
            action.setOnClickListener(null);
            action.setText("...");
            dbManager.sendInvite(userId, username, friend.userId, code, success ->
                    runOnUiThread(() -> action.setText(success ? "SENT" : "FAILED")));
        });
        list.addView(row);
    }

    private void copyCode() {
        if (lobbyCode == null) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Party code", lobbyCode));
        toast("Code copied");
    }

    /* ============================================================
       UI UTILITIES
       ============================================================ */

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
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
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }

    private void hideSystemUIOnWindow(android.view.Window window) {
        if (window == null) return;
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
}
