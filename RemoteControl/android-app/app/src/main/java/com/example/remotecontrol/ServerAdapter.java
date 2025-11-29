package com.example.remotecontrol;

import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ServerAdapter extends RecyclerView.Adapter<ServerAdapter.VH> {

    public interface OnComputerClick {
        void onClick(Computer computer);
    }

    private final List<Computer> items;
    private final OnComputerClick listener;

    public ServerAdapter(List<Computer> items, OnComputerClick listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.server_item, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Computer c = items.get(pos);
        h.ip.setText(c.getDisplayAddress());
        h.itemView.setOnClickListener(v -> listener.onClick(c));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, ip;
        ImageView dot;
        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.tv_server_name);
            ip   = v.findViewById(R.id.tv_server_ip);
            dot  = v.findViewById(R.id.iv_connection_status);
        }
    }
}
