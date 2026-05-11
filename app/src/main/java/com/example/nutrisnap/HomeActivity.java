package com.example.nutrisnap;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import android.content.*;
import android.graphics.PorterDuff;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.*;

public class HomeActivity extends Activity {

    TextView dailyCaloriesText, weeklyCaloriesText, summary;
    TextView fatText, carbsText, proteinText;

    ProgressBar dailyProgress, weeklyProgress;
    ListView foodList;

    ArrayList<String> items = new ArrayList<>();
    ArrayAdapter<String> adapter;

    FirebaseFirestore db;
    FirebaseAuth auth;

    int calorieGoal = 2000;

    private String format(double value) {
        return String.format(Locale.getDefault(), "%.2f", value);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_home);

        dailyCaloriesText = findViewById(R.id.dailyCaloriesText);
        weeklyCaloriesText = findViewById(R.id.weeklyCaloriesText);

        fatText = findViewById(R.id.fatText);
        carbsText = findViewById(R.id.carbsText);
        proteinText = findViewById(R.id.proteinText);

        dailyProgress = findViewById(R.id.dailyProgress);
        weeklyProgress = findViewById(R.id.weeklyProgress);

        summary = findViewById(R.id.summary);
        foodList = findViewById(R.id.foodList);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, items);

        foodList.setAdapter(adapter);

        loadUserGoal();
        setupNavigation();
        setActivePage();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (auth.getCurrentUser() != null) {
            loadCalories();
        }
    }

    private void loadCalories() {

        String uid = auth.getCurrentUser().getUid();

        Calendar todayCal = Calendar.getInstance();

        Calendar weekStart = Calendar.getInstance();
        weekStart.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        weekStart.set(Calendar.HOUR_OF_DAY, 0);
        weekStart.set(Calendar.MINUTE, 0);
        weekStart.set(Calendar.SECOND, 0);
        weekStart.set(Calendar.MILLISECOND, 0);

        final double[] todayCalories = {0};
        final double[] todayFat = {0};
        final double[] todayCarbs = {0};
        final double[] todayProtein = {0};
        final double[] weekCalories = {0};

        items.clear();

        db.collection("food_logs")
                .get()
                .addOnSuccessListener(query -> {

                    for (DocumentSnapshot doc : query.getDocuments()) {

                        String docUser = doc.getString("user");


                        if (docUser == null || !docUser.equals(uid)) {
                            continue;
                        }

                        Date date = doc.getDate("date");
                        if (date == null) continue;

                        Calendar logCal = Calendar.getInstance();
                        logCal.setTime(date);

                        double cal = getVal(doc, "calories");
                        double fat = getVal(doc, "fat");
                        double carbs = getVal(doc, "carbs");
                        double protein = getVal(doc, "protein");

                        boolean sameDay =
                                logCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR)
                                        && logCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR);

                        if (sameDay) {
                            todayCalories[0] += cal;
                            todayFat[0] += fat;
                            todayCarbs[0] += carbs;
                            todayProtein[0] += protein;
                        }

                        if (!date.before(weekStart.getTime())) {
                            weekCalories[0] += cal;
                        }

                        if (sameDay) {

                            items.add(
                                    doc.getString("food") + " (" + format(cal) + " kcal)\n"
                                            + "Fat: " + format(fat) + "g | Carbs: " + format(carbs) + "g | Protein: " + format(protein) + "g"
                            );
                        }
                    }

                    adapter.notifyDataSetChanged();

                    dailyCaloriesText.setText(format(todayCalories[0]) + " kcal");
                    weeklyCaloriesText.setText("Weekly: " + format(weekCalories[0]) + " kcal");

                    fatText.setText("Fat: " + format(todayFat[0]) + " g");
                    carbsText.setText("Carbs: " + format(todayCarbs[0]) + " g");
                    proteinText.setText("Protein: " + format(todayProtein[0]) + " g");

                    generateSummary(todayCalories[0]);
                });
    }

    private double getVal(DocumentSnapshot doc, String key){
        Double v = doc.getDouble(key);
        return v != null ? v : 0;
    }

    private void generateSummary(double todayTotal) {

        if (todayTotal == 0) {
            summary.setText("No food logged today.");
        } else if (todayTotal > calorieGoal) {
            summary.setText("You exceeded today's calorie goal.");
        } else if (todayTotal < calorieGoal * 0.7) {
            summary.setText("You can add more healthy foods.");
        } else {
            summary.setText("Good job maintaining balance.");
        }
    }

    private void loadUserGoal() {
        db.collection("users")
                .document(auth.getCurrentUser().getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Long goal = doc.getLong("dailyCalories");
                        if (goal != null) calorieGoal = goal.intValue();
                    }
                });
    }

    private void setupNavigation() {
        findViewById(R.id.navCapture).setOnClickListener(v ->
                startActivity(new Intent(this, CaptureActivity.class)));

        findViewById(R.id.navAssist).setOnClickListener(v ->
                startActivity(new Intent(this, FeedbackActivity.class)));

        findViewById(R.id.navGuide).setOnClickListener(v ->
                startActivity(new Intent(this, CalendarActivity.class)));
    }

    private void setActivePage() {
        ImageView icon = findViewById(R.id.iconHome);
        TextView txt = findViewById(R.id.textHome);

        icon.setColorFilter(getResources().getColor(R.color.primary), PorterDuff.Mode.SRC_IN);
        txt.setTextColor(getResources().getColor(R.color.primary));
    }
}