package com.thecaptaincook.zenith;

public class ActionOption {
    public static final int TYPE_HEADER = 0;
    public static final int TYPE_ITEM = 1;

    public int type;
    public String name;
    public String description;
    public int iconResId;
    public String category; // used for sorting/grouping if needed

    // Constructor for Header
    public ActionOption(String category) {
        this.type = TYPE_HEADER;
        this.category = category;
        this.name = category; // Header uses name as category title
    }

    // Constructor for Item
    public ActionOption(String name, String description, int iconResId, String category) {
        this.type = TYPE_ITEM;
        this.name = name;
        this.description = description;
        this.iconResId = iconResId;
        this.category = category;
    }
}
