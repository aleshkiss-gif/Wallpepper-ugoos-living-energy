package com.ugoos.livingenergy;

import android.service.wallpaper.WallpaperService;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
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
  int backgroundProgram,bubbleProgram,texture;
  FloatBuffer quad,points;
  long start;
  final int COUNT=1250;
  final Runnable frame=new Runnable(){public void run(){if(!visible)return;try{if(ready)draw();}catch(Exception ignored){}if(visible)handler.postDelayed(this,16);}};
  OceanEngine(){
   thread.start();handler=new Handler(thread.getLooper());
   float[] vertices={-1,-1,1,-1,-1,1,1,1};
   quad=buffer(vertices);
   float[] particles=new float[COUNT*4];Random rng=new Random(73);
   for(int i=0;i<COUNT;i++){
    float center=.12f+(i%5)*.19f;
    particles[i*4]=Math.max(.01f,Math.min(.99f,center+(rng.nextFloat()-.5f)*.19f));
    particles[i*4+1]=rng.nextFloat();
    particles[i*4+2]=rng.nextFloat();
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
    String fragment="precision highp float; varying vec2 uv; uniform sampler2D tex; uniform float t;" +
      "float heightWave(vec2 p){return .48*sin(p.x*19.+p.y*12.+t*.42)+.24*sin(p.x*37.-p.y*17.-t*.59)+.13*sin(p.x*81.+p.y*24.+t*.31)+.09*sin(p.x*123.-t*.67);}" +
      "void main(){" +
      "vec2 p=vec2(uv.x,1.-uv.y); float d=clamp(p.y,0.,1.);" +
      "vec3 col=mix(vec3(.015,.31,.48),vec3(.002,.012,.065),pow(d,.82));" +
      "float h=heightWave(p);" +
      "float hx=heightWave(p+vec2(.002,0.))-heightWave(p-vec2(.002,0.));" +
      "float hy=heightWave(p+vec2(0.,.002))-heightWave(p-vec2(0.,.002));" +
      "vec3 n=normalize(vec3(-hx*18.,-hy*12.,1.));" +
      "float nearSurface=1.-smoothstep(.02,.33,d);" +
      "float fresnel=pow(1.-max(0.,n.z),2.);" +
      "float reflection=pow(max(0.,dot(n,normalize(vec3(.27,-.24,1.)))),18.);" +
      "float ripples=pow(max(0.,sin(p.x*46.+h*2.2+t*.3)*sin(p.x*73.-h*2.7-t*.4)),4.);" +
      "col+=nearSurface*(.09+.27*reflection+.12*ripples+.18*fresnel)*vec3(.48,.87,1.);" +
      "float sun=exp(-pow((p.x-.52+sin(t*.11)*.012)*1.9,2.))*exp(-d*5.8);" +
      "col+=sun*vec3(.22,.32,.31);" +
      "float beams=0.;" +
      "for(int i=0;i<14;i++){" +
      "float k=float(i);float angle=(k-6.5)*.105;" +
      "float center=.51+angle*(.13+d*.88)+sin(t*.25+k*1.41)*.019+sin(d*5.+t*.17+k)*.008;" +
      "float diff=p.x-center;" +
      "float soft=exp(-diff*diff/(.0012+.00035*mod(k,3.)));" +
      "beams+=soft*(.75+.25*sin(t*.38+k*1.21))*(1.-smoothstep(.17,.98,d));" +
      "}" +
      "col+=beams*.083*vec3(.48,.84,1.);" +
      "float caustic=pow(max(0.,sin(p.x*61.+h*3.2+t*.36)*sin(p.x*39.-h*2.3-t*.28)),5.);" +
      "col+=nearSurface*caustic*.09*vec3(.44,.86,1.);" +
      "gl_FragColor=vec4(clamp(col,0.,1.),1.);" +
      "}";
    backgroundProgram=program(vertex,fragment);
    String bubbleVertex="attribute vec4 a; uniform float t; uniform float scale; varying float alpha; varying float rim; void main(){float speed=.06+a.z*.13;float y=fract(a.y+t*speed);float x=a.x+sin(t*(.5+a.w)+a.y*9.)*.012;gl_Position=vec4(x*2.-1.,y*2.-1.,0.,1.);gl_PointSize=(1.5+a.z*a.z*9.)*scale;alpha=.25+a.z*.6;rim=a.z;}";
    String bubbleFrag="precision mediump float; varying float alpha;varying float rim;void main(){vec2 q=gl_PointCoord-.5;float r=length(q)*2.;float edge=smoothstep(.72,.93,r)*(1.-smoothstep(.94,1.,r));float center=(1.-smoothstep(.0,.65,r))*.2;float shine=1.-smoothstep(.04,.23,length(q-vec2(-.2,-.2)));float a=(edge+center+shine*.65)*alpha;gl_FragColor=vec4(.65,.9,1.,a);}";
    bubbleProgram=program(bubbleVertex,bubbleFrag);
    int[] ids=new int[1];GLES20.glGenTextures(1,ids,0);texture=ids[0];
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
    try(InputStream input=getAssets().open("ocean_bg.jpg")){
     Bitmap bitmap=BitmapFactory.decodeStream(input);GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0);bitmap.recycle();
    }
    start=SystemClock.uptimeMillis();ready=true;
   }catch(Exception ignored){ready=false;releaseGL();}
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
   GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);GLES20.glDisableVertexAttribArray(loc);
   GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
   GLES20.glUseProgram(bubbleProgram);
   loc=GLES20.glGetAttribLocation(bubbleProgram,"a");
   points.position(0);GLES20.glEnableVertexAttribArray(loc);GLES20.glVertexAttribPointer(loc,4,GLES20.GL_FLOAT,false,0,points);
   GLES20.glUniform1f(GLES20.glGetUniformLocation(bubbleProgram,"t"),t);
   GLES20.glUniform1f(GLES20.glGetUniformLocation(bubbleProgram,"scale"),Math.max(1f,viewport[2]/1920f));
   GLES20.glDrawArrays(GLES20.GL_POINTS,0,COUNT);
   GLES20.glDisableVertexAttribArray(loc);GLES20.glDisable(GLES20.GL_BLEND);
   egl.eglSwapBuffers(display,surface);
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
