package com.example.obliviondescent;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Adapter עבור RecyclerView של לוח המובילים.
 * מציגה שורות עם דירוג, שם משתמש, קומה גבוהה ביותר וסטטיסטיקות.
 * שלושת הדירוגים הראשונים מוצגים עם צבעים מיוחדים (זהב/כסף/ארד).
 */
public class LeaderboardAdapter
        extends RecyclerView.Adapter<LeaderboardAdapter.ViewHolder> {

    /**
     * ממשק מאזין ללחיצה על פריט בלוח המובילים.
     */
    public interface OnItemClickListener {
        void onItemClick(LeaderboardItem item);
    }

    private final List<LeaderboardItem> items;
    private final OnItemClickListener   listener;

    /**
     * יוצר Adapter חדש לרשימת לוח המובילים.
     * @param items רשימת פריטי לוח המובילים
     * @param listener מאזין לחיצה על פריט
     */
    public LeaderboardAdapter(List<LeaderboardItem> items,
                              OnItemClickListener listener) {
        this.items    = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_leaderboard, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LeaderboardItem item = items.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() { return items.size(); }

    /* ============================================================
       VIEW HOLDER
       ============================================================ */
    static class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView rankView;
        private final TextView usernameView;
        private final TextView subtitleView;
        private final TextView floorView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            rankView     = itemView.findViewById(R.id.lb_rank);
            usernameView = itemView.findViewById(R.id.lb_username);
            subtitleView = itemView.findViewById(R.id.lb_subtitle);
            floorView    = itemView.findViewById(R.id.lb_highest_floor);
        }

        void bind(LeaderboardItem item, OnItemClickListener listener) {
            // Rank — top 3 get special colors
            rankView.setText("#" + item.rank);
            switch (item.rank) {
                case 1: rankView.setTextColor(Color.parseColor("#FFD700")); break;
                case 2: rankView.setTextColor(Color.parseColor("#CCCCCC")); break;
                case 3: rankView.setTextColor(Color.parseColor("#CD7F32")); break;
                default: rankView.setTextColor(Color.parseColor("#555555")); break;
            }

            usernameView.setText(item.username);
            subtitleView.setText(
                    item.enemiesKilled + " kills  •  " +
                            item.bossesKilled  + " bosses  •  " +
                            StatsManager.secondsToHours(item.playtimeSeconds) + "h");

            floorView.setText(String.valueOf(item.highestFloor));

            // Highlight top 3 rows
            if (item.rank <= 3) {
                android.graphics.drawable.GradientDrawable bg =
                        new android.graphics.drawable.GradientDrawable();
                int strokeColor;
                switch (item.rank) {
                    case 1: strokeColor = Color.parseColor("#FFD700"); break;
                    case 2: strokeColor = Color.parseColor("#CCCCCC"); break;
                    default: strokeColor = Color.parseColor("#CD7F32"); break;
                }
                bg.setColor(Color.parseColor("#1A1A1A"));
                bg.setCornerRadius(10f);
                bg.setStroke(2, strokeColor);
                itemView.setBackground(bg);
            } else {
                itemView.setBackgroundResource(R.drawable.dialog_bg);
            }

            itemView.setOnClickListener(v -> listener.onItemClick(item));
        }
    }
}