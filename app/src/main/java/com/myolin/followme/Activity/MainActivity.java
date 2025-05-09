package com.myolin.followme.Activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.myolin.followme.R;
import com.myolin.followme.Utils.NetworkChecker;
import com.myolin.followme.Volley.CreateUserAccountAPIVolley;
import com.myolin.followme.Volley.VerifyUserCredentialsAPIVolley;
import com.myolin.followme.databinding.ActivityMainBinding;
import com.myolin.followme.databinding.DialogLoginBinding;
import com.myolin.followme.databinding.DialogRegisterBinding;

import java.util.Objects;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static final int LOCATION_REQUEST = 111;
    private static final int BACKGROUND_LOCATION_REQUEST = 222;
    private static final int NOTIFICATION_REQUEST = 333;

    private ActivityMainBinding binding;
    private boolean isLoggedIn = false;
    private SharedPreferences myPrefs;

    private boolean splashKeepOn = true;
    private final long minSplashTime = 2000;
    private long splashStartTime;

    private String username = "";
    private String firstName = "";
    private String lastName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());

        splashStartTime = System.currentTimeMillis();
        SplashScreen.installSplashScreen(this)
                        .setKeepOnScreenCondition(() -> splashKeepOn ||
                                (System.currentTimeMillis() - splashStartTime <= minSplashTime));

        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        splashKeepOn = false;

        myPrefs = getSharedPreferences("MY_PREFS", Context.MODE_PRIVATE);
    }

    public void goToTripLeadActivity(View v) {
        if (isLoggedIn) {
            boolean hasPerm = checkAppPermission();
            if (hasPerm) {
                initTrip();
            }
        } else {
            loginDialog();
        }
    }

    /// User Login Dialog ///
    public void loginDialog() {
        if (!NetworkChecker.hasNetworkConnection(this)) {
            noNetworkStartTripDialog();
            return;
        }

        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            gpsNotEnabledDialog();
            return;
        }


        DialogLoginBinding binding1 = DialogLoginBinding.inflate(getLayoutInflater());
        setupLoginInputValidation(binding1);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_title));
        builder.setMessage(getString(R.string.dialog_login_body));
        builder.setIcon(R.drawable.logo);
        builder.setPositiveButton(getString(R.string.login), ((dialog, which) -> {
            if (NetworkChecker.hasNetworkConnection(this)) {
                verifyUser(binding1);
            } else {
                noNetworkLoginDialog();
            }
        }));
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setNeutralButton(getString(R.string.register), ((dialog, which) -> registerDialog()));
        builder.setView(binding1.getRoot());

        if (myPrefs.contains("selection")) {
            boolean isSelected = myPrefs.getBoolean("selection", false);
            if (isSelected) {
                binding1.saveCred.setChecked(true);
                if (myPrefs.contains("username") && myPrefs.contains("password")) {
                    String username = myPrefs.getString("username", "");
                    String password = myPrefs.getString("password", "");
                    binding1.loginUsername.setText(username);
                    binding1.loginPassword.setText(password);
                }
            } else {
                binding1.saveCred.setChecked(false);
            }
        }

        binding1.saveCred.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor prefsEditor = myPrefs.edit();
            if (isChecked) {
                // save to sharedPrefs username, password and checkbox selection
                prefsEditor.putBoolean("selection", true);
                prefsEditor.putString("username", Objects.requireNonNull(binding1.loginUsername.getText()).toString());
                prefsEditor.putString("password", Objects.requireNonNull(binding1.loginPassword.getText()).toString());
            } else {
                // delete username, password from sharedPrefs
                prefsEditor.remove("username");
                prefsEditor.remove("password");
                prefsEditor.putBoolean("selection", false);
            }
            prefsEditor.apply();
        });

        builder.create().show();
    }

    private void setupLoginInputValidation(DialogLoginBinding binding1) {
        binding1.loginUsername.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() < 8) {
                    binding1.usernameInputLayout.setError("Must be between 8 and 12 characters");
                } else {
                    binding1.usernameInputLayout.setError(null);
                }
            }
        });

        binding1.loginPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() < 8) {
                    binding1.passwordInputLayout.setError("Must be between 8 and 12 characters");
                } else {
                    binding1.passwordInputLayout.setError(null);
                }
            }
        });
    }

    private void verifyUser(DialogLoginBinding binding1) {
        binding.progressBar.setVisibility(View.VISIBLE);
        String username = Objects.requireNonNull(binding1.loginUsername.getText()).toString();
        String password = Objects.requireNonNull(binding1.loginPassword.getText()).toString();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_empty_field), Toast.LENGTH_SHORT).show();
            loginDialog();
            return;
        }
        VerifyUserCredentialsAPIVolley api = new VerifyUserCredentialsAPIVolley(this);
        api.checkCredentials(username, password);
    }

    /// User Login Success Handler ///
    public void handleVerifyUserCredentialsSuccess(String userName, String firstName, String lastName) {
        binding.progressBar.setVisibility(View.GONE);
        Log.d(TAG, "handleVerifyUserCredentialsSuccess: " + userName + "," + firstName + "," + lastName);
        username = userName;
        this.firstName = firstName;
        this.lastName = lastName;
        isLoggedIn = true;
        boolean hasPerm = checkAppPermission();
        if (hasPerm) {
            initTrip();
        }
    }

    /// User Login Fail Handler ///
    public void handleVerifyUserCredentialsFail() {
        binding.progressBar.setVisibility(View.GONE);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_login_fail_title));
        builder.setMessage(getString(R.string.dialog_login_fail_body));
        builder.setIcon(R.drawable.logo);
        builder.setPositiveButton("OK", ((dialog, which) -> loginDialog()));
        builder.create().show();
    }

    /// User Registration Dialog ///
    public void registerDialog() {
        DialogRegisterBinding binding1 = DialogRegisterBinding.inflate(getLayoutInflater());
        setupRegisterInputValidation(binding1);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_title));
        builder.setMessage(getString(R.string.dialog_register_body));
        builder.setIcon(R.drawable.logo);
        builder.setPositiveButton(getString(R.string.register), ((dialog, which) -> {
            if (NetworkChecker.hasNetworkConnection(this)) {
                registerUser(binding1);
            } else {
                noNetworkRegisterDialog();
            }
        }));
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setView(binding1.getRoot());
        builder.create().show();
    }

    private void setupRegisterInputValidation(DialogRegisterBinding binding1) {
        binding1.registerUsername.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() < 8) {
                    binding1.inputLayoutUsername.setError("Must be between 8 and 12 characters");
                } else {
                    binding1.inputLayoutUsername.setError(null);
                }
            }
        });

        binding1.registerPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() < 8) {
                    binding1.inputLayoutPassword.setError("Must be between 8 and 12 characters");
                } else {
                    binding1.inputLayoutPassword.setError(null);
                }
            }
        });
    }

    private void registerUser(DialogRegisterBinding binding1) {
        binding.progressBar.setVisibility(View.VISIBLE);
        String firstName = Objects.requireNonNull(binding1.registerFirstName.getText()).toString();
        String lastName = Objects.requireNonNull(binding1.registerLastName.getText()).toString();
        String email = Objects.requireNonNull(binding1.registerEmail.getText()).toString();
        String username = Objects.requireNonNull(binding1.registerUsername.getText()).toString();
        String password = Objects.requireNonNull(binding1.registerPassword.getText()).toString();

        if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || username.isEmpty() ||
            password.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_empty_field), Toast.LENGTH_SHORT).show();
            registerDialog();
            return;
        }

        CreateUserAccountAPIVolley api = new CreateUserAccountAPIVolley(this);
        api.createUser(firstName, lastName, email, username, password);
    }

    /// User Registration Success Handler ///
    public void handleCreateUserAccountSuccess(String firstName, String lastName, String email,
                                               String userName) {
        binding.progressBar.setVisibility(View.GONE);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_registration_successful));
        builder.setPositiveButton("OK", ((dialog, which) -> loginDialog()));
        builder.setIcon(R.drawable.logo);
        builder.setMessage("Welcome " + firstName + " " + lastName + "!\n\nYour username is: " +
               userName + "\nYour email is: " + email);
        builder.create().show();
    }

    /// User Registration Fail Handler ///
    public void handleCreateUserAccountFail(Object o) {
        binding.progressBar.setVisibility(View.GONE);
        String errorMessage = "";
        if (o != null) {
            errorMessage = o.toString();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_registration_fail));
        builder.setPositiveButton("OK", ((dialog, which) -> registerDialog()));
        builder.setIcon(R.drawable.logo);
        builder.setMessage(errorMessage);
        builder.create().show();
    }

    public void goToTripFollowerActivity(View v) {
        if (!NetworkChecker.hasNetworkConnection(this)) {
            noNetworkFollowDialog();
            return;
        }

        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setGravity(Gravity.CENTER_HORIZONTAL);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_title));
        builder.setMessage(getString(R.string.dialog_follow_trip_body));
        builder.setIcon(R.drawable.logo);
        builder.setView(et);
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton("OK", ((dialog, which) -> {
            if (et.getText().toString().isEmpty()) {
                showEmptyTripIdDialog();
                return;
            }
            Intent intent = new Intent(this, TripFollowerActivity.class);
            intent.putExtra("TripId", et.getText().toString());
            startActivity(intent);
        }));
        builder.create().show();
    }

    private boolean checkAppPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST);
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[] {
                        Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
                return false;
            }
        }

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION}, BACKGROUND_LOCATION_REQUEST);
            return false;
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_REQUEST) {
            if (permissions[0].equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkAppPermission();
                    return;
                } else {
                    Toast.makeText(this, "Location Permission not Granted",
                            Toast.LENGTH_SHORT).show();
                }
            }
        }

        if (requestCode == NOTIFICATION_REQUEST) {
            if (permissions[0].equals(Manifest.permission.POST_NOTIFICATIONS)) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkAppPermission();
                    return;
                } else {
                    Toast.makeText(this, "Notification Permission not Granted",
                            Toast.LENGTH_SHORT).show();
                }
            }
        }

        if (requestCode == BACKGROUND_LOCATION_REQUEST) {
            if (permissions[0].equals(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                if (grantResults[0]== PackageManager.PERMISSION_GRANTED) {
                    initTrip();
                } else {
                    Toast.makeText(this, "Background Location Permission not Granted",
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    public void initTrip() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setGravity(Gravity.CENTER_HORIZONTAL);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_title));
        builder.setMessage(getString(R.string.dialog_start_trip_body));
        builder.setIcon(R.drawable.logo);
        builder.setView(et);
        builder.setPositiveButton("OK", ((dialog, which) -> {
            if (et.getText().toString().isEmpty()) {
                showEmptyTripIdDialog();
                return;
            }
            Intent intent = new Intent(this, TripLeadActivity.class);
            intent.putExtra("TripId", et.getText().toString());
            intent.putExtra("UserName", username);
            intent.putExtra("FirstName", firstName);
            intent.putExtra("LastName", lastName);
            startActivity(intent);
        }));
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setNeutralButton(getString(R.string.generate), null);

        AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            String generatedId = makeTripId();
            et.setText(generatedId);
        });
    }

    /// Generate random trip id
    private String makeTripId() {
        String ALLOWED_CHARACTERS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        final Random random = new Random();
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; ++i)
            sb.append(ALLOWED_CHARACTERS.charAt(random.nextInt(ALLOWED_CHARACTERS.length())));
        sb.append("-");
        for (int i = 0; i < 5; ++i)
            sb.append(ALLOWED_CHARACTERS.charAt(random.nextInt(ALLOWED_CHARACTERS.length())));
        return sb.toString();
    }

    private void showEmptyTripIdDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_title));
        builder.setMessage(getString(R.string.dialog_empty_trip_id));
        builder.setIcon(R.drawable.logo);
        builder.setPositiveButton("OK", null);
        builder.create().show();
    }

    private void noNetworkLoginDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Follow Me - No Network");
        builder.setMessage("No network connection - cannot login now");
        builder.setIcon(R.drawable.logo);
        builder.setPositiveButton("OK", null);
        builder.create().show();
    }

    private void noNetworkStartTripDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Follow Me - No Network");
        builder.setMessage("No network connection - cannot start trip  now");
        builder.setIcon(R.drawable.logo);
        builder.setPositiveButton("OK", null);
        builder.create().show();
    }

    private void noNetworkRegisterDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Follow Me - No Network");
        builder.setMessage("No network connection - cannot create user account now");
        builder.setIcon(R.drawable.logo);
        builder.setPositiveButton("OK", null);
        builder.create().show();
    }

    private void noNetworkFollowDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Follow Me - No Network");
        builder.setMessage("No network connection - cannot follow the trip now");
        builder.setIcon(R.drawable.logo);
        builder.setPositiveButton("OK", null);
        builder.create().show();
    }

    private void gpsNotEnabledDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Follow Me - Cannot follow!");
        builder.setMessage("GPS is not enabled. Please enable GPS by turning on \"Use location\" in" +
                "the Location settings to continue.");
        builder.setIcon(R.drawable.logo);
        builder.setNegativeButton(getString(R.string.cancel), null);
        builder.setPositiveButton("Go to Location Settings", ((dialog, which) -> {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }));
        builder.create().show();
    }
}