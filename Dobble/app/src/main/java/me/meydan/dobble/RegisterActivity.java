package me.meydan.dobble;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText nameEditText;
    private EditText emailEditText;
    private EditText passwordEditText;
    private EditText confirmPasswordEditText;
    private ProgressBar progressBar;
    private Button createAccountButton;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        nameEditText = findViewById(R.id.nameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        confirmPasswordEditText =
                findViewById(R.id.confirmPasswordEditText);
        progressBar = findViewById(R.id.progressBar);
        createAccountButton =
                findViewById(R.id.createAccountButton);

        createAccountButton.setOnClickListener(v -> register());
    }

    private void register() {
        String name = nameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();
        String confirmPassword =
                confirmPasswordEditText.getText().toString();

        if (TextUtils.isEmpty(name)) {
            nameEditText.setError("Enter a display name");
            return;
        }

        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Enter an email");
            return;
        }

        if (password.length() < 6) {
            passwordEditText.setError(
                    "Password must contain at least 6 characters"
            );
            return;
        }

        if (!password.equals(confirmPassword)) {
            confirmPasswordEditText.setError(
                    "Passwords do not match"
            );
            return;
        }

        setLoading(true);

        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        setLoading(false);

                        String message = task.getException() != null
                                ? task.getException().getMessage()
                                : "Registration failed";

                        Toast.makeText(
                                RegisterActivity.this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();

                        return;
                    }

                    FirebaseUser user = firebaseAuth.getCurrentUser();

                    if (user == null) {
                        setLoading(false);

                        Toast.makeText(
                                RegisterActivity.this,
                                "Could not load the new user",
                                Toast.LENGTH_LONG
                        ).show();

                        return;
                    }

                    updateFirebaseProfile(user, name);
                    saveUserToFirestore(user, name, email);

                    // לא מחכים ל-Firestore כדי לעבור מסך
                    openMainActivity();
                });
    }

    private void updateFirebaseProfile(
            FirebaseUser user,
            String displayName
    ) {
        UserProfileChangeRequest profileUpdates =
                new UserProfileChangeRequest.Builder()
                        .setDisplayName(displayName)
                        .build();

        user.updateProfile(profileUpdates)
                .addOnFailureListener(error ->
                        Toast.makeText(
                                RegisterActivity.this,
                                "Profile name was not saved",
                                Toast.LENGTH_SHORT
                        ).show()
                );
    }

    private void saveUserToFirestore(
            FirebaseUser user,
            String name,
            String email
    ) {
        Map<String, Object> userData = new HashMap<>();

        userData.put("uid", user.getUid());
        userData.put("displayName", name);
        userData.put("email", email);
        userData.put("wins", 0);
        userData.put("losses", 0);
        userData.put("gamesPlayed", 0);
        userData.put("totalScore", 0);
        userData.put("createdAt", FieldValue.serverTimestamp());

        firestore.collection("users")
                .document(user.getUid())
                .set(userData)
                .addOnFailureListener(error ->
                        Toast.makeText(
                                RegisterActivity.this,
                                "Account created, but profile data was not saved: "
                                        + error.getMessage(),
                                Toast.LENGTH_LONG
                        ).show()
                );
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(
                loading ? View.VISIBLE : View.GONE
        );

        createAccountButton.setEnabled(!loading);
    }

    private void openMainActivity() {
        Intent intent = new Intent(
                RegisterActivity.this,
                MainActivity.class
        );

        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }
}