package com.reader.oghma;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

public class SignUpActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private EditText signupEmail, signupPassword;
    private Button signupButton;
    private TextView loginRedirectText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_up);

        auth = FirebaseAuth.getInstance();
        signupEmail = findViewById(R.id.signup_email);
        signupPassword = findViewById(R.id.signup_password);
        signupButton = findViewById(R.id.signup_button);
        loginRedirectText = findViewById(R.id.loginRedirectText);

        signupButton.setOnClickListener(v -> {
            String email = signupEmail.getText().toString().trim();
            String pass = signupPassword.getText().toString();

            // FIX: the old code had no `return` after the empty-email check, so an
            // empty email still reached createUserWithEmailAndPassword(), which
            // throws IllegalArgumentException and crashes the app.
            if (email.isEmpty()) {
                signupEmail.setError("Email cannot be empty");
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                signupEmail.setError("Please enter a valid email");
                return;
            }
            if (pass.length() < 6) { // Firebase minimum
                signupPassword.setError("Password must be at least 6 characters");
                return;
            }

            signupButton.setEnabled(false);
            auth.createUserWithEmailAndPassword(email, pass).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(this, "Sign up successful", Toast.LENGTH_SHORT).show();
                    // Firebase signs the new user in automatically, so go straight in.
                    Intent i = new Intent(this, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(i);
                    finish();
                } else {
                    signupButton.setEnabled(true);
                    Exception e = task.getException();
                    String msg;
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        msg = "An account with this email already exists.";
                    } else if (e instanceof FirebaseAuthWeakPasswordException) {
                        msg = "Password is too weak.";
                    } else {
                        msg = "Sign up failed: " + (e != null ? e.getMessage() : "unknown error");
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                }
            });
        });

        loginRedirectText.setOnClickListener(v -> finish());
    }
}
