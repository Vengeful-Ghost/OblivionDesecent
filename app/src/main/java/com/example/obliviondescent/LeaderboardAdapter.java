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
 * Adapter for the leaderboard RecyclerView.
 * Displays rows with rank, username, highest floor and statistics.
 * The top three ranks are highlighted with special colours (gold/silver/bronze).
 */
public class LeaderboardAdapter
        extends RecyclerView.Adapter<LeaderboardAdapter.ViewHolder> {

    /**
     * Listener interface for taps on a leaderboard item.
     */
    public interface OnItemClickListener {
        void onItemClick(LeaderboardItem item);
    }

    private final List<LeaderboardItem> items;
    private final OnItemClickListener   listener;

    /**
     * Creates a new adapter for the leaderboard list.
     * @param items list of leaderboard items
     * @param listener click listener for items
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
            // Rank — top 3 get medal colours
            rankView.setText("#" + item.rank);
            android.content.Context ctx = itemView.getContext();
            int medal;
            switch (item.rank) {
                case 1:  medal = ctx.getColor(R.color.gold_bright); break;
                case 2:  medal = ctx.getColor(R.color.medal_silver); break;
                case 3:  medal = ctx.getColor(R.color.medal_bronze); break;
                default: medal = ctx.getColor(R.color.bone_muted); break;
            }
            rankView.setTextColor(medal);

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
                bg.setColor(ctx.getColor(R.color.bg_surface));
                bg.setCornerRadius(10f);
                bg.setStroke(2, medal);
                itemView.setBackground(bg);
            } else {
                itemView.setBackgroundResource(R.drawable.dialog_bg);
            }

            itemView.setOnClickListener(v -> listener.onItemClick(item));
        }
    }
}