package com.example.nutrisnap;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

public class SplashActivity extends Activity {

 @Override
 protected void onCreate(Bundle b) {
  super.onCreate(b);
  setContentView(R.layout.activity_splash);

  new Handler().postDelayed(() -> {
   startActivity(new Intent(this, LoginActivity.class));
   finish();
  }, 1800);
 }
}