package com.ugoos.livingenergy;

import android.service.wallpaper.WallpaperService;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceHolder;
import java.nio.*;
import javax.microedition.khronos.egl.*;

public class EnergyWallpaper extends WallpaperService {
 @Override public Engine onCreateEngine(){return new WaveEngine();}
 class WaveEngine extends Engine {
  final HandlerThread thread=new HandlerThread("ParticleWaveGPU");
  final int COLS=228,ROWS=108,COUNT=COLS*ROWS;
  Handler handler;
  volatile boolean visible=false,ready=false;
  EGL10 egl;EGLDisplay display;EGLContext context;EGLSurface surface;
  FloatBuffer points;
  int program;
  long start;
  int width=1920,height=1080;
  final Runnable frame=new Runnable(){@Override public void run(){
   if(!visible)return;
   try{if(ready)draw();}catch(Exception e){Log.e("ParticleWave","Frame failed",e);ready=false;}
   if(visible)handler.postDelayed(this,16);
  }};
  WaveEngine(){
   thread.start();handler=new Handler(thread.getLooper());
   float[] data=new float[COUNT*3];int k=0;
   for(int j=0;j<ROWS;j++)for(int i=0;i<COLS;i++){
    data[k++]=i/(float)(COLS-1);
    data[k++]=j/(float)(ROWS-1);
    data[k++]=(float)((Math.sin(i*17.3+j*13.7)*43758.5453)%1.);
   }
   ByteBuffer bb=ByteBuffer.allocateDirect(data.length*4).order(ByteOrder.nativeOrder());
   points=bb.asFloatBuffer();points.put(data);points.position(0);
  }
  @Override public void onSurfaceCreated(SurfaceHolder h){super.onSurfaceCreated(h);handler.post(()->{releaseGL();initGL();});}
  @Override public void onSurfaceChanged(SurfaceHolder h,int format,int w,int ht){super.onSurfaceChanged(h,format,w,ht);width=Math.max(1,w);height=Math.max(1,ht);handler.post(()->{if(ready)GLES20.glViewport(0,0,width,height);});}
  @Override public void onVisibilityChanged(boolean v){visible=v;handler.removeCallbacks(frame);if(v)handler.post(frame);}
  @Override public void onSurfaceDestroyed(SurfaceHolder h){visible=false;handler.removeCallbacks(frame);handler.post(this::releaseGL);super.onSurfaceDestroyed(h);}
  @Override public void onDestroy(){visible=false;handler.removeCallbacks(frame);handler.post(()->{releaseGL();thread.quitSafely();});super.onDestroy();}
  int shader(int type,String source){
   int id=GLES20.glCreateShader(type);GLES20.glShaderSource(id,source);GLES20.glCompileShader(id);
   int[] ok=new int[1];GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,ok,0);
   if(ok[0]==0)throw new RuntimeException("GLSL: "+GLES20.glGetShaderInfoLog(id));
   return id;
  }
  void initGL(){
   try{
    egl=(EGL10)EGLContext.getEGL();display=egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
    if(!egl.eglInitialize(display,new int[2]))throw new RuntimeException("EGL init");
    EGLConfig[] cfg=new EGLConfig[1];int[] count=new int[1];
    egl.eglChooseConfig(display,new int[]{0x3040,4,EGL10.EGL_RED_SIZE,8,EGL10.EGL_GREEN_SIZE,8,EGL10.EGL_BLUE_SIZE,8,EGL10.EGL_NONE},cfg,1,count);
    if(count[0]<1)throw new RuntimeException("No GLES2 config");
    context=egl.eglCreateContext(display,cfg[0],EGL10.EGL_NO_CONTEXT,new int[]{0x3098,2,EGL10.EGL_NONE});
    surface=egl.eglCreateWindowSurface(display,cfg[0],getSurfaceHolder(),null);
    if(!egl.eglMakeCurrent(display,surface,surface,context))throw new RuntimeException("eglMakeCurrent");
    String vs="precision highp float;\nattribute vec3 a;uniform float t;uniform float pixelScale;\nvarying vec3 color;varying float opacity;\nfloat wave(vec2 p){\nfloat d=p.y;\nfloat amp=mix(.205,.035,smoothstep(.0,1.,d));\nreturn amp*(.62*sin(p.x*12.2+d*8.2-t*.48)+.30*sin(p.x*21.7-d*12.8+t*.69)+.08*sin(p.x*37.+d*21.-t*.37));\n}\nvoid main(){\nvec2 p=a.xy;float d=p.y;\nfloat h=wave(p);\nfloat x=(p.x-.5)*2.11*(1.-.06*d);\nfloat y=-.97+1.20*d+h*(1.-.32*d);\ngl_Position=vec4(x,y,0.,1.);\nfloat near=1.-d;\ngl_PointSize=clamp((2.7+4.3*pow(near,1.1))*pixelScale,2.2,10.);\nfloat slope=(wave(p+vec2(.003,0.))-wave(p-vec2(.003,0.)))/.006;\nfloat ridge=pow(max(0.,.5+.5*cos(p.x*13.2+d*10.3-t*.44)),18.);\nfloat glimmer=pow(max(0.,sin(p.x*22.7-d*15.4+t*.65)),28.);\nfloat travelling=pow(max(0.,sin(p.x*7.4+d*11.6-t*.46)),20.);\nfloat spec=pow(max(0.,.92-.27*abs(slope)),6.)*(.34+.66*ridge);\nfloat shimmer=pow(max(0.,sin(t*1.5+p.x*81.+d*63.+a.z*9.)),12.);\nfloat cyan=.5+.5*sin(p.x*8.2+d*9.4-t*.19);\nfloat violet=.5+.5*sin(p.x*5.6-d*8.2+t*.16+1.6);\nvec3 deep=vec3(.018,.085,.58);\nvec3 electric=vec3(.01,.74,1.);\nvec3 purple=vec3(.44,.075,1.);\nvec3 pink=vec3(1.,.13,.74);\ncolor=mix(deep,electric,.26+.67*cyan);\ncolor=mix(color,purple,.13+.52*violet);\ncolor=mix(color,pink,.50*glimmer);\nfloat gold=travelling*(.15+.85*ridge);\ncolor=mix(color,vec3(1.,.72,.32),gold*.75);\nfloat light=.67+.92*spec+.85*ridge+.62*glimmer+.72*shimmer;\ncolor*=light;\nopacity=mix(.88,.54,d)*(.78+.22*fract(abs(a.z)*1.73));\n}";
    String fs="precision mediump float;\nvarying vec3 color;varying float opacity;\nvoid main(){\nvec2 q=gl_PointCoord-.5;float r=length(q)*2.;\nfloat core=1.-smoothstep(.22,.64,r);\nfloat halo=(1.-smoothstep(.18,1.,r))*.28;\ngl_FragColor=vec4(color*(.73+.63*core),opacity*(core+halo));\n}";
    int v=shader(GLES20.GL_VERTEX_SHADER,vs),fr=shader(GLES20.GL_FRAGMENT_SHADER,fs);
    program=GLES20.glCreateProgram();GLES20.glAttachShader(program,v);GLES20.glAttachShader(program,fr);GLES20.glLinkProgram(program);
    int[] ok=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);
    if(ok[0]==0)throw new RuntimeException("Link: "+GLES20.glGetProgramInfoLog(program));
    GLES20.glDeleteShader(v);GLES20.glDeleteShader(fr);
    android.graphics.Rect bounds=getSurfaceHolder().getSurfaceFrame();
    width=Math.max(1,bounds.width());height=Math.max(1,bounds.height());
    GLES20.glViewport(0,0,width,height);
    start=SystemClock.uptimeMillis();ready=true;
    Log.i("ParticleWave","GPU particle wave initialized, particles="+COUNT);
   }catch(Exception e){Log.e("ParticleWave","Init failed",e);ready=false;releaseGL();}
  }
  void draw(){
   float t=(SystemClock.uptimeMillis()-start)/1000f;
   GLES20.glViewport(0,0,width,height);
   GLES20.glClearColor(.001f,.002f,.014f,1f);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
   GLES20.glEnable(GLES20.GL_BLEND);
   GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
   GLES20.glUseProgram(program);
   int a=GLES20.glGetAttribLocation(program,"a");
   points.position(0);GLES20.glEnableVertexAttribArray(a);
   GLES20.glVertexAttribPointer(a,3,GLES20.GL_FLOAT,false,0,points);
   GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"t"),t);
   GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"pixelScale"),Math.max(1f,width/1920f));
   GLES20.glDrawArrays(GLES20.GL_POINTS,0,COUNT);
   GLES20.glDisableVertexAttribArray(a);GLES20.glDisable(GLES20.GL_BLEND);
   if(!egl.eglSwapBuffers(display,surface)){Log.e("ParticleWave","Swap failed "+egl.eglGetError());ready=false;}
  }
  void releaseGL(){
   ready=false;if(egl==null)return;
   if(display!=null&&display!=EGL10.EGL_NO_DISPLAY){
    egl.eglMakeCurrent(display,EGL10.EGL_NO_SURFACE,EGL10.EGL_NO_SURFACE,EGL10.EGL_NO_CONTEXT);
    if(surface!=null&&surface!=EGL10.EGL_NO_SURFACE)egl.eglDestroySurface(display,surface);
    if(context!=null&&context!=EGL10.EGL_NO_CONTEXT)egl.eglDestroyContext(display,context);
    egl.eglTerminate(display);
   }
   egl=null;display=null;context=null;surface=null;
  }
 }
}
