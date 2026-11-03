package com.ugoos.livingenergy;

import android.service.wallpaper.WallpaperService;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceHolder;
import java.io.InputStream;
import java.nio.*;
import java.util.Random;
import javax.microedition.khronos.egl.*;

public class EnergyWallpaper extends WallpaperService {
 @Override public Engine onCreateEngine(){return new OceanEngine();}
 class OceanEngine extends Engine {
  final HandlerThread thread=new HandlerThread("LivingOceanGPU");
  Handler handler;
  volatile boolean visible=false,ready=false;
  EGL10 egl;EGLDisplay display;EGLContext context;EGLSurface surface;
  int backgroundProgram,bubbleProgram,texture,photoTexture;
  FloatBuffer quad,points;
  long start;
  final int COUNT=3200;
  final Runnable frame=new Runnable(){public void run(){if(!visible)return;try{if(ready)draw();}catch(Exception error){Log.e("LivingOcean","GPU frame failed",error);ready=false;}if(visible)handler.postDelayed(this,16);}};
  OceanEngine(){
   thread.start();handler=new Handler(thread.getLooper());
   float[] vertices={-1,-1,1,-1,-1,1,1,1};
   quad=buffer(vertices);
   float[] particles=new float[COUNT*4];Random rng=new Random(731);
   // Depth-weighted field: 76% distant microbubbles, 19% mid-depth, 5% foreground.
   float[] centers={.07f,.19f,.35f,.52f,.71f,.88f,.96f};
   for(int i=0;i<COUNT;i++){
     float layer=rng.nextFloat();
     float plume=centers[rng.nextInt(centers.length)];
     float spread=layer<.76f?.16f:layer<.95f?.09f:.045f;
     float x=rng.nextFloat()<.42f?rng.nextFloat():
       plume+(float)rng.nextGaussian()*spread;
     particles[i*4]=Math.max(.005f,Math.min(.995f,x));
     particles[i*4+1]=rng.nextFloat();
     // z encodes optical depth: small values are distant, larger values near camera.
     particles[i*4+2]=layer<.76f?
       .025f+rng.nextFloat()*.24f:
       layer<.95f?.30f+rng.nextFloat()*.39f:
       .73f+rng.nextFloat()*.27f;
     particles[i*4+3]=rng.nextFloat();
   }
   points=buffer(particles);
  }
  FloatBuffer buffer(float[] a){ByteBuffer b=ByteBuffer.allocateDirect(a.length*4).order(ByteOrder.nativeOrder());FloatBuffer f=b.asFloatBuffer();f.put(a);f.position(0);return f;}
  @Override public void onSurfaceCreated(SurfaceHolder h){super.onSurfaceCreated(h);handler.post(()->{releaseGL();initGL();});}
  @Override public void onSurfaceChanged(SurfaceHolder h,int format,int w,int ht){super.onSurfaceChanged(h,format,w,ht);handler.post(()->{if(ready)GLES20.glViewport(0,0,w,ht);});}
  @Override public void onVisibilityChanged(boolean v){visible=v;handler.removeCallbacks(frame);if(v)handler.post(frame);}
  @Override public void onSurfaceDestroyed(SurfaceHolder h){visible=false;handler.removeCallbacks(frame);handler.post(this::releaseGL);super.onSurfaceDestroyed(h);}
  @Override public void onDestroy(){visible=false;handler.removeCallbacks(frame);handler.post(()->{releaseGL();thread.quitSafely();});super.onDestroy();}
  int shader(int type,String source){
   int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);
   int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);
   if(ok[0]==0)throw new RuntimeException(GLES20.glGetShaderInfoLog(s));
   return s;
  }
  int program(String vertex,String fragment){
   int p=GLES20.glCreateProgram();int v=shader(GLES20.GL_VERTEX_SHADER,vertex),f=shader(GLES20.GL_FRAGMENT_SHADER,fragment);
   GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);
   int[] ok=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);
   if(ok[0]==0)throw new RuntimeException(GLES20.glGetProgramInfoLog(p));
   GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);return p;
  }
  void initGL(){
   try{
    egl=(EGL10)EGLContext.getEGL();display=egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
    egl.eglInitialize(display,new int[2]);
    EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
    int[] configAttrs={0x3040,4,EGL10.EGL_RED_SIZE,8,EGL10.EGL_GREEN_SIZE,8,EGL10.EGL_BLUE_SIZE,8,EGL10.EGL_NONE};
    egl.eglChooseConfig(display,configAttrs,configs,1,count);
    if(count[0]<1)throw new RuntimeException("No GLES2 config");
    context=egl.eglCreateContext(display,configs[0],EGL10.EGL_NO_CONTEXT,new int[]{0x3098,2,EGL10.EGL_NONE});
    surface=egl.eglCreateWindowSurface(display,configs[0],getSurfaceHolder(),null);
    if(!egl.eglMakeCurrent(display,surface,surface,context))throw new RuntimeException("EGL makeCurrent failed");
    String vertex="attribute vec2 a; varying vec2 uv; void main(){uv=(a+1.0)*0.5;gl_Position=vec4(a,0.,1.);}";
    String safeFragment="precision highp float;varying vec2 uv;uniform sampler2D tex;uniform float t;" +
      "float wave(vec2 p){return .42*sin(p.x*12.+p.y*7.+t*.61)+.26*sin(p.x*27.-p.y*13.-t*.79)+.17*sin(p.x*51.+p.y*23.+t*.44);}" +
      "void main(){" +
      "vec2 p=vec2(uv.x,1.-uv.y);float d=p.y;" +
      "vec3 col=mix(vec3(.012,.27,.43),vec3(.001,.012,.052),pow(d,.75));" +
      "float near=1.-smoothstep(.025,.33,d);" +
      "vec2 q=vec2(p.x*1.65,p.y*1.45);float w=wave(q);" +
      "vec2 uv1=fract(q+vec2(t*.047,t*.018)+vec2(w*.026));" +
      "vec2 uv2=fract(q*1.93+vec2(-t*.034,t*.039));" +
      "float h1=texture2D(tex,uv1).r;float h2=texture2D(tex,uv2).r;" +
      "float h=h1*.58+h2*.42;" +
      "float e=.003;" +
      "float dx=texture2D(tex,fract(uv1+vec2(e,0.))).r-texture2D(tex,fract(uv1-vec2(e,0.))).r;" +
      "float dy=texture2D(tex,fract(uv1+vec2(0.,e))).r-texture2D(tex,fract(uv1-vec2(0.,e))).r;" +
      "vec3 n=normalize(vec3(-dx*24.-cos(q.x*12.+t*.61)*.24,-dy*24.,1.));" +
      "float spec=pow(max(0.,dot(n,normalize(vec3(.18,.30,1.)))),28.);" +
      "float caustic=pow(max(0.,h+.13*w-.46)*2.1,3.);" +
      "col+=near*(.16*spec+.23*caustic)*vec3(.43,.84,.97);" +
      "float sky=(.78+.22*cos((p.x-.5)*3.0+sin(t*.09)*.12))*exp(-d*3.8);" +
      "col+=sky*vec3(.12,.19,.20);" +
      "float beams=0.;" +
      "for(int i=0;i<15;i++){" +
      "float k=float(i);float origin=(k+.5)/15.;" +
      "float slope=.13*sin(k*2.39)+.035*cos(k*4.17);" +
      "float wobble=sin(t*.21+k*1.7)*.012+sin(t*.12+k*.7)*.008;" +
      "float pos=origin+slope*d+wobble+sin(d*4.+t*.18+k)*.007;" +
      "float diff=p.x-pos;" +
      "float width=.0025+d*.0035+.0008*mod(k,3.);" +
      "float beam=exp(-diff*diff/width);" +
      "beams+=beam*(.57+.28*sin(t*.31+k*1.3))*(1.-smoothstep(.15,.95,d));" +
      "}" +
      "col+=beams*.064*vec3(.49,.83,.99);" +
      "float gold=(.65+.35*cos((p.x-.45)*2.4))*exp(-d*7.);" +
      "col+=gold*.025*vec3(1.,.78,.41);" +
      "gl_FragColor=vec4(clamp(col,0.,1.),1.);" +
      "}";
    String fragment="precision highp float;varying vec2 uv;uniform sampler2D tex;uniform sampler2D photo;uniform float t;" +
      "float wave(vec2 q){return .42*sin(q.x*14.+q.y*7.+t*.48)+.24*sin(q.x*27.-q.y*12.-t*.65)+.13*sin(q.x*52.+q.y*19.+t*.82);}" +
      "void main(){" +
      "vec2 p=vec2(uv.x,1.-uv.y);float d=p.y;" +
      "vec2 q=vec2(p.x*1.5,d*1.6);float w=wave(q);" +
      "vec2 flow=fract(q+vec2(t*.029,t*.016));" +
      "float n1=texture2D(tex,flow).r;" +
      "float n2=texture2D(tex,fract(flow*1.91+vec2(-t*.024,t*.035))).r;" +
      "float e=.004;" +
      "float dx=texture2D(tex,fract(flow+vec2(e,0.))).r-texture2D(tex,fract(flow-vec2(e,0.))).r;" +
      "float dy=texture2D(tex,fract(flow+vec2(0.,e))).r-texture2D(tex,fract(flow-vec2(0.,e))).r;" +
      "vec2 refractOffset=vec2(dx,dy)*(.032*exp(-d*3.)+.003);" +
      "vec2 sampleUV=clamp(uv+refractOffset+vec2(w*.003*exp(-d*3.),0.),vec2(.003),vec2(.997));" +
      "vec3 photographic=texture2D(photo,sampleUV).rgb;" +
      "vec3 deep=mix(vec3(.006,.16,.25),vec3(.001,.007,.029),pow(d,.85));" +
      "vec3 col=mix(photographic,deep,.12+.26*smoothstep(.25,1.,d));" +
      "float surface=1.-smoothstep(.045,.31,d);" +
      "float center=exp(-pow((p.x-.52)*1.55,4.));" +
      "float skylight=center*exp(-d*4.3);" +
      "col+=skylight*vec3(.14,.17,.10);" +
      "float glitter=pow(max(0.,n1*.62+n2*.38+.12*w-.47)*2.1,4.);" +
      "float folds=pow(max(0.,sin(q.x*44.+w*3.+t*.6)*sin(q.y*21.-t*.43)),6.);" +
      "col+=surface*(glitter*.31+folds*.15)*mix(vec3(.37,.83,1.),vec3(1.,.78,.38),.64*center);" +
      "float sunSpec=pow(max(0.,1.-abs(p.x-.52)*1.5),4.)*exp(-d*11.);" +
      "col+=sunSpec*.16*vec3(1.,.79,.43);" +
      "float rays=0.;" +
      "for(int i=0;i<32;i++){" +
      "float k=float(i);float origin=.06+fract(k*.6180339)*.88;" +
      "float slope=(fract(k*.754877)-.5)*.18;" +
      "float pos=origin+slope*d+sin(t*.18+k*2.3)*.006;" +
      "float diff=p.x-pos;" +
      "float width=.00020+.00095*d+.0002*fract(k*.37);" +
      "float strength=.32+.5*fract(k*.4142);" +
      "rays+=exp(-diff*diff/width)*strength;" +
      "}" +
      "col+=rays*.011*center*exp(-d*2.1)*vec3(.85,.81,.63);" +
      "col=mix(col,col*vec3(.79,.93,1.),smoothstep(.24,1.,d)*.20);" +
      "gl_FragColor=vec4(clamp(col,0.,1.),1.);" +
      "}";
    try{backgroundProgram=program(vertex,fragment);}catch(Exception shaderError){Log.e("LivingOcean","Advanced water shader failed, using safe renderer",shaderError);backgroundProgram=program(vertex,safeFragment);}
    String bubbleVertex="attribute vec4 a;uniform float t;uniform float scale;varying float alpha;varying float seed;varying float depth;"+
      "void main(){float velocity=.018+a.z*.11+a.w*.025;float y=fract(a.y+t*velocity);"+
      "float drift=(.003+a.z*.014)*sin(t*(.3+a.w*.6)+a.y*9.)+(.002+a.z*.008)*sin(t*.19+a.w*24.);"+
      "float x=a.x+drift;gl_Position=vec4(x*2.-1.,y*2.-1.,0.,1.);"+
      "float r=mix(1.25,24.,pow(a.z,2.65));gl_PointSize=max(1.,r*scale);"+
      "alpha=mix(.11,.71,pow(a.z,.85));seed=a.w;depth=a.z;}";
    String bubbleFrag="precision mediump float;varying float alpha;varying float seed;varying float depth;"+
      "void main(){vec2 q=gl_PointCoord-.5;"+
      "q.x*=1.+.10*sin(seed*31.);q.y*=1.+.13*cos(seed*43.);"+
      "float r=length(q)*2.;float irregular=.025*sin(atan(q.y,q.x)*5.+seed*19.);"+
      "float edge=1.-smoothstep(.76+irregular,.97+irregular,r);"+
      "float shell=smoothstep(.66,.87,r)*(1.-smoothstep(.91,1.,r));"+
      "float glint=exp(-length((q-vec2(-.20,-.23))*9.)*length((q-vec2(-.20,-.23))*9.));"+
      "float secondary=exp(-length((q-vec2(.25,.18))*15.)*length((q-vec2(.25,.18))*15.));"+
      "float far=1.-smoothstep(.12,.38,depth);"+
      "float haze=(1.-smoothstep(.15,.98,r))*.20*far;"+
      "float opacity=(shell*.64+glint*.72+secondary*.22+(.07+.12*depth)*edge+haze)*alpha;"+
      "gl_FragColor=vec4(mix(vec3(.45,.73,.85),vec3(.89,.97,1.),glint*.8+depth*.15),opacity*edge);}";
    bubbleProgram=program(bubbleVertex,bubbleFrag);
    int[] ids=new int[1];GLES20.glGenTextures(1,ids,0);texture=ids[0];
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
    // Deterministic seamless multi-frequency water-height texture.
    final int n=256;
    Bitmap water=Bitmap.createBitmap(n,n,Bitmap.Config.ARGB_8888);
    int[] pixels=new int[n*n];
    for(int y=0;y<n;y++)for(int x=0;x<n;x++){
      double u=x*2*Math.PI/n,v=y*2*Math.PI/n;
      double wave=.5+.17*Math.sin(3*u+2*Math.sin(2*v))
        +.12*Math.sin(5*v-2*u)+.08*Math.sin(11*u+7*v)
        +.06*Math.cos(17*v-9*u)+.04*Math.sin(23*u+19*v);
      int z=Math.max(0,Math.min(255,(int)(wave*255)));
      pixels[y*n+x]=android.graphics.Color.rgb(z,z,z);
    }
    water.setPixels(pixels,0,n,0,0,n,n);
    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,water,0);
    water.recycle();
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_REPEAT);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_REPEAT);
    // The existing photographic ocean asset is a stable fallback-independent visual layer.
    int[] photoIds=new int[1];GLES20.glGenTextures(1,photoIds,0);photoTexture=photoIds[0];
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,photoTexture);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
    Bitmap ocean=null;
    String oceanAsset="ocean_bg.jpg";
    try{getAssets().open("ocean_new.jpg").close();oceanAsset="ocean_new.jpg";}catch(java.io.IOException ignored){}
    try(InputStream stream=getAssets().open(oceanAsset)){
      ocean=BitmapFactory.decodeStream(stream);
      if(ocean==null)throw new RuntimeException("Cannot decode ocean asset");
      GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,ocean,0);
      Log.i("LivingOcean","Loaded ocean image: "+oceanAsset);
    }catch(Exception assetError){
      Log.e("LivingOcean","Photo asset unavailable; using solid fallback",assetError);
      int[] fallbackPixel={android.graphics.Color.rgb(5,45,75)};
      Bitmap fallback=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888);
      fallback.setPixels(fallbackPixel,0,1,0,0,1,1);
      GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,fallback,0);fallback.recycle();
    }finally{if(ocean!=null)ocean.recycle();}
    start=SystemClock.uptimeMillis();ready=true;
   }catch(Exception error){Log.e("LivingOcean","GPU initialization failed",error);ready=false;releaseGL();}
  }
  void draw(){
   if(!ready)return;
   int[] viewport=new int[4];GLES20.glGetIntegerv(GLES20.GL_VIEWPORT,viewport,0);
   if(viewport[2]==0||viewport[3]==0){android.graphics.Rect r=getSurfaceHolder().getSurfaceFrame();GLES20.glViewport(0,0,r.width(),r.height());}
   float t=(SystemClock.uptimeMillis()-start)/1000f;
   GLES20.glClearColor(0,0,.05f,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
   GLES20.glUseProgram(backgroundProgram);
   int loc=GLES20.glGetAttribLocation(backgroundProgram,"a");
   quad.position(0);GLES20.glEnableVertexAttribArray(loc);GLES20.glVertexAttribPointer(loc,2,GLES20.GL_FLOAT,false,0,quad);
   GLES20.glUniform1f(GLES20.glGetUniformLocation(backgroundProgram,"t"),t);
   GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
   GLES20.glUniform1i(GLES20.glGetUniformLocation(backgroundProgram,"tex"),0);
   GLES20.glActiveTexture(GLES20.GL_TEXTURE1);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,photoTexture);
   GLES20.glUniform1i(GLES20.glGetUniformLocation(backgroundProgram,"photo"),1);
   GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(loc);
   GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
   GLES20.glUseProgram(bubbleProgram);
   loc=GLES20.glGetAttribLocation(bubbleProgram,"a");
   points.position(0);GLES20.glEnableVertexAttribArray(loc);GLES20.glVertexAttribPointer(loc,4,GLES20.GL_FLOAT,false,0,points);
   GLES20.glUniform1f(GLES20.glGetUniformLocation(bubbleProgram,"t"),t);
   GLES20.glUniform1f(GLES20.glGetUniformLocation(bubbleProgram,"scale"),Math.max(1f,viewport[2]/1920f));
   GLES20.glDrawArrays(GLES20.GL_POINTS,0,COUNT);
   GLES20.glDisableVertexAttribArray(loc);GLES20.glDisable(GLES20.GL_BLEND);
   if(!egl.eglSwapBuffers(display,surface)){Log.e("LivingOcean","EGL swap failed: "+egl.eglGetError());ready=false;}
  }
  void releaseGL(){
   ready=false;
   if(egl==null)return;
   if(display!=null&&display!=EGL10.EGL_NO_DISPLAY){
    egl.eglMakeCurrent(display,EGL10.EGL_NO_SURFACE,EGL10.EGL_NO_SURFACE,EGL10.EGL_NO_CONTEXT);
    if(surface!=null&&surface!=EGL10.EGL_NO_SURFACE)egl.eglDestroySurface(display,surface);
    if(context!=null&&context!=EGL10.EGL_NO_CONTEXT)egl.eglDestroyContext(display,context);
    egl.eglTerminate(display);
   }
   surface=null;context=null;display=null;egl=null;
  }
 }
}
