package com.example.nutrisnap;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.*;
import android.content.*;
import android.graphics.PorterDuff;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;

import java.util.*;

public class CalendarActivity extends Activity {

    CalendarView calendarView;

    FirebaseFirestore db;
    FirebaseAuth auth;

    int calorieGoal = 2000;
    String userGoal = "healthy";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_calendar);

        calendarView = findViewById(R.id.calendarView);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        setupNavigation();
        setActivePage();
        loadUserGoal();

        calendarView.setOnDateChangeListener((view, year, month, dayOfMonth) -> {

            Calendar cal = Calendar.getInstance();
            cal.set(year, month, dayOfMonth);

            showPopupForDate(cal.getTime());
        });
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

    private String format(double value) {
        return String.format(Locale.getDefault(), "%.2f", value);
    }

    private void showPopupForDate(Date date) {

        String uid = auth.getCurrentUser().getUid();

        Calendar selected = Calendar.getInstance();
        selected.setTime(date);

        db.collection("food_logs")
                .whereEqualTo("user", uid)
                .get()
                .addOnSuccessListener(query -> {

                    double calories = 0, fat = 0, carbs = 0, protein = 0;

                    StringBuilder logs = new StringBuilder();

                    for (DocumentSnapshot doc : query) {

                        Date d = doc.getDate("date");
                        if (d == null) continue;

                        Calendar logCal = Calendar.getInstance();
                        logCal.setTime(d);

                        boolean sameDay =
                                logCal.get(Calendar.YEAR) == selected.get(Calendar.YEAR)
                                        && logCal.get(Calendar.DAY_OF_YEAR) == selected.get(Calendar.DAY_OF_YEAR);

                        if (!sameDay) continue;

                        String food = doc.getString("food");

                        double c = getVal(doc, "calories");
                        double f = getVal(doc, "fat");
                        double cb = getVal(doc, "carbs");
                        double p = getVal(doc, "protein");

                        calories += c;
                        fat += f;
                        carbs += cb;
                        protein += p;

                        logs.append(food)
                                .append("\nCalories: ").append(format(c))
                                .append("\nFat: ").append(format(f))
                                .append(" | Carbs: ").append(format(cb))
                                .append(" | Protein: ").append(format(p))
                                .append("\n\n");
                    }

                    if (logs.length() == 0) {
                        new AlertDialog.Builder(this)
                                .setTitle("Food Logs")
                                .setMessage("No logs for this day.")
                                .setPositiveButton("OK", null)
                                .show();
                        return;
                    }

                    loadHistoryForDate(selected.getTime(), logs, calories, fat, carbs, protein);
                });
    }

    private String formatUKTime(Date date) {
        java.text.SimpleDateFormat sdf =
                new java.text.SimpleDateFormat("EEE dd MMM yyyy HH:mm:ss", Locale.UK);

        sdf.setTimeZone(TimeZone.getTimeZone("Europe/London"));

        return sdf.format(date);
    }
    private void loadHistoryForDate(Date date, StringBuilder logs,
                                    double calories, double fat, double carbs, double protein) {

        String uid = auth.getCurrentUser().getUid();

        db.collection("user_history")
                .whereEqualTo("user", uid)
                .get()
                .addOnSuccessListener(query -> {

                    StringBuilder historyText = new StringBuilder();

                    Date firstDate = null;

                    for (DocumentSnapshot doc : query) {

                        Date updated = doc.getDate("updatedAt");
                        if (updated == null) continue;

                        if (firstDate == null || updated.before(firstDate)) {
                            firstDate = updated;
                        }

                        Calendar cal1 = Calendar.getInstance();
                        Calendar cal2 = Calendar.getInstance();

                        cal1.setTime(updated);
                        cal2.setTime(date);

                        boolean sameDay =
                                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
                                        && cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);

                        if (sameDay) {

                            String goal = doc.getString("goal");
                            Long cal = doc.getLong("dailyCalories");

                            historyText.append("Changed to → ")
                                    .append(goal)
                                    .append(" | ")
                                    .append(cal)
                                    .append(" kcal\n")
                                    .append("Time: ")
                                    .append(formatUKTime(updated))
                                    .append("\n\n");
                        }
                    }
                    String feedback = "";

                    for (DocumentSnapshot doc : query) {

                        Date d = doc.getDate("date");
                        if (d == null) continue;

                        Calendar logCal = Calendar.getInstance();
                        logCal.setTime(d);

                        Calendar selectedCal = Calendar.getInstance();
                        selectedCal.setTime(date);

                        boolean sameDay =
                                logCal.get(Calendar.YEAR) == selectedCal.get(Calendar.YEAR)
                                        && logCal.get(Calendar.DAY_OF_YEAR) == selectedCal.get(Calendar.DAY_OF_YEAR);

                        if (!sameDay) continue;

                        String savedFeedback = doc.getString("feedback");

                        if (savedFeedback != null && !savedFeedback.isEmpty()) {
                            feedback = savedFeedback;
                            break;
                        }
                    }


                    if (feedback.isEmpty()) {
                        feedback = generateAIFeedback(calories, fat, carbs, protein);
                    }

                    String message =
                            "Tracking Started: " + (firstDate != null ? formatUKTime(firstDate) : "N/A") + "\n\n"
                                    + logs.toString()
                                    + "\nTotal Calories: " + format(calories) + " kcal\n"
                                    + "\nDiet Goal: " + userGoal + "\n\n"
                                    + historyText.toString()
                                    + "\n--- AI Feedback ---\n"
                                    + feedback;

                    new AlertDialog.Builder(this)
                            .setTitle("Food Logs")
                            .setMessage(message)
                            .setPositiveButton("OK", null)
                            .show();
                });
    }

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
            text += "Protein is naturally low in fruits and vegetables. Consider adding legumes if needed.\n\n";
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

    private double getVal(DocumentSnapshot doc, String key) {
        Double v = doc.getDouble(key);
        return v != null ? v : 0;
    }

    private void setupNavigation() {

        findViewById(R.id.navHome).setOnClickListener(v ->
                startActivity(new Intent(this, HomeActivity.class)));

        findViewById(R.id.navCapture).setOnClickListener(v ->
                startActivity(new Intent(this, CaptureActivity.class)));

        findViewById(R.id.navAssist).setOnClickListener(v ->
                startActivity(new Intent(this, FeedbackActivity.class)));

        findViewById(R.id.navGuide).setOnClickListener(v -> {});
    }

    private void setActivePage() {

        ImageView icon = findViewById(R.id.iconGuide);
        TextView txt = findViewById(R.id.textGuide);

        icon.setColorFilter(
                getResources().getColor(R.color.primary),
                PorterDuff.Mode.SRC_IN
        );

        txt.setTextColor(
                getResources().getColor(R.color.primary)
        );
    }
}