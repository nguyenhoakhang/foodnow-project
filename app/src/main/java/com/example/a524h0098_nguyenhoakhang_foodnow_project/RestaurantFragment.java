package com.example.a524h0098_nguyenhoakhang_foodnow_project;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.DocumentSnapshot;
import android.text.Editable;
import android.text.TextWatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RestaurantFragment extends Fragment {
    private ListenerRegistration listener;
    private final List<DocumentSnapshot> restaurants = new ArrayList<>();
    private LinearLayout list;
    private android.widget.EditText search;
    private TextView message;
    private boolean cached;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_restaurant, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        list = view.findViewById(R.id.storeList);
        search = view.findViewById(R.id.catalogSearch);
        message = view.findViewById(R.id.catalogMessage);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                render();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        listener = FirebaseFirestore.getInstance().collection("restaurants")
                .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE, (snapshot, error) -> {
                    if (!isAdded())
                        return;
                    if (error != null || snapshot == null) {
                        message.setText(R.string.catalog_error);
                        return;
                    }
                    cached = snapshot.getMetadata().isFromCache();
                    restaurants.clear();
                    restaurants.addAll(snapshot.getDocuments());
                    render();
                });
    }

    private void render() {
        if (!isAdded() || list == null)
            return;
        list.removeAllViews();
        String query = normalize(search.getText().toString().trim());
        for (DocumentSnapshot restaurant : restaurants) {
            String name = restaurant.getString("name");
            String address = restaurant.getString("address");
            if (!normalize(name).contains(query) && !normalize(address).contains(query))
                continue;
            View card = getLayoutInflater().inflate(R.layout.item_store, list, false);
            int image = CatalogImages.drawable(restaurant.getString("image"));
            int cover = CatalogImages.drawable(restaurant.getString("cover"));
            if (cover != 0)
                ((ImageView) card.findViewById(R.id.storeCover)).setImageResource(cover);
            ImageView logo = card.findViewById(R.id.storeLogo);
            if (image != 0)
                logo.setImageResource(image);
            else
                logo.setVisibility(View.GONE);
            ((TextView) card.findViewById(R.id.storeName)).setText(restaurant.getString("name"));
            ((TextView) card.findViewById(R.id.storeAddress)).setText(restaurant.getString("address"));
            ((TextView) card.findViewById(R.id.storeDescription)).setText(R.string.menu_title);
            card.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), RestaurantActivity.class);
                intent.putExtra(RestaurantActivity.EXTRA_ID, restaurant.getId());
                startActivity(intent);
            });
            list.addView(card);
        }
        boolean empty = list.getChildCount() == 0;
        message.setText(empty ? getString(cached ? R.string.catalog_cached_empty : R.string.catalog_empty)
                : cached ? getString(R.string.catalog_cached) : "");
    }

    @Override
    public void onDestroyView() {
        if (listener != null)
            listener.remove();
        listener = null;
        list = null;
        super.onDestroyView();
    }

    private String normalize(String value) {
        if (value == null)
            return "";
        return java.text.Normalizer.normalize(value.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('\u0111', 'd');
    }
}
