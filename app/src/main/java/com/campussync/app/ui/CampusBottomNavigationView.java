package com.campussync.app.ui;

import android.content.Context;
import android.util.AttributeSet;

import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Bottom bar that accepts the six campus tabs.
 *
 * <p>{@link BottomNavigationView} caps its menu at five items and throws while inflating a sixth.
 * CampusSync has Home, Social, Chats, Events, Groups and Profile, so the cap is raised here; the
 * material library reads {@link #getMaxItemCount()} while building its menu, including from the
 * superclass constructor.
 */
public class CampusBottomNavigationView extends BottomNavigationView {

    private static final int MAX_ITEMS = 6;

    public CampusBottomNavigationView(Context context) {
        super(context);
    }

    public CampusBottomNavigationView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CampusBottomNavigationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public int getMaxItemCount() {
        return MAX_ITEMS;
    }
}
