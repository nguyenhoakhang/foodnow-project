package com.example.a524h0098_nguyenhoakhang_foodnow_project;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CartStore {
    private final SharedPreferences preferences;
    private final String key;

    public CartStore(Context context, String uid) {
        preferences = context.getSharedPreferences("foodnow_carts", Context.MODE_PRIVATE);
        key = "cart_" + uid;
    }

    public JSONObject read() {
        try {
            return new JSONObject(preferences.getString(key, "{}"));
        } catch (JSONException error) {
            return new JSONObject();
        }
    }

    public void clear() {
        preferences.edit().remove(key).apply();
    }

    public void add(String restaurantId, String foodId, String name, long price) throws JSONException {
        JSONObject cart = read();
        String currentId = cart.optString("restaurantId", "");
        if (!currentId.isEmpty() && !currentId.equals(restaurantId))
            throw new IllegalStateException("Different restaurant");
        JSONArray items = cart.optJSONArray("items");
        if (items == null)
            items = new JSONArray();
        JSONObject selected = null;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (foodId.equals(item.optString("id"))) {
                selected = item;
                break;
            }
        }
        if (selected == null) {
            if (items.length() >= 4)
                throw new IllegalStateException("Distinct item limit");
            selected = new JSONObject().put("id", foodId).put("quantity", 0);
            items.put(selected);
        }
        int quantity = selected.optInt("quantity", 0);
        if (quantity >= 99)
            throw new IllegalStateException("Quantity limit");
        selected.put("name", name).put("price", price).put("quantity", quantity + 1);
        cart.put("restaurantId", restaurantId).put("items", items);
        preferences.edit().putString(key, cart.toString()).apply();
    }

    public void removeOne(String foodId) throws JSONException {
        JSONObject cart = read();
        JSONArray items = cart.optJSONArray("items");
        if (items == null)
            return;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            if (!foodId.equals(item.optString("id")))
                continue;
            int quantity = item.optInt("quantity", 0);
            if (quantity <= 1)
                items.remove(i);
            else
                item.put("quantity", quantity - 1);
            if (items.length() == 0)
                clear();
            else
                preferences.edit().putString(key, cart.toString()).apply();
            return;
        }
    }
}
