package com.example.nutrisnap;

import android.app.*;
import android.os.*;
import android.widget.*;
import android.content.*;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.*;

public class SettingsActivity extends Activity {

    Spinner diet;
    EditText calories;
    Switch weekly;
    Button save, logout;

    FirebaseFirestore db;
    FirebaseAuth auth;

    String[] goals = {
            "Weight Loss",
            "Healthy Lifestyle",
            "Fruit & Vegetable Balance"
    };

    @Override
    protected void onCreate(Bundle b){

        super.onCreate(b);
        setContentView(R.layout.activity_settings);

        diet = findViewById(R.id.diet);
        calories = findViewById(R.id.calories);
        weekly = findViewById(R.id.weekly);
        save = findViewById(R.id.save);
        logout = findViewById(R.id.logout);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        setActiveGuide();

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_dropdown_item,
                        goals);

        diet.setAdapter(adapter);

        loadSettings();

        save.setOnClickListener(v -> updateSettings());

        logout.setOnClickListener(v -> {
            auth.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        setupNavigation();
    }

    private void setActiveGuide(){

        ImageView icon = findViewById(R.id.iconGuide);
        TextView label = findViewById(R.id.textGuide);

        icon.setColorFilter(getResources().getColor(R.color.primary));
        label.setTextColor(getResources().getColor(R.color.primary));
    }

    private void setupNavigation(){

        findViewById(R.id.navHome).setOnClickListener(v ->
                startActivity(new Intent(this, HomeActivity.class)));

        findViewById(R.id.navCapture).setOnClickListener(v ->
                startActivity(new Intent(this, CaptureActivity.class)));

        findViewById(R.id.navAssist).setOnClickListener(v ->
                startActivity(new Intent(this, FeedbackActivity.class)));

        findViewById(R.id.navGuide).setOnClickListener(v ->
                startActivity(new Intent(this, CalendarActivity.class)));
    }

    private void loadSettings(){

        db.collection("users")
                .document(auth.getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(doc -> {

                    if(doc.exists()){

                        Long cal = doc.getLong("dailyCalories");
                        if(cal != null)
                            calories.setText(cal + "");

                        Boolean w = doc.getBoolean("weeklyFeedbackEnabled");
                        if(w != null)
                            weekly.setChecked(w);

                        String goal = doc.getString("goal");

                        if(goal != null){

                            if(goal.equals("weight_loss")) {
                                diet.setSelection(0);
                            }
                            else if(goal.equals("healthy")) {
                                diet.setSelection(1);
                            }
                            else if(goal.equals("fruits")) {
                                diet.setSelection(2);
                            }
                        }
                    }
                });
    }

    private void updateSettings(){

        String selected = diet.getSelectedItem().toString();


        String goalValue = "healthy";

        if(selected.equals("Weight Loss")){
            goalValue = "weight_loss";
        }
        else if(selected.equals("Fruit & Vegetable Balance")){
            goalValue = "fruits";
        }
        else{
            goalValue = "healthy";
        }

        Map<String,Object> data = new HashMap<>();

        data.put("goal", goalValue);
        data.put("dailyCalories",
                Integer.parseInt(calories.getText().toString()));
        data.put("weeklyFeedbackEnabled",
                weekly.isChecked());

        db.collection("users")
                .document(auth.getCurrentUser().getUid())
                .set(data, SetOptions.merge());

        Toast.makeText(this,
                "Settings updated",
                Toast.LENGTH_SHORT).show();
    }
}