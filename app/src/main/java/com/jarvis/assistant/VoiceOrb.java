package com.jarvis.assistant;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

final class VoiceOrb extends View {
    final MainActivity host;
    final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final float[] sx=new float[660], sy=new float[660], sz=new float[660];
    final Path rim = new Path();
    final RadialGradient glow = new RadialGradient(0,0,1f,
        new int[]{0xff174968,0xff103149,0xff0a1f32,0x00071728},
        new float[]{0,.48f,.76f,1},Shader.TileMode.CLAMP);
    float energy;
    VoiceOrb(MainActivity activity) {
        super(activity);
        host=activity;
        setContentDescription("Jarvis voice orb. Tap to speak. Hold for settings.");
        setFocusable(true);
        for(int i=0;i<sx.length;i++) {
            double y=1-2*(i+.5)/sx.length, ring=Math.sqrt(1-y*y), theta=i*2.39996323;
            sx[i]=(float)(ring*Math.cos(theta)); sy[i]=(float)y; sz[i]=(float)(ring*Math.sin(theta));
        }
    }
    @Override protected void onDraw(Canvas c) {
        float t=(SystemClock.uptimeMillis()%600000)/1000f;
        JarvisService s=host.service;
        boolean speaking=s!=null && s.speakingNow();
        boolean waiting=s!=null && (s.busy || s.renderingVoice);
        boolean listening=s!=null && s.active;
        float target=speaking ? .43f+.3f*(float)Math.sin(t*13) :
            (s!=null && s.listening && SystemClock.uptimeMillis()-s.levelAt<220 ? s.micLevel : 0);
        energy += (target-energy)*.22f;
        float radius=Math.min(getWidth(),getHeight())*(.30f+.036f*energy+.005f*(float)Math.sin(t*1.7));
        c.drawColor(0xff030810); c.save();
        c.translate(getWidth()/2f,getHeight()/2f);
        c.scale(radius,radius);
        paint.setStyle(Paint.Style.FILL); paint.setColor(-1); paint.setShader(glow);
        c.save(); c.scale(1.57f,1.57f); c.drawCircle(0,0,1,paint); c.restore(); paint.setShader(null);
        double angle=t*.18, cs=Math.cos(angle), sn=Math.sin(angle);
        for(int i=0;i<sx.length;i++) {
            float x=(float)(sx[i]*cs+sz[i]*sn), z=(float)(sz[i]*cs-sx[i]*sn);
            float depth=(z+1)*.5f;
            float ripple=1+.035f*(float)Math.sin(7*sy[i]+3*z+t*(waiting?4:1.3))*(.4f+energy*2);
            paint.setColor(Color.argb((int)(26+207*depth*depth),waiting?160:64,(int)(140+100*depth),255));
            c.drawCircle(x*ripple,sy[i]*ripple,.003f+.007f*depth,paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        for(int band=0;band<3;band++) {
            rim.rewind();
            for(int i=0;i<=144;i++) {
                double a=i*Math.PI/72, spread=.012+.07*energy;
                double r=.95+band*.027+spread*Math.sin(5*a+t*2+band)*Math.cos(3*a-t);
                float x=(float)(Math.cos(a)*r),y=(float)(Math.sin(a)*r);
                if(i==0) rim.moveTo(x,y); else rim.lineTo(x,y);
            }
            rim.close(); paint.setStrokeWidth(.0025f+energy*.003f);
            paint.setColor(Color.argb((listening||speaking?155:83)-band*23,waiting?164:82,219,255));
            c.drawPath(rim,paint);
        }
        c.restore();
        if(isShown()) postInvalidateDelayed(33);
    }
}
