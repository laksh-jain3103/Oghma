package com.reader.oghma.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * The Settings screen. Open it with:
 *
 * requireActivity().getSupportFragmentManager().beginTransaction()
 *         .replace(R.id.fragmentContainer, new SettingsFragment())
 *         .addToBackStack(null).commit();
 *
 * The screen itself is SettingsScreen.kt.
 */
public class SettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        return new SettingsScreen(
                requireContext(),
                new SettingsScreen.Listener() {

                    @Override
                    public void onBack() {

                        requireActivity()
                                .getSupportFragmentManager()
                                .popBackStack();
                    }
                }
        );
    }
}
