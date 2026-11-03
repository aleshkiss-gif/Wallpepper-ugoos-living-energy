package com.ugoos.livingenergy;
import android.service.wallpaper.WallpaperService;
import android.graphics.*;
import android.os.*;
import android.view.SurfaceHolder;
import android.content.SharedPreferences;
import java.util.Random;

public class EnergyWallpaper extends WallpaperService {
 @Override public Engine onCreateEngine(){return new OceanEngine();}
 class OceanEngine extends Engine {
  final Handler handler=new Handler(Looper.getMainLooper());
  final Paint paint=new Paint(3);
  final Random random=new Random(20261103);
  final int COUNT=220;
  final float[] x=new float[COUNT],y=new float[COUNT],radius=new float[COUNT],phase=new float[COUNT],rate=new float[COUNT];
  boolean visible=false;float time=0;long last=0;
  final Runnable frame=this::render;
  OceanEngine(){
   for(int i=0;i<COUNT;i++){
    x[i]=random.nextFloat();y[i]=random.nextFloat();phase[i]=random.nextFloat()*6.28318f;
    radius[i]=random.nextFloat();rate[i]=.013f+random.nextFloat()*.026f;
   }
  }
  @Override public void onVisibilityChanged(boolean v){visible=v;handler.removeCallbacks(frame);if(v){last=0;handler.post(frame);}}
  @Override public void onSurfaceDestroyed(SurfaceHolder h){visible=false;handler.removeCallbacks(frame);super.onSurfaceDestroyed(h);}
  @Override public void onDestroy(){visible=false;handler.removeCallbacks(frame);super.onDestroy();}
  void render(){
   if(!visible)return;
   long now=SystemClock.uptimeMillis();
   float dt=last==0?.016f:Math.min(.05f,(now-last)/1000f);last=now;
   SharedPreferences prefs=getSharedPreferences("energy",MODE_PRIVATE);
   float speed=(.3f+prefs.getInt("speed",45)/100f)*(prefs.getBoolean("dynamic",false)?1.8f:1f);
   float light=.65f+prefs.getInt("light",70)/100f*.5f;
   time+=dt*speed;
   Canvas c=null;
   try{
    c=getSurfaceHolder().lockCanvas();if(c==null)return;
    int w=c.getWidth(),h=c.getHeight();
    // Deep sea gradient with cyan water near the surface.
    paint.setStyle(Paint.Style.FILL);
    paint.setShader(new LinearGradient(0,0,0,h,new int[]{
      Color.rgb(21,118,163),Color.rgb(5,82,132),Color.rgb(2,43,87),Color.rgb(0,14,43)
    },new float[]{0f,.25f,.64f,1f},Shader.TileMode.CLAMP));
    c.drawRect(0,0,w,h,paint);paint.setShader(null);
    // Bright, cold sunlight with a barely warm white-gold center.
    float cx=w*(.51f+(float)Math.sin(time*.08f)*.025f);
    paint.setShader(new RadialGradient(cx,-h*.13f,h*.83f,
      new int[]{Color.argb((int)(190*light),255,247,219),
      Color.argb((int)(110*light),153,233,255),Color.argb(0,65,185,255)},
      new float[]{0f,.24f,1f},Shader.TileMode.CLAMP));
    c.drawRect(0,0,w,h,paint);paint.setShader(null);
    // Volumetric light shafts gently refracting in the water.
    for(int i=0;i<22;i++){
      float u=(i+.5f)/22f;
      float sway=(float)Math.sin(time*.17f+i*.8f)*w*.018f;
      float top=cx+(u-.5f)*w*.74f+sway;
      float bottom=cx+(u-.5f)*w*1.75f+sway*2f;
      float width=w*(.012f+(i%4)*.004f);
      Path ray=new Path();
      ray.moveTo(top-width*.25f,0);ray.lineTo(top+width*.25f,0);
      ray.lineTo(bottom+width*2.5f,h*.92f);ray.lineTo(bottom-width*2.5f,h*.92f);ray.close();
      paint.setShader(new LinearGradient(0,0,0,h*.92f,
       new int[]{Color.argb((int)(58*light),180,241,255),Color.argb((int)(19*light),92,210,255),Color.TRANSPARENT},
       null,Shader.TileMode.CLAMP));
      c.drawPath(ray,paint);paint.setShader(null);
    }
    // Moving ripples of the water surface.
    paint.setStyle(Paint.Style.STROKE);
    for(int j=0;j<10;j++){
      Path ripple=new Path();float yy=h*(.008f+j*.013f);
      for(int px=0;px<=w+20;px+=Math.max(8,w/150)){
       float fy=yy+(float)Math.sin(px/(float)w*25+time*.6f+j*.9f)*h*.005f;
       if(px==0)ripple.moveTo(px,fy);else ripple.lineTo(px,fy);
      }
      paint.setColor(Color.argb((int)(75*light),150,238,255));
      paint.setStrokeWidth(Math.max(1f,w/1400f));c.drawPath(ripple,paint);
    }
    paint.setStyle(Paint.Style.FILL);
    // Bubbles: glassy rims, transparent interiors and tiny highlights.
    for(int i=0;i<COUNT;i++){
      float yy=(y[i]-time*rate[i])%1.18f;
      if(yy<-.08f)yy+=1.18f;
      float xx=(x[i]+(float)Math.sin(time*.55f+phase[i]+yy*5f)*.018f)*w;
      float py=yy*h;
      float rr=(1.5f+radius[i]*radius[i]*11f)*w/1600f;
      int alpha=(int)(65+radius[i]*115);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(Math.max(.8f,rr*.17f));
      paint.setColor(Color.argb(alpha,160,232,255));c.drawCircle(xx,py,rr,paint);
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(Color.argb((int)(alpha*.72f),230,250,255));
      c.drawCircle(xx-rr*.36f,py-rr*.42f,Math.max(.6f,rr*.18f),paint);
    }
   }catch(Exception ignored){}finally{if(c!=null)try{getSurfaceHolder().unlockCanvasAndPost(c);}catch(Exception ignored){}}
   if(visible)handler.postDelayed(frame,16);
  }
 }
}