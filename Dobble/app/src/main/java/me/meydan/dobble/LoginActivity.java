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

public class LoginActivity extends AppCompatActivity {

    private EditText emailEditText;
    private EditText passwordEditText;
    private ProgressBar progressBar;

    // Firebase Auth instance for managing user authentication sessions
    private FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Initialize Firebase Authentication instance
        firebaseAuth = FirebaseAuth.getInstance();

        // Bind UI components from the XML layout
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        progressBar = findViewById(R.id.progressBar);

        Button loginButton = findViewById(R.id.loginButton);
        Button registerButton = findViewById(R.id.registerButton);

        // Trigger login sequence when the login button is clicked
        loginButton.setOnClickListener(v -> login());

        // Navigate to RegisterActivity when the register button is clicked
        registerButton.setOnClickListener(v -> {
            Intent intent = new Intent(
                    LoginActivity.this,
                    RegisterActivity.class
            );
            startActivity(intent);
        });
    }

    /**
     * Checked during the activity lifecycle start phase.
     * Automatically redirects the user to the MainActivity if they are already authenticated.
     */
    @Override
    protected void onStart() {
        super.onStart();

        // Check if a user session is already active
        if (firebaseAuth.getCurrentUser() != null) {
            openMainActivity();
        }
    }

    /**
     * Validates inputs and authenticates the credentials against Firebase.
     * Displays errors locally on fields if inputs are empty or invalid.
     */
    private void login() {
        // Retrieve credentials and trim whitespace from the email string
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();

        // Validate that the email field is not empty
        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Enter an email");
            return;
        }

        // Validate that the password field is not empty
        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Enter a password");
            return;
        }

        // Display the loader while processing the network request
        setLoading(true);

        // Attempt sign-in via Firebase Auth
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    // Hide the loader once the asynchronous response returns
                    setLoading(false);

                    if (task.isSuccessful()) {
                        // Routing to MainActivity on successful login
                        openMainActivity();
                    } else {
                        // Extract server error details or fallback to generic message
                        String message = task.getException() != null
                                ? task.getException().getMessage()
                                : "Login failed";

                        Toast.makeText(
                                LoginActivity.this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }

    /**
     * Toggles the visibility of the progress bar spinner.
     *
     * @param loading true to show progress bar, false to hide it.
     */
    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    /**
     * Redirects to MainActivity and clears the current activity stack.
     * This prevents the user from navigating back to the login screen using the back button.
     */
    private void openMainActivity() {
        Intent intent = new Intent(
                LoginActivity.this,
                MainActivity.class
        );

        // Set flags to clear the historical back stack completely
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish(); // Terminate the current instance of LoginActivity
    }
}
