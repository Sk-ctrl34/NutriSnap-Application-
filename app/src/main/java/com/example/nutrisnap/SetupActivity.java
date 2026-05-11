package com.example.nutrisnap;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import android.content.Intent;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SetupActivity extends Activity {

 Spinner diet;
 EditText calories;
 Button save;

 FirebaseFirestore db;
 FirebaseAuth auth;

 @Override
 protected void onCreate(Bundle b) {

  super.onCreate(b);
  setContentView(R.layout.activity_setup);

  diet = findViewById(R.id.diet);
  calories = findViewById(R.id.calories);
  save = findViewById(R.id.save);

  db = FirebaseFirestore.getInstance();
  auth = FirebaseAuth.getInstance();

  String[] goals = {
          "Weight Loss",
          "Healthy Lifestyle",
          "Fruit & Vegetable Balance"
  };

  ArrayAdapter<String> adapter =
          new ArrayAdapter<>(
                  this,
                  android.R.layout.simple_spinner_dropdown_item,
                  goals);

  diet.setAdapter(adapter);

  save.setOnClickListener(v -> savePreferences());
 }

 private void savePreferences() {

  String calText = calories.getText().toString().trim();

  if (calText.isEmpty()) {

   Toast.makeText(this,
           "Enter calorie goal",
           Toast.LENGTH_SHORT).show();
   return;
  }

  int cal = Integer.parseInt(calText);

  String goal = diet.getSelectedItem().toString();

  Map<String,Object> user = new HashMap<>();

  user.put("dietGoal",goal);
  user.put("dailyCalories",cal);
  user.put("weeklyFeedbackEnabled",true);

  String uid = auth.getCurrentUser().getUid();

  db.collection("users")
          .document(uid)
          .set(user)
          .addOnSuccessListener(unused -> {

           Toast.makeText(this,
                   "Preferences saved",
                   Toast.LENGTH_SHORT).show();

           startActivity(new Intent(
                   SetupActivity.this,
                   HomeActivity.class));

           finish();
          });
 }
}