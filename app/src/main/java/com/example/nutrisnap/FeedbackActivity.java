package com.example.nutrisnap;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import android.view.View;
import android.content.*;
import android.graphics.PorterDuff;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.google.firebase.firestore.SetOptions;

import java.util.*;

public class FeedbackActivity extends Activity {

    TextView summaryText;
    Button dailyBtn, weeklyBtn;

    Button openSettingsBtn;
    LinearLayout settingsLayout;

    Spinner diet;
    EditText calories;
    Switch weekly;
    Button save, logout;

    FirebaseFirestore db;
    FirebaseAuth auth;

    int calorieGoal = 2000;
    String userGoal = "healthy";

    @Override
    protected void onCreate(Bundle b) {

        super.onCreate(b);
        setContentView(R.layout.activity_feedback);

        summaryText = findViewById(R.id.summaryText);
        dailyBtn = findViewById(R.id.dailyBtn);
        weeklyBtn = findViewById(R.id.weeklyBtn);

        openSettingsBtn = findViewById(R.id.openSettingsBtn);
        settingsLayout = findViewById(R.id.settingsLayout);

        diet = findViewById(R.id.diet);
        calories = findViewById(R.id.calories);
        weekly = findViewById(R.id.weekly);
        save = findViewById(R.id.save);
        logout = findViewById(R.id.logout);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        setupNavigation();
        setActivePage();

        setupSettings();
        loadUserGoal();

        dailyBtn.setOnClickListener(v -> loadDailyData());

        weeklyBtn.setOnClickListener(v -> {
            if (!weekly.isChecked()) {
                Toast.makeText(this, "Enable weekly summary in settings", Toast.LENGTH_SHORT).show();
                return;
            }
            generateWeeklyFeedback();
        });

        openSettingsBtn.setOnClickListener(v -> {
            settingsLayout.setVisibility(
                    settingsLayout.getVisibility() == View.GONE ? View.VISIBLE : View.GONE
            );
        });
    }

//SETTINGS

    private void setupSettings(){

        String[] goals = {
                "Weight Loss",
                "Healthy Lifestyle",
                "Fruit & Vegetable Balance"
        };

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

        weekly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateWeeklyButton(isChecked);
        });
    }

    private void updateWeeklyButton(boolean enabled){
        weeklyBtn.setEnabled(enabled);
        weeklyBtn.setAlpha(enabled ? 1f : 0.5f);
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

                        if(w != null){
                            weekly.setChecked(w);
                            updateWeeklyButton(w); // 🔥 APPLY STATE
                        }

                        String goal = doc.getString("goal");

                        if(goal != null){

                            if(goal.equals("weight_loss")) diet.setSelection(0);
                            else if(goal.equals("healthy")) diet.setSelection(1);
                            else diet.setSelection(2);
                        }
                    }
                });
    }

    private void updateSettings(){

        String selected = diet.getSelectedItem().toString();

        String goalValue = "healthy";

        if(selected.equals("Weight Loss")) goalValue = "weight_loss";
        else if(selected.equals("Fruit & Vegetable Balance")) goalValue = "fruits";

        int calValue = Integer.parseInt(calories.getText().toString());

        Map<String,Object> data = new HashMap<>();

        data.put("goal", goalValue);
        data.put("dailyCalories", calValue);
        data.put("weeklyFeedbackEnabled", weekly.isChecked());

        db.collection("users")
                .document(auth.getCurrentUser().getUid())
                .set(data, SetOptions.merge());

        Map<String,Object> history = new HashMap<>();
        history.put("goal", goalValue);
        history.put("dailyCalories", calValue);
        history.put("updatedAt", new Date());
        history.put("user", auth.getCurrentUser().getUid());

        db.collection("user_history").add(history);

        updateWeeklyButton(weekly.isChecked());

        Toast.makeText(this, "Settings updated", Toast.LENGTH_SHORT).show();
    }

    // NAVIGATION

    private void setupNavigation() {

        findViewById(R.id.navHome).setOnClickListener(v ->
                startActivity(new Intent(this, HomeActivity.class)));

        findViewById(R.id.navCapture).setOnClickListener(v ->
                startActivity(new Intent(this, CaptureActivity.class)));

        findViewById(R.id.navAssist).setOnClickListener(v -> {});

        findViewById(R.id.navGuide).setOnClickListener(v ->
                startActivity(new Intent(this, CalendarActivity.class)));
    }

    private void setActivePage() {

        ImageView icon = findViewById(R.id.iconAssist);
        TextView txt = findViewById(R.id.textAssist);

        icon.setColorFilter(getResources().getColor(R.color.primary), PorterDuff.Mode.SRC_IN);
        txt.setTextColor(getResources().getColor(R.color.primary));
    }



    private void loadUserGoal() {

        db.collection("users")
                .document(auth.getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(doc -> {

                    if (doc.exists()) {

                        Long goal = doc.getLong("dailyCalories");
                        if (goal != null) calorieGoal = goal.intValue();

                        String g = doc.getString("goal");
                        if (g != null) userGoal = g;
                    }
                });
    }

    //  DAILY Logs

    private void loadDailyData() {

        String uid = auth.getCurrentUser().getUid();
        Calendar today = Calendar.getInstance();

        db.collection("food_logs")
                .whereEqualTo("user", uid)
                .get()
                .addOnSuccessListener(query -> {

                    double calories = 0, fat = 0, carbs = 0, protein = 0;

                    for (DocumentSnapshot doc : query) {

                        Date date = doc.getDate("date");
                        if (date == null) continue;

                        Calendar logCal = Calendar.getInstance();
                        logCal.setTime(date);

                        boolean sameDay =
                                logCal.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                                        && logCal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);

                        if (!sameDay) continue;

                        calories += getVal(doc, "calories");
                        fat += getVal(doc, "fat");
                        carbs += getVal(doc, "carbs");
                        protein += getVal(doc, "protein");
                    }

                    String feedback = generateAIFeedback(calories, fat, carbs, protein);

                    summaryText.setText(feedback);

                    Calendar todayDate = Calendar.getInstance();

                    Map<String, Object> feedbackData = new HashMap<>();
                    feedbackData.put("feedback", feedback);

                    db.collection("food_logs")
                            .whereEqualTo("user", uid)
                            .get()
                            .addOnSuccessListener(logs -> {

                                for (DocumentSnapshot foodDoc : logs) {

                                    Date logDate = foodDoc.getDate("date");
                                    if (logDate == null) continue;

                                    Calendar logCal = Calendar.getInstance();
                                    logCal.setTime(logDate);

                                    boolean sameDay =
                                            logCal.get(Calendar.YEAR) == todayDate.get(Calendar.YEAR)
                                                    && logCal.get(Calendar.DAY_OF_YEAR) == todayDate.get(Calendar.DAY_OF_YEAR);

                                    if (!sameDay) continue;

                                    foodDoc.getReference().update(feedbackData);
                                }
                            });
                });
    }

    //  FEEDBACK

    private double fiberEstimate(double carbs) {
        return carbs * 0.1;
    }

    private boolean waterRichCheck(double calories, double carbs) {
        return calories > 300 && carbs > 100;
    }
    private String generateAIFeedback(double calories, double fat, double carbs, double protein) {

        if (calories == 0) return "No food logged.";

        double ratio = calories / calorieGoal;

        String text = "";


        if (ratio < 0.4) {
            text += "Your intake is very low today. Try adding more fruits and vegetables to maintain energy levels.\n\n";
        }
        else if (ratio < 0.75) {
            text += "Your calorie intake is under the expected level for today. Consider adding more fruits and vegetables.\n\n";
        }
        else if (ratio <= 1.1) {
            text += "Good job! Your intake is well balanced today.\n\n";
        }
        else {
            text += "You have exceeded your calorie goal. Try lighter fruits and more water-rich vegetables.\n\n";
        }

        if (userGoal.equals("weight_loss")) {
            text += "Focus on low-calorie vegetables like cucumber and leafy greens.\n\n";
        } else if (userGoal.equals("fruits")) {
            text += "Maintain a good mix of fruits and vegetables for better nutrient diversity.\n\n";
        } else {
            text += "Maintain a balanced intake of fruits and vegetables for overall wellness.\n\n";
        }


        double carbRatio = carbs / (calorieGoal * 0.5);
        double proteinRatio = protein / (calorieGoal * 0.2);
        double fatRatio = fat / (calorieGoal * 0.3);


        if (carbRatio < 0.5) {
            text += "Your fruit-based carbohydrate intake is low. Try adding more fruits like bananas or apples.\n\n";
        } else if (carbRatio > 1.5) {
            text += "Your carbohydrate intake from fruits is quite high. Balance it with more vegetables.\n\n";
        } else {
            text += "Your carbohydrate intake from fruits looks balanced.\n\n";
        }


        if (proteinRatio < 0.3) {
            text += "Protein is naturally low in fruits and vegetables.\n\n";
        } else {
            text += "Your protein level is reasonable for a fruit and vegetable-based diet.\n\n";
        }


        if (fatRatio > 1.2) {
            text += "Fat intake is slightly high for a fruit-vegetable diet. Reduce oily additions.\n\n";
        } else {
            text += "Fat intake is appropriate for a fruit and vegetable-based diet.\n\n";
        }


        if (carbs > 200) {
            text += "Your natural sugar intake is high. Try adding more vegetables to balance it.\n\n";
        }

        if (fiberEstimate(carbs) < 10) {
            text += "Include more fiber-rich vegetables like carrots or greens.\n\n";
        }

        if (waterRichCheck(calories, carbs)) {
            text += "You can include more water-rich foods like cucumber or watermelon.\n\n";
        }

        if (ratio >= 0.8 && ratio <= 1.1) {
            text += "You're maintaining a healthy pattern—keep it consistent 👍";
        } else {
            text += "Small improvements can make your diet more balanced 👍";
        }

        return text;
    }

    private double getVal(DocumentSnapshot doc, String key){
        Double v = doc.getDouble(key);
        return v != null ? v : 0;
    }

    //  WEEKLY

    private void generateWeeklyFeedback() {

        String uid = auth.getCurrentUser().getUid();

        Calendar weekStart = Calendar.getInstance();
        weekStart.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        weekStart.set(Calendar.HOUR_OF_DAY, 0);
        weekStart.set(Calendar.MINUTE, 0);
        weekStart.set(Calendar.SECOND, 0);
        weekStart.set(Calendar.MILLISECOND, 0);

        Calendar now = Calendar.getInstance();
        if (now.before(weekStart)) {
            weekStart.add(Calendar.WEEK_OF_YEAR, -1);
        }

        db.collection("food_logs")
                .whereEqualTo("user", uid)
                .get()
                .addOnSuccessListener(query -> {

                    double totalCalories = 0;

                    for (DocumentSnapshot doc : query) {

                        Date date = doc.getDate("date");
                        if (date == null) continue;

                        if (!date.before(weekStart.getTime())) {
                            totalCalories += getVal(doc, "calories");
                        }
                    }

                    if (totalCalories == 0) {
                        summaryText.setText("No entries recorded this week.");
                        return;
                    }

                    if (totalCalories > calorieGoal * 7)
                        summaryText.setText("Weekly intake is higher than expected.\n\nTry reducing calories.");
                    else
                        summaryText.setText("Weekly intake looks balanced.\n\nKeep going!");
                });
    }
}