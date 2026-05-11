package com.example.nutrisnap;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.*;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.*;

import okhttp3.*;

public class CaptureActivity extends Activity {

 Button cameraBtn, galleryBtn;
 ImageView preview;
 TextView result;
 ProgressBar loading;

 FirebaseFirestore db;
 FirebaseAuth auth;

 Interpreter interpreter;
 List<String> labels = new ArrayList<>();

 int imageSize;

 private static final int CAMERA_REQUEST = 100;
 private static final int GALLERY_REQUEST = 200;

 String API_KEY = "ij3DRLtpMUrdf2SkIsU0qqHzyIdm5VoIYsxgdox0";

 @Override
 protected void onCreate(Bundle savedInstanceState) {

  super.onCreate(savedInstanceState);
  setContentView(R.layout.activity_capture);

  cameraBtn = findViewById(R.id.camera);
  galleryBtn = findViewById(R.id.gallery);
  preview = findViewById(R.id.preview);
  result = findViewById(R.id.result);
  loading = findViewById(R.id.loading);

  db = FirebaseFirestore.getInstance();
  auth = FirebaseAuth.getInstance();

  setupNavigation();
  setActivePage();

  loadMLModel();

  askPermission();

  cameraBtn.setOnClickListener(v -> openCamera());
  galleryBtn.setOnClickListener(v -> openGallery());
 }

 private void loadMLModel() {

  try {

   interpreter = new Interpreter(loadModelFile());

   imageSize =
           interpreter.getInputTensor(0).shape()[1];

   loadLabels();

  } catch (Exception e) {

   result.setText("Model Load Failed");
  }
 }

 private void askPermission() {

  if (ContextCompat.checkSelfPermission(
          this,
          Manifest.permission.CAMERA
  ) != PackageManager.PERMISSION_GRANTED) {

   ActivityCompat.requestPermissions(
           this,
           new String[]{Manifest.permission.CAMERA},
           1000
   );
  }
 }

 private void openCamera() {

  Intent i =
          new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

  startActivityForResult(i, CAMERA_REQUEST);
 }

 private void openGallery() {

  Intent i =
          new Intent(
                  Intent.ACTION_PICK,
                  MediaStore.Images.Media.EXTERNAL_CONTENT_URI
          );

  startActivityForResult(i, GALLERY_REQUEST);
 }

 @Override
 protected void onActivityResult(
         int requestCode,
         int resultCode,
         Intent data
 ) {

  super.onActivityResult(
          requestCode,
          resultCode,
          data
  );

  if (resultCode != RESULT_OK || data == null) return;

  try {

   Bitmap bitmap = null;

   if (requestCode == CAMERA_REQUEST) {

    bitmap =
            (Bitmap) data.getExtras().get("data");

   } else if (requestCode == GALLERY_REQUEST) {

    Uri uri = data.getData();

    bitmap =
            MediaStore.Images.Media.getBitmap(
                    getContentResolver(),
                    uri
            );
   }

   preview.setImageBitmap(bitmap);

   processImage(bitmap);

  } catch (Exception e) {

   result.setText("Error Processing Image");
  }
 }

 private void showNutritionData(
         String food,
         float confidence
 ) {

  Map<String,double[]> nutritionMap =
          new HashMap<>();

  nutritionMap.put("apple", new double[]{52,0.2,14,0.3});
  nutritionMap.put("banana", new double[]{89,0.3,23,1.1});
  nutritionMap.put("orange", new double[]{47,0.1,12,0.9});
  nutritionMap.put("mango", new double[]{60,0.4,15,0.8});
  nutritionMap.put("papaya", new double[]{43,0.3,11,0.5});
  nutritionMap.put("tomato", new double[]{18,0.2,3.9,0.9});
  nutritionMap.put("carrot", new double[]{41,0.2,10,0.9});
  nutritionMap.put("cucumber", new double[]{15,0.1,3.6,0.7});
  nutritionMap.put("grape", new double[]{69,0.2,18,0.7});
  nutritionMap.put("watermelon", new double[]{30,0.2,8,0.6});
  nutritionMap.put("cabbage", new double[]{25,0.1,6,1.3});
  nutritionMap.put("cauliflower", new double[]{25,0.3,5,1.9});
  nutritionMap.put("beetroot", new double[]{43,0.2,10,1.6});
  nutritionMap.put("strawberry", new double[]{32,0.3,8,0.7});
  nutritionMap.put("avocado", new double[]{160,15,9,2});
  nutritionMap.put("pepper", new double[]{20,0.2,4.6,0.9});
  nutritionMap.put("cherry", new double[]{50,0.3,12,1});
  nutritionMap.put("gooseberry", new double[]{44,0.6,10,0.9});
  nutritionMap.put("onion", new double[]{40,0.1,9.3,1.1});
  nutritionMap.put("lemon", new double[]{29,0.3,9,1.1});
  nutritionMap.put("guava", new double[]{68,1,14,2.6});
  nutritionMap.put("pear", new double[]{57,0.1,15,0.4});

  double[] values =
          nutritionMap.get(food.toLowerCase());

  if (values == null) {

   result.setText("Nutrition Data Not Found");
   return;
  }

  double calories = values[0];
  double fat = values[1];
  double carbs = values[2];
  double protein = values[3];

  String msg =
          "Food: " + food +
                  "\nConfidence: " +
                  (int)(confidence * 100) + "%" +
                  "\nCalories: " + calories + " kcal" +
                  "\nFat: " + fat + " g" +
                  "\nCarbs: " + carbs + " g" +
                  "\nProtein: " + protein + " g";

  result.setText(msg);

  new AlertDialog.Builder(this)
          .setTitle("Save Food?")
          .setMessage(msg)
          .setPositiveButton(
                  "Save",
                  (d,w)-> saveFood(
                          food,
                          calories,
                          fat,
                          carbs,
                          protein
                  )
          )
          .setNegativeButton(
                  "Cancel",
                  null
          )
          .show();
 }

 private void processImage(Bitmap originalBitmap) {

  loading.setVisibility(View.VISIBLE);

  Bitmap croppedBitmap = centerCropBitmap(originalBitmap);

  Bitmap bitmap = Bitmap.createScaledBitmap(
          croppedBitmap,
          imageSize,
          imageSize,
          true
  );

  ByteBuffer buffer = ByteBuffer.allocateDirect(
          4 * imageSize * imageSize * 3
  );

  buffer.order(ByteOrder.nativeOrder());

  int[] pixels = new int[imageSize * imageSize];

  bitmap.getPixels(
          pixels,
          0,
          imageSize,
          0,
          0,
          imageSize,
          imageSize
  );

  for (int pixel : pixels) {

   float r = ((pixel >> 16) & 0xFF) / 255f;
   float g = ((pixel >> 8) & 0xFF) / 255f;
   float b = (pixel & 0xFF) / 255f;

   buffer.putFloat(r);
   buffer.putFloat(g);
   buffer.putFloat(b);
  }

  int outputSize =
          interpreter.getOutputTensor(0).shape()[1];

  float[][] output =
          new float[1][outputSize];

  interpreter.run(buffer, output);

  int maxIndex = 0;
  float maxConfidence = 0;

  for (int i = 0; i < output[0].length; i++) {

   if (output[0][i] > maxConfidence) {

    maxConfidence = output[0][i];
    maxIndex = i;
   }
  }

  loading.setVisibility(View.GONE);

  if (maxConfidence < 0.65f) {

   result.setText(
           "Unable to detect confidently.\nTry centering food and improving lighting."
   );
   return;
  }

  String detectedFood = labels.get(maxIndex);

  showNutritionData(detectedFood, maxConfidence);
 }
 private Bitmap centerCropBitmap(Bitmap src) {

  int width = src.getWidth();
  int height = src.getHeight();

  int newDimension = Math.min(width, height);

  int xOffset = (width - newDimension) / 2;
  int yOffset = (height - newDimension) / 2;

  return Bitmap.createBitmap(
          src,
          xOffset,
          yOffset,
          newDimension,
          newDimension
  );
 }

 private void fetchNutritionFromAPI(
         String food,
         float confidence
 ) {

  OkHttpClient client =
          new OkHttpClient();

  String url =
          "https://api.nal.usda.gov/fdc/v1/foods/search?query="
                  + food +
                  "&api_key="
                  + API_KEY;

  Request request =
          new Request.Builder()
                  .url(url)
                  .build();

  client.newCall(request)
          .enqueue(new Callback() {

           @Override
           public void onFailure(
                   Call call,
                   IOException e
           ) {

            runOnUiThread(() -> {

             loading.setVisibility(View.GONE);

             result.setText("API Failed");
            });
           }

           @Override
           public void onResponse(
                   Call call,
                   Response response
           ) throws IOException {

            try {

             JSONObject obj =
                     new JSONObject(
                             response.body().string()
                     );

             JSONArray foods =
                     obj.getJSONArray("foods");

             JSONObject firstFood =
                     foods.getJSONObject(0);

             JSONArray nutrients =
                     firstFood.getJSONArray(
                             "foodNutrients"
                     );

             double calories = 0;
             double fat = 0;
             double carbs = 0;
             double protein = 0;

             for (int i = 0; i < nutrients.length(); i++) {

              JSONObject nutrient =
                      nutrients.getJSONObject(i);

              String name =
                      nutrient.getString(
                              "nutrientName"
                      );

              double value =
                      nutrient.getDouble(
                              "value"
                      );

              if (name.contains("Energy"))
               calories = value;

              if (name.contains("Total lipid"))
               fat = value;

              if (name.contains("Carbohydrate"))
               carbs = value;

              if (name.contains("Protein"))
               protein = value;
             }

             double finalCalories = calories;
             double finalFat = fat;
             double finalCarbs = carbs;
             double finalProtein = protein;

             runOnUiThread(() -> {

              loading.setVisibility(View.GONE);

              showNutritionPopup(
                      food,
                      confidence,
                      finalCalories,
                      finalFat,
                      finalCarbs,
                      finalProtein
              );
             });

            } catch (Exception e) {

             runOnUiThread(() -> {

              loading.setVisibility(View.GONE);

              result.setText("Nutrition Parse Error");
             });
            }
           }
          });
 }

 private void showNutritionPopup(
         String food,
         float confidence,
         double calories,
         double fat,
         double carbs,
         double protein
 ) {

  String msg =
          "Food: " + food +
                  "\nConfidence: " +
                  (int)(confidence * 100) + "%" +
                  "\nCalories: " + calories +
                  "\nFat: " + fat +
                  "\nCarbs: " + carbs +
                  "\nProtein: " + protein;

  result.setText(msg);

  new AlertDialog.Builder(this)
          .setTitle("Save Food?")
          .setMessage(msg)
          .setPositiveButton(
                  "Save",
                  (d,w)-> saveFood(
                          food,
                          calories,
                          fat,
                          carbs,
                          protein
                  )
          )
          .setNegativeButton(
                  "Cancel",
                  null
          )
          .show();
 }

 private void saveFood(
         String food,
         double calories,
         double fat,
         double carbs,
         double protein
 ) {

  Map<String,Object> map =
          new HashMap<>();

  map.put("food", food);
  map.put("calories", calories);
  map.put("fat", fat);
  map.put("carbs", carbs);
  map.put("protein", protein);
  map.put("date", new Date());
  map.put("user", auth.getCurrentUser().getUid());

  db.collection("food_logs")
          .add(map);
 }

 private MappedByteBuffer loadModelFile()
         throws IOException {

  AssetFileDescriptor file =
          getAssets().openFd("model.tflite");

  FileInputStream input =
          new FileInputStream(
                  file.getFileDescriptor()
          );

  FileChannel channel =
          input.getChannel();

  return channel.map(
          FileChannel.MapMode.READ_ONLY,
          file.getStartOffset(),
          file.getDeclaredLength()
  );
 }

 private void loadLabels()
         throws IOException {

  BufferedReader reader =
          new BufferedReader(
                  new InputStreamReader(
                          getAssets().open("labels.txt")
                  )
          );

  String line;

  while ((line = reader.readLine()) != null) {

   labels.add(line);
  }

  reader.close();
 }

 private void setupNavigation() {

  findViewById(R.id.navHome)
          .setOnClickListener(v ->
                  startActivity(
                          new Intent(this, HomeActivity.class)));

  findViewById(R.id.navCapture)
          .setOnClickListener(v -> {});

  findViewById(R.id.navAssist)
          .setOnClickListener(v ->
                  startActivity(
                          new Intent(this, FeedbackActivity.class)));

  findViewById(R.id.navGuide)
          .setOnClickListener(v ->
                  startActivity(
                          new Intent(this, CalendarActivity.class)));
 }

 private void setActivePage() {

  ImageView icon =
          findViewById(R.id.iconCapture);

  TextView txt =
          findViewById(R.id.textCapture);

  icon.setColorFilter(
          getResources().getColor(R.color.primary),
          PorterDuff.Mode.SRC_IN);

  txt.setTextColor(
          getResources().getColor(R.color.primary));
 }
}