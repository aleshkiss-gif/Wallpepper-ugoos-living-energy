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
  final float[] xs=new float[260],ys=new float[260],ph=new float[260];
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
    for(int layer=0;layer<7;layer++){
      Path wave=new Path();float baseline=h*(.13f+layer*.115f);
      wave.moveTo(0,h);
      for(int x=0;x<=w+24;x+=Math.max(8,w/160)){
       float u=x/(float)w;
       float y=baseline+(float)Math.sin(u*7+t*(.4f+layer*.055f)+layer*.7f)*h*.075f+(float)Math.sin(u*14-t*.3f+layer)*h*.025f;
       wave.lineTo(x,y);
      }
      wave.lineTo(w,h);wave.close();
      p.setShader(new LinearGradient(0,baseline-h*.12f,0,baseline+h*.25f,new int[]{Color.argb((int)(90*light),0,160,255),Color.argb(45,0,45,150),Color.TRANSPARENT},null,Shader.TileMode.CLAMP));
      c.drawPath(wave,p);p.setShader(null);
      p.setColor(Color.argb((int)(150*light),15,190,255));p.setStrokeWidth(Math.max(2,w/1300f));p.setStyle(Paint.Style.STROKE);
      c.drawPath(wave,p);p.setStyle(Paint.Style.FILL);
    }
    for(int i=0;i<xs.length;i++){
     float x=((xs[i]+t*(.007f+i%7*.002f))%1f)*w;
     float y=(ys[i]+(float)Math.sin(t+ph[i])*.025f)*h;
     float glow=(float)(.5+.5*Math.sin(t*2+ph[i]));
     p.setColor(Color.argb(Math.min(255,(int)((30+190*glow)*light)),30,185,255));
     c.drawCircle(x,y,Math.max(1,w/1600f)*(1+glow*2),p);
    }
   }catch(Exception ignored){}finally{if(c!=null)try{getSurfaceHolder().unlockCanvasAndPost(c);}catch(Exception ignored){}}
   if(visible)handler.postDelayed(frame,16);
  }
 }
}