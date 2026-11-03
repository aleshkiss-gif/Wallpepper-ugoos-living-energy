package com.ugoos.livingenergy;
import android.service.wallpaper.WallpaperService;
import android.graphics.*;
import android.os.*;
import android.view.SurfaceHolder;
import android.content.SharedPreferences;
import java.util.Random;
public class EnergyWallpaper extends WallpaperService {
 @Override public Engine onCreateEngine(){return new EnergyEngine();}
 class EnergyEngine extends Engine {
  final Handler handler=new Handler(Looper.getMainLooper());
  final Paint p=new Paint(3);
  final Random r=new Random(913);
  final float[] xs=new float[650],ys=new float[650],ph=new float[650];
  boolean visible=false; float t=0; long last=0;
  final Runnable frame=this::draw;
  EnergyEngine(){for(int i=0;i<xs.length;i++){xs[i]=r.nextFloat();ys[i]=r.nextFloat();ph[i]=r.nextFloat()*6.283f;}}
  @Override public void onVisibilityChanged(boolean v){visible=v;handler.removeCallbacks(frame);if(v){last=0;handler.post(frame);}}
  @Override public void onSurfaceDestroyed(SurfaceHolder h){visible=false;handler.removeCallbacks(frame);super.onSurfaceDestroyed(h);}
  @Override public void onDestroy(){visible=false;handler.removeCallbacks(frame);super.onDestroy();}
  void draw(){
   if(!visible)return;
   long now=SystemClock.uptimeMillis();float dt=last==0?.016f:Math.min(.05f,(now-last)/1000f);last=now;
   SharedPreferences prefs=getSharedPreferences("energy",MODE_PRIVATE);
   float speed=(.3f+prefs.getInt("speed",45)/100f)*(prefs.getBoolean("dynamic",false)?1.8f:1f);
   float light=prefs.getInt("light",70)/100f;t+=dt*speed;
   Canvas c=null;
   try{
    c=getSurfaceHolder().lockCanvas();if(c==null)return;
    int w=c.getWidth(),h=c.getHeight();
    c.drawColor(Color.rgb(1,9,27));
    p.setStyle(Paint.Style.FILL);
    // Layered translucent silk ribbons with moving highlights and depth.
    final int layers=9;
    for(int layer=layers-1;layer>=0;layer--){
      float baseline=h*(.12f+layer*.105f);
      Path ribbon=new Path();
      Path crest=new Path();
      float amplitude=h*(.060f+layer*.003f);
      int step=Math.max(9,w/180);
      for(int x=0;x<=w+step;x+=step){
        float u=x/(float)w;
        float y=baseline+(float)Math.sin(u*7+t*(.4f+layer*.055f)+layer*.7f)*amplitude
          +(float)Math.sin(u*14-t*.3f+layer)*h*.025f;
        if(x==0){ribbon.moveTo(x,y);crest.moveTo(x,y);}
        else{ribbon.lineTo(x,y);crest.lineTo(x,y);}
      }
      float thickness=h*(.10f+layer*.003f);
      for(int x=w+step;x>=0;x-=step){
        float u=x/(float)w;
        float y=baseline+(float)Math.sin(u*7+t*(.4f+layer*.055f)+layer*.7f)*amplitude
          +(float)Math.sin(u*14-t*.3f+layer)*h*.025f;
        ribbon.lineTo(x,y+thickness);
      }
      ribbon.close();
      int a=(int)(light*(layer%2==0?125:105));
      p.setStyle(Paint.Style.FILL);
      p.setShader(new LinearGradient(0,baseline-h*.07f,0,baseline+thickness,
        new int[]{Color.argb(a,0,170,255),Color.argb((int)(light*78),0,65,190),
          Color.argb(12,0,15,75),Color.TRANSPARENT},
        new float[]{0f,.25f,.72f,1f},Shader.TileMode.CLAMP));
      c.drawPath(ribbon,p);p.setShader(null);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(Math.max(1.5f,w/1600f));
      p.setColor(Color.argb((int)(165*light),35,190,255));
      c.drawPath(crest,p);
      p.setStrokeWidth(Math.max(2f,w/480f));
      p.setColor(Color.argb((int)(28*light),0,130,255));
      c.drawPath(crest,p);
      p.setStyle(Paint.Style.FILL);
    }
    // Scattered light motes, with a gentle twinkle and drift.
    for(int i=0;i<xs.length;i++){
     float x=((xs[i]+t*(.007f+i%7*.002f))%1f)*w;
     float y=(ys[i]+(float)Math.sin(t+ph[i])*.025f)*h;
     float glow=(float)(.5+.5*Math.sin(t*2+ph[i]));
     p.setColor(Color.argb(Math.min(255,(int)((30+190*glow)*light)),30,185,255));
     c.drawCircle(x,y,Math.max(.7f,w/2300f)*(1+glow*1.7f),p);
    }
   }catch(Exception ignored){}finally{if(c!=null)try{getSurfaceHolder().unlockCanvasAndPost(c);}catch(Exception ignored){}}
   if(visible)handler.postDelayed(frame,16);
  }
 }
}