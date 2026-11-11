package com.example.a524h0098_nguyenhoakhang_foodnow_project;

public final class CatalogImages {
    private CatalogImages() {
    }

    public static int drawable(String key) {
        if (key == null)
            return 0;
        switch (key) {
            case "cover_menu_1":
                return R.drawable.cover_menu_1;
            case "ic_banh_mi":
                return R.drawable.ic_banh_mi;
            case "ic_black_coffee":
                return R.drawable.ic_black_coffee;
            case "ic_milk_tea":
                return R.drawable.ic_milk_tea;
            case "ic_circlek":
                return R.drawable.ic_circlek;
            case "ic_seveneleven":
                return R.drawable.ic_seveneleven;
            case "ic_ministop":
                return R.drawable.ic_ministop;
            case "ic_highland":
                return R.drawable.ic_highland;
            case "ic_vinmart":
                return R.drawable.ic_vinmart;
            default:
                return 0;
        }
    }
}
