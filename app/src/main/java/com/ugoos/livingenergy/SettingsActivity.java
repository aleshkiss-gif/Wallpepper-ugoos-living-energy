package com.ugoos.livingenergy;
import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
public class SettingsActivity extends Activity {
 @Override public void onCreate(Bundle b){super.onCreate(b);
 LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(32,32,32,32);
 TextView title=new TextView(this);title.setText("Living Energy 4K — настройки");title.setTextSize(24);l.addView(title);
 Switch dynamic=new Switch(this);dynamic.setText("Динамичная анимация");dynamic.setChecked(getSharedPreferences("energy",0).getBoolean("dynamic",false));dynamic.setOnCheckedChangeListener((v,on)->getSharedPreferences("energy",0).edit().putBoolean("dynamic",on).apply());l.addView(dynamic);
 TextView a=new TextView(this);a.setText("Скорость");l.addView(a);
 SeekBar speed=new SeekBar(this);speed.setProgress(getSharedPreferences("energy",0).getInt("speed",45));speed.setOnSeekBarChangeListener(listener("speed"));l.addView(speed);
 TextView c=new TextView(this);c.setText("Яркость");l.addView(c);
 SeekBar light=new SeekBar(this);light.setProgress(getSharedPreferences("energy",0).getInt("light",70));light.setOnSeekBarChangeListener(listener("light"));l.addView(light);
 Button apply=new Button(this);apply.setText("Установить живые обои");apply.setOnClickListener(v->{Intent i=new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,new android.content.ComponentName(this,EnergyWallpaper.class));startActivity(i);});l.addView(apply);
 setContentView(l);
 }
 SeekBar.OnSeekBarChangeListener listener(String key){return new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int n,boolean u){if(u)getSharedPreferences("energy",0).edit().putInt(key,n).apply();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}};}
}