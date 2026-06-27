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

        // Initialize Firebase Authentication and Firestore database
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        // Connect variables to the XML layout components
        nameEditText = findViewById(R.id.nameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText);
        progressBar = findViewById(R.id.progressBar);
        createAccountButton = findViewById(R.id.createAccountButton);

        // Set what happens when clicking the create account button
        createAccountButton.setOnClickListener(v -> register());
    }

    /**
     * Handles the full registration logic. It reads input values, verifies they are valid,
     * and sends the signup request to Firebase server.
     */
    private void register() {
        // Read input text values and remove unnecessary spaces from text fields
        String name = nameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();
        String confirmPassword = confirmPasswordEditText.getText().toString();

        // Local Form Validation: Make sure the display name is entered
        if (TextUtils.isEmpty(name)) {
            nameEditText.setError("Enter a display name");
            return;
        }

        // Local Form Validation: Make sure the email field is not empty
        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Enter an email");
            return;
        }

        // Local Form Validation: Require password to be at least 6 characters long
        if (password.length() < 6) {
            passwordEditText.setError("Password must contain at least 6 characters");
            return;
        }

        // Local Form Validation: Ensure both password inputs match exactly
        if (!password.equals(confirmPassword)) {
            confirmPasswordEditText.setError("Passwords do not match");
            return;
        }

        // Start the loading screen view state
        setLoading(true);

        // Call Firebase API to create a new user profile online
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        // Registration failed: stop loading and show the error reason
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

                    // Get the newly registered user object
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

                    // Save username to the account profile and save full user info to Firestore database
                    updateFirebaseProfile(user, name);
                    saveUserToFirestore(user, name, email);

                    // Open the main app dashboard
                    openMainActivity();
                });
    }

    /**
     * Updates the local account profile info inside Firebase Authentication with the chosen display name.
     */
    private void updateFirebaseProfile(FirebaseUser user, String displayName) {
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

    /**
     * Saves user details and initializes default statistics map to 0 inside the Firestore database.
     */
    private void saveUserToFirestore(FirebaseUser user, String name, String email) {
        Map<String, Object> userData = new HashMap<>();

        // Put the initial user profile details and reset game counters to zero
        userData.put("uid", user.getUid());
        userData.put("displayName", name);
        userData.put("email", email);
        userData.put("wins", 0);
        userData.put("losses", 0);
        userData.put("gamesPlayed", 0);
        userData.put("totalScore", 0);
        userData.put("createdAt", FieldValue.serverTimestamp()); // Use server clock time

        // Write the data to a document named after the user's UID inside the "users" collection
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

    /**
     * Toggles the loading state by changing the progress bar visibility and disabling the signup button.
     */
    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        createAccountButton.setEnabled(!loading);
    }

    /**
     * Navigates to the MainActivity and clears the page history so users cannot return via back click.
     */
    private void openMainActivity() {
        Intent intent = new Intent(
                RegisterActivity.this,
                MainActivity.class
        );

        // Use intent flags to clear the navigation task back stack completely
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish(); // Close RegisterActivity completely
    }
}