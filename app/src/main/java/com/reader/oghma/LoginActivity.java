package com.reader.oghma;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.developer.gbuttons.GoogleSignInButton;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "OghmaLogin";

    private EditText loginEmail, loginPassword;
    private TextView signupRedirectText, forgotPassword;
    private Button loginButton;
    private GoogleSignInButton googleBtn;

    private FirebaseAuth auth;
    private GoogleSignInClient gClient;
    private ActivityResultLauncher<Intent> googleLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        auth = FirebaseAuth.getInstance();

        // Already signed in (e.g. app relaunch) -> skip the login screen.
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            openMain();
            return;
        }

        setContentView(R.layout.activity_login);

        loginEmail = findViewById(R.id.login_email);
        loginPassword = findViewById(R.id.login_password);
        loginButton = findViewById(R.id.login_button);
        signupRedirectText = findViewById(R.id.signUpRedirectText);
        forgotPassword = findViewById(R.id.forgot_password);
        googleBtn = findViewById(R.id.googleBtn);

        setupGoogleSignIn();

        loginButton.setOnClickListener(v -> attemptEmailLogin());
        signupRedirectText.setOnClickListener(v ->
                startActivity(new Intent(this, SignUpActivity.class)));
        forgotPassword.setOnClickListener(v -> showForgotDialog());
        googleBtn.setOnClickListener(v -> {
            setBusy(true);
            // Clear any cached account so the account picker always shows.
            gClient.signOut().addOnCompleteListener(t ->
                    googleLauncher.launch(gClient.getSignInIntent()));
        });
    }

    // ------------------------------------------------------------------
    // Email + password
    // ------------------------------------------------------------------

    private void attemptEmailLogin() {
        // FIX: trim — keyboard autocomplete often appends a trailing space,
        // which made valid emails fail the pattern check / Firebase lookup.
        String email = loginEmail.getText().toString().trim();
        String pass = loginPassword.getText().toString();

        if (email.isEmpty()) {
            loginEmail.setError("Email cannot be empty");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            loginEmail.setError("Please enter a valid email");
            return;
        }
        if (pass.isEmpty()) {
            loginPassword.setError("Password cannot be empty");
            return;
        }

        setBusy(true);
        auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(result -> {
                    Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show();
                    openMain();
                })
                .addOnFailureListener(e -> {
                    setBusy(false);
                    Log.e(TAG, "Email sign-in failed", e);
                    // FIX: show the real reason instead of a generic "Login Failed".
                    Toast.makeText(this, describe(e), Toast.LENGTH_LONG).show();
                });
    }

    // ------------------------------------------------------------------
    // Google
    // ------------------------------------------------------------------

    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        gClient = GoogleSignIn.getClient(this, gso);

        googleLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // FIX: the old code only handled RESULT_OK, so every failure
                    // (including DEVELOPER_ERROR / code 10 from a missing SHA-1,
                    // which comes back as RESULT_CANCELED) was silently ignored.
                    Task<GoogleSignInAccount> task =
                            GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        String idToken = account.getIdToken();
                        if (idToken == null) {
                            setBusy(false);
                            Toast.makeText(this,
                                    "Google returned no ID token. Check the web client ID.",
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        firebaseAuthWithGoogle(idToken);
                    } catch (ApiException e) {
                        setBusy(false);
                        Log.e(TAG, "Google sign-in failed, code=" + e.getStatusCode(), e);
                        if (e.getStatusCode() == 12501) {
                            return; // user backed out of the picker; not an error
                        }
                        Toast.makeText(this, describeGoogleError(e.getStatusCode()),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this, "Login successful", Toast.LENGTH_SHORT).show();
                openMain();
            } else {
                setBusy(false);
                Log.e(TAG, "Firebase Google auth failed", task.getException());
                Toast.makeText(this, describe(task.getException()), Toast.LENGTH_LONG).show();
            }
        });
    }

    private String describeGoogleError(int code) {
        switch (code) {
            case 10:
                return "Google sign-in is not configured for this build (error 10). "
                        + "Add this app's SHA-1 in Firebase and re-download google-services.json.";
            case 7:
                return "Network error. Check your connection.";
            case 12500:
                return "Google sign-in failed. Update Google Play services and try again.";
            default:
                return "Google sign-in failed (code " + code + ")";
        }
    }

    // ------------------------------------------------------------------
    // Forgot password
    // ------------------------------------------------------------------

    private void showForgotDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.activity_dialog_forgot, null);
        EditText emailBox = dialogView.findViewById(R.id.emailBox);
        String typed = loginEmail.getText().toString().trim();
        if (!typed.isEmpty()) emailBox.setText(typed); // convenience

        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();

        dialogView.findViewById(R.id.btnReset).setOnClickListener(v -> {
            String userEmail = emailBox.getText().toString().trim();
            // FIX: was `isEmpty && !matches` so malformed non-empty emails slipped through.
            if (userEmail.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(userEmail).matches()) {
                emailBox.setError("Enter a valid email");
                return;
            }
            auth.sendPasswordResetEmail(userEmail).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(this, "Check your email", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                } else {
                    Toast.makeText(this, describe(task.getException()), Toast.LENGTH_LONG).show();
                }
            });
        });
        dialogView.findViewById(R.id.btnCancel).setOnClickListener(v -> dialog.dismiss());

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(0));
        }
        dialog.show();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void openMain() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void setBusy(boolean busy) {
        loginButton.setEnabled(!busy);
        googleBtn.setEnabled(!busy);
    }

    private String describe(@Nullable Exception e) {
        if (e instanceof FirebaseNetworkException) {
            return "No internet connection.";
        }
        if (e instanceof FirebaseAuthInvalidUserException) {
            return "No account found for that email, or it has been disabled.";
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            return "Incorrect email or password.";
        }
        if (e instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) e).getErrorCode();
            if ("ERROR_OPERATION_NOT_ALLOWED".equals(code)) {
                return "This sign-in method is disabled in the Firebase console.";
            }
            if ("ERROR_TOO_MANY_REQUESTS".equals(code)) {
                return "Too many attempts. Try again later.";
            }
            return "Sign-in failed: " + code;
        }
        return e != null && e.getMessage() != null ? e.getMessage() : "Sign-in failed.";
    }
}
