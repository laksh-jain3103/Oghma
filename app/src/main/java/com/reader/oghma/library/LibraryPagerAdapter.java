package com.reader.oghma.library;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class LibraryPagerAdapter extends FragmentStateAdapter {

    public LibraryPagerAdapter(
            @NonNull FragmentActivity fragmentActivity
    ) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {

        switch (position) {

            case 0:
                return new LibraryPageFragment();

            case 1:
                return new BookmarksPageFragment();

            case 2:
                return new FindMorePageFragment();

            default:
                throw new IllegalArgumentException(
                        "Invalid page position: " + position
                );
        }
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}