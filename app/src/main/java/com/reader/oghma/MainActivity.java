package com.reader.oghma;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.reader.oghma.library.LibraryFragment;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {

            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(
                            R.id.fragmentContainer,
                            new LibraryFragment()
                    )
                    .commit();
        }
    }
}