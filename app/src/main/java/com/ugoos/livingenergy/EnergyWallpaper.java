package com.ugoos.livingenergy;

import android.service.wallpaper.WallpaperService;
import android.graphics.*;
import android.os.*;
import android.view.SurfaceHolder;
import android.content.SharedPreferences;
import java.util.Random;
import java.io.InputStream;

public class EnergyWallpaper extends WallpaperService {
 @Override public Engine onCreateEngine(){return new OceanEngine();}
 class OceanEngine extends Engine {
  final Handler handler=new Handler(Looper.getMainLooper());
  final Paint p=new Paint(3);
  final Random rng=new Random(20261103);
  static final int COUNT=2800;
  final float[] xx=new float[COUNT], yy=new float[COUNT], size=new float[COUNT], drift=new float[COUNT], rate=new float[COUNT];
  Bitmap background;
  boolean visible=false;
  long last=0;float t=0;
  final Runnable frame=this::render;
  OceanEngine(){
   for(int i=0;i<COUNT;i++){
    // Bubble streams are clustered into five vertical plumes.
    float center=(.12f+(i%5)*.19f);
    xx[i]=Math.max(0,Math.min(1,center+(rng.nextFloat()-.5f)*.17f));
    yy[i]=rng.nextFloat()*1.2f;
    size[i]=rng.nextFloat();
    drift[i]=rng.nextFloat()*6.28318f;
    rate[i]=.055f+rng.nextFloat()*.105f;
   }
   try(InputStream stream=getAssets().open("ocean_bg.jpg")){
    background=BitmapFactory.decodeStream(stream);
   }catch(Exception ignored){}
  }
  @Override public void onVisibilityChanged(boolean v){visible=v;handler.removeCallbacks(frame);if(v){last=0;handler.post(frame);}}
  @Override public void onSurfaceDestroyed(SurfaceHolder h){visible=false;handler.removeCallbacks(frame);super.onSurfaceDestroyed(h);}
  @Override public void onDestroy(){visible=false;handler.removeCallbacks(frame);if(background!=null){background.recycle();background=null;}super.onDestroy();}
  void render(){
   if(!visible)return;
   long now=SystemClock.uptimeMillis();
   float dt=last==0?.016f:Math.min(.05f,(now-last)/1000f);last=now;
   SharedPreferences prefs=getSharedPreferences("energy",MODE_PRIVATE);
   float speed=(.3f+prefs.getInt("speed",45)/100f)*(prefs.getBoolean("dynamic",false)?1.8f:1f);
   float light=.6f+prefs.getInt("light",70)/100f*.5f;
   t+=dt*speed;
   Canvas c=null;
   try{
    c=getSurfaceHolder().lockCanvas();if(c==null)return;
    int w=c.getWidth(),h=c.getHeight();
    if(background!=null){
      float bw=background.getWidth(),bh=background.getHeight();
      float scale=Math.max(w/bw,h/bh)*1.045f;
      float dw=bw*scale,dh=bh*scale;
      float sway=(float)Math.sin(t*.16f)*w*.005f;
      p.setAlpha(255);
      // Displace narrow horizontal strips to simulate underwater refraction.
      final int strips=100;
      for(int k=0;k<strips;k++){
        float top=k*h/(float)strips;
        float bottom=(k+1)*h/(float)strips+1;
        float y0=(top-(h-dh)/2)*bh/dh;
        float y1=(bottom-(h-dh)/2)*bh/dh;
        int sy0=Math.max(0,Math.min((int)bh-1,(int)y0));
        int sy1=Math.max(sy0+1,Math.min((int)bh,(int)Math.ceil(y1)));
        float distortion=(float)Math.sin(t*.95f+k*.21f)*w*(.003f+.004f*(1f-top/h));
        float ripple=(float)Math.sin(t*.52f+k*.065f)*w*.002f;
        c.drawBitmap(background,new Rect(0,sy0,(int)bw,sy1),
          new RectF((w-dw)/2+sway+distortion+ripple,top,(w+dw)/2+sway+distortion+ripple,bottom),p);
      }
    }else{
      p.setShader(new LinearGradient(0,0,0,h,Color.rgb(15,133,184),Color.rgb(0,12,44),Shader.TileMode.CLAMP));
      c.drawRect(0,0,w,h,p);p.setShader(null);
    }
    // Subtle moving cool-white surface glints, with a hint of gold.
    p.setShader(new RadialGradient(w*(.51f+(float)Math.sin(t*.12f)*.02f),-h*.16f,h*.72f,
      new int[]{Color.argb((int)(18*light),255,248,224),Color.argb((int)(12*light),155,226,255),Color.TRANSPARENT},
      null,Shader.TileMode.CLAMP));
    c.drawRect(0,0,w,h*.65f,p);p.setShader(null);
    // Soft animated shafts of sunlight layered above the image.
    p.setStyle(Paint.Style.FILL);
    for(int j=0;j<14;j++){
      float center=w*(.46f+(j-7)*.033f);
      float sway=(float)Math.sin(t*.4f+j*.71f)*w*.024f;
      Path ray=new Path();
      ray.moveTo(center-w*.007f+sway,0);
      ray.lineTo(center+w*.007f+sway,0);
      ray.lineTo(center+(j-7)*w*.045f+w*.045f+sway,h*.88f);
      ray.lineTo(center+(j-7)*w*.045f-w*.045f+sway,h*.88f);
      ray.close();
      p.setShader(new LinearGradient(0,0,0,h*.88f,
        new int[]{Color.argb((int)(28*light),202,242,255),Color.argb((int)(9*light),142,220,255),Color.TRANSPARENT},
        null,Shader.TileMode.CLAMP));
      c.drawPath(ray,p);p.setShader(null);
    }
    // Fine microbubbles: a mixture of points and refractive transparent rings.
    for(int i=0;i<COUNT;i++){
      float y=(yy[i]-t*rate[i])%1.2f;if(y<-.07f)y+=1.2f;
      float x=xx[i]+(float)Math.sin(t*.65f+drift[i]+y*7f)*.013f;
      float px=x*w,py=y*h;
      float r=(.6f+size[i]*size[i]*5.0f)*w/1920f;
      int a=(int)((70+size[i]*115)*light);
      if(size[i]<.73f){
        p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(Math.min(255,a),185,237,255));
        c.drawCircle(px,py,Math.max(.45f,r),p);
      }else{
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(.75f,r*.17f));
        p.setColor(Color.argb(Math.min(255,a),163,231,255));
        c.drawCircle(px,py,r,p);
        p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(Math.min(255,a),250,253,255));
        c.drawCircle(px-r*.35f,py-r*.4f,Math.max(.45f,r*.17f),p);
      }
    }
    p.setStyle(Paint.Style.FILL);
   }catch(Exception ignored){}finally{if(c!=null)try{getSurfaceHolder().unlockCanvasAndPost(c);}catch(Exception ignored){}}
   if(visible)handler.postDelayed(frame,16);
  }
 }
}