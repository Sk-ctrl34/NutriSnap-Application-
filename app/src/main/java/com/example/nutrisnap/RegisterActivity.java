package com.example.nutrisnap;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import android.content.Intent;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.AuthResult;

public class RegisterActivity extends Activity {

 EditText email, password;
 Button register;
 FirebaseAuth auth;

 @Override
 protected void onCreate(Bundle b) {
  super.onCreate(b);
  setContentView(R.layout.activity_register);

  auth = FirebaseAuth.getInstance();

  email = findViewById(R.id.email);
  password = findViewById(R.id.password);
  register = findViewById(R.id.register);

  register.setOnClickListener(v -> {

   String userEmail = email.getText().toString().trim();
   String userPassword = password.getText().toString().trim();

   // Validation
   if (userEmail.isEmpty()) {
    email.setError("Enter email");
    return;
   }

   if (userPassword.isEmpty()) {
    password.setError("Enter password");
    return;
   }

   if (userPassword.length() < 6) {
    password.setError("Password must be at least 6 characters");
    return;
   }

   auth.createUserWithEmailAndPassword(userEmail, userPassword)
           .addOnCompleteListener(task -> {

            if (task.isSuccessful()) {

             Toast.makeText(RegisterActivity.this,
                     "Account created successfully",
                     Toast.LENGTH_SHORT).show();

             startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
             finish();

            } else {

             Toast.makeText(RegisterActivity.this,
                     "Error: " + task.getException().getMessage(),
                     Toast.LENGTH_LONG).show();
            }
           });
  });
 }
}