package com.example.a524h0098_nguyenhoakhang_foodnow_project;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.a524h0098_nguyenhoakhang_foodnow_project.databinding.ItemFoodBinding;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FoodAdapter extends RecyclerView.Adapter<FoodAdapter.Holder> {
    public interface OnAddListener {
        void onAdd(DocumentSnapshot food);
    }

    private final List<DocumentSnapshot> foods = new ArrayList<>();
    private final OnAddListener listener;
    private boolean orderingAvailable;

    public FoodAdapter(OnAddListener listener) {
        this.listener = listener;
    }

    public void setOrderingAvailable(boolean available) {
        orderingAvailable = available;
        notifyDataSetChanged();
    }

    public void setFoods(List<DocumentSnapshot> items) {
        foods.clear();
        foods.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemFoodBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        DocumentSnapshot food = foods.get(position);
        int image = CatalogImages.drawable(food.getString("image"));
        holder.binding.image.setVisibility(image == 0 ? View.GONE : View.VISIBLE);
        if (image != 0)
            holder.binding.image.setImageResource(image);
        holder.binding.name.setText(food.getString("name"));
        Long price = food.getLong("price");
        holder.binding.price
                .setText(price == null ? "" : NumberFormat.getCurrencyInstance(new Locale("vi", "VN")).format(price));
        holder.binding.unavailable
                .setVisibility(Boolean.FALSE.equals(food.getBoolean("available")) ? View.VISIBLE : View.GONE);
        holder.binding.add.setEnabled(
                orderingAvailable && Boolean.TRUE.equals(food.getBoolean("available")) && price != null && price > 0);
        holder.binding.add.setOnClickListener(view -> listener.onAdd(food));
    }

    @Override
    public int getItemCount() {
        return foods.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemFoodBinding binding;

        Holder(ItemFoodBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
