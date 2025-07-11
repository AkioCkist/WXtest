package com.midterm.mobiledesignfinalterm.authentication;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.midterm.mobiledesignfinalterm.homepage.Homepage;
import com.midterm.mobiledesignfinalterm.R;

import org.mindrot.jbcrypt.BCrypt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Login extends AppCompatActivity {

    private EditText editTextPhoneNumber;
    private EditText editTextPassword;
    private ImageButton buttonTogglePassword;
    private CheckBox checkBoxRememberMe;
    private Button buttonSignIn;
    private TextView textViewForgotPassword;
    private TextView textViewSignUp;
    private Button buttonGoogleSignIn;
    private Button buttonFacebookSignIn;

    private boolean isPasswordVisible = false;

    // Google Sign-In
    private static final String TAG = "GoogleSignIn";
    private GoogleSignInClient mGoogleSignInClient;
    private ActivityResultLauncher<Intent> mGoogleSignInLauncher;

    // Firestore
    private FirebaseFirestore db;
    private static final String USERS_COLLECTION = "users";
    private static final String ROLES_COLLECTION = "roles";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        windowInsetsController.setAppearanceLightStatusBars(false);

        // Initialize Firestore
        db = FirebaseFirestore.getInstance();

        initializeViews();
        setupClickListeners();
        setupFocusListeners();
        setupPasswordToggle();
        configureGoogleSignIn();
        setupGoogleSignInLauncher();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Automatic Google sign-in disabled. User must manually sign in.
    }

    private void initializeViews() {
        editTextPhoneNumber = findViewById(R.id.editTextPhoneNumber);
        editTextPassword = findViewById(R.id.editTextPassword);
        buttonTogglePassword = findViewById(R.id.buttonTogglePassword);
        checkBoxRememberMe = findViewById(R.id.checkBoxRememberMe);
        buttonSignIn = findViewById(R.id.buttonSignIn);
        textViewForgotPassword = findViewById(R.id.textViewForgotPassword);
        textViewSignUp = findViewById(R.id.textViewSignUp);
        buttonGoogleSignIn = findViewById(R.id.buttonGoogleSignIn);
        buttonFacebookSignIn = findViewById(R.id.buttonFacebookSignIn);
    }

    private void setupClickListeners() {
        buttonSignIn.setOnClickListener(v -> animateButtonClick(v, this::handleSignIn));
        textViewForgotPassword.setOnClickListener(v -> {
            animateTextClick(v);
            handleForgotPassword();
        });
        textViewSignUp.setOnClickListener(v -> {
            animateTextClick(v);
            handleSignUp();
        });
        checkBoxRememberMe.setOnClickListener(this::animateCheckboxClick);
        buttonGoogleSignIn.setOnClickListener(v -> {
            animateButtonClick(v, this::signInWithGoogle);
        });
        buttonFacebookSignIn.setOnClickListener(v -> {
            trollFacebookButton(v);
        });
    }

    // Google Sign-In Methods
    private void configureGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void setupGoogleSignInLauncher() {
        mGoogleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                        handleGoogleSignInResult(task);
                    } else {
                        Log.w(TAG, "Sign-in flow cancelled by user.");
                        Toast.makeText(Login.this, "Google Sign-In was cancelled.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        mGoogleSignInLauncher.launch(signInIntent);
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            Log.d(TAG, "signInResult:success, user: " + account.getEmail());
            // Check if user exists in Firestore, create if not, then navigate to homepage
            checkUserInFirestore(account);
        } catch (ApiException e) {
            Log.w(TAG, "signInResult:failed code=" + e.getStatusCode());
            Toast.makeText(this, "Google Sign-In failed. Please try again.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Checks if the Google-signed-in user exists in Firestore.
     * If they exist, navigate to the homepage.
     * If not, create a new user entry and then navigate.
     * @param account The GoogleSignInAccount of the user.
     */
    private void checkUserInFirestore(GoogleSignInAccount account) {
        String userId = account.getId();
        if (userId == null) {
            Toast.makeText(this, "Google Sign-In failed: User ID is null.", Toast.LENGTH_LONG).show();
            return;
        }

        // Reference to the user document using their Google ID
        db.collection(USERS_COLLECTION).document(userId).get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document != null && document.exists()) {
                    // User exists
                    Log.d(TAG, "User " + userId + " already exists in Firestore.");
                    Toast.makeText(this, "Welcome back, " + account.getDisplayName() + "!", Toast.LENGTH_SHORT).show();
                    navigateToHomepage(account);
                } else {
                    // User does not exist, create a new one
                    Log.d(TAG, "User " + userId + " not found. Creating new user.");
                    createNewUserInFirestore(account);
                }
            } else {
                // Error getting document
                Log.w(TAG, "Error checking user in Firestore.", task.getException());
                Toast.makeText(Login.this, "Failed to verify user. Please try again.", Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Creates a new user document in the 'users' collection in Firestore.
     * @param account The GoogleSignInAccount of the new user.
     */
    private void createNewUserInFirestore(GoogleSignInAccount account) {
        String userId = account.getId();
        String userName = account.getDisplayName();
        String email = account.getEmail();

        // Create a new user map object
        Map<String, Object> newUser = new HashMap<>();
        newUser.put("id", userId);
        newUser.put("username", userName);
        newUser.put("email", email);
        newUser.put("role_id", 1L); // Default role_id for a new user (1 = renter based on your JSON)
        newUser.put("created_at", Timestamp.now()); // Use server timestamp

        // Add the new document to the 'users' collection with the Google User ID as the document ID
        db.collection(USERS_COLLECTION).document(userId).set(newUser)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "New user created successfully in Firestore.");
                    Toast.makeText(this, "Welcome, " + userName + "! Your account has been created.", Toast.LENGTH_SHORT).show();
                    navigateToHomepage(account);
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error creating new user in Firestore.", e);
                    Toast.makeText(Login.this, "Failed to create your account. Please try again.", Toast.LENGTH_LONG).show();
                });
    }


    private void navigateToHomepage(GoogleSignInAccount account) {
        Intent intent = new Intent(Login.this, Homepage.class);
        intent.putExtra("user_name", account.getDisplayName());
        intent.putExtra("user_email", account.getEmail());
        intent.putExtra("user_id", account.getId());

        if (account.getPhotoUrl() != null) {
            intent.putExtra("user_photo_uri", account.getPhotoUrl().toString());
        }

        startActivity(intent);
        finish();
    }

    // Firestore Authentication Methods
    public void handleSignIn() {
        String phoneNumber = editTextPhoneNumber.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        // Virtual test accounts
        if (phoneNumber.equals("01234567890") && password.equals("01234567890")) {
            Toast.makeText(Login.this, "Welcome, Test Account!", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Login.this, Homepage.class);
            intent.putExtra("user_name", "Test Account");
            intent.putExtra("user_phone", phoneNumber);
            intent.putExtra("user_id", "999");

            ArrayList<String> userRoles = new ArrayList<>();
            userRoles.add("tester");
            intent.putStringArrayListExtra("user_roles", userRoles);

            String userData = "{\"account_id\":999,\"phone_number\":\"01234567890\",\"full_name\":\"Test Account\",\"role\":\"tester\"}";
            intent.putExtra("user_data", userData);

            startActivity(intent);
            finish();
            return;
        }

        if (phoneNumber.equals("0123456789") && password.equals("123456")) {
            Intent intent = new Intent(Login.this, Homepage.class);
            intent.putExtra("user_name", "Tài khoản ảo");
            intent.putExtra("user_phone", phoneNumber);
            intent.putExtra("user_id", "virtual_user");
            intent.putExtra("user_data", "{}");
            startActivity(intent);
            finish();
            return;
        }

        // Validation
        if (phoneNumber.isEmpty()) {
            editTextPhoneNumber.setError("Phone number is required");
            editTextPhoneNumber.requestFocus();
            animateErrorShake(editTextPhoneNumber);
            return;
        }

        String phoneRegex = "^[+]?[0-9]{10,13}$";
        if (!phoneNumber.matches(phoneRegex)) {
            editTextPhoneNumber.setError("Please enter a valid phone number");
            editTextPhoneNumber.requestFocus();
            animateErrorShake(editTextPhoneNumber);
            return;
        }

        if (password.isEmpty()) {
            editTextPassword.setError("Password is required");
            editTextPassword.requestFocus();
            animateErrorShake(editTextPassword);
            return;
        }

        // Authenticate with Firestore
        authenticateWithFirestore(phoneNumber, password);
    }

    private void authenticateWithFirestore(String phoneNumber, String password) {
        // Show loading state
        buttonSignIn.setEnabled(false);
        buttonSignIn.setText("Signing in...");

        // Query Firestore for user with matching phone number
        CollectionReference usersRef = db.collection(USERS_COLLECTION);
        Query query = usersRef.whereEqualTo("phone_number", phoneNumber);

        query.get().addOnCompleteListener(task -> {
            // Reset button state
            buttonSignIn.setEnabled(true);
            buttonSignIn.setText("Sign In");

            if (task.isSuccessful()) {
                QuerySnapshot querySnapshot = task.getResult();

                if (querySnapshot != null && !querySnapshot.isEmpty()) {
                    // User found, verify password
                    DocumentSnapshot userDoc = querySnapshot.getDocuments().get(0);
                    String storedPasswordHash = userDoc.getString("password_hash");

                    if (storedPasswordHash != null && verifyPassword(password, storedPasswordHash)) {
                        // Password is correct, get user data
                        String userId = userDoc.getId();
                        String username = userDoc.getString("username");
                        Long roleId = userDoc.getLong("role_id");

                        // Get role information
                        getRoleAndProceed(userId, username, phoneNumber, roleId, userDoc.getData());

                    } else {
                        // Password is incorrect
                        Toast.makeText(Login.this, "Incorrect password", Toast.LENGTH_SHORT).show();
                        animateErrorShake(editTextPassword);
                    }
                } else {
                    // User not found
                    Toast.makeText(Login.this, "User not found", Toast.LENGTH_SHORT).show();
                    animateErrorShake(editTextPhoneNumber);
                }
            } else {
                // Firestore query failed
                Exception exception = task.getException();
                Log.e(TAG, "Error querying Firestore", exception);
                Toast.makeText(Login.this, "Authentication failed: " +
                                (exception != null ? exception.getMessage() : "Unknown error"),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void getRoleAndProceed(String userId, String username, String phoneNumber, Long roleId, Map<String, Object> userData) {
        if (roleId == null) {
            // Default role if not specified
            proceedToApp(userId, username, phoneNumber, "user", userData);
            return;
        }

        // Get role information from roles collection
        db.collection(ROLES_COLLECTION).document(String.valueOf(roleId))
                .get()
                .addOnCompleteListener(task -> {
                    String roleName = "user"; // default role

                    if (task.isSuccessful()) {
                        DocumentSnapshot roleDoc = task.getResult();
                        if (roleDoc.exists()) {
                            roleName = roleDoc.getString("role_name");
                        }
                    }

                    proceedToApp(userId, username, phoneNumber, roleName, userData);
                });
    }

    private void proceedToApp(String userId, String username, String phoneNumber, String roleName, Map<String, Object> userData) {
        Log.d(TAG, "User authenticated: " + username + ", Role: " + roleName);

        // Create user roles list
        ArrayList<String> userRoles = new ArrayList<>();
        userRoles.add(roleName);

        if ("admin".equals(roleName)) {
            // Redirect to Admin Dashboard
            Toast.makeText(Login.this, "Chào mừng Admin " + username, Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Login.this, com.midterm.mobiledesignfinalterm.admin.AdminDashboard.class);
            intent.putExtra("user_name", username);
            intent.putExtra("user_phone", phoneNumber);
            intent.putExtra("user_id", userId);
            intent.putExtra("user_role", roleName);
            intent.putStringArrayListExtra("user_roles", userRoles);
            intent.putExtra("user_data", userData.toString());
            startActivity(intent);
            finish();
        } else {
            // Redirect to Homepage for regular users
            Toast.makeText(Login.this, "Welcome " + username, Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Login.this, Homepage.class);
            intent.putExtra("user_name", username);
            intent.putExtra("user_phone", phoneNumber);
            intent.putExtra("user_id", userId);
            intent.putStringArrayListExtra("user_roles", userRoles);
            intent.putExtra("user_data", userData.toString());
            startActivity(intent);
            finish();
        }
    }

    private boolean verifyPassword(String plainPassword, String hashedPassword) {
        if (hashedPassword == null || hashedPassword.isEmpty()) {
            Log.e(TAG, "Password hash is null or empty");
            return false;
        }

        Log.d(TAG, "Attempting password verification");

        try {
            // First try direct comparison for legacy or plain text passwords
            if (plainPassword.equals(hashedPassword)) {
                Log.d(TAG, "Password verified using direct comparison");
                return true;
            }

            // Handle BCrypt verification
            if (hashedPassword.startsWith("$2")) {
                Log.d(TAG, "Attempting BCrypt verification");
                // Normalize $2y$ to $2a$ for compatibility
                String normalizedHash = hashedPassword;
                if (hashedPassword.startsWith("$2y$")) {
                    normalizedHash = "$2a$" + hashedPassword.substring(4);
                }
                return BCrypt.checkpw(plainPassword, normalizedHash);
            } else {
                Log.e(TAG, "Unsupported password hash format");
                return false;
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "BCrypt verification failed: " + e.getMessage());
            // Fallback to direct comparison for migration period
            return plainPassword.equals(hashedPassword);
        } catch (Exception e) {
            Log.e(TAG, "Password verification error", e);
            return false;
        }
    }

    // Password toggle and animation methods (improved)
    private void setupPasswordToggle() {
        // Set touch listener to handle touch events on toggle button
        View.OnTouchListener touchListener = (v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                togglePasswordVisibility();
                return true;
            }
            return false;
        };

        // Apply touch listener directly to the button
        buttonTogglePassword.setOnTouchListener(touchListener);
    }

    private void togglePasswordVisibility() {
        if (isPasswordVisible) {
            editTextPassword.setTransformationMethod(PasswordTransformationMethod.getInstance());
            buttonTogglePassword.setImageResource(R.drawable.ic_eye_off);
            isPasswordVisible = false;
        } else {
            editTextPassword.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
            buttonTogglePassword.setImageResource(R.drawable.ic_eye_on);
            isPasswordVisible = true;
        }
        editTextPassword.setSelection(editTextPassword.getText().length());
        animatePasswordToggle(buttonTogglePassword);
    }

    private void animatePasswordToggle(View view) {
        ObjectAnimator rotation = ObjectAnimator.ofFloat(view, "rotation", 0f, 360f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.2f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.2f, 1f);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(rotation, scaleX, scaleY);
        animatorSet.setDuration(300);
        animatorSet.setInterpolator(new OvershootInterpolator(1.1f));
        animatorSet.start();
    }

    private void setupFocusListeners() {
        View.OnFocusChangeListener focusListener = (v, hasFocus) -> animateEditTextFocus(v, hasFocus);
        editTextPhoneNumber.setOnFocusChangeListener(focusListener);
        editTextPassword.setOnFocusChangeListener(focusListener);
    }

    private void animateEditTextFocus(View view, boolean hasFocus) {
        if (hasFocus) {
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.05f, 1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.05f, 1f);
            ObjectAnimator elevation = ObjectAnimator.ofFloat(view, "elevation", 0f, 8f);
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(scaleX, scaleY, elevation);
            animatorSet.setDuration(300);
            animatorSet.setInterpolator(new OvershootInterpolator(1.1f));
            animatorSet.start();
        } else {
            ObjectAnimator.ofFloat(view, "elevation", 8f, 0f).setDuration(200).start();
        }
    }

    private void animateButtonClick(View button, Runnable onComplete) {
        button.animate().scaleX(0.95f).scaleY(0.95f).setDuration(70).withEndAction(() ->
                button.animate().scaleX(1f).scaleY(1f).setDuration(100).withEndAction(onComplete).start()).start();
    }

    private void animateTextClick(View view) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.2f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.2f, 1f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(view, "alpha", 1f, 0.7f, 1f);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(scaleX, scaleY, alpha);
        animatorSet.setDuration(250);
        animatorSet.setInterpolator(new OvershootInterpolator(1.3f));
        animatorSet.start();
    }

    private void animateCheckboxClick(View view) {
        ObjectAnimator rotation = ObjectAnimator.ofFloat(view, "rotation", 0f, 15f, -15f, 0f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.3f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.3f, 1f);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(rotation, scaleX, scaleY);
        animatorSet.setDuration(350);
        animatorSet.setInterpolator(new BounceInterpolator());
        animatorSet.start();
    }

    private void animateErrorShake(View view) {
        ObjectAnimator.ofFloat(view, "translationX", 0f, 25f, -25f, 25f, -25f, 15f, -15f, 6f, -6f, 0f).setDuration(600).start();
    }

    private void handleForgotPassword() {
        Intent intent = new Intent(Login.this, ForgotPassword.class);
        startActivity(intent);
    }

    private void handleSignUp() {
        Intent intent = new Intent(Login.this, Register.class);
        startActivity(intent);
    }

    // Facebook Troll Animation - Fun way to let users know it's not ready yet!
    private void trollFacebookButton(View view) {
        // Disable the button temporarily to prevent spam clicking
        view.setEnabled(false);

        // Phase 1: Excited bounce animation (like it's about to work)
        ObjectAnimator bounceX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.3f, 0.8f, 1.2f, 0.9f, 1.1f, 1f);
        ObjectAnimator bounceY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.3f, 0.8f, 1.2f, 0.9f, 1.1f, 1f);
        ObjectAnimator rotation1 = ObjectAnimator.ofFloat(view, "rotation", 0f, 15f, -10f, 8f, -5f, 0f);

        AnimatorSet phase1 = new AnimatorSet();
        phase1.playTogether(bounceX, bounceY, rotation1);
        phase1.setDuration(800);
        phase1.setInterpolator(new BounceInterpolator());

        // Phase 2: "Loading" effect - rapid spin
        ObjectAnimator rapidSpin = ObjectAnimator.ofFloat(view, "rotation", 0f, 1080f); // 3 full rotations
        rapidSpin.setDuration(1200);

        // Phase 3: "Error" shake effect
        ObjectAnimator errorShake = ObjectAnimator.ofFloat(view, "translationX", 0f, 15f, -15f, 12f, -12f, 8f, -8f, 4f, -4f, 0f);
        ObjectAnimator errorBounce = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.8f, 1.2f, 0.9f, 1.1f, 1f);
        ObjectAnimator errorBounceY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.8f, 1.2f, 0.9f, 1.1f, 1f);

        AnimatorSet phase3 = new AnimatorSet();
        phase3.playTogether(errorShake, errorBounce, errorBounceY);
        phase3.setDuration(600);

        // Chain the animations together
        phase1.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                // Show fake loading toast
                Toast.makeText(Login.this, "Connecting to Facebook... 📱", Toast.LENGTH_SHORT).show();
                rapidSpin.start();
            }
        });

        rapidSpin.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                phase3.start();
            }
        });

        phase3.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                // Show the final troll message with a delay for dramatic effect
                new Handler().postDelayed(() -> {
                    Toast.makeText(Login.this,
                            "Oops! 😅 Facebook login is still in development.\nPlease use Google Sign-In for now! 🚀",
                            Toast.LENGTH_LONG).show();

                    // Re-enable the button
                    view.setEnabled(true);
                }, 300);
            }
        });

        // Start the troll sequence!
        phase1.start();
    }
}