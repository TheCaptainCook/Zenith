package com.thecaptaincook.zenith;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ActionPickerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<ActionOption> originalItems;
    private List<ActionOption> items;
    private OnActionSelectedListener listener;

    public interface OnActionSelectedListener {
        void onActionSelected(ActionOption action);
    }

    public ActionPickerAdapter(List<ActionOption> items, OnActionSelectedListener listener) {
        this.originalItems = new java.util.ArrayList<>(items);
        this.items = new java.util.ArrayList<>(items);
        this.listener = listener;
    }

    public void filter(String query) {
        query = query.toLowerCase().trim();
        items.clear();
        if (query.isEmpty()) {
            items.addAll(originalItems);
        } else {
            for (ActionOption option : originalItems) {
                if (option.type == ActionOption.TYPE_HEADER) {
                    continue;
                }
                if (option.name.toLowerCase().contains(query) || (option.description != null && option.description.toLowerCase().contains(query))) {
                    items.add(option);
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == ActionOption.TYPE_HEADER) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_trigger_category, parent, false);
            return new HeaderViewHolder(v);
        } else {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_trigger_option, parent, false);
            return new ItemViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ActionOption item = items.get(position);
        if (getItemViewType(position) == ActionOption.TYPE_HEADER) {
            ((HeaderViewHolder) holder).textCategoryName.setText(item.name);
        } else {
            ItemViewHolder itemHolder = (ItemViewHolder) holder;
            itemHolder.textName.setText(item.name);
            itemHolder.textDesc.setText(item.description);
            if (item.iconResId != 0) {
                itemHolder.icon.setImageResource(item.iconResId);
                itemHolder.icon.setVisibility(View.VISIBLE);
            } else {
                itemHolder.icon.setVisibility(View.GONE);
            }
            
            itemHolder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onActionSelected(item);
            });
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView textCategoryName;
        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            textCategoryName = itemView.findViewById(R.id.text_category_name);
        }
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView textName;
        TextView textDesc;
        ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.icon_trigger);
            textName = itemView.findViewById(R.id.text_trigger_name);
            textDesc = itemView.findViewById(R.id.text_trigger_desc);
        }
    }
}
