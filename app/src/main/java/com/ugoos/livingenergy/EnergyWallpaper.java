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
  final int COLS=300,ROWS=155,COUNT=COLS*ROWS;
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
    String vs="precision highp float;attribute vec3 a;uniform float t;uniform float pixelScale;varying vec3 color;varying float opacity;"+
      "float wave(vec2 p){"+
      "float far=1.-smoothstep(.08,.80,p.y);"+
      "return (.075+.035*far)*sin(p.x*10.5+t*.42+p.y*4.)"+
      "+(.047+.010*far)*sin(p.x*18.5-p.y*7.-t*.59)"+
      "+.014*sin(p.x*39.+p.y*13.+t*.88);"+
      "}"+
      "void main(){"+
      "vec2 p=a.xy;float depth=p.y;"+
      "float h=wave(p);"+
      "float x=(p.x-.5)*2.55*(.98-.16*depth);"+
      "float y=-.92+depth*.92+h*(.90-.48*depth);"+
      "gl_Position=vec4(x,y,0.,1.);"+
      "float foreground=1.-depth;"+
      "gl_PointSize=clamp((2.8+5.2*foreground)*pixelScale,2.,12.);"+
      "float streak=pow(max(0.,sin(p.x*17.+depth*13.+t*.22)),22.);"+
      "float cyan=.5+.5*sin(p.x*11.+depth*7.+t*.24);"+
      "float violet=.5+.5*sin(p.x*8.-depth*14.-t*.17+2.);"+
      "float warm=pow(max(0.,sin(p.x*19.+depth*23.-t*.31)),32.)*.34;"+
      "vec3 blue=vec3(.015,.25,.99),aqua=vec3(.02,.90,1.),purple=vec3(.44,.09,.98),pink=vec3(1.,.14,.72);"+
      "color=mix(blue,aqua,cyan);color=mix(color,purple,violet*.62);"+
      "color=mix(color,pink,streak*.55);"+
      "color=mix(color,vec3(1.,.72,.34),warm);"+
      "color*=.86+.72*streak+.13*sin(t*.7+p.x*31.+depth*19.);"+
      "opacity=(.70+.27*foreground)*(.78+.22*fract(abs(a.z)*1.73));"+
      "}";
    String fs="precision mediump float;varying vec3 color;varying float opacity;"+
      "void main(){vec2 q=gl_PointCoord-.5;float r=length(q)*2.;"+
      "float core=1.-smoothstep(.20,.63,r);"+
      "float halo=(1.-smoothstep(.24,1.,r))*.42;"+
      "gl_FragColor=vec4(color*(.75+.60*core),opacity*(core+halo));}";
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
   GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE);
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
