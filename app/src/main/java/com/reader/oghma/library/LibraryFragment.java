package com.reader.oghma.library;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.reader.oghma.LoginActivity;
import com.reader.oghma.R;


public class LibraryFragment extends Fragment {


    // =========================================================
    // Navigation
    // =========================================================

    private ViewPager2 libraryPager;

    private LibraryPagerAdapter libraryPagerAdapter;

    private BottomNavigationView bottomNavigation;


    // =========================================================
    // Header
    // =========================================================

    private ImageButton searchButton;

    private ImageButton sortButton;

    private ImageButton settingsButton;


    // =========================================================
    // Add book
    // =========================================================

    private FloatingActionButton addBookButton;


    // =========================================================
    // Settings
    // =========================================================

    private View settingsPanel;

    private ImageButton settingsBackButton;

    private TextView settingsEmail;

    private TextView logoutButton;


    // =========================================================
    // Constructor
    // =========================================================

    public LibraryFragment() {
        // Required empty constructor
    }


    // =========================================================
    // Create layout
    // =========================================================

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        return inflater.inflate(
                R.layout.fragment_library,
                container,
                false
        );
    }


    // =========================================================
    // View created
    // =========================================================

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {

        super.onViewCreated(
                view,
                savedInstanceState
        );


        // -----------------------------------------------------
        // Header
        // -----------------------------------------------------

        searchButton =
                view.findViewById(
                        R.id.searchButton
                );


        sortButton =
                view.findViewById(
                        R.id.sortButton
                );


        settingsButton =
                view.findViewById(
                        R.id.settingsButton
                );


        // -----------------------------------------------------
        // Add book
        // -----------------------------------------------------

        addBookButton =
                view.findViewById(
                        R.id.addBookButton
                );


        // -----------------------------------------------------
        // Settings
        // -----------------------------------------------------

        settingsPanel =
                view.findViewById(
                        R.id.settingsPanel
                );


        settingsBackButton =
                view.findViewById(
                        R.id.settingsBackButton
                );


        settingsEmail =
                view.findViewById(
                        R.id.settingsEmail
                );


        logoutButton =
                view.findViewById(
                        R.id.logoutButton
                );


        // -----------------------------------------------------
        // Bottom navigation
        // -----------------------------------------------------

        bottomNavigation =
                view.findViewById(
                        R.id.bottomNavigation
                );


        // -----------------------------------------------------
        // ViewPager
        // -----------------------------------------------------

        libraryPager =
                view.findViewById(
                        R.id.libraryPager
                );


        libraryPagerAdapter =
                new LibraryPagerAdapter(
                        requireActivity()
                );


        libraryPager.setAdapter(
                libraryPagerAdapter
        );


        // -----------------------------------------------------
        // Buttons
        // -----------------------------------------------------

        setupButtons();


        // -----------------------------------------------------
        // Start on Library
        // -----------------------------------------------------

        libraryPager.setCurrentItem(
                0,
                false
        );


        bottomNavigation.setSelectedItemId(
                R.id.nav_library
        );
    }


    // =========================================================
    // Buttons
    // =========================================================

    private void setupButtons() {


        // -----------------------------------------------------
        // Search
        // -----------------------------------------------------

        searchButton.setOnClickListener(
                v -> {

                    libraryPager.setCurrentItem(
                            0,
                            true
                    );


                    LibraryPageFragment page =
                            getLibraryPage();


                    if (page != null) {

                        page.showSearchDialog();
                    }
                }
        );


        // -----------------------------------------------------
        // Sort
        // -----------------------------------------------------

        sortButton.setOnClickListener(
                v -> {

                    libraryPager.setCurrentItem(
                            0,
                            true
                    );


                    LibraryPageFragment page =
                            getLibraryPage();


                    if (page != null) {

                        page.showSortDialog();
                    }
                }
        );


        // -----------------------------------------------------
        // Add book
        // -----------------------------------------------------

        addBookButton.setOnClickListener(
                v -> {

                    libraryPager.setCurrentItem(
                            0,
                            true
                    );


                    LibraryPageFragment page =
                            getLibraryPage();


                    if (page != null) {

                        page.startImport();
                    }
                }
        );


        // -----------------------------------------------------
        // Settings
        // -----------------------------------------------------

        settingsButton.setOnClickListener(
                v -> showSettings()
        );


        settingsBackButton.setOnClickListener(
                v -> hideSettings()
        );


        logoutButton.setOnClickListener(
                v -> confirmLogout()
        );


        // -----------------------------------------------------
        // Bottom navigation
        // -----------------------------------------------------

        bottomNavigation.setOnItemSelectedListener(
                item -> {

                    int itemId =
                            item.getItemId();


                    if (
                            itemId
                                    == R.id.nav_library
                    ) {

                        libraryPager.setCurrentItem(
                                0,
                                true
                        );

                        return true;
                    }


                    if (
                            itemId
                                    == R.id.nav_bookmarks
                    ) {

                        libraryPager.setCurrentItem(
                                1,
                                true
                        );

                        return true;
                    }


                    if (
                            itemId
                                    == R.id.nav_find_more
                    ) {

                        libraryPager.setCurrentItem(
                                2,
                                true
                        );

                        return true;
                    }


                    return false;
                }
        );


        // -----------------------------------------------------
        // Swipe
        // -----------------------------------------------------

        libraryPager.registerOnPageChangeCallback(
                new ViewPager2.OnPageChangeCallback() {

                    @Override
                    public void onPageSelected(
                            int position
                    ) {

                        super.onPageSelected(
                                position
                        );


                        if (position == 0) {

                            bottomNavigation
                                    .setSelectedItemId(
                                            R.id.nav_library
                                    );

                        } else if (position == 1) {

                            bottomNavigation
                                    .setSelectedItemId(
                                            R.id.nav_bookmarks
                                    );

                        } else if (position == 2) {

                            bottomNavigation
                                    .setSelectedItemId(
                                            R.id.nav_find_more
                                    );
                        }
                    }
                }
        );
    }


    // =========================================================
    // Get Library page
    // =========================================================

    @Nullable
    private LibraryPageFragment getLibraryPage() {

        if (libraryPager == null) {
            return null;
        }


        String tag =
                "f"
                        + R.id.libraryPager
                        + ":"
                        + libraryPager.getCurrentItem();


        Fragment fragment =
                getChildFragmentManager()
                        .findFragmentByTag(
                                tag
                        );


        if (
                fragment
                        instanceof LibraryPageFragment
        ) {

            return (LibraryPageFragment) fragment;
        }


        return null;
    }


    // =========================================================
    // Settings
    // =========================================================

    private void showSettings() {

        settingsPanel.setVisibility(
                View.VISIBLE
        );


        FirebaseUser user =
                FirebaseAuth
                        .getInstance()
                        .getCurrentUser();


        if (user != null) {

            String email =
                    user.getEmail();


            if (
                    email != null
                            && !email.isEmpty()
            ) {

                settingsEmail.setText(
                        email
                );

            } else {

                settingsEmail.setText(
                        "Signed in"
                );
            }

        } else {

            settingsEmail.setText(
                    "Not signed in"
            );
        }
    }


    private void hideSettings() {

        settingsPanel.setVisibility(
                View.GONE
        );
    }


    // =========================================================
    // Logout
    // =========================================================

    private void confirmLogout() {

        new AlertDialog.Builder(
                requireContext()
        )
                .setTitle(
                        "Log out?"
                )
                .setMessage(
                        "Are you sure you want to log out of Oghma?"
                )
                .setNegativeButton(
                        "Cancel",
                        null
                )
                .setPositiveButton(
                        "Log out",
                        (dialog, which) ->
                                logout()
                )
                .show();
    }


    private void logout() {

        FirebaseAuth
                .getInstance()
                .signOut();


        Intent intent =
                new Intent(
                        requireContext(),
                        LoginActivity.class
                );


        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );


        startActivity(
                intent
        );


        requireActivity().finish();
    }
}