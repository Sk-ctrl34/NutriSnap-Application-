package com.example.nutrisnap;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import android.content.Intent;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends Activity {

 EditText email,password;
 Button login,register;

 FirebaseAuth auth;
 FirebaseFirestore db;

 @Override
 protected void onCreate(Bundle b){

  super.onCreate(b);
  setContentView(R.layout.activity_login);

  email=findViewById(R.id.email);
  password=findViewById(R.id.password);
  login=findViewById(R.id.login);
  register=findViewById(R.id.register);

  auth=FirebaseAuth.getInstance();
  db=FirebaseFirestore.getInstance();

  login.setOnClickListener(v -> performLogin());

  register.setOnClickListener(v ->
          startActivity(new Intent(LoginActivity.this,RegisterActivity.class)));
 }

 private void performLogin(){

  String e=email.getText().toString().trim();
  String p=password.getText().toString().trim();

  if(e.isEmpty()){
   email.setError("Enter email");
   return;
  }

  if(p.isEmpty()){
   password.setError("Enter password");
   return;
  }

  auth.signInWithEmailAndPassword(e,p)
          .addOnCompleteListener(task -> {

           if(!task.isSuccessful()){

            Toast.makeText(LoginActivity.this,
                    "Login Failed",
                    Toast.LENGTH_SHORT).show();
            return;
           }

           if(auth.getCurrentUser()==null){
            Toast.makeText(LoginActivity.this,
                    "Authentication error",
                    Toast.LENGTH_SHORT).show();
            return;
           }

           checkSetup();
          });
 }

 private void checkSetup(){

  String uid=auth.getCurrentUser().getUid();

  db.collection("users")
          .document(uid)
          .get()
          .addOnSuccessListener(doc -> {

           if(doc.exists()){

            startActivity(new Intent(
                    LoginActivity.this,
                    HomeActivity.class));

           }else{

            startActivity(new Intent(
                    LoginActivity.this,
                    SetupActivity.class));
           }

           finish();
          })
          .addOnFailureListener(e ->
                  Toast.makeText(LoginActivity.this,
                          "Database error",
                          Toast.LENGTH_SHORT).show());
 }
}